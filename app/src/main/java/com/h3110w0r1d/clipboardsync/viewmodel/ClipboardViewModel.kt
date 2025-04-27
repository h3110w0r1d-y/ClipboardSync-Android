package com.h3110w0r1d.clipboardsync.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.h3110w0r1d.clipboardsync.R
import com.h3110w0r1d.clipboardsync.service.SyncService
import com.h3110w0r1d.clipboardsync.service.SyncStatus
import com.h3110w0r1d.clipboardsync.ui.uistate.MqttSettingUIState
import com.h3110w0r1d.clipboardsync.utils.MmkvUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class ClipboardViewModel: ViewModel() {
    private val _isBound = mutableStateOf(false)
    val isBound: State<Boolean> = _isBound

    private val _moduleActive = mutableStateOf(false)
    val moduleActive: State<Boolean> = _moduleActive

    var serviceBinder: SyncService.SyncBinder? = null

    // 同步服务状态
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Disconnected)
    val syncStatus = _syncStatus.asStateFlow()

    // 设备ID
    private val _deviceId = mutableStateOf("")
    val deviceId: State<String> = _deviceId

    // 当前剪贴板内容
    private val _clipboardContent = mutableStateOf("")
    var clipboardContent: State<String> = _clipboardContent

    private var serviceConnection = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("SyncServiceConnection", "onServiceConnected")
            serviceBinder = binder as SyncService.SyncBinder
            _deviceId.value = serviceBinder?.getDeviceId() ?: "Android"
            _isBound.value = true
            Log.d("SyncServiceConnection", serviceBinder.toString())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("SyncServiceConnection", "onServiceDisconnected")
            _isBound.value = false
            serviceBinder = null
        }
    }

    fun updateSyncStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    fun updateClipboardContent(text: String) {
        _clipboardContent.value = text
    }

    // 绑定服务
    fun bindService(context: Context) {
        Log.d("ClipboardViewModel", "bindService")
        val intent = Intent(context, SyncService::class.java)
        context.startService(intent)
        // 再绑定服务（支持交互）
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("ClipboardViewModel", serviceConnection.toString())
    }

    // 解绑服务
    fun unbindService(context: Context) {
        context.unbindService(serviceConnection)
    }

    // UI事件处理
    fun toggleSync(): Boolean {
        return serviceBinder?.toggleSync() ?: false
    }

    fun openLSPosedManager(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage("org.lsposed.manager")
        if (intent == null) {
            Toast.makeText(context, context.getString(R.string.lsposed_manager_not_install), Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(intent)
    }

    fun syncClipboardContent() {
        serviceBinder?.syncClipboard()
    }

    fun saveSetting(uiState: MqttSettingUIState) {
        MmkvUtils["serverAddress"] = uiState.serverAddress.value
        MmkvUtils["port"] = uiState.port.value.toIntOrNull() ?: 8883
        MmkvUtils["enableSSL"] = uiState.enableSSL.value
        MmkvUtils["username"] = uiState.username.value
        MmkvUtils["password"] = uiState.password.value
        MmkvUtils["secretKey"] = uiState.secretKey.value
        MmkvUtils["topic"] = uiState.topic.value
    }

    fun setModuleActive() {
        _moduleActive.value = true
    }
}