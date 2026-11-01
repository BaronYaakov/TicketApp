package com.ticketapp.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ticketapp.TicketApplication
import com.ticketapp.backup.*
import com.ticketapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class Draft(val id:String=uid(),val values:Map<String,String> = emptyMap(),val due:Long?=null,val attachments:List<Attachment> = emptyList(),val reminders:List<Long> = emptyList())
class DeskViewModel(application:Application):AndroidViewModel(application) {
    val app=application as TicketApplication
    val repository=app.repository
    val data=combine(repository.stream,app.preferences.flow,app.initialized) { snapshot, _, ready -> if(ready)snapshot else Snapshot() }.stateIn(viewModelScope,SharingStarted.Eagerly,Snapshot())
    val preferences=app.preferences.flow.stateIn(viewModelScope,SharingStarted.Eagerly,emptyMap())
    val backup=BackupService(app,repository,app.preferences,app.media)
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    val selected=MutableStateFlow<String?>(null)
    var draftJob:Job?=null
    fun task(success:String?=null,block:suspend ()->Unit) {
        if(busy.value) return
        busy.value=true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO){block();app.scheduler.reconcile(repository.snapshot(),app.preferences.all()["notifications"]!="false")}; if(success!=null)message.value=success }
            catch(e:Exception){message.value=e.message?:"Operation failed"}
            finally { busy.value=false }
        }
    }
    fun preference(key:String,value:String)=task { val old=app.preferences.all()[key];app.preferences.set(key,value);repository.settingsAudit(key,old,value) }
    fun draft(d:Draft) { draftJob?.cancel();draftJob=viewModelScope.launch { delay(250);app.preferences.set("draft",codec.encodeToString(d)) } }
    suspend fun clearDraft(){draftJob?.cancel();app.preferences.set("draft","")}
    fun import(uri:Uri,ticket:String,field:String?,onImported:(Attachment)->Unit) = task {
        val a=app.media.import(uri,ticket,field)
        withContext(Dispatchers.Main){onImported(a)}
    }
    fun writeBackup(uri:Uri)=task("Complete backup saved") { backup.write(requireNotNull(app.contentResolver.openOutputStream(uri))) }
    fun csv(uri:Uri)=task("CSV exported") { requireNotNull(app.contentResolver.openOutputStream(uri)).bufferedWriter().use{it.write(BackupValidation.csv(repository.snapshot()))} }
}
