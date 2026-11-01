package com.ticketapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test

class CoreUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var originalWindow:TestWindow.Overrides?=null
    @Before fun compactWindow() {
        originalWindow=TestWindow.capture()
        TestWindow.resize(400,880)
        compose.waitUntil(90000){runCatching{compose.onAllNodesWithTag("phone-pane").fetchSemanticsNodes().isNotEmpty() && compose.activity.hasWindowFocus()}.getOrDefault(false)}
        if(compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Back").performClick()
        }
    }
    @After fun restoreWindow() { originalWindow?.let(TestWindow::restore) }
    @Test fun createTicketThenArchiveAndReopen() {
        compose.waitForIdle()
        compose.onNodeWithText("New ticket",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Title *").performTextInput("UI tested maintenance part")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("UI tested maintenance part").assertExists()
        compose.onNodeWithTag("ticket-form").performScrollToNode(hasText("Save ticket"))
        compose.onNodeWithText("Save ticket").performClick()
        compose.waitUntil(90000){compose.onAllNodesWithText("Change status").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("UI tested maintenance part").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Change status").performClick()
        compose.onNodeWithText("New status").performClick()
        compose.onNodeWithText("Ordered").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(60000){compose.onAllNodesWithText("Reopen").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Reopen").performClick()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Reopening reason *").performTextInput("Additional parts needed")
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(60000){compose.onAllNodesWithText("Change status").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Edit ticket").assertExists()
    }
}
