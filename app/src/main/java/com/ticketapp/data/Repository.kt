package com.ticketapp.data

import androidx.room.withTransaction
import com.ticketapp.domain.Rules
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class DeskRepository(val db: DeskDatabase) {
    val dao = db.dao()
    // Every emission is read inside one SQLite transaction: the UI never sees half an update.
    val changes = combine(dao.tickets(),dao.fields(),dao.values(),dao.audit()) { _,_,_,_ -> Unit }
    val stream = combine(changes,dao.attachments(),dao.reminders(),dao.notes()) { _,_,_,_ -> snapshot() }
    val gate = Mutex()
    suspend fun snapshot():Snapshot = db.withTransaction {
        Snapshot(tickets=dao.allTickets(),fields=dao.allFields(),values=dao.allValues(),audit=dao.allAudit(),attachments=dao.allAttachments(),reminders=dao.allReminders(),notes=dao.allNotes())
    }
    suspend fun initialize() = gate.withLock { db.withTransaction { if(dao.allFields().isEmpty()) dao.fields(defaultFields()) } }
    private suspend fun active(id:String, revision:Int?=null):Ticket {
        val t=requireNotNull(dao.ticket(id)) { "Ticket no longer exists" }
        require(!t.state.archived) { "Reopen this ticket before editing it." }
        require(revision==null || t.revision==revision) { "Ticket changed. Reopen the editor and try again." }
        return t
    }
    suspend fun save(id:String, existing:Boolean, revision:Int, input:Map<String,String>, due:Long?, media:List<Attachment>, reminderTimes:List<Long> = emptyList()):String = gate.withLock {
        db.withTransaction {
            val old=if(existing) active(id,revision) else null
            if(!existing && dao.ticket(id)!=null) return@withTransaction id // Idempotent repeated submit.
            val definitions=dao.allFields().filter{it.enabled&&!it.retired}
            val previous=dao.allValues().filter{it.ticketId==id}.associate{it.fieldId to it.value}
            val savedMedia=dao.allAttachments().filter{it.ticketId==id&&!it.removed}+media
            definitions.forEach { f ->
                val v=input[f.id].orEmpty()
                val unchanged=old!=null && previous[f.id]==v
                if(!unchanged) Rules.validate(f,v,savedMedia.count{it.fieldId==f.id})?.let { throw IllegalArgumentException(it) }
            }
            val ticket=(old?:Ticket(id=id)).copy(updated=now(),due=due,revision=(old?.revision?:-1)+1)
            dao.ticket(ticket)
            val events=mutableListOf<Audit>()
            if(old==null) events+=Audit(ticketId=id,type="CREATED",newValue=ticket.number)
            definitions.filter{!it.kind.media}.forEach { f ->
                val v=input[f.id].orEmpty(); val before=previous[f.id]
                if(before!=v) {
                    dao.values(listOf(Value(id,f.id,v)))
                    events+=Audit(ticketId=id,type=if(f.id=="priority") "PRIORITY_CHANGED" else "FIELD_CHANGED",fieldId=f.id,fieldName=f.name,oldValue=before,newValue=v)
                }
            }
            if(old?.due!=due) events+=Audit(ticketId=id,type="DUE_CHANGED",oldValue=old?.due?.toString(),newValue=due?.toString())
            media.forEach { a -> dao.attachment(a); events+=Audit(ticketId=id,type="ATTACHMENT_ADDED",fieldId=a.fieldId,newValue=a.name,reason=a.id) }
            reminderTimes.distinct().forEach { at ->
                require(at>now()) { "Reminder must be in the future." }
                val r=Reminder(ticketId=id,at=at);dao.reminder(r);events+=Audit(ticketId=id,type="REMINDER_ADDED",fieldId=r.id,newValue=at.toString())
            }
            if(events.isNotEmpty()) dao.audit(events)
            id
        }
    }
    suspend fun transition(id:String,revision:Int,target:Status,reason:String="",hold:String="") = gate.withLock {
        db.withTransaction {
            val t=requireNotNull(dao.ticket(id))
            require(t.revision==revision) { "Ticket changed. Please try again." }
            Rules.transition(t,target,reason,hold)
            val events=mutableListOf(Audit(ticketId=id,type="STATUS_CHANGED",oldValue=t.state.label,newValue=target.label,reason=reason.ifBlank{null}))
            if(t.state.archived) events+=Audit(ticketId=id,type="REOPENED",oldValue=t.state.label,newValue=target.label,reason=reason.trim())
            if(t.state==Status.HOLD) events+=Audit(ticketId=id,type="RELEASED_FROM_HOLD",oldValue=t.state.label,newValue=target.label)
            if(target==Status.HOLD) events+=Audit(ticketId=id,type="ON_HOLD",oldValue=t.state.label,newValue=target.label,reason=hold.trim())
            if(target.archived) events+=Audit(ticketId=id,type=if(target==Status.ORDERED) "COMPLETED" else "CANCELLED",oldValue=t.state.label,newValue=target.label,reason=reason.ifBlank{null})
            dao.ticket(t.copy(status=target.name,closed=if(target.archived) now() else null,updated=now(),beforeHold=if(target==Status.HOLD) (if(t.state.archived) Status.OPEN.name else t.status) else t.beforeHold,revision=t.revision+1))
            dao.audit(events)
        }
    }
    suspend fun saveNote(id:String,text:String,noteId:String?=null) = gate.withLock {
        require(text.isNotBlank()) { "Enter a note." }
        db.withTransaction {
            val t=active(id)
            val old=dao.allNotes().find{it.id==noteId&&it.ticketId==id}
            if(old?.text==text.trim()) return@withTransaction
            val n=old?.copy(text=text.trim())?:Note(ticketId=id,text=text.trim())
            dao.note(n); dao.ticket(t.copy(updated=now(),revision=t.revision+1))
            dao.audit(listOf(Audit(ticketId=id,type=if(old==null) "NOTE_ADDED" else "NOTE_EDITED",fieldId=n.id,fieldName="Note",oldValue=old?.text,newValue=n.text)))
        }
    }
    suspend fun addAttachment(a:Attachment) = gate.withLock { db.withTransaction {
        val t=active(a.ticketId)
        if(dao.allAttachments().any{it.id==a.id}) return@withTransaction
        dao.attachment(a); dao.ticket(t.copy(updated=now(),revision=t.revision+1))
        dao.audit(listOf(Audit(ticketId=a.ticketId,type="ATTACHMENT_ADDED",fieldId=a.fieldId,newValue=a.name,reason=a.id)))
    } }
    suspend fun removeAttachment(id:String) = gate.withLock { db.withTransaction {
        val a=dao.allAttachments().first{it.id==id}; val t=active(a.ticketId)
        if(a.removed) return@withTransaction
        dao.attachment(a.copy(removed=true)); dao.ticket(t.copy(updated=now(),revision=t.revision+1))
        dao.audit(listOf(Audit(ticketId=a.ticketId,type="ATTACHMENT_REMOVED",fieldId=a.fieldId,oldValue=a.name,reason=a.id)))
    } }
    suspend fun saveReminder(id:String,at:Long) = gate.withLock { db.withTransaction {
        val t=active(id); require(at>now()) { "Choose a future reminder time." }
        if(dao.allReminders().any{it.ticketId==id&&it.at==at&&it.enabled}) return@withTransaction
        val r=Reminder(ticketId=id,at=at); dao.reminder(r)
        dao.ticket(t.copy(updated=now(),revision=t.revision+1))
        dao.audit(listOf(Audit(ticketId=id,type="REMINDER_ADDED",fieldId=r.id,newValue=at.toString())))
    } }
    suspend fun rescheduleReminder(id:String,at:Long) = gate.withLock { db.withTransaction {
        val old=dao.allReminders().first{it.id==id};val ticket=active(old.ticketId)
        require(old.enabled && !old.delivered) { "Only pending reminders can be rescheduled." }
        require(at>now()) { "Choose a future reminder time." }
        dao.reminder(old.copy(enabled=false))
        val replacement=Reminder(ticketId=old.ticketId,at=at);dao.reminder(replacement)
        dao.ticket(ticket.copy(updated=now(),revision=ticket.revision+1))
        dao.audit(listOf(Audit(ticketId=old.ticketId,type="REMINDER_RESCHEDULED",fieldId=replacement.id,oldValue=old.at.toString(),newValue=at.toString())))
    } }
    suspend fun cancelReminder(id:String) = gate.withLock { db.withTransaction {
        val r=dao.allReminders().first{it.id==id}; val ticket=active(r.ticketId)
        if(!r.enabled) return@withTransaction
        dao.reminder(r.copy(enabled=false));dao.ticket(ticket.copy(updated=now(),revision=ticket.revision+1)); dao.audit(listOf(Audit(ticketId=r.ticketId,type="REMINDER_CANCELLED",fieldId=r.id,oldValue=r.at.toString())))
    } }
    suspend fun saveField(field:Field,confirm:Boolean=false) = gate.withLock { db.withTransaction {
        require(field.name.isNotBlank()) { "Field name is required." }
        require(field.id!="priority" || field.kind==FieldType.SINGLE) { "Priority must remain a single-select dropdown." }
        require(!field.kind.media || field.defaultValue.isBlank()) { "Attachments cannot have default values." }
        if(field.kind in listOf(FieldType.SINGLE,FieldType.MULTI)) require(field.selections.isNotEmpty() && field.selections.distinct().size==field.selections.size && field.selections.none{it.isBlank()}) { "Supply unique, nonempty dropdown options." }
        Rules.validate(field.copy(required=false),field.defaultValue)?.let{throw IllegalArgumentException("Invalid default: $it")}
        val old=dao.allFields().find{it.id==field.id}
        if(old==field) return@withTransaction
        val values=dao.allValues().filter{it.fieldId==field.id&&it.value.isNotEmpty()}
        val affected=if(old==null) emptyList() else values.filter{!Rules.compatible(old.kind,field.kind) || Rules.validate(field.copy(required=false),it.value)!=null}
        val destructive=affected.isNotEmpty()
        require(!destructive || confirm) { "CONFIRM_CONVERSION" }
        if(destructive) {
            affected.forEach { v ->
                dao.audit(listOf(Audit(ticketId=v.ticketId,type="FIELD_VALUE_RETAINED",fieldId=old!!.id,fieldName=old.name,oldValue=v.value,newValue="",reason="Field configuration changed. Prior value retained here.")))
                dao.values(listOf(v.copy(value="")))
                val ticket=requireNotNull(dao.ticket(v.ticketId))
                dao.ticket(ticket.copy(updated=now(),revision=ticket.revision+1))
            }
        }
        dao.fields(listOf(field.copy(name=field.name.trim())))
        dao.audit(listOf(Audit(type="FIELD_CONFIGURATION",fieldId=field.id,fieldName=field.name,oldValue=old?.let{codec.encodeToString(Field.serializer(),it)},newValue=codec.encodeToString(Field.serializer(),field))))
    } }
    suspend fun reorder(ids:List<String>) = gate.withLock { db.withTransaction {
        val old=dao.allFields(); val mapped=old.associateBy{it.id}
        require(ids.toSet()==mapped.keys && ids.size==old.size)
        dao.fields(ids.mapIndexed{i,id->mapped.getValue(id).copy(position=i)})
        dao.audit(listOf(Audit(type="FIELDS_REORDERED",oldValue=packed(old.map{it.id}),newValue=packed(ids))))
    } }
    suspend fun settingsAudit(name:String,before:String?,after:String)=gate.withLock {
        if(before!=after) dao.audit(listOf(Audit(type="SETTING_CHANGED",fieldName=name,oldValue=before,newValue=after)))
    }
}
