package com.h3110w0r1d.clipboardsync.activity

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.h3110w0r1d.clipboardsync.ui.components.MainPage
import com.h3110w0r1d.clipboardsync.viewmodel.ClipboardViewModel


class MainActivity : BaseComposeActivity() {

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermissions(arrayOf(POST_NOTIFICATIONS))
        }

        val powerManager = this.getSystemService(POWER_SERVICE) as PowerManager

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            this.startActivity(
                Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:${this.packageName}".toUri())
            )
        }
    }

    @Composable
    override fun Content() {
        val viewModel by viewModels<ClipboardViewModel>()
        MainPage(viewModel)
    }
}