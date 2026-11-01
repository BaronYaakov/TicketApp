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
        vm.selected.value=intent.getStringExtra("ticket")
        setContent { DeskApp(vm) }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);vm.selected.value=intent.getStringExtra("ticket") }
}
