@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.ticketapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var tab by rememberSaveable{mutableStateOf("Dashboard")}
    var editor by rememberSaveable{mutableStateOf<String?>(null)}
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(message){message?.let{snackbar.showSnackbar(it);vm.message.value=null}}
    MaterialTheme(colorScheme=if(prefs["theme"]=="Dark" || (prefs["theme"]!="Light"&&isSystemInDarkTheme()))dark else light) {
        Scaffold(snackbarHost={SnackbarHost(snackbar)},topBar={TopAppBar(title={Column{Text("PURCHASING DESK",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Text(if(editor!=null)if(editor=="new")"New request" else "Edit request" else if(selected!=null) "Request detail" else tab, fontWeight=FontWeight.SemiBold)}},navigationIcon={if(selected!=null&&editor==null)IconButton({vm.selected.value=null}){Icon(Icons.Outlined.ArrowBack,"Back")}})},
            bottomBar={if(selected==null&&editor==null)NavigationBar{listOf("Dashboard" to Icons.Outlined.SpaceDashboard,"Active" to Icons.Outlined.Inbox,"Archive" to Icons.Outlined.Inventory2,"Settings" to Icons.Outlined.Settings).forEach{(name,icon)->NavigationBarItem(tab==name,{tab=name},icon={Icon(icon,name)},label={Text(name)})}}},
            floatingActionButton={if(selected==null&&editor==null&&tab!="Settings")ExtendedFloatingActionButton(onClick={editor="new"},icon={Icon(Icons.Outlined.Add,"New ticket")},text={Text("New ticket")})}
        ) { padding ->
            Column(Modifier.padding(padding).imePadding().fillMaxSize()) {
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    startupError!=null->Text("Unable to open local storage: $startupError. Close the app and check available device storage before reopening.",Modifier.padding(24.dp))
                    state.fields.isEmpty()->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                    editor!=null -> TicketEditor(vm,state,editor!!,{editor=null},{id->editor=null;vm.selected.value=id})
                    selected!=null -> {
                        BackHandler{vm.selected.value=null}
                        val ticket=state.tickets.find{it.id==selected}
                        if(ticket==null)Text("Ticket not found",Modifier.padding(24.dp)) else TicketDetail(vm,state,ticket,{editor=ticket.id})
                    }
                    tab=="Settings" -> SettingsScreen(vm,state)
                    else -> TicketList(vm,state,tab)
                }
            }
        }
    }
}
@Composable
fun TicketList(vm:DeskViewModel,state:Snapshot,tab:String) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var query by remember(tab){mutableStateOf(Query(archived=tab=="Archive",sort=prefs["sort"]?:"priority",descending=prefs["descending"]=="true"))}
    var filters by remember{mutableStateOf(false)}
    val tickets=remember(state,query){selectTickets(state,query)}
    val active=state.tickets.filter{!it.state.archived}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(tab=="Dashboard") {
            item { Text("Your maintenance purchasing queue",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Metric("Active",active.size,Modifier.weight(1f));Metric("Overdue",active.count{it.due?.let{d->d<now()}==true},Modifier.weight(1f));Metric("Ordered",state.tickets.count{it.state==Status.ORDERED},Modifier.weight(1f))
                }
            }
            item { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ Status.entries.filter{!it.archived}.forEach{s->FilterChip(s.name in query.statuses,{query=query.copy(statuses=if(s.name in query.statuses)query.statuses-s.name else query.statuses+s.name)},label={Text("${s.label} ${active.count{it.state==s}}")})} } }
        }
        item { OutlinedTextField(query.search,{query=query.copy(search=it)},label={Text("Search ${if(query.archived)"archived" else "active"} requests")},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                OutlinedButton({filters=true}){Icon(Icons.Outlined.FilterList,null);Text(" Filters")}
                Text("${tickets.size} requests",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                IconButton({query=query.copy(descending=!query.descending);vm.preference("descending",query.descending.toString())}){Icon(if(query.descending)Icons.Outlined.South else Icons.Outlined.North,"Reverse sort direction")}
            }
        }
        item { Select("Sort by",query.sort,listOf("created" to "Date created","updated" to "Last updated","due" to "Due date","status" to "Status","id" to "Ticket ID")+state.fields.filter{!it.kind.media&&it.kind!=FieldType.MULTI}.map{it.id to it.name},onChange={query=query.copy(sort=it);vm.preference("sort",it)}) }
        if(tickets.isEmpty())item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp)){Icon(Icons.Outlined.Assignment,null,Modifier.size(36.dp));Spacer(Modifier.height(12.dp));Text(if(query.search.isNotEmpty()||query.filters.isNotEmpty())"No matching requests" else if(query.archived)"Your archive is clear" else "A clear desk. A fresh start.",style=MaterialTheme.typography.titleLarge);Text(if(query.archived)"Ordered and cancelled requests stay here with their complete history." else "Create a request to keep parts, people, and follow-ups in one place.",Modifier.padding(top=8.dp))}} }
        items(tickets,key={it.id}) { t -> TicketCard(state,t){vm.selected.value=t.id} }
    }
    if(filters) FilterDialog(state,query,{filters=false}){query=it;filters=false}
}
@Composable
private fun Metric(label:String,value:Int,modifier:Modifier) { Card(modifier){Column(Modifier.padding(14.dp)){Text(value.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelMedium)}} }
@Composable
fun TicketCard(s:Snapshot,t:Ticket,onClick:()->Unit) {
    val v=s.values.filter{it.ticketId==t.id}.associate{it.fieldId to it.value}
    ElevatedCard(onClick,Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(t.number,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(t.state.label,style=MaterialTheme.typography.labelMedium)}
            Text(v["title"]?.ifBlank{null}?:"Untitled request",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            listOfNotNull(v["equipment"]?.takeIf{it.isNotBlank()},v["requester"]?.takeIf{it.isNotBlank()}).joinToString(" · ").takeIf{it.isNotEmpty()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
            v["priority"]?.takeIf{it.isNotBlank()}?.let { priority ->
                val rank=s.fields.find{it.id=="priority"}?.selections?.indexOf(priority)?:3
                Text("●  $priority",style=MaterialTheme.typography.labelMedium,color=if(rank==0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            }
            Text("Updated ${stamp(t.updated)}",style=MaterialTheme.typography.bodySmall)
            t.due?.let{Text("${if(it<now()&&!t.state.archived)"OVERDUE · " else "Due · "}${stamp(it)}",style=MaterialTheme.typography.labelSmall,color=if(it<now()&&!t.state.archived)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable
fun FilterDialog(s:Snapshot,q:Query,onDismiss:()->Unit,onSave:(Query)->Unit) {
    var draft by remember{mutableStateOf(q)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Filter requests")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScrollCompat(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Status.entries.filter{it.archived==q.archived}.forEach{status->Row{Checkbox(status.name in draft.statuses,{checked->draft=draft.copy(statuses=if(checked)draft.statuses+status.name else draft.statuses-status.name)});Text(status.label,Modifier.padding(top=12.dp))}}
            Row{Checkbox(draft.overdue,{draft=draft.copy(overdue=it)});Text("Overdue only",Modifier.padding(top=12.dp))}
            DateInput("Created after",draft.createdFrom){draft=draft.copy(createdFrom=it)}
            DateInput("Created before",draft.createdTo){draft=draft.copy(createdTo=it)}
            DateInput("Due after",draft.dueFrom){draft=draft.copy(dueFrom=it)}
            DateInput("Due before",draft.dueTo){draft=draft.copy(dueTo=it)}
            s.fields.filter{!it.kind.media}.forEach{f->OutlinedTextField(draft.filters[f.id].orEmpty(),{draft=draft.copy(filters=draft.filters+(f.id to it))},label={Text("${f.name} contains")},singleLine=true)}
        }
    },confirmButton={TextButton({onSave(draft)}){Text("Apply")}},dismissButton={Row{TextButton({onSave(Query(archived=q.archived,sort=q.sort,descending=q.descending))}){Text("Clear")};TextButton(onDismiss){Text("Cancel")}}})
}
@Composable
fun Modifier.verticalScrollCompat():Modifier = this.verticalScroll(rememberScrollState())
