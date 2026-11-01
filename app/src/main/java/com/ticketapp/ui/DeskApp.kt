@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.ticketapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketapp.data.*
import com.ticketapp.domain.*

private val light=lightColorScheme(primary=Color(0xFF176B60),secondary=Color(0xFF9A562C),background=Color(0xFFF5F6F2),surface=Color(0xFFFCFDF9),primaryContainer=Color(0xFFD6EBE3))
private val dark=darkColorScheme(primary=Color(0xFF8DD5BF),secondary=Color(0xFFE5B18A),background=Color(0xFF101C1E),surface=Color(0xFF19272A))
@Composable
fun DeskApp(vm:DeskViewModel) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val state by vm.data.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val startupError by vm.app.startupError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Dashboard") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable(stateSaver = Saver<Query, String>(
        save = { codec.encodeToString(Query.serializer(), it) },
        restore = { codec.decodeFromString<Query>(it) }
    )) { mutableStateOf(Query(sort=prefs["sort"]?:"priority", descending=prefs["descending"]=="true", includeArchived=true)) }
    var sortInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.fields.isNotEmpty()) {
        if(state.fields.isNotEmpty() && !sortInitialized) {
            val stored=vm.app.preferences.all()
            query=query.copy(sort=stored["sort"]?:"priority",descending=stored["descending"]=="true")
            sortInitialized=true
        }
    }
    var paneFraction by rememberSaveable { mutableFloatStateOf(PaneSizing.DEFAULT_FRACTION) }
    var resizing by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val listState = rememberLazyListState()
    val detailState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
    LaunchedEffect(prefs["listPaneFraction"]) {
        if (!resizing) prefs["listPaneFraction"]?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { paneFraction=it }
    }
    fun navigate(action:() -> Unit) {
        if (busy) return
        if (editor!=null) pendingNavigation=action else action()
    }
    fun openTicket(id:String) = navigate {
        focusManager.clearFocus()
        keyboard?.hide()
        editor=null
        if (tab=="Settings") tab="Dashboard"
        vm.selected.value=id
    }
    fun newTicket() = navigate { focusManager.clearFocus();keyboard?.hide();editor="new"; if(tab=="Settings")tab="Dashboard" }
    fun phoneTab(name:String) = navigate {
        tab=name
        vm.selected.value=null
        query=query.copy(archived=name=="Archive",includeArchived=name=="Dashboard",statuses=emptySet(),overdue=false)
    }

    MaterialTheme(colorScheme=if(prefs["theme"]=="Dark" || (prefs["theme"]!="Light"&&isSystemInDarkTheme()))dark else light) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentInsets=WindowInsets.systemBars.union(WindowInsets.displayCutout)
            val insetPadding=contentInsets.asPaddingValues()
            val layoutDirection=LocalLayoutDirection.current
            val paneWidth=(maxWidth-insetPadding.calculateLeftPadding(layoutDirection)-insetPadding.calculateRightPadding(layoutDirection)).value
            val expanded=PaneSizing.isExpanded(paneWidth)
            val effectiveQuery=if(expanded)query else query.copy(includeArchived=false,archived=tab=="Archive")
            val visible=remember(state,effectiveQuery) { selectTickets(state,effectiveQuery) }
            // Preserve an explicitly selected ticket, including after it leaves the active filter.
            LaunchedEffect(expanded,visible.firstOrNull()?.id,selected,editor,tab,sortInitialized) {
                if(expanded && sortInitialized && selected==null && editor==null && tab!="Settings") vm.selected.value=visible.firstOrNull()?.id
            }
            Scaffold(
                contentWindowInsets=contentInsets,
                snackbarHost={SnackbarHost(snackbar)},
                topBar={
                    TopAppBar(
                        title={Column {
                            Text("PURCHASING DESK",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            Text(when { editor=="new"->"New request";editor!=null->"Edit request";tab=="Settings"->"Settings";expanded->"Ticket workspace";selected!=null->"Request detail";else->tab },fontWeight=FontWeight.SemiBold)
                        }},
                        navigationIcon={
                            if(!expanded && selected!=null && editor==null && tab!="Settings") IconButton({vm.selected.value=null}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back") }
                        },
                        actions={
                            if(expanded) {
                                FilledTonalButton(onClick={newTicket()},enabled=!busy,modifier=Modifier.testTag("workspace-new-ticket")) {
                                    Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(6.dp)); Text("New ticket")
                                }
                                IconButton(onClick={navigate { editor=null;tab=if(tab=="Settings")"Dashboard" else "Settings" }},enabled=!busy) {
                                    Icon(if(tab=="Settings")Icons.Outlined.Close else Icons.Outlined.Settings,if(tab=="Settings")"Close settings" else "Settings")
                                }
                            }
                        }
                    )
                },
                bottomBar={
                    if(!expanded && editor==null && (selected==null || tab=="Settings")) NavigationBar {
                        listOf("Dashboard" to Icons.Outlined.SpaceDashboard,"Active" to Icons.Outlined.Inbox,"Archive" to Icons.Outlined.Inventory2,"Settings" to Icons.Outlined.Settings).forEach { (name,icon) ->
                            NavigationBarItem(tab==name,{phoneTab(name)},icon={Icon(icon,null)},label={Text(name)})
                        }
                    }
                },
                floatingActionButton={
                    if(!expanded && selected==null && editor==null && tab!="Settings") ExtendedFloatingActionButton(onClick={newTicket()},icon={Icon(Icons.Outlined.Add,"New ticket")},text={Text("New ticket")})
                }
            ) { padding ->
                Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    when {
                        startupError!=null -> Text("Unable to open local storage: $startupError. Check available device storage before reopening.",Modifier.padding(24.dp))
                        state.fields.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                        else -> Row(Modifier.fillMaxSize()) {
                            if(expanded) {
                                val width=PaneSizing.listWidth(paneWidth,paneFraction)
                                Box(Modifier.width(width.dp).fillMaxHeight().testTag("ticket-list-pane")) {
                                    TicketBrowser(state,effectiveQuery,listState,selected,busy,onQuery={
                                        query=it
                                        if(tab!="Settings")tab=when { it.includeArchived->"Dashboard";it.archived->"Archive";else->"Active" }
                                    },onSelect={openTicket(it)},onSort={field,descending ->
                                        query=query.copy(sort=field,descending=descending)
                                        vm.workspaceSort(field,descending)
                                    })
                                }
                                PaneDivider(paneWidth,paneFraction,onResizing={resizing=it},onChange={paneFraction=it},onCommit={vm.paneFraction(it)})
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().testTag(if(expanded)"ticket-detail-pane" else "phone-pane"),contentAlignment=Alignment.TopCenter) {
                                Box(Modifier.widthIn(max=960.dp).fillMaxSize()) {
                                    when {
                                        editor!=null -> TicketEditor(vm,state,editor!!,{editor=null},{id->editor=null;vm.selected.value=id},stickyActions=expanded)
                                        tab=="Settings" -> SettingsScreen(vm,state)
                                        selected!=null -> {
                                            if(!expanded) BackHandler { vm.selected.value=null }
                                            val ticket=state.tickets.find { it.id==selected }
                                            if(ticket==null) Text("Ticket not found",Modifier.padding(24.dp)) else detailState.SaveableStateProvider(ticket.id) {
                                                key(ticket.id) { TicketDetail(vm,state,ticket,{editor=ticket.id},paneMode=expanded) }
                                            }
                                        }
                                        expanded -> EmptyTicketSelection(onNew={newTicket()})
                                        else -> TicketList(vm,state,tab,effectiveQuery,listState,{query=it},{openTicket(it)})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        pendingNavigation?.let { action ->
            Confirm("Leave this form?","The new-ticket draft stays saved on this device. Unsaved edits to an existing ticket will be discarded.",{pendingNavigation=null}) {
                pendingNavigation=null
                editor=null
                action()
            }
        }
    }
}
@Composable
fun TicketList(vm:DeskViewModel,state:Snapshot,tab:String,query:Query,listState:LazyListState,onQuery:(Query)->Unit,onSelect:(String)->Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var filters by remember{mutableStateOf(false)}
    val tickets=remember(state,query){selectTickets(state,query)}
    val active=state.tickets.filter{!it.state.archived}
    LazyColumn(Modifier.fillMaxSize().testTag("phone-ticket-list"),state=listState,contentPadding=PaddingValues(16.dp,12.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(tab=="Dashboard") {
            item { Text("Your maintenance purchasing queue",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Metric("Active",active.size,Modifier.weight(1f));Metric("Overdue",active.count{it.due?.let{d->d<now()}==true},Modifier.weight(1f));Metric("Ordered",state.tickets.count{it.state==Status.ORDERED},Modifier.weight(1f))
                }
            }
            item { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ Status.entries.filter{!it.archived}.forEach{s->FilterChip(s.name in query.statuses,{onQuery(query.copy(statuses=if(s.name in query.statuses)query.statuses-s.name else query.statuses+s.name))},label={Text("${s.label} ${active.count{it.state==s}}")})} } }
        }
        item { OutlinedTextField(query.search,{onQuery(query.copy(search=it))},label={Text("Search ${if(query.archived)"archived" else "active"} requests")},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                OutlinedButton({filters=true}){Icon(Icons.Outlined.FilterList,null);Text(" Filters")}
                Text("${tickets.size} requests",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                IconButton({onQuery(query.copy(descending=!query.descending));vm.preference("descending",(!query.descending).toString())}){Icon(if(query.descending)Icons.Outlined.South else Icons.Outlined.North,"Reverse sort direction")}
            }
        }
        item { Select("Sort by",query.sort,listOf("created" to "Date created","updated" to "Last updated","due" to "Due date","status" to "Status","id" to "Ticket ID")+state.fields.filter{!it.kind.media&&it.kind!=FieldType.MULTI}.map{it.id to it.name},onChange={onQuery(query.copy(sort=it));vm.preference("sort",it)}) }
        if(tickets.isEmpty())item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp)){Icon(Icons.Outlined.Assignment,null,Modifier.size(36.dp));Spacer(Modifier.height(12.dp));Text(if(query.search.isNotEmpty()||query.filters.isNotEmpty())"No matching requests" else if(query.archived)"Your archive is clear" else "A clear desk. A fresh start.",style=MaterialTheme.typography.titleLarge);Text(if(query.archived)"Ordered and cancelled requests stay here with their complete history." else "Create a request to keep parts, people, and follow-ups in one place.",Modifier.padding(top=8.dp))}} }
        items(tickets,key={it.id}) { t -> TicketCard(state,t){onSelect(t.id)} }
    }
    if(filters) FilterDialog(state,query,{filters=false}){onQuery(it);filters=false}
}
@Composable
private fun Metric(label:String,value:Int,modifier:Modifier) { Card(modifier){Column(Modifier.padding(14.dp)){Text(value.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelMedium)}} }
@Composable
fun TicketCard(s:Snapshot,t:Ticket,isSelected:Boolean=false,compact:Boolean=false,enabled:Boolean=true,onClick:()->Unit) {
    val v=s.values.filter{it.ticketId==t.id}.associate{it.fieldId to it.value}
    ElevatedCard(onClick,Modifier.fillMaxWidth().testTag("ticket-row-${t.id}").semantics { selected=isSelected }.then(if(isSelected)Modifier.border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp))else Modifier),shape=RoundedCornerShape(16.dp),enabled=enabled,colors=CardDefaults.elevatedCardColors(containerColor=if(isSelected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(if(compact)12.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(t.number,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(t.state.label,style=MaterialTheme.typography.labelMedium)}
            Text(v["title"]?.ifBlank{null}?:"Untitled request",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            listOfNotNull(v["equipment"]?.takeIf{it.isNotBlank()},v["requester"]?.takeIf{it.isNotBlank()}).joinToString(" · ").takeIf{it.isNotEmpty()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
            v["priority"]?.takeIf{it.isNotBlank()}?.let { priority ->
                val rank=s.fields.find{it.id=="priority"}?.selections?.indexOf(priority)?:3
                Text("●  $priority",style=MaterialTheme.typography.labelMedium,color=if(rank==0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            }
            if(!compact)Text("Updated ${stamp(t.updated)}",style=MaterialTheme.typography.bodySmall)
            t.due?.let{Text("${if(it<now()&&!t.state.archived)"OVERDUE · " else "Due · "}${stamp(it)}",style=MaterialTheme.typography.labelSmall,color=if(it<now()&&!t.state.archived)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable
fun FilterDialog(s:Snapshot,q:Query,onDismiss:()->Unit,onSave:(Query)->Unit) {
    var draft by remember{mutableStateOf(q)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Filter requests")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScrollCompat(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Status.entries.filter{q.includeArchived || it.archived==q.archived}.forEach{status->Row{Checkbox(status.name in draft.statuses,{checked->draft=draft.copy(statuses=if(checked)draft.statuses+status.name else draft.statuses-status.name)});Text(status.label,Modifier.padding(top=12.dp))}}
            Row{Checkbox(draft.overdue,{draft=draft.copy(overdue=it)});Text("Overdue only",Modifier.padding(top=12.dp))}
            DateInput("Created after",draft.createdFrom){draft=draft.copy(createdFrom=it)}
            DateInput("Created before",draft.createdTo){draft=draft.copy(createdTo=it)}
            DateInput("Due after",draft.dueFrom){draft=draft.copy(dueFrom=it)}
            DateInput("Due before",draft.dueTo){draft=draft.copy(dueTo=it)}
            s.fields.filter{!it.kind.media}.forEach{f->OutlinedTextField(draft.filters[f.id].orEmpty(),{draft=draft.copy(filters=draft.filters+(f.id to it))},label={Text("${f.name} contains")},singleLine=true)}
        }
    },confirmButton={TextButton({onSave(draft)}){Text("Apply")}},dismissButton={Row{TextButton({onSave(Query(archived=q.archived,sort=q.sort,descending=q.descending,includeArchived=q.includeArchived))}){Text("Clear")};TextButton(onDismiss){Text("Cancel")}}})
}
@Composable
fun Modifier.verticalScrollCompat():Modifier = this.verticalScroll(rememberScrollState())
