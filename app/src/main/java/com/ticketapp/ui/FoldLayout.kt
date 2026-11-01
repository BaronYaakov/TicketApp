@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ticketapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ticketapp.data.*
import com.ticketapp.domain.*
import kotlin.math.roundToInt

@Composable
fun TicketBrowser(
    state:Snapshot,
    query:Query,
    listState:LazyListState,
    selectedId:String?,
    busy:Boolean,
    onQuery:(Query)->Unit,
    onSelect:(String)->Unit,
    onSort:(String,Boolean)->Unit
) {
    var filters by rememberSaveable { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    val tickets=remember(state,query) { selectTickets(state,query) }
    val sorts=state.fields.filter { !it.kind.media && it.kind!=FieldType.MULTI }.map { it.id to it.name }+
        listOf("created" to "Date created","updated" to "Last updated","due" to "Due date","status" to "Status","id" to "Ticket ID")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Tickets",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                Text("${tickets.size}",style=MaterialTheme.typography.labelLarge)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("All","Active","Archived").forEachIndexed { index,label ->
                    val chosen=when(index) { 0->query.includeArchived;1->!query.includeArchived&&!query.archived;else->!query.includeArchived&&query.archived }
                    SegmentedButton(
                        selected=chosen,
                        onClick={onQuery(query.copy(includeArchived=index==0,archived=index==2,statuses=emptySet(),overdue=false))},
                        shape=SegmentedButtonDefaults.itemShape(index,3),
                        modifier=Modifier.testTag("scope-$label"),
                        icon={}
                    ) { Text(label,style=MaterialTheme.typography.labelMedium) }
                }
            }
            OutlinedTextField(query.search,{onQuery(query.copy(search=it))},
                label={Text("Search tickets")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)},
                trailingIcon={if(query.search.isNotEmpty())IconButton({onQuery(query.copy(search=""))}){Icon(Icons.Outlined.Close,"Clear search")}},
                modifier=Modifier.fillMaxWidth().testTag("workspace-search"))
            Row(verticalAlignment=Alignment.CenterVertically) {
                val filtered=query.statuses.isNotEmpty() || query.filters.values.any { it.isNotBlank() } || query.overdue || query.createdFrom!=null || query.createdTo!=null || query.dueFrom!=null || query.dueTo!=null
                TextButton({filters=true}) { Icon(Icons.Outlined.FilterList,null,Modifier.size(18.dp));Text(if(filtered)" Filters •" else " Filters") }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton({sorting=true}) { Icon(Icons.Outlined.Sort,"Sort tickets") }
                    DropdownMenu(sorting,{sorting=false}) {
                        DropdownMenuItem(text={Text(if(query.descending)"Descending ↓" else "Ascending ↑")},onClick={onSort(query.sort,!query.descending);sorting=false})
                        HorizontalDivider()
                        sorts.forEach { (id,name) -> DropdownMenuItem(text={Text(name)},leadingIcon={if(id==query.sort)Icon(Icons.Outlined.Check,null)},onClick={onSort(id,query.descending);sorting=false}) }
                    }
                }
            }
            Text("${sorts.find { it.first==query.sort }?.second?:"Priority"} · ${if(query.descending)"Descending" else "Ascending"}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(selectedId!=null && tickets.none { it.id==selectedId }) {
            Text("The selected ticket is outside these results.",Modifier.padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            TextButton({onQuery(Query(includeArchived=true,sort=query.sort,descending=query.descending))}) { Text("Show all tickets") }
        }
        HorizontalDivider()
        LazyColumn(
            state=listState,
            modifier=Modifier.weight(1f).fillMaxWidth().testTag("workspace-ticket-list"),
            contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            if(tickets.isEmpty()) item {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("No matching tickets",style=MaterialTheme.typography.titleMedium)
                    Text("Try another search or filter. New requests appear here as soon as they are saved.")
                    TextButton({onQuery(Query(includeArchived=true,sort=query.sort,descending=query.descending))}) { Text("Show all tickets") }
                }
            }
            items(tickets,key={it.id}) { ticket ->
                TicketCard(state,ticket,isSelected=ticket.id==selectedId,compact=true,enabled=!busy) { onSelect(ticket.id) }
            }
        }
    }
    if(filters)FilterDialog(state,query,{filters=false}){onQuery(it);filters=false}
}

