@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.ticketapp.ui

import android.Manifest
import android.os.Build
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.selection.SelectionContainer
import coil.compose.AsyncImage
import com.ticketapp.data.*
import java.time.Instant

@Composable
fun TicketDetail(vm:DeskViewModel,s:Snapshot,t:Ticket,onEdit:()->Unit) {
    var change by remember{mutableStateOf(false)}
    var note by remember{mutableStateOf<Note?>(null)}
    var addingNote by remember{mutableStateOf(false)}
    var attaching by remember{mutableStateOf(false)}
    var historyMedia by remember{mutableStateOf(false)}
    var viewing by remember{mutableStateOf<Attachment?>(null)}
    var removing by remember{mutableStateOf<Attachment?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->vm.message.value=if(granted)"Notifications enabled" else "Reminders are saved; allow notifications in Android settings to receive them."}
    val v=s.values.filter{it.ticketId==t.id}.associate{it.fieldId to it.value}
    val attachments=s.attachments.filter{it.ticketId==t.id && (!it.removed||historyMedia)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text(t.number,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text(v["title"]?.ifBlank{null}?:"Untitled request",style=MaterialTheme.typography.headlineMedium);Text(t.state.label,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary) }
        item { Text("Created ${stamp(t.created)}\nUpdated ${stamp(t.updated)}"+(t.closed?.let{"\nClosed ${stamp(it)}"}?:""),style=MaterialTheme.typography.bodySmall) }
        if(t.state==Status.HOLD)item { Card { Text("ON HOLD\n"+(s.audit.firstOrNull{it.ticketId==t.id&&it.type=="ON_HOLD"}?.reason?:""),Modifier.padding(16.dp)) } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(!t.state.archived)OutlinedButton(onEdit){Text("Edit ticket")}
            Button({change=true}){Text(if(t.state.archived)"Reopen" else "Change status")}
            if(!t.state.archived)OutlinedButton({addingNote=true}){Text("Add note")}
        } }
        if(t.state.archived)item { Text("Archived requests are read-only. Reopen to make operational updates.",style=MaterialTheme.typography.bodyMedium) }
        items(s.fields.filter{!it.kind.media && (v[it.id].orEmpty().isNotBlank()||(!it.retired&&it.enabled))}.sortedBy{it.position},key={it.id}){f->
            Column { Text(f.name+if(f.retired)" (retired)" else "",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer{Text(displayValue(f,v[f.id].orEmpty()).ifBlank{"—"},style=MaterialTheme.typography.bodyLarge)};HorizontalDivider(Modifier.padding(top=8.dp)) }
        }
        item { Text("Due date & reminders",style=MaterialTheme.typography.titleLarge);Text(stamp(t.due)) }
        if(!t.state.archived)item { Column {
            Button({
                if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                pickDate(context,null){at->vm.task("Reminder saved"){vm.repository.saveReminder(t.id,at)}}
            }){Text("Add custom reminder")}
            if(t.due!=null)FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(0L to "At due",1L to "1 hour before",24L to "1 day before",72L to "3 days before").forEach{(hours,label)->TextButton({
                if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.task("Reminder saved"){vm.repository.saveReminder(t.id,t.due-hours*3600000)}
            }){Text(label)}}}
            Text("Android may delay reminders to conserve battery. No exact-alarm permission is requested.",style=MaterialTheme.typography.bodySmall)
        } }
        items(s.reminders.filter{it.ticketId==t.id},key={it.id}){r->Row{Text("${stamp(r.at)}\n${when{!r.enabled->"Cancelled";r.delivered->"Delivered";t.state.archived->"Paused while archived";else->"Scheduled"}}",Modifier.weight(1f));if(!t.state.archived&&r.enabled&&!r.delivered)Column{TextButton({pickDate(context,r.at){at->vm.task("Reminder rescheduled"){vm.repository.rescheduleReminder(r.id,at)}}}){Text("Reschedule")};TextButton({vm.task{vm.repository.cancelReminder(r.id)}}){Text("Cancel")}}}}
        item { HorizontalDivider();Text("Notes",style=MaterialTheme.typography.titleLarge) }
        items(s.notes.filter{it.ticketId==t.id}.sortedByDescending{it.timestamp},key={it.id}){n->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(n.text);Text(stamp(n.timestamp),style=MaterialTheme.typography.labelSmall);if(!t.state.archived)TextButton({note=n}){Text("Edit note")}}}}
        item { Row { Text("Attachments",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);if(!t.state.archived)TextButton({attaching=true}){Text("Add")} };Row{Checkbox(historyMedia,{historyMedia=it});Text("Include removed attachments",Modifier.padding(top=12.dp))} }
        items(attachments,key={it.id}){a->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                if(a.mime.startsWith("image/"))AsyncImage(vm.app.media.file(a.file),contentDescription=a.name,modifier=Modifier.fillMaxWidth().height(140.dp).clickable{viewing=a})
                Text(a.name+if(a.removed)" · Removed from active view" else "",style=MaterialTheme.typography.titleSmall)
                Text(stamp(a.timestamp),style=MaterialTheme.typography.bodySmall)
                Row{TextButton({
                    if(a.mime.startsWith("image/")||a.mime.startsWith("video/"))viewing=a else runCatching{context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(vm.app.media.uri(a),a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{vm.message.value="Install an application that can open ${a.mime}."}
                }){Text(if(a.mime.startsWith("video/"))"Play video" else "Open")}
                    if(!t.state.archived&&!a.removed)TextButton({removing=a}){Text("Remove")}
                }
            } }
        }
        item { HorizontalDivider();Text("Activity timeline",style=MaterialTheme.typography.titleLarge);Text("Newest first · permanent history",style=MaterialTheme.typography.bodySmall) }
        items(s.audit.filter{it.ticketId==t.id},key={it.id}){event->AuditRow(event)}
    }
    if(change)TransitionDialog(vm,t){change=false}
    if(addingNote || note!=null)TextDialog(if(note==null)"Add note" else "Edit note",note?.text.orEmpty(),{addingNote=false;note=null}){text->val id=note?.id;vm.task{vm.repository.saveNote(t.id,text,id)};addingNote=false;note=null}
    if(attaching)AttachmentPicker(vm,t.id,null,{attaching=false},persist=true,onImported={attaching=false})
    removing?.let{a->Confirm("Remove attachment?","The file and its audit record remain available in history.",{removing=null}){vm.task{vm.repository.removeAttachment(a.id)};removing=null}}
    viewing?.takeIf{it.mime.startsWith("image/")}?.let{a->Dialog({viewing=null},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){Column{TextButton({viewing=null}){Text("Close · ${a.name}")};var scale by remember{mutableFloatStateOf(1f)};val gesture=rememberTransformableState{zoom,_,_->scale=(scale*zoom).coerceIn(1f,5f)};AsyncImage(vm.app.media.file(a.file),a.name,Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale).transformable(gesture))}}}}
    viewing?.takeIf{it.mime.startsWith("video/")}?.let{a->Dialog({viewing=null},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){Column{TextButton({viewing=null}){Text("Close · ${a.name}")}
            androidx.compose.ui.viewinterop.AndroidView(factory={ctx->android.widget.VideoView(ctx).apply{
                val controls=android.widget.MediaController(ctx);controls.setAnchorView(this);setMediaController(controls)
                setVideoURI(vm.app.media.uri(a));setOnPreparedListener{start()}
                setOnErrorListener{_,_,_->vm.message.value="This device cannot play this video format.";true}
            }},modifier=Modifier.fillMaxSize(),onRelease={it.stopPlayback()})
        }}
    }}
}
fun displayValue(f:Field,value:String):String=when(f.kind){FieldType.BOOLEAN->when(value){"true"->"Yes";"false"->"No";else->value};FieldType.DATE,FieldType.DATETIME->value.toLongOrNull()?.let{stamp(it)}?:value;FieldType.MULTI->runCatching{choices(value).joinToString(", ")}.getOrDefault(value);else->value}
@Composable
fun TransitionDialog(vm:DeskViewModel,t:Ticket,onDismiss:()->Unit) {
    val targets=Status.entries.filter{if(t.state.archived)!it.archived else it!=t.state}
    var selected by remember{mutableStateOf(if(t.state==Status.HOLD) t.beforeHold else Status.OPEN.name)}
    var reason by remember{mutableStateOf("")};var hold by remember{mutableStateOf("")}
    val target=Status.valueOf(selected)
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(t.state.archived)"Reopen ${t.number}" else "Change status")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Select("New status",selected,targets.map{it.name to it.label},onChange={selected=it})
        OutlinedTextField(reason,{reason=it},label={Text(if(t.state.archived)"Reopening reason *" else "Reason (optional)")},minLines=2)
        if(target==Status.HOLD)OutlinedTextField(hold,{hold=it},label={Text("Hold reason *")},minLines=2)
        if(target.archived)Text("This will archive the request and cancel pending notifications. All history is preserved.")
    }},confirmButton={TextButton({vm.task{vm.repository.transition(t.id,t.revision,target,reason,hold)};onDismiss()},enabled=!busy&&target!=t.state&&(!t.state.archived||reason.isNotBlank())&&(target!=Status.HOLD||hold.isNotBlank())){Text("Confirm")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
@Composable
fun AuditRow(a:Audit) {
    var expanded by remember{mutableStateOf(false)}
    OutlinedCard({expanded=!expanded},Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(stamp(a.timestamp),style=MaterialTheme.typography.labelSmall)
        Text(a.type.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()}+(a.fieldName?.let{" · $it"}?:""),style=MaterialTheme.typography.titleSmall)
        if(a.type in listOf("STATUS_CHANGED","PRIORITY_CHANGED"))Text("${a.oldValue?:"—"} → ${a.newValue?:"—"}")
        a.reason?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
        if(expanded)SelectionContainer{Text("Event: ${a.id}\nUTC: ${Instant.ofEpochMilli(a.timestamp)}\nField ID: ${a.fieldId?:"—"}\nPrevious: ${a.oldValue?:"—"}\nNew: ${a.newValue?:"—"}",style=MaterialTheme.typography.bodySmall)}
        Text(if(expanded)"Hide details" else "View full details",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
    } }
}
