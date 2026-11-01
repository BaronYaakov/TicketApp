package com.ticketapp

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.ticketapp.ui.*

class MainActivity:ComponentActivity() {
    private lateinit var vm:DeskViewModel
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm=ViewModelProvider(this)[DeskViewModel::class.java]
        vm.selected.value=if(savedInstanceState!=null) savedInstanceState.getString("selectedTicket") else intent.getStringExtra("ticket")
        setContent { DeskApp(vm) }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("selectedTicket",vm.selected.value)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);vm.selected.value=intent.getStringExtra("ticket") }
}
