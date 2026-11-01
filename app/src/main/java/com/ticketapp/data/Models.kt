package com.ticketapp.data

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

val codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }
fun uid(): String = UUID.randomUUID().toString()
fun now(): Long = Instant.now().toEpochMilli()
fun choices(raw: String): List<String> = codec.decodeFromString(raw)
fun packed(values: List<String>): String = codec.encodeToString(values)

enum class FieldType(val label: String) {
    TEXT("Short text"), LONG_TEXT("Long text"), INTEGER("Integer number"), DECIMAL("Decimal number"),
    BOOLEAN("Yes / No"), SINGLE("Single select"), MULTI("Multiple select"), DATE("Date"), DATETIME("Date & time"),
    PHOTO("Photo"), VIDEO("Video"), FILE("File attachment"), URL("URL");
    val media: Boolean get() = this in listOf(PHOTO, VIDEO, FILE)
}
enum class Status(val label: String, val archived: Boolean = false) {
    OPEN("Open"), QUOTE("Awaiting Quote"), APPROVAL("Awaiting Approval"), HOLD("On Hold"), ORDERED("Ordered", true), CANCELLED("Cancelled", true)
}
@Serializable
@Entity(tableName = "tickets")
data class Ticket(
    @PrimaryKey val id: String = uid(), val created: Long = now(), val updated: Long = created,
    val status: String = Status.OPEN.name, val closed: Long? = null, val due: Long? = null,
    val beforeHold: String = Status.OPEN.name, val revision: Int = 0
) { val state: Status get() = Status.valueOf(status); val number: String get() = "PD-${id.take(8).uppercase()}" }
@Serializable
@Entity(tableName = "fields")
data class Field(
    @PrimaryKey val id: String = uid(), val name: String, val type: String = FieldType.TEXT.name,
    val required: Boolean = false, val placeholder: String = "", val helper: String = "", val defaultValue: String = "",
    val options: String = "[]", val position: Int = 0, val enabled: Boolean = true, val retired: Boolean = false
) { val kind: FieldType get() = FieldType.valueOf(type); val selections: List<String> get() = choices(options) }
@Serializable
@Entity(tableName = "field_values", primaryKeys = ["ticketId", "fieldId"],
    foreignKeys = [ForeignKey(entity=Ticket::class,parentColumns=["id"],childColumns=["ticketId"],onDelete=ForeignKey.RESTRICT), ForeignKey(entity=Field::class,parentColumns=["id"],childColumns=["fieldId"],onDelete=ForeignKey.RESTRICT)], indices=[Index("fieldId")])
data class Value(val ticketId: String, val fieldId: String, val value: String)
@Serializable
@Entity(tableName = "audit", indices = [Index("ticketId"), Index("timestamp")])
data class Audit(
    @PrimaryKey val id: String = uid(), val ticketId: String? = null, val type: String,
    val timestamp: Long = now(), val fieldId: String? = null, val fieldName: String? = null,
    val oldValue: String? = null, val newValue: String? = null, val reason: String? = null
)
@Serializable
@Entity(tableName = "attachments", foreignKeys=[ForeignKey(entity=Ticket::class,parentColumns=["id"],childColumns=["ticketId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("ticketId")])
data class Attachment(@PrimaryKey val id: String = uid(), val ticketId: String, val fieldId: String? = null,
    val name: String, val mime: String, val file: String, val timestamp: Long = now(), val removed: Boolean = false)
@Serializable
@Entity(tableName = "reminders",foreignKeys=[ForeignKey(entity=Ticket::class,parentColumns=["id"],childColumns=["ticketId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("ticketId")])
data class Reminder(@PrimaryKey val id: String = uid(), val ticketId: String, val at: Long,
    val delivered: Boolean = false, val enabled: Boolean = true)
@Serializable
@Entity(tableName = "notes",foreignKeys=[ForeignKey(entity=Ticket::class,parentColumns=["id"],childColumns=["ticketId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("ticketId")])
data class Note(@PrimaryKey val id: String = uid(), val ticketId: String, val text: String, val timestamp: Long = now())
@Serializable
data class Snapshot(val version: Int = 1, val tickets: List<Ticket> = emptyList(), val fields: List<Field> = emptyList(),
    val values: List<Value> = emptyList(), val audit: List<Audit> = emptyList(), val attachments: List<Attachment> = emptyList(),
    val reminders: List<Reminder> = emptyList(), val notes: List<Note> = emptyList(), val preferences: Map<String,String> = emptyMap())

fun defaultFields() = listOf(
    Field("title", "Title", required=true), Field("description","Description",FieldType.LONG_TEXT.name),
    Field("requester","Requester"), Field("equipment","Equipment"), Field("part","Part Number"),
    Field("quantity","Quantity Requested",FieldType.INTEGER.name),
    Field("priority","Priority",FieldType.SINGLE.name, defaultValue="Standard Leadtime",options=packed(listOf("Line Down","Imminent Failure","Weekend PM Item Request","Standard Leadtime"))),
    Field("notes","Notes",FieldType.LONG_TEXT.name), Field("photos","Photos",FieldType.PHOTO.name), Field("videos","Videos",FieldType.VIDEO.name)
).mapIndexed { i,f -> f.copy(position=i) }