@Composable
fun PaneDivider(
    width:Float,fraction:Float,
    onResizing:(Boolean)->Unit,
    onChange:(Float)->Unit,
    onCommit:(Float)->Unit
) {
    val density=LocalDensity.current
    var dialog by rememberSaveable { mutableStateOf(false) }
    val listWidth=PaneSizing.listWidth(width,fraction)
    val available=width-PaneSizing.DIVIDER
    val range=PaneSizing.MIN_LIST/available .. (available-PaneSizing.MIN_DETAIL)/available
    val actual=listWidth/available
    fun set(value:Float,commit:Boolean=true) {
        val constrained=PaneSizing.fraction(width,value*available)
        onChange(constrained)
        if(commit)onCommit(constrained)
    }
    var draggedWidth by remember(width) { mutableFloatStateOf(listWidth) }
    val drag=rememberDraggableState { delta ->
        draggedWidth=(draggedWidth+with(density){delta.toDp().value}).coerceIn(PaneSizing.MIN_LIST,available-PaneSizing.MIN_DETAIL)
        set(draggedWidth/available,commit=false)
    }
    Box(
        Modifier.width(PaneSizing.DIVIDER.dp).fillMaxHeight().testTag("pane-divider")
            .draggable(drag,Orientation.Horizontal,onDragStarted={draggedWidth=listWidth;onResizing(true)},onDragStopped={onResizing(false);onCommit(PaneSizing.fraction(width,draggedWidth))})
            .semantics(mergeDescendants=true) {
                contentDescription="Resize ticket panes"
                stateDescription="Ticket list ${(actual*100).roundToInt()} percent"
                progressBarRangeInfo=ProgressBarRangeInfo(actual,range,0)
                setProgress { set(it);true }
                customActions=listOf(
                    CustomAccessibilityAction("Widen ticket list") { set(actual+24f/available);true },
                    CustomAccessibilityAction("Narrow ticket list") { set(actual-24f/available);true },
                    CustomAccessibilityAction("Reset pane widths") { set(PaneSizing.DEFAULT_FRACTION);true }
                )
            }
            .onKeyEvent { event ->
                if(event.type!=KeyEventType.KeyDown)false else when(event.key) {
                    Key.DirectionLeft -> {set(actual-24f/available);true}
                    Key.DirectionRight -> {set(actual+24f/available);true}
                    Key.MoveHome -> {set(PaneSizing.DEFAULT_FRACTION);true}
                    else -> false
                }
            }.focusable(),
        contentAlignment=Alignment.Center
    ) {
        VerticalDivider(Modifier.fillMaxHeight(),color=MaterialTheme.colorScheme.outlineVariant)
        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.secondaryContainer,
            modifier=Modifier.size(32.dp,64.dp).clickable(onClickLabel="Adjust pane widths"){dialog=true}) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Outlined.DragIndicator,null,Modifier.size(20.dp)) }
        }
    }
    if(dialog)AlertDialog(
        onDismissRequest={dialog=false},title={Text("Pane widths")},
        text={Column {
            Text("Ticket list: ${(actual*100).roundToInt()}% · Details: ${((1-actual)*100).roundToInt()}%")
            Slider(value=actual,onValueChange={set(it,false)},onValueChangeFinished={onCommit(PaneSizing.fraction(width,PaneSizing.listWidth(width,fraction)))},valueRange=range)
            Text("Drag the divider, use this slider, or focus the divider and use the left/right arrow keys.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton({dialog=false}){Text("Done")}},
        dismissButton={TextButton({set(PaneSizing.DEFAULT_FRACTION)}){Text("Reset")}}
    )
}

@Composable
fun EmptyTicketSelection(onNew:()->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=380.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Assignment,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Text("Select a ticket",style=MaterialTheme.typography.headlineSmall)
            Text("Choose a request on the left to view its details, attachments, and full activity history here.")
            Button(onNew) { Text("New ticket") }
        }
    }
}
