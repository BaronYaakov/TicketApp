package com.ticketapp.domain

import com.ticketapp.data.*
import java.math.BigDecimal
import java.net.URI

object Rules {
    fun transition(ticket: Ticket, target: Status, reason: String, holdReason: String) {
        require(ticket.state != target) { "Ticket is already ${target.label}." }
        if (ticket.state.archived) {
            require(!target.archived) { "Reopen to an active status first." }
            require(reason.isNotBlank()) { "A reopening reason is required." }
        }
        if (target == Status.HOLD) require(holdReason.isNotBlank()) { "A hold reason is required." }
    }
    fun validate(field: Field, value: String, attachmentCount: Int = 0): String? {
        if (field.required && (if(field.kind.media) attachmentCount == 0 else value.isBlank() || (field.kind==FieldType.MULTI && runCatching{choices(value).isEmpty()}.getOrDefault(true)))) return "${field.name} is required"
        if (value.isBlank() || field.kind.media) return null
        val valid = when(field.kind) {
            FieldType.INTEGER -> value.toLongOrNull() != null
            FieldType.DECIMAL -> value.toBigDecimalOrNull() != null
            FieldType.BOOLEAN -> value in listOf("true","false")
            FieldType.SINGLE -> value in field.selections
            FieldType.MULTI -> runCatching { choices(value).all { it in field.selections } }.getOrDefault(false)
            FieldType.DATE, FieldType.DATETIME -> value.toLongOrNull()?.let { it >= 0 } == true
            FieldType.URL -> runCatching { URI(value).let { it.scheme in listOf("https","http") && !it.host.isNullOrBlank() } }.getOrDefault(false)
            else -> true
        }
        return if(valid) null else "Enter a valid ${field.kind.label.lowercase()} for ${field.name}"
    }
    fun compatible(old: FieldType, new: FieldType): Boolean = old == new || (old in listOf(FieldType.TEXT,FieldType.LONG_TEXT) && new in listOf(FieldType.TEXT,FieldType.LONG_TEXT)) || (old==FieldType.INTEGER && new==FieldType.DECIMAL) || (old==FieldType.DATE && new==FieldType.DATETIME)
}
data class Query(
    val archived: Boolean = false, val search: String = "", val statuses: Set<String> = emptySet(),
    val filters: Map<String,String> = emptyMap(), val createdFrom: Long? = null, val createdTo: Long? = null,
    val dueFrom: Long? = null, val dueTo: Long? = null, val overdue: Boolean = false,
    val sort: String = "priority", val descending: Boolean = false
)
fun selectTickets(s: Snapshot, q: Query, clock: Long = now()): List<Ticket> {
    val values = s.values.groupBy { it.ticketId }.mapValues { (_,v) -> v.associate { it.fieldId to it.value } }
    val fields=s.fields.associateBy { it.id }
    fun value(t:Ticket,id:String)=values[t.id]?.get(id).orEmpty()
    val found=s.tickets.filter { t ->
        t.state.archived==q.archived && (q.statuses.isEmpty() || t.status in q.statuses) &&
        (q.search.isBlank() || (listOf(t.id,t.number)+values[t.id].orEmpty().values+s.notes.filter{it.ticketId==t.id}.map{it.text}).any { it.contains(q.search,true) }) &&
        q.filters.all { (id,filter) -> filter.isBlank() || value(t,id).contains(filter,true) } &&
        (q.createdFrom==null || t.created>=q.createdFrom) && (q.createdTo==null || t.created<=q.createdTo) &&
        (q.dueFrom==null || (t.due!=null && t.due>=q.dueFrom)) && (q.dueTo==null || (t.due!=null && t.due<=q.dueTo)) &&
        (!q.overdue || (!t.state.archived && t.due!=null && t.due<clock))
    }
    fun key(t:Ticket): Comparable<*>? = when(q.sort) {
        "created" -> t.created; "updated" -> t.updated; "due" -> t.due; "status" -> Status.entries.indexOf(t.state); "id" -> t.number
        else -> value(t,q.sort).takeIf{it.isNotBlank()}?.let { raw -> when(fields[q.sort]?.kind) {
            FieldType.INTEGER,FieldType.DECIMAL -> raw.toBigDecimalOrNull()
            FieldType.DATE,FieldType.DATETIME -> raw.toLongOrNull()
            FieldType.SINGLE -> fields[q.sort]?.selections?.indexOf(raw)?.takeIf{it>=0}
            else -> raw.lowercase()
        } }
    }
    return found.sortedWith { a,b ->
        val x=key(a); val y=key(b)
        val c=when { x==null && y==null -> 0; x==null -> 1; y==null -> -1; else -> {
            @Suppress("UNCHECKED_CAST")
            val result=(x as Comparable<Any>).compareTo(y)
            if(q.descending) -result else result
        } }
        if(c==0) a.id.compareTo(b.id) else c
    }
}
fun eligible(reminder:Reminder,ticket:Ticket,clock:Long)=reminder.enabled && !reminder.delivered && !ticket.state.archived && reminder.at>clock
