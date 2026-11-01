package com.ticketapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ticketapp.data.*
import com.ticketapp.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db:DeskDatabase
    private lateinit var repo:DeskRepository
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup()=runBlocking {db=Room.inMemoryDatabaseBuilder(context,DeskDatabase::class.java).build();repo=DeskRepository(db);repo.initialize()}
    @After fun close(){db.close()}
    private suspend fun create(id:String="ticket"){repo.save(id,false,0,mapOf("title" to "Bearing","priority" to "Line Down"),null,emptyList())}
    @Test fun createEditAndAtomicAudit()=runBlocking {
        create();var s=repo.snapshot();assertEquals(1,s.tickets.size);assertEquals("Bearing",s.values.first{it.fieldId=="title"}.value)
        repo.save("ticket",true,0,mapOf("title" to "Seal","priority" to "Standard Leadtime"),1000,emptyList())
        s=repo.snapshot();val change=s.audit.first{it.fieldId=="title"&&it.oldValue=="Bearing"};assertEquals("Seal",change.newValue)
        val count=s.audit.size
        try{repo.save("ticket",true,1,mapOf("title" to ""),null,emptyList());fail()}catch(_:IllegalArgumentException){}
        s=repo.snapshot();assertEquals(count,s.audit.size);assertEquals("Seal",s.values.first{it.fieldId=="title"}.value)
        assertEquals(1000L,s.tickets.single().due)
    }
    @Test fun failingAuditInsertRollsBackTheFieldAndTicketTogether()=runBlocking {
        create();val before=repo.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_audit BEFORE INSERT ON audit WHEN NEW.newValue = 'explode' BEGIN SELECT RAISE(ABORT,'simulated interruption'); END")
        var failed=false
        try{repo.save("ticket",true,0,mapOf("title" to "explode"),null,emptyList())}catch(_:Exception){failed=true}
        assertTrue(failed);val after=repo.snapshot()
        assertEquals(before.tickets,after.tickets);assertEquals(before.values.toSet(),after.values.toSet());assertEquals(before.audit,after.audit)
    }
    @Test fun repeatedClosureReopeningAndHoldPreserveAllHistory()=runBlocking {
        create()
        repeat(3){
            var t=repo.snapshot().tickets.single();repo.transition(t.id,t.revision,Status.HOLD,hold="Hold $it")
            t=repo.snapshot().tickets.single();repo.transition(t.id,t.revision,Status.ORDERED)
            t=repo.snapshot().tickets.single();repo.transition(t.id,t.revision,Status.APPROVAL,"Reopen $it")
        }
        val s=repo.snapshot();assertEquals(3,s.audit.count{it.type=="ON_HOLD"});assertEquals(3,s.audit.count{it.type=="COMPLETED"});assertEquals(3,s.audit.count{it.type=="REOPENED"});assertFalse(s.tickets.single().state.archived)
    }
    @Test fun doubleSubmissionIsIdempotentAndStaleEditsFail()=runBlocking {
        create();create();assertEquals(1,repo.snapshot().audit.count{it.type=="CREATED"})
        repo.transition("ticket",0,Status.CANCELLED)
        try{repo.transition("ticket",0,Status.ORDERED);fail()}catch(_:IllegalArgumentException){}
        try{repo.save("ticket",true,1,mapOf("title" to "Bad edit"),null,emptyList());fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun fieldRenameRetirementAndConversionPreserveValues()=runBlocking {
        create();var f=repo.snapshot().fields.first{it.id=="title"}
        repo.saveField(f.copy(name="Request title"));f=repo.snapshot().fields.first{it.id=="title"}
        repo.saveField(f.copy(retired=true));assertEquals("Bearing",repo.snapshot().values.first{it.fieldId=="title"}.value)
        val incompatible=f.copy(type=FieldType.INTEGER.name,required=false)
        try{repo.saveField(incompatible);fail()}catch(_:IllegalArgumentException){}
        repo.saveField(incompatible,true)
        val s=repo.snapshot();assertEquals("",s.values.first{it.fieldId=="title"}.value);assertEquals("Bearing",s.audit.first{it.type=="FIELD_VALUE_RETAINED"}.oldValue);assertEquals(1,s.tickets.single().revision)
        assertTrue(s.audit.any{it.fieldName=="Title"&&it.newValue=="Bearing"})
    }
    @Test fun notesAttachmentsAndRemindersAreAuditedAndRetained()=runBlocking {
        create();repo.saveNote("ticket","Vendor called");val n=repo.snapshot().notes.single();repo.saveNote("ticket","Vendor confirmed",n.id)
        val a=Attachment(ticketId="ticket",name="Photo",mime="image/jpeg",file=uid());repo.addAttachment(a);repo.removeAttachment(a.id)
        val at=now()+3600000;repo.saveReminder("ticket",at);repo.saveReminder("ticket",at)
        var s=repo.snapshot();assertEquals(1,s.reminders.size);assertTrue(s.attachments.single().removed)
        val reminder=s.reminders.single();val revision=s.tickets.single().revision
        repo.rescheduleReminder(reminder.id,at+3600000);s=repo.snapshot()
        assertEquals(revision+1,s.tickets.single().revision);assertFalse(s.reminders.first{it.id==reminder.id}.enabled)
        repo.cancelReminder(s.reminders.first{it.enabled}.id);s=repo.snapshot()
        assertEquals(revision+2,s.tickets.single().revision);assertTrue(s.audit.any{it.type=="REMINDER_RESCHEDULED"})
        assertTrue(s.audit.any{it.type=="NOTE_EDITED"&&it.oldValue=="Vendor called"});assertTrue(s.audit.any{it.type=="ATTACHMENT_REMOVED"});assertTrue(s.audit.any{it.type=="REMINDER_CANCELLED"})
    }
    @Test fun migrationOneToTwoKeepsTicketsAndAudit()=runBlocking {
        val name="migration-test-${uid()}.db"
        val original=Room.databaseBuilder(context,DeskDatabase::class.java,name).build()
        val repository=DeskRepository(original);repository.initialize()
        repository.save("retained",false,0,mapOf("title" to "Retained through migration"),null,emptyList())
        original.openHelper.writableDatabase.execSQL("DROP INDEX index_audit_timestamp")
        original.openHelper.writableDatabase.version=1
        original.close()
        val upgraded=Room.databaseBuilder(context,DeskDatabase::class.java,name).addMigrations(DeskDatabase.MIGRATION_1_2).build()
        assertEquals("retained",upgraded.dao().allTickets().single().id)
        assertTrue(upgraded.dao().allAudit().any{it.type=="CREATED"})
        assertEquals("Retained through migration",upgraded.dao().allValues().first{it.fieldId=="title"}.value)
        upgraded.close();context.deleteDatabase(name);Unit
    }
    @Test fun interruptedRestoreRecoversSettingsFromTheDatabaseCommitMarker()=runBlocking {
        create();val prefs=DeskPreferences(context);val original=prefs.all()
        val service=BackupService(context,repo,prefs,MediaStore(context))
        val journal=android.util.AtomicFile(File(context.filesDir,"restore-journal.json"))
        suspend fun simulate(committed:Boolean) {
            val record=RestoreJournal(uid(),mapOf("theme" to "Light"),mapOf("theme" to "Dark"))
            val out=journal.startWrite();out.write(codec.encodeToString(RestoreJournal.serializer(),record).toByteArray());journal.finishWrite(out)
            prefs.replace(record.after)
            if(committed)repo.dao.audit(listOf(Audit(id=record.id,type="BACKUP_RESTORED")))
            service.recover()
            assertEquals(if(committed)"Dark" else "Light",prefs.all()["theme"])
            assertFalse(journal.baseFile.exists())
        }
        try {simulate(false);simulate(true)}finally{journal.delete();prefs.replace(original)}
    }
    @Test fun corruptedBackupIsRejectedBeforeAnyDataChanges()=runBlocking {
        create();val media=MediaStore(context);val service=BackupService(context,repo,DeskPreferences(context),media)
        val file=File(File(context.cacheDir,"capture").apply{mkdirs()},"corrupt.zip")
        java.util.zip.ZipOutputStream(file.outputStream()).use{zip->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"));zip.write("{\"version\":1,\"hashes\":{\"data.json\":\"wrong-hash\"}}".toByteArray());zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("data.json"));zip.write("{}".toByteArray());zip.closeEntry()
        }
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        val before=repo.snapshot();var rejected=false
        try{service.inspect(uri)}catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected);assertEquals(before,repo.snapshot())
    }
    @Test fun backupRoundTripIncludesRemovedMediaAndFullAudit()=runBlocking {
        create();val media=MediaStore(context);val a=Attachment(ticketId="ticket",name="manual",mime="text/plain",file=uid());media.file(a.file).writeText("Original durable media");repo.addAttachment(a);repo.removeAttachment(a.id)
        val prefs=DeskPreferences(context);prefs.set("theme","Dark")
        val service=BackupService(context,repo,prefs,media)
        val file=File(context.cacheDir,"roundtrip.zip");service.write(file.outputStream())
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",File(File(context.cacheDir,"capture").apply{mkdirs()},"backup.zip").apply{file.copyTo(this,true)})
        val valid=service.inspect(uri);assertEquals("Dark",valid.snapshot.preferences["theme"])
        create("second");service.restore(valid)
        val restored=repo.snapshot();assertEquals(1,restored.tickets.size);assertTrue(restored.attachments.single().removed);assertEquals("Original durable media",media.file(restored.attachments.single().file).readText());assertTrue(restored.audit.any{it.type=="ATTACHMENT_REMOVED"})
    }
}
