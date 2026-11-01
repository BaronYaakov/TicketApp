package com.ticketapp

import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.ticketapp.data.*
import com.ticketapp.reminders.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TicketApplication
    private suspend fun setup(): Pair<Ticket,Reminder> {
        val repo=app.repository;repo.initialize();val id=uid()
        repo.save(id,false,0,mapOf("title" to "Reminder test $id"),null,emptyList())
        repo.saveReminder(id,now()+3600000)
        val s=repo.snapshot();return s.tickets.first{it.id==id} to s.reminders.first{it.ticketId==id}
    }
    private suspend fun runWorker(id:String)=TestListenableWorkerBuilder<ReminderWorker>(app).setInputData(workDataOf("id" to id)).build().doWork()
    @Test fun deliveryIsAuditedAndRepeatedWorkDoesNotDuplicate()=runBlocking {
        val (t,r)=setup();app.repository.dao.reminder(r.copy(at=now()-1000))
        runWorker(r.id);runWorker(r.id)
        val s=app.repository.snapshot();assertTrue(s.reminders.first{it.id==r.id}.delivered);assertEquals(1,s.audit.count{it.ticketId==t.id&&it.type=="REMINDER_DELIVERED"})
        val manager=app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification=manager.activeNotifications.first{it.tag==r.id}
        assertEquals("Reminder test ${t.id}",notification.notification.extras.getString(Notification.EXTRA_TITLE))
        manager.cancel(r.id,0)
    }
    @Test fun archivedAndFutureRemindersDoNotNotify()=runBlocking {
        val(t,r)=setup();assertEquals(ListenableWorker.Result.retry(),runWorker(r.id))
        app.repository.transition(t.id,app.repository.snapshot().tickets.first{it.id==t.id}.revision,Status.ORDERED)
        app.repository.dao.reminder(r.copy(at=now()-1000));runWorker(r.id)
        assertFalse(app.repository.snapshot().reminders.first{it.id==r.id}.delivered)
    }
    @Test fun schedulesAreUniqueAndCancellationTracksArchivalAndReopening()=runBlocking {
        val(t,r)=setup();val work=WorkManager.getInstance(app)
        app.scheduler.reconcile(app.repository.snapshot());app.scheduler.reconcile(app.repository.snapshot())
        withTimeout(20000){while(work.getWorkInfosForUniqueWork("reminder-${r.id}").get().count{!it.state.isFinished}!=1)delay(50)}
        var ticket=app.repository.snapshot().tickets.first{it.id==t.id};app.repository.transition(t.id,ticket.revision,Status.CANCELLED);app.scheduler.reconcile(app.repository.snapshot())
        withTimeout(20000){while(work.getWorkInfosForUniqueWork("reminder-${r.id}").get().any{!it.state.isFinished})delay(50)}
        ticket=app.repository.snapshot().tickets.first{it.id==t.id};app.repository.transition(t.id,ticket.revision,Status.OPEN,"Still required");app.scheduler.reconcile(app.repository.snapshot())
        withTimeout(20000){while(work.getWorkInfosForUniqueWork("reminder-${r.id}").get().none{!it.state.isFinished})delay(50)}
        app.repository.cancelReminder(r.id);app.scheduler.reconcile(app.repository.snapshot())
    }
}
