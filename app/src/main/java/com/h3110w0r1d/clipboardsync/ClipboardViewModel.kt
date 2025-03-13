package com.h3110w0r1d.clipboardsync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.ctrip.flight.mmkv.MMKV_KMP
import com.ctrip.flight.mmkv.defaultMMKV
import com.ctrip.flight.mmkv.initialize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class ClipboardViewModel: ViewModel() {
    private val _isBound = mutableStateOf(false)
    val isBound: State<Boolean> = _isBound
    var serviceBinder: SyncService.SyncBinder? = null
    private var kv: MMKV_KMP? = null

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
    private val _settingsState = MutableStateFlow(MqttSettingsState())
    val settingsState = _settingsState.asStateFlow()

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

    init {
        Log.d("ClipboardViewModel", "init")
    }

    fun initializeMMKV(context: Context) {
        val rootDir = initialize(context)
        Log.d("MMKV Path", rootDir)
        kv = defaultMMKV()
        _settingsState.value = MqttSettingsState(
            serverAddress = kv!!.takeString("serverAddress"),
            port = kv!!.takeString("port", 8883.toString()),
            enableSSL = kv!!.takeBoolean("enableSSL", true),
            username = kv!!.takeString("username"),
            password = kv!!.takeString("password"),
            topic = kv!!.takeString("topic", "clipboard"),
        )
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
            serviceBinder?.startSync(MqttConfig(
                serverAddress = settingsState.value.serverAddress,
                port = settingsState.value.port.toIntOrNull() ?: 8883,
                enableSSL = settingsState.value.enableSSL,
                username = settingsState.value.username,
                password = settingsState.value.password,
                topic = settingsState.value.topic,
            ))
        } else {
            Log.d("ClipboardViewModel", "stopSync")
            serviceBinder?.stopSync()
        }
    }

    fun updateSettings(newSettings: MqttSettingsState) {
        _settingsState.value = newSettings
        kv?.set("serverAddress", newSettings.serverAddress)
        kv?.set("port", newSettings.port)
        kv?.set("enableSSL", newSettings.enableSSL)
        kv?.set("username", newSettings.username)
        kv?.set("password", newSettings.password)
        kv?.set("topic", newSettings.topic)
    }

    fun syncClipboardContent() {
        serviceBinder?.syncClipboard()
    }
}

data class MqttSettingsState(
    val serverAddress: String = "",
    val port: String = "8883",
    val enableSSL: Boolean = true,
    val username: String = "",
    val password: String = "",
    val topic: String = "clipboard",
)