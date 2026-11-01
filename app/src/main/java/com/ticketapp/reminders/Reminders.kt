package com.ticketapp.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.*
import com.ticketapp.*
import com.ticketapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context:Context) {
    private val work=WorkManager.getInstance(context)
    suspend fun reconcile(s:Snapshot,notifications:Boolean=true,force:Boolean=false) {
        s.reminders.forEach { r ->
            val ticket=s.tickets.find{it.id==r.ticketId}
            val name="reminder-${r.id}"
            if(ticket==null || ticket.state.archived || !r.enabled || r.delivered || !notifications) {
                work.cancelUniqueWork(name)
                if(ticket==null || ticket.state.archived || !r.enabled) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(r.id,0)
            } else {
                val request=OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInputData(workDataOf("id" to r.id)).setInitialDelay((r.at-now()).coerceAtLeast(0),TimeUnit.MILLISECONDS).build()
                work.enqueueUniqueWork(name,if(force)ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,request)
            }
        }
    }
    fun maintenance() {
        work.enqueueUniquePeriodicWork("reconcile-reminders",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ReconcileWorker>(15,TimeUnit.MINUTES).build())
    }
    fun cancelAll() { work.cancelAllWork() }
}
class ReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as TicketApplication
        if(withTimeoutOrNull(30000){app.initialized.first{it}}!=true)return Result.retry()
        val id=inputData.getString("id")?:return Result.failure()
        if(app.preferences.all()["notifications"]=="false") return Result.success()
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return Result.success()
        var notYetDue=false
        return try { app.repository.gate.withLock { app.database.withTransaction {
            val dao=app.database.dao(); val r=dao.allReminders().find{it.id==id}?:return@withTransaction
            val t=dao.ticket(r.ticketId)?:return@withTransaction
            if(r.delivered || !r.enabled || t.state.archived) return@withTransaction
            if(r.at>now()) { notYetDue=true;return@withTransaction }
            val title=dao.allValues().find{it.ticketId==t.id&&it.fieldId=="title"}?.value?.ifBlank{null}?:t.number
            val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("purchasing","Purchasing reminders",NotificationManager.IMPORTANCE_DEFAULT))
            val intent=Intent(applicationContext,MainActivity::class.java).putExtra("ticket",t.id).setAction("ticket:${t.id}")
            val pending=PendingIntent.getActivity(applicationContext,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(applicationContext,"purchasing").setSmallIcon(com.ticketapp.R.drawable.ic_ticket)
                .setContentTitle(title).setContentText("${t.number} · ${t.state.label}").setContentIntent(pending).setAutoCancel(true).build()
            manager.notify(r.id,0,notification)
            dao.reminder(r.copy(delivered=true))
            dao.audit(listOf(Audit(ticketId=t.id,type="REMINDER_DELIVERED",fieldId=r.id,newValue=r.at.toString())))
        } }; if(notYetDue) Result.retry() else Result.success() } catch(e:Exception) { Result.retry() }
    }
}
class ReconcileWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as TicketApplication
        if(withTimeoutOrNull(30000){app.initialized.first{it}}!=true)return Result.retry()
        return try {
            app.scheduler.reconcile(app.repository.snapshot(),app.preferences.all()["notifications"]!="false",inputData.getBoolean("force",false))
            Result.success()
        }catch(e:Exception){Result.retry()}
    }
}
class ScheduleReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED)) return
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReconcileWorker>().setInputData(workDataOf("force" to true)).build())
    }
}
