package com.ticketapp

import android.app.Application
import androidx.room.Room
import com.ticketapp.data.*
import com.ticketapp.reminders.ReminderScheduler
import kotlinx.coroutines.*

class TicketApplication:Application() {
    val initialized=kotlinx.coroutines.flow.MutableStateFlow(false)
    val startupError=kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this,DeskDatabase::class.java,"purchasing.db").addMigrations(DeskDatabase.MIGRATION_1_2).build() }
    val repository by lazy { DeskRepository(database) }
    val preferences by lazy { DeskPreferences(this) }
    val media by lazy { MediaStore(this) }
    val scheduler by lazy { ReminderScheduler(this) }
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
            com.ticketapp.backup.BackupService(this@TicketApplication,repository,preferences,media).recover()
            repository.initialize()
            initialized.value=true
            scheduler.maintenance()
            scheduler.reconcile(repository.snapshot(),preferences.all()["notifications"]!="false")
            }catch(e:Exception){startupError.value=e.message?:"Local storage could not be opened"}
        }
    }
}
