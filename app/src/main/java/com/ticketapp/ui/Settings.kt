package com.ticketapp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.ticketapp.BuildConfig
import com.ticketapp.data.*
import com.ticketapp.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm:DeskViewModel,s:Snapshot) {
    val prefs by vm.preferences.collectAsState()
    var managing by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<Field?>(null)}
    var creating by remember{mutableStateOf(false)}
    var settingsHistory by remember{mutableStateOf(false)}
    var restore by remember{mutableStateOf<BackupService.Validated?>(null)}
    val backup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){it?.let(vm::writeBackup)}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){it?.let(vm::csv)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.task {
        val valid=vm.backup.inspect(uri);withContext(Dispatchers.Main){restore=valid}
    }}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok->vm.message.value=if(ok)"Notification permission granted" else "Notifications are blocked in Android settings."}
    when {
        editing!=null || creating -> {
            BackHandler{editing=null;creating=false}
            FieldEditor(vm,editing?:Field(name="",position=s.fields.size),{editing=null;creating=false})
        }
        managing -> {
            BackHandler{managing=false}
            val ordered=s.fields.sortedBy{it.position}
            val rowHeight=with(LocalDensity.current){100.dp.toPx()}
            fun move(id:String,delta:Int) {
                val ids=ordered.map{it.id}.toMutableList();val index=ids.indexOf(id);val target=(index+delta).coerceIn(0,ids.lastIndex)
                if(target!=index){ids.removeAt(index);ids.add(target,id);vm.task{vm.repository.reorder(ids)}}
            }
            LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item { TextButton({managing=false}){Text("Back to settings")};Text("Field management",style=MaterialTheme.typography.headlineSmall);Text("Long-press and drag to reorder, or use the arrow controls. Retired fields and their histories are retained.") }
                item { Button({creating=true}){Text("Add field")} }
                items(ordered,key={it.id}){f->
                    var dy by remember{mutableFloatStateOf(0f)}
                    Card(Modifier.fillMaxWidth().pointerInput(f.id,ordered){detectDragGesturesAfterLongPress(onDragStart={dy=0f},onDragEnd={move(f.id,(dy/rowHeight).roundToInt())},onDragCancel={dy=0f}){change,amount->change.consume();dy+=amount.y}}) {
                        Column(Modifier.padding(12.dp)) {
                            Text(f.name+if(f.retired)" · Retired" else if(!f.enabled)" · Disabled" else "",style=MaterialTheme.typography.titleMedium)
                            Text("${f.kind.label} · ${if(f.required)"Required" else "Optional"}",style=MaterialTheme.typography.bodySmall)
                            Row{TextButton({editing=f}){Text("Configure")};IconButton({move(f.id,-1)}){Icon(Icons.Outlined.ArrowUpward,"Move ${f.name} up")};IconButton({move(f.id,1)}){Icon(Icons.Outlined.ArrowDownward,"Move ${f.name} down")};TextButton({vm.task{vm.repository.saveField(f.copy(retired=!f.retired))}}){Text(if(f.retired)"Restore" else "Retire")}}
                        }
                    }
                }
            }
        }
        else -> LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,40.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("Make the desk yours",style=MaterialTheme.typography.headlineSmall);Text("Everything stays on this device. No account. No cloud.",style=MaterialTheme.typography.bodyMedium) }
            item { SettingsCard("Forms & workflow") {
                OutlinedButton({managing=true},Modifier.fillMaxWidth()){Text("Manage ticket fields")}
                OutlinedButton({editing=s.fields.first{it.id=="priority"}},Modifier.fillMaxWidth()){Text("Manage priorities & urgency order")}
                Text("Status categories",style=MaterialTheme.typography.titleSmall)
                Text("Active: Open, Awaiting Quote, Awaiting Approval, On Hold.\nArchived: Ordered (completed), Cancelled (terminated).",style=MaterialTheme.typography.bodyMedium)
            } }
            item { SettingsCard("Notifications") {
                Row { Switch(prefs["notifications"]!="false",{vm.preference("notifications",it.toString());if(it&&Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)});Text("Enable reminders",Modifier.padding(12.dp)) }
                Select("Default reminder lead time",prefs["defaultReminderHours"]?:"24",listOf("0" to "At due date","1" to "1 hour before","24" to "1 day before","72" to "3 days before"),onChange={vm.preference("defaultReminderHours",it)})
                Text("Reminders run offline through Android WorkManager. Battery restrictions may delay delivery. Pending reminders pause in the archive and resume after reopening. Android restores scheduled work after a restart.",style=MaterialTheme.typography.bodySmall)
                if(Build.VERSION.SDK_INT>=33)TextButton({permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow Android notifications")}
            } }
            item { SettingsCard("Display") { Select("Appearance",prefs["theme"]?:"System",listOf("System","Light","Dark").map{it to it},onChange={vm.preference("theme",it)}) } }
            item { SettingsCard("Data management") {
                Button({backup.launch("purchasing-desk-${java.time.LocalDate.now()}.zip")},Modifier.fillMaxWidth()){Text("Save complete backup")}
                OutlinedButton({picker.launch(arrayOf("application/zip","application/octet-stream"))},Modifier.fillMaxWidth()){Text("Restore a backup")}
                OutlinedButton({csv.launch("purchasing-tickets.csv")},Modifier.fillMaxWidth()){Text("Export all tickets to CSV")}
                Text("Backups contain current data, complete history, settings, reminder configurations, and every attachment, including removed media. Keep copies outside this app. Uninstalling the app erases its private storage.",style=MaterialTheme.typography.bodySmall)
                TextButton({settingsHistory=!settingsHistory}){Text(if(settingsHistory)"Hide settings history" else "View settings audit history")}
            } }
            if(settingsHistory)items(s.audit.filter{it.ticketId==null},key={it.id}){AuditRow(it)}
            item { SettingsCard("About") { Text("Purchasing Desk ${BuildConfig.VERSION_NAME}");Text("Android 9+ · Room database v2\n${s.tickets.size} tickets · ${s.audit.size} audit events\nLocal-only storage · No Internet permission",style=MaterialTheme.typography.bodyMedium) } }
        }
    }
    restore?.let{valid->Confirm("Replace local data?","Validated backup: ${valid.snapshot.tickets.size} tickets and ${valid.snapshot.audit.size} audit events. This replaces the current database and settings. A complete safety backup of your current data will be retained in private storage first.",{valid.close();restore=null}){
        vm.task("Backup restored. Previous data is in the app's safety-backups directory.") { vm.app.scheduler.cancelAll();vm.backup.restore(valid) };restore=null
    }}
}
@Composable
fun SettingsCard(title:String,content:@Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleLarge);content()}} }
@Composable
fun FieldEditor(vm:DeskViewModel,initial:Field,onDone:()->Unit) {
    var f by remember(initial.id){mutableStateOf(initial)}
    var options by remember(initial.id){mutableStateOf(initial.selections)}
    var newOption by remember{mutableStateOf("")}
    var conversion by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var cancel by remember{mutableStateOf(false)}
    val busy by vm.busy.collectAsState()
    fun save(confirm:Boolean) {
        vm.task {
            try { vm.repository.saveField(f.copy(options=packed(options)),confirm);withContext(Dispatchers.Main){onDone()} }
            catch(e:IllegalArgumentException){withContext(Dispatchers.Main){if(e.message=="CONFIRM_CONVERSION")conversion=true else error=e.message}}
        }
    }
    BackHandler{cancel=true}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Configure field",style=MaterialTheme.typography.headlineSmall);Text("Stable ID: ${f.id}",style=MaterialTheme.typography.bodySmall) }
        item { OutlinedTextField(f.name,{f=f.copy(name=it)},label={Text("Field name *")},modifier=Modifier.fillMaxWidth()) }
        item { Select("Field type",f.type,FieldType.entries.map{it.name to it.label},onChange={f=f.copy(type=it)}) }
        item { Row { Checkbox(f.required,{f=f.copy(required=it)});Text("Required",Modifier.padding(top=12.dp)) };Row { Checkbox(f.enabled,{f=f.copy(enabled=it)});Text("Enabled",Modifier.padding(top=12.dp)) } }
        item { OutlinedTextField(f.placeholder,{f=f.copy(placeholder=it)},label={Text("Placeholder")},modifier=Modifier.fillMaxWidth());OutlinedTextField(f.helper,{f=f.copy(helper=it)},label={Text("Helper description")},modifier=Modifier.fillMaxWidth()) }
        if(f.kind in listOf(FieldType.SINGLE,FieldType.MULTI)) {
            item { Text("Dropdown choices · order defines sorting",style=MaterialTheme.typography.titleMedium) }
            items(options.size){i->Row { OutlinedTextField(options[i],{value->options=options.toMutableList().apply{set(i,value)}},Modifier.weight(1f),singleLine=true);Column{IconButton({if(i>0)options=options.toMutableList().apply{add(i-1,removeAt(i))}}){Icon(Icons.Outlined.ArrowUpward,"Move option up")};IconButton({if(i<options.lastIndex)options=options.toMutableList().apply{add(i+1,removeAt(i))}}){Icon(Icons.Outlined.ArrowDownward,"Move option down")}};IconButton({options=options.filterIndexed{index,_->index!=i}}){Icon(Icons.Outlined.Close,"Remove option")}} }
            item { OutlinedTextField(newOption,{newOption=it},label={Text("New option")});TextButton({if(newOption.isNotBlank()){options=options+newOption.trim();newOption=""}}){Text("Add choice")} }
        }
        if(!f.kind.media)item { FieldInput(f.copy(required=false,options=packed(options),name="Default value"),f.defaultValue,{f=f.copy(defaultValue=it)},{}) }
        error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
        item { Text("Retiring a field preserves its definition, values, and audit history. Type conversions that cannot preserve current values require confirmation and retain prior values in each ticket's history.",style=MaterialTheme.typography.bodySmall) }
        item { Row { TextButton({cancel=true}){Text("Cancel")};Button({save(false)},enabled=!busy){Text("Save configuration")} } }
    }
    if(conversion)Confirm("Convert existing values?","Some existing values cannot be safely represented by the new configuration. They will be retained in ticket audit events, and the affected current values will be cleared. This includes historical values on archived tickets.",{conversion=false}){conversion=false;save(true)}
    if(cancel)Confirm("Discard field edits?","Your unsaved field configuration will be discarded.",{cancel=false},onDone)
}
