package com.ticketapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

class MediaStore(private val context:Context) {
    val root=File(context.filesDir,"media").apply{mkdirs()}
    fun file(name:String):File {
        require(name.matches(Regex("[a-zA-Z0-9._-]+")) && name!="." && name!="..") { "Unsafe media path" }
        return File(root,name)
    }
    fun uri(a:Attachment):Uri = FileProvider.getUriForFile(context,"${context.packageName}.files",file(a.file))
    fun capture(video:Boolean):Pair<File,Uri> {
        val f=File(File(context.cacheDir,"capture").apply{mkdirs()},uid()+if(video) ".mp4" else ".jpg")
        return f to FileProvider.getUriForFile(context,"${context.packageName}.files",f)
    }
    fun import(uri:Uri,ticketId:String,fieldId:String?):Attachment {
        var name="Attachment"
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) name=c.getString(0)?:name }
        val mime=context.contentResolver.getType(uri)?:"application/octet-stream"
        val a=Attachment(ticketId=ticketId,fieldId=fieldId,name=name,mime=mime,file=uid())
        val dest=file(a.file); val temporary=File(root,"${a.file}.partial")
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> temporary.outputStream().use { output ->
                val count=input.copyTo(output); require(count>0) { "The selected file is empty." }; output.fd.sync()
            } }
            check(temporary.renameTo(dest)) { "Could not store attachment" }
            return a
        } catch(e:Exception) { temporary.delete(); dest.delete(); throw e }
    }
}
