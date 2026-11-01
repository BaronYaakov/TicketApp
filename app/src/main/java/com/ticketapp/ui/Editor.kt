package com.ticketapp.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ticketapp.data.*
import com.ticketapp.domain.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TicketEditor(vm:DeskViewModel,s:Snapshot,ticketId:String,onCancel:()->Unit,onSaved:(String)->Unit,stickyActions:Boolean=false) {
    val existing=s.tickets.find{it.id==ticketId}
    val prefs by vm.preferences.collectAsState()
    val fields=s.fields.filter{it.enabled&&!it.retired}.sortedBy{it.position}
    var draft by rememberSaveable(ticketId,stateSaver=Saver<Draft,String>(save={codec.encodeToString(Draft.serializer(),it)},restore={codec.decodeFromString<Draft>(it)})) { mutableStateOf(
        if(existing==null) (runCatching{codec.decodeFromString<Draft>(prefs["draft"].orEmpty())}.getOrNull()?:Draft()).let { it.copy(values=fields.associate{f->f.id to f.defaultValue}+it.values) }
        else Draft(existing.id,s.values.filter{it.ticketId==existing.id}.associate{it.fieldId to it.value},existing.due)
    ) }
    val revision=rememberSaveable(ticketId){existing?.revision?:0}
    var confirmExit by remember{mutableStateOf(false)}
    var mediaField by remember{mutableStateOf<Field?>(null)}
    var validation by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val busy by vm.busy.collectAsState()
    fun update(d:Draft){draft=d;if(existing==null)vm.draft(d)}
    BackHandler {confirmExit=true}
    val saveActions: @Composable () -> Unit = {
Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton({confirmExit=true},Modifier.weight(1f)){Text("Cancel")}
            Button({
                validation=fields.firstNotNullOfOrNull{f->Rules.validate(f,draft.values[f.id].orEmpty(),draft.attachments.count{it.fieldId==f.id}+s.attachments.count{it.ticketId==draft.id&&it.fieldId==f.id&&!it.removed})}
                if(validation==null) vm.task {
                    val id=vm.repository.save(draft.id,existing!=null,revision,draft.values,draft.due,draft.attachments,draft.reminders)
                    if(existing==null)vm.clearDraft()
                    withContext(Dispatchers.Main){onSaved(id)}
                }
            },Modifier.weight(1f),enabled=!busy){Text("Save ticket")}
        }
    }
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ticket-form"),contentPadding=PaddingValues(16.dp,12.dp,16.dp,32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text(if(existing==null)"Track the request. Keep the whole story." else existing.number,style=MaterialTheme.typography.bodyLarge) }
        if(existing==null && prefs["draft"].orEmpty().isNotBlank())item{Text("New-ticket draft is saved on this device.",style=MaterialTheme.typography.bodySmall)}
        items(fields,key={it.id}) { f -> FieldInput(f,draft.values[f.id].orEmpty(),{update(draft.copy(values=draft.values+(f.id to it)))},{mediaField=f},draft.attachments.count{it.fieldId==f.id}+s.attachments.count{it.ticketId==draft.id&&it.fieldId==f.id&&!it.removed}) }
        items(draft.attachments,key={it.id}) { a -> Row { Text(a.name,Modifier.weight(1f));TextButton({update(draft.copy(attachments=draft.attachments-a))}){Text("Remove")}} }
        item { HorizontalDivider();DateInput("Due",draft.due){update(draft.copy(due=it))} }
        if(existing==null) {
            item { OutlinedButton({pickDate(context,null){update(draft.copy(reminders=draft.reminders+it))}}){Text("Add reminder")}}
            draft.reminders.forEach { at->item { Row { Text(stamp(at),Modifier.weight(1f));TextButton({update(draft.copy(reminders=draft.reminders-at))}){Text("Remove")}} } }
            if(draft.due!=null) item { TextButton({
                val offset=prefs["defaultReminderHours"]?.toLongOrNull()?:24L
                update(draft.copy(reminders=(draft.reminders+(draft.due!!-offset*3600000)).distinct()))
            }){Text("Remind ${prefs["defaultReminderHours"]?:"24"} hours before due date")} }
        }
        validation?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
        if(!stickyActions)item { saveActions() }
    }
    if(stickyActions)Surface(tonalElevation=3.dp,shadowElevation=2.dp) { saveActions() }
    }
    if(confirmExit)Confirm("Leave this form?",if(existing==null)"Your draft will stay saved on this device." else "Unsaved edits will be discarded.",{confirmExit=false}){confirmExit=false;onCancel()}
    mediaField?.let { f -> AttachmentPicker(vm,draft.id,f,{mediaField=null}){a->update(draft.copy(attachments=draft.attachments+a));mediaField=null} }
}
@Composable
fun AttachmentPicker(vm:DeskViewModel,ticketId:String,field:Field?,onDismiss:()->Unit,persist:Boolean=false,onImported:(Attachment)->Unit) {
    var capture by remember{mutableStateOf<Pair<File,Uri>?>(null)}
    fun import(uri:Uri) { vm.task { val a=vm.app.media.import(uri,ticketId,field?.id);if(persist)vm.repository.addAttachment(a);withContext(Dispatchers.Main){capture?.first?.delete();onImported(a)} } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty())vm.task {
            val media=uris.map{vm.app.media.import(it,ticketId,field?.id)}
            if(persist)media.forEach{vm.repository.addAttachment(it)}
            withContext(Dispatchers.Main){media.forEach(onImported)}
        }
    }
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)capture?.second?.let{import(it)}else capture?.first?.delete()}
    val video=rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()){ok->if(ok)capture?.second?.let{import(it)}else capture?.first?.delete()}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Attach ${field?.name?:"media or file"}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton({picker.launch(when(field?.kind){FieldType.PHOTO->arrayOf("image/*");FieldType.VIDEO->arrayOf("video/*");else->arrayOf("*/*")})}){Text("Choose from device")}
        if(field==null || field.kind==FieldType.PHOTO)OutlinedButton({
            runCatching{capture=vm.app.media.capture(false);photo.launch(capture!!.second)}.onFailure{vm.message.value="No camera application is available."}
        }){Text("Take photo")}
        if(field==null || field.kind==FieldType.VIDEO)OutlinedButton({
            runCatching{capture=vm.app.media.capture(true);video.launch(capture!!.second)}.onFailure{vm.message.value="No video camera application is available."}
        }){Text("Record video")}
        Text("Files are copied into private device storage and kept with the ticket history.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onDismiss){Text("Done")}})
}
