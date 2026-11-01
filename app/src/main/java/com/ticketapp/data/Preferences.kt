package com.ticketapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("desk_preferences")
class DeskPreferences(private val context:Context) {
    val flow=context.preferences.data.map { p -> p.asMap().mapKeys{it.key.name}.mapValues{it.value.toString()} }
    suspend fun all()=flow.first()
    suspend fun setAll(values:Map<String,String>) {
        context.preferences.edit { preferences ->
            values.forEach { (name,value) -> preferences[stringPreferencesKey(name)]=value }
        }
    }
    suspend fun set(name:String,value:String) { context.preferences.edit{it[stringPreferencesKey(name)]=value} }
    suspend fun replace(values:Map<String,String>) { context.preferences.edit { p->p.clear();values.forEach{(k,v)->p[stringPreferencesKey(k)]=v} } }
}
