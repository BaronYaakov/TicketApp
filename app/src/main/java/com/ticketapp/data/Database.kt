package com.ticketapp.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface DeskDao {
    @Query("SELECT * FROM tickets") fun tickets(): Flow<List<Ticket>>
    @Query("SELECT * FROM fields ORDER BY position") fun fields(): Flow<List<Field>>
    @Query("SELECT * FROM field_values") fun values(): Flow<List<Value>>
    @Query("SELECT * FROM audit ORDER BY timestamp DESC, rowid DESC") fun audit(): Flow<List<Audit>>
    @Query("SELECT * FROM attachments") fun attachments(): Flow<List<Attachment>>
    @Query("SELECT * FROM reminders") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM notes") fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM tickets") suspend fun allTickets(): List<Ticket>
    @Query("SELECT * FROM fields ORDER BY position") suspend fun allFields(): List<Field>
    @Query("SELECT * FROM field_values") suspend fun allValues(): List<Value>
    @Query("SELECT * FROM audit ORDER BY timestamp DESC, rowid DESC") suspend fun allAudit(): List<Audit>
    @Query("SELECT * FROM attachments") suspend fun allAttachments(): List<Attachment>
    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<Reminder>
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>
    @Query("SELECT * FROM tickets WHERE id=:id") suspend fun ticket(id:String): Ticket?
    @Upsert suspend fun ticket(value:Ticket)
    @Upsert suspend fun fields(value:List<Field>)
    @Upsert suspend fun values(value:List<Value>)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun audit(value:List<Audit>)
    @Upsert suspend fun attachment(value:Attachment)
    @Upsert suspend fun reminder(value:Reminder)
    @Upsert suspend fun note(value:Note)
}
@Database(entities=[Ticket::class,Field::class,Value::class,Audit::class,Attachment::class,Reminder::class,Note::class],version=2,exportSchema=true)
abstract class DeskDatabase: RoomDatabase() {
    abstract fun dao(): DeskDao
    companion object {
        // Version 2 introduces query indexes only; all version 1 rows survive unchanged.
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) { db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_timestamp ON audit(timestamp)") }
        }
    }
}
