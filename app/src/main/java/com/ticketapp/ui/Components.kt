@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ticketapp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import com.ticketapp.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Calendar

fun stamp(value:Long?):String = value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a z")) }?:"Not set"
fun pickDate(context:Context,value:Long?,time:Boolean=true,onValue:(Long)->Unit) {
    val calendar=Calendar.getInstance().apply{timeInMillis=value?:now()}
    DatePickerDialog(context,{_,year,month,day->
        calendar.set(year,month,day,0,0,0);calendar.set(Calendar.MILLISECOND,0)
        if(time) TimePickerDialog(context,{_,h,m->calendar.set(Calendar.HOUR_OF_DAY,h);calendar.set(Calendar.MINUTE,m);onValue(calendar.timeInMillis)},12,0,android.text.format.DateFormat.is24HourFormat(context)).show()
        else onValue(calendar.timeInMillis)
    },calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH),calendar.get(Calendar.DAY_OF_MONTH)).show()
}
@Composable
fun Select(label:String,value:String,options:List<Pair<String,String>>,modifier:Modifier=Modifier,onChange:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(expanded,{expanded=!expanded},modifier) {
        OutlinedTextField(options.find{it.first==value}?.second?:value,onValueChange={},readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded,{expanded=false}) { options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onChange(id);expanded=false})} }
    }
}
@Composable
fun DateInput(label:String,value:Long?,onChange:(Long?)->Unit) {
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton({pickDate(context,value,onValue={onChange(it)})},Modifier.weight(1f)){Text("$label: ${stamp(value)}")}
        if(value!=null) TextButton({onChange(null)}){Text("Clear")}
    }
}
@Composable
fun FieldInput(field:Field,value:String,onValue:(String)->Unit,onMedia:()->Unit,attachmentCount:Int=0) {
    val label=field.name+if(field.required) " *" else ""
    val error=RulesForUi(field,value)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        when(field.kind) {
            FieldType.BOOLEAN -> Select(label,value,listOf("" to "Not set","true" to "Yes","false" to "No"),onChange=onValue)
            FieldType.SINGLE -> Select(label,value,listOf("" to "Not set")+field.selections.map{it to it},onChange=onValue)
            FieldType.MULTI -> {
                Text(label,style=MaterialTheme.typography.titleSmall)
                val selected=runCatching{choices(value)}.getOrDefault(emptyList())
                field.selections.forEach { option -> Row { Checkbox(option in selected,{checked->onValue(packed(if(checked) selected+option else selected-option))});Text(option,Modifier.padding(top=12.dp)) } }
            }
            FieldType.DATE,FieldType.DATETIME -> {
                val context=LocalContext.current
                OutlinedButton({pickDate(context,value.toLongOrNull(),field.kind==FieldType.DATETIME){onValue(it.toString())}}){Text("$label: ${stamp(value.toLongOrNull())}")}
                if(value.isNotBlank())TextButton({onValue("")}){Text("Clear date")}
            }
            FieldType.PHOTO,FieldType.VIDEO,FieldType.FILE -> OutlinedButton(onMedia,Modifier.fillMaxWidth()){Text("Add ${field.name} · $attachmentCount attached")}
            else -> OutlinedTextField(value,onValue,label={Text(label)},placeholder={Text(field.placeholder)},supportingText={if(error!=null)Text(error) else if(field.helper.isNotBlank())Text(field.helper)},isError=error!=null,
                modifier=Modifier.fillMaxWidth(),minLines=if(field.kind==FieldType.LONG_TEXT)3 else 1,singleLine=field.kind!=FieldType.LONG_TEXT,
                keyboardOptions=KeyboardOptions(keyboardType=when(field.kind){FieldType.INTEGER->KeyboardType.Number;FieldType.DECIMAL->KeyboardType.Decimal;FieldType.URL->KeyboardType.Uri;else->KeyboardType.Text}))
        }
        if(field.kind.media || field.kind in listOf(FieldType.SINGLE,FieldType.MULTI,FieldType.DATE,FieldType.DATETIME,FieldType.BOOLEAN)) if(field.helper.isNotBlank())Text(field.helper,style=MaterialTheme.typography.bodySmall)
    }
}
private fun RulesForUi(field:Field,value:String):String? = if(value.isBlank())null else com.ticketapp.domain.Rules.validate(field.copy(required=false),value)
@Composable
fun TextDialog(title:String,initial:String="",onDismiss:()->Unit,onSave:(String)->Unit) {
    var text by remember{mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(text,{text=it},label={Text("Text")},minLines=3)},confirmButton={TextButton({onSave(text)},enabled=text.isNotBlank()){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
@Composable
fun Confirm(title:String,body:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onConfirm){Text("Confirm")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
