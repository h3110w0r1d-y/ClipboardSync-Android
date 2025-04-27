package com.h3110w0r1d.clipboardsync.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.h3110w0r1d.clipboardsync.ui.components.MainPage
import com.h3110w0r1d.clipboardsync.ui.theme.ClipboardSyncTheme
import com.h3110w0r1d.clipboardsync.viewmodel.ClipboardViewModel


class MainActivity : ComponentActivity() {
    private var broadCastReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel by viewModels<ClipboardViewModel>()

        broadCastReceiver = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context?, intent: Intent?) {
                if (intent?.action == "com.h3110w0r1d.clipboardsync.MODULE_ACTIVE") {
                    Log.d("MainActivity", "MODULE_ACTIVE")
                    viewModel.setModuleActive()
                }
            }
        }
        val intentFilter = IntentFilter("com.h3110w0r1d.clipboardsync.MODULE_INACTIVE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadCastReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(broadCastReceiver, intentFilter)
        }
        sendBroadcast(Intent("com.h3110w0r1d.clipboardsync.MODULE_ACTIVE"))

        setContent {
            ClipboardSyncTheme {
                MainPage(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadCastReceiver)
    }
}
