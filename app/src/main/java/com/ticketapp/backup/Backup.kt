package com.ticketapp.backup

import android.content.Context
import android.net.Uri
import com.ticketapp.data.*
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.*
import java.security.MessageDigest
import java.util.zip.*

@Serializable
data class RestoreJournal(val id:String,val before:Map<String,String>,val after:Map<String,String>)
@Serializable
data class BackupManifest(val version:Int=1,val hashes:Map<String,String>)
fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
fun fileHash(file:File):String {
    val digest=MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input-> val buffer=ByteArray(65536); while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
    return digest.digest().joinToString(""){"%02x".format(it)}
}
object BackupValidation {
    fun snapshot(s:Snapshot) {
        require(s.version==1) { "Unsupported backup version" }
        fun unique(ids:List<String>) { require(ids.distinct().size==ids.size && ids.none{it.isBlank()}) { "Duplicate or empty identifiers" } }
        unique(s.tickets.map{it.id});unique(s.fields.map{it.id});unique(s.audit.map{it.id});unique(s.attachments.map{it.id});unique(s.notes.map{it.id});unique(s.reminders.map{it.id})
        require(s.values.map{it.ticketId to it.fieldId}.distinct().size==s.values.size)
        val tickets=s.tickets.map{it.id}.toSet();val fields=s.fields.map{it.id}.toSet()
        require(fields.containsAll(defaultFields().map{it.id})) { "Backup is missing built-in field definitions" }
        s.fields.forEach { it.kind;it.selections }
        s.tickets.forEach { require(it.state.archived==(it.closed!=null));require(it.revision>=0) }
        s.values.forEach { require(it.ticketId in tickets && it.fieldId in fields) }
        s.audit.forEach { require(it.ticketId==null || it.ticketId in tickets) }
        s.attachments.forEach { require(it.ticketId in tickets && (it.fieldId==null || it.fieldId in fields));require(it.file.matches(Regex("[A-Za-z0-9_-]+"))) }
        s.notes.forEach { require(it.ticketId in tickets) };s.reminders.forEach { require(it.ticketId in tickets) }
    }
    fun csv(s:Snapshot):String {
        val fields=s.fields.sortedBy{it.position};val values=s.values.groupBy{it.ticketId}
        fun cell(value:String):String {
            // Neutralize spreadsheet formulas in user-entered cells.
            val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$value" else value
            return "\""+safe.replace("\"","\"\"")+"\""
        }
        val rows=listOf(listOf("Ticket ID","Status","Created (UTC)","Updated (UTC)","Due (UTC)")+fields.map{it.name})+s.tickets.map { t ->
            val v=values[t.id].orEmpty().associate{it.fieldId to it.value}
            listOf(t.number,t.state.label,java.time.Instant.ofEpochMilli(t.created).toString(),java.time.Instant.ofEpochMilli(t.updated).toString(),t.due?.let{java.time.Instant.ofEpochMilli(it).toString()}.orEmpty())+fields.map{v[it.id].orEmpty()}
        }
        return rows.joinToString("\r\n"){row->row.joinToString(","){cell(it)}}
    }
}
class BackupService(private val context:Context,private val repository:DeskRepository,private val prefs:DeskPreferences,private val media:MediaStore) {
    private val journal get()=android.util.AtomicFile(File(context.filesDir,"restore-journal.json"))
    suspend fun recover() = repository.gate.withLock {
        if(journal.baseFile.exists() || File(journal.baseFile.path+".bak").exists()) {
            val record=journal.openRead().bufferedReader().use{codec.decodeFromString<RestoreJournal>(it.readText())}
            val committed=repository.dao.allAudit().any{it.id==record.id}
            prefs.replace(if(committed)record.after else record.before)
            journal.delete()
        }
    }
    private fun saveJournal(record:RestoreJournal) {
        val out=journal.startWrite()
        try { out.write(codec.encodeToString(record).toByteArray());journal.finishWrite(out) }
        catch(e:Exception){journal.failWrite(out);throw e}
    }
    suspend fun write(output:OutputStream) = repository.gate.withLock { writeSnapshot(output,repository.snapshot().copy(preferences=prefs.all().filterKeys{!it.startsWith("draft")})) }
    private fun writeSnapshot(output:OutputStream,s:Snapshot) {
        val json=codec.encodeToString(s).toByteArray(Charsets.UTF_8)
        val hashes=mutableMapOf("data.json" to sha256(json))
        s.attachments.map{it.file}.distinct().forEach { name-> val f=media.file(name);require(f.isFile){"Attachment is missing: $name"};hashes["media/$name"]=fileHash(f) }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            fun entry(name:String,body:()->Unit) {zip.putNextEntry(ZipEntry(name));body();zip.closeEntry()}
            entry("manifest.json"){zip.write(codec.encodeToString(BackupManifest(hashes=hashes)).toByteArray())}
            entry("data.json"){zip.write(json)}
            s.attachments.map{it.file}.distinct().forEach { name -> entry("media/$name"){media.file(name).inputStream().use{it.copyTo(zip)}} }
        }
    }
    data class Validated(val directory:File,val snapshot:Snapshot) { fun close(){directory.deleteRecursively()} }
    fun inspect(uri:Uri):Validated {
        val dir=File(context.cacheDir,"restore-${uid()}").apply{mkdirs()}
        try {
            val seen=mutableSetOf<String>();var total=0L
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input->ZipInputStream(BufferedInputStream(input)).use { zip ->
                while(true){
                    val entry=zip.nextEntry?:break;val name=entry.name
                    require(!entry.isDirectory && (name in listOf("manifest.json","data.json") || name.matches(Regex("media/[A-Za-z0-9_-]+")))) { "Invalid backup path" }
                    require(seen.add(name) && seen.size<=100000){"Duplicate entries or too many files"}
                    val f=File(dir,name);f.parentFile!!.mkdirs()
                    f.outputStream().use { out ->
                        val buffer=ByteArray(65536);var entrySize=0L
                        while(true){val n=zip.read(buffer);if(n<0)break;entrySize+=n;total+=n
                            require(total<=16L*1024*1024*1024 && (name.startsWith("media/") || entrySize<=64L*1024*1024)){"Backup exceeds safety limit"};out.write(buffer,0,n)}
                    }
                }
            } }
            val manifest=codec.decodeFromString<BackupManifest>(File(dir,"manifest.json").readText())
            require(manifest.version==1 && manifest.hashes.keys==seen-"manifest.json") { "Incomplete backup manifest" }
            manifest.hashes.forEach{(name,hash)->require(fileHash(File(dir,name))==hash){"Integrity check failed: $name"}}
            val s=codec.decodeFromString<Snapshot>(File(dir,"data.json").readText());BackupValidation.snapshot(s)
            require(manifest.hashes.keys==setOf("data.json")+s.attachments.map{"media/${it.file}"}.toSet()){ "Backup media does not match metadata" }
            return Validated(dir,s)
        }catch(e:Exception){dir.deleteRecursively();throw e}
    }
    suspend fun restore(valid:Validated):File = repository.gate.withLock {
        val previous=repository.snapshot().copy(preferences=prefs.all())
        val safety=File(File(context.filesDir,"safety-backups").apply{mkdirs()},"before-restore-${now()}.zip")
        writeSnapshot(safety.outputStream(),previous)
        val prefix=uid()+"-";val copied=mutableListOf<File>()
        val record=RestoreJournal(uid(),previous.preferences,valid.snapshot.preferences)
        try {
            valid.snapshot.attachments.map{it.file}.distinct().forEach { name->
                val file=media.file(prefix+name);File(valid.directory,"media/$name").copyTo(file);copied+=file
            }
            saveJournal(record)
            prefs.replace(valid.snapshot.preferences)
            repository.db.withTransaction {
                val db=repository.db.openHelper.writableDatabase
                listOf("field_values","attachments","reminders","notes","audit","tickets","fields").forEach{db.execSQL("DELETE FROM $it")}
                val s=valid.snapshot;val dao=repository.dao
                dao.fields(s.fields);s.tickets.forEach{dao.ticket(it)};dao.values(s.values);dao.audit(s.audit.asReversed())
                s.attachments.forEach{dao.attachment(it.copy(file=prefix+it.file))};s.reminders.forEach{dao.reminder(it)};s.notes.forEach{dao.note(it)}
                dao.audit(listOf(Audit(id=record.id,type="BACKUP_RESTORED",reason="Replaced local data after explicit confirmation; safety copy: ${safety.name}")))
            }
            journal.delete()
            safety
        }catch(e:Exception){copied.forEach{it.delete()};prefs.replace(previous.preferences);journal.delete();throw e}
        finally { valid.close() }
    }
}
