package com.ticketapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.ticketapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import kotlin.math.abs

/** Real window reconfiguration exercises Android recreation as well as Compose remeasurement. */
class FoldWorkspaceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as TicketApplication
    private val run=uid().take(6)
    private val prefix="Fold QA $run"
    private fun id(index:Int)="fold-$run-${index.toString().padStart(2,'0')}"
    private var originalWindow:TestWindow.Overrides?=null
    private fun window(width:Int,height:Int)=TestWindow.resize(width,height)
    private fun expanded() {
        compose.waitUntil(90000){runCatching{compose.onAllNodesWithTag("ticket-list-pane").fetchSemanticsNodes().isNotEmpty() && compose.activity.hasWindowFocus()}.getOrDefault(false)}
        compose.waitForIdle()
    }
    private fun compact() {
        compose.waitUntil(90000){runCatching{compose.onAllNodesWithTag("phone-pane").fetchSemanticsNodes().isNotEmpty() && compose.activity.hasWindowFocus()}.getOrDefault(false)}
    }
    private fun choose(index:Int) {
        compose.onNodeWithTag("workspace-ticket-list").performScrollToNode(hasTestTag("ticket-row-${id(index)}"))
        compose.onNodeWithTag("ticket-row-${id(index)}").performClick()
        compose.onNodeWithTag("ticket-row-${id(index)}").assertIsSelected()
    }
    private fun axis(tag:String)=compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun leftWidth()=compose.onNodeWithTag("ticket-list-pane").fetchSemanticsNode().boundsInRoot.width/compose.activity.resources.displayMetrics.density
    private fun rightWidth()=compose.onNodeWithTag("ticket-detail-pane").fetchSemanticsNode().boundsInRoot.width/compose.activity.resources.displayMetrics.density

    @Before fun prepare() {
        originalWindow=TestWindow.capture()
        runBlocking {
            app.repository.initialize()
            app.preferences.set("theme","Light")
            app.preferences.set("listPaneFraction","0.4")
            repeat(21) { i ->
                app.repository.save(id(i),false,0,mapOf("title" to "$prefix request ${i.toString().padStart(2,'0')}","description" to "Offline foldable test request $i","priority" to "Standard Leadtime"),null,emptyList())
            }
            app.repository.transition(id(20),0,Status.ORDERED)
        }
        window(768,896)
        expanded()
        compose.onNodeWithTag("workspace-search").performTextReplacement(prefix)
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
    }
    @After fun resetWindow() {
        originalWindow?.let(TestWindow::restore)
    }

    @Test fun allTicketsIndependentScrollingAndPinnedActions() {
        compose.onNodeWithTag("scope-All").assertIsSelected()
        choose(0)
        val left=compose.onNodeWithTag("ticket-list-pane").fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag("ticket-detail-pane").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right<right.left)
        val before=axis("workspace-ticket-list")
        compose.onNodeWithTag("ticket-detail-list").performScrollToNode(hasText("Activity timeline"))
        compose.onNodeWithText("Edit ticket").assertIsDisplayed()
        compose.onNodeWithText("Change status").assertIsDisplayed()
        assertEquals(before,axis("workspace-ticket-list"),0.001f)
        val detailBefore=axis("ticket-detail-list")
        // The ordered ticket must be available in the default All scope.
        compose.onNodeWithTag("workspace-ticket-list").performScrollToNode(hasTestTag("ticket-row-${id(20)}"))
        compose.onNodeWithTag("ticket-row-${id(20)}").assertExists()
        assertEquals(detailBefore,axis("ticket-detail-list"),0.001f)
        compose.onNodeWithTag("scope-Archived").performClick()
        compose.onNodeWithTag("ticket-row-${id(20)}").assertExists()
        compose.onNodeWithTag("ticket-row-${id(0)}").assertDoesNotExist()
        compose.onNodeWithTag("scope-All").performClick()
        choose(1)
        choose(0)
        // Each ticket keeps its own detail scroll position.
        compose.onNodeWithText("Activity timeline").assertIsDisplayed()
        compose.onNodeWithText("Edit ticket").assertIsDisplayed()
    }

    @Test fun dividerSupportsDragBoundsAndPersistentWidth() {
        choose(0)
        val before=leftWidth()
        compose.onNodeWithTag("pane-divider").performTouchInput { swipe(center,center+Offset(50f,0f),500) }
        assertTrue(leftWidth()>before+10)
        assertTrue(rightWidth()>=359.5f)
        compose.onNodeWithTag("pane-divider").performSemanticsAction(SemanticsActions.SetProgress){it(-99f)}
        assertEquals(260f,leftWidth(),1f)
        compose.onNodeWithTag("pane-divider").performSemanticsAction(SemanticsActions.SetProgress){it(99f)}
        assertEquals(360f,rightWidth(),1f)
        compose.onNodeWithTag("pane-divider").performSemanticsAction(SemanticsActions.SetProgress){it(0.43f)}
        compose.waitUntil(20000){runBlocking{abs((app.preferences.all()["listPaneFraction"]?.toFloatOrNull()?:0f)-0.43f)<0.001f}}
        val saved=leftWidth()
        compose.activityRule.scenario.recreate()
        expanded()
        assertEquals(saved,leftWidth(),1f)
        compose.onNodeWithTag("ticket-row-${id(0)}").assertIsSelected()
    }

    @Test fun foldingPreservesSelectionDraftAndNavigationConfirmation() {
        choose(0)
        compose.onNodeWithText("Edit ticket").performClick()
        val edited="$prefix edited without losing work"
        compose.onNodeWithText("Title *").performTextReplacement(edited)
        compose.onNodeWithText(edited).assertExists()
        window(400,880)
        compact()
        compose.onNodeWithTag("ticket-form").performScrollToNode(hasText("Title *"))
        compose.onNodeWithText(edited).assertExists()
        window(768,896)
        expanded()
        compose.onNodeWithTag("ticket-form").performScrollToNode(hasText("Title *"))
        compose.onNodeWithText(edited).assertExists()
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("workspace-ticket-list").performScrollToNode(hasTestTag("ticket-row-${id(1)}"))
        compose.onNodeWithTag("ticket-row-${id(1)}").performClick()
        compose.onNodeWithText("Leave this form?").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText(edited).assertExists()
        compose.onNodeWithText("Save ticket").assertIsDisplayed().performClick()
        compose.waitUntil(90000){compose.onAllNodesWithTag("ticket-detail-list").fetchSemanticsNodes().isNotEmpty()}
        runBlocking { assertEquals(edited,app.repository.snapshot().values.first{it.ticketId==id(0)&&it.fieldId=="title"}.value) }
        window(400,880)
        compact()
        compose.onAllNodesWithText(edited).onFirst().assertIsDisplayed()
        window(768,896)
        expanded()
        choose(1)
        compose.activityRule.scenario.recreate()
        expanded()
        compose.onNodeWithTag("ticket-row-${id(1)}").assertIsSelected()
    }
}
