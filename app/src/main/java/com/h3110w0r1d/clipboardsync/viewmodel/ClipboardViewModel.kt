package com.h3110w0r1d.clipboardsync.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.h3110w0r1d.clipboardsync.entity.HistoryItem
import com.h3110w0r1d.clipboardsync.entity.MqttSetting
import com.h3110w0r1d.clipboardsync.service.SyncService
import com.h3110w0r1d.clipboardsync.service.SyncStatus
import com.h3110w0r1d.clipboardsync.ui.uistate.MqttSettingUIState
import com.h3110w0r1d.clipboardsync.utils.MmkvUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class ClipboardViewModel: ViewModel() {
    private val _isBound = mutableStateOf(false)
    val isBound: State<Boolean> = _isBound
    var serviceBinder: SyncService.SyncBinder? = null

    // 同步服务状态
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Disconnected)
    val syncStatus = _syncStatus.asStateFlow()

    // 设备ID
    private val _deviceId = MutableStateFlow("")
    val deviceId = _deviceId.asStateFlow()

    // 当前剪贴板内容
    private val _clipboardContent = MutableStateFlow("")
    val clipboardContent = _clipboardContent.asStateFlow()

    // UI状态
    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems = _historyItems.asStateFlow()

    // 设置状态
    val mqttSettingUIState = MqttSettingUIState(MqttSetting())

    var serviceConnection = object : ServiceConnection{
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
    fun toggleSync(enabled: Boolean) {
        Log.d("ClipboardViewModel", "toggleSync")
        if (enabled) {
            Log.d("ClipboardViewModel", "startSync")
            Log.d("ClipboardViewModel", serviceConnection.toString())
            serviceBinder?.startSync(MqttSetting(
                serverAddress = mqttSettingUIState.serverAddress.value,
                port = mqttSettingUIState.port.value.toIntOrNull() ?: 8883,
                enableSSL = mqttSettingUIState.enableSSL.value,
                username = mqttSettingUIState.username.value,
                password = mqttSettingUIState.password.value,
                topic = mqttSettingUIState.topic.value,
            ))
        } else {
            Log.d("ClipboardViewModel", "stopSync")
            serviceBinder?.stopSync()
        }
    }

    fun syncClipboardContent() {
        serviceBinder?.syncClipboard()
    }

    fun saveSetting(){
        MmkvUtils["serverAddress"] = mqttSettingUIState.serverAddress.value
        MmkvUtils["port"] = mqttSettingUIState.port.value
        MmkvUtils["enableSSL"] = mqttSettingUIState.enableSSL.value
        MmkvUtils["username"] = mqttSettingUIState.username.value
        MmkvUtils["password"] = mqttSettingUIState.password.value
        MmkvUtils["topic"] = mqttSettingUIState.topic.value
    }
}