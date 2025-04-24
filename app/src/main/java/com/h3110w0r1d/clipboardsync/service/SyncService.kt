package com.h3110w0r1d.clipboardsync.service

import android.content.ClipboardManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.coroutineScope
import com.h3110w0r1d.clipboardsync.entity.MqttSetting
import com.h3110w0r1d.clipboardsync.entity.SyncMessage
import com.h3110w0r1d.clipboardsync.service.Backend.clipboardListener
import com.h3110w0r1d.clipboardsync.service.Backend.clipboardManager
import com.h3110w0r1d.clipboardsync.service.Backend.lastTimestamp
import com.h3110w0r1d.clipboardsync.service.Backend.mqttClient
import com.h3110w0r1d.clipboardsync.service.Backend.mqttConfig
import com.h3110w0r1d.clipboardsync.service.Backend.secretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

class SyncService : TileService(), LifecycleOwner {
    companion object {
        private const val TAG = "SyncService"
        private const val DEFAULT_QOS = 1
    }

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle
    private val LifecycleOwner.lifecycleScope: LifecycleCoroutineScope
        get() = lifecycle.coroutineScope

    private val binder = SyncBinder()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Disconnected)
    val syncStatus = _syncStatus.asStateFlow()

    private val jsonProcessor = Json { ignoreUnknownKeys = true }

    inner class SyncBinder : Binder() {
        fun getDeviceId(): String = Backend.deviceId
        fun getStatusFlow(): Flow<SyncStatus> = syncStatus
        fun syncClipboard() = syncClipboardContent()
        fun startSync() = startSyncService()
        fun stopSync() = stopSyncService()
        fun getStatus(): SyncStatus = syncStatus.value
    }

    override fun onBind(intent: Intent): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        Log.d(TAG, "Service bound")

        return if (intent.action == null) {
            binder
        } else {
            super.onBind(intent)
        }
    }

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()

        Backend.deviceId = Settings.System.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "android"

        if (!Backend.isClipboardManagerInitialized()) {
            clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        }

        Backend.syncService = this

        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Backend.syncService = null
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile is listening")

        updateStatus(
            if (Backend.isMqttInitialized() && mqttClient.isConnected) {
                SyncStatus.Connected
            } else {
                _syncStatus.value
            }
        )
    }

    override fun onStopListening() {
        super.onStopListening()
        updateTileState()
    }

    override fun onClick() {
        val action = {
            Log.d(TAG, "Tile clicked")
            toggleSyncStatus()
        }

        if (isLocked) {
            unlockAndRun(action)
        } else {
            action()
        }
    }

    fun updateStatus(status: SyncStatus) {
        updateTileState(status)
        lifecycleScope.launch { _syncStatus.emit(status) }
    }

    private fun updateTileState(status: SyncStatus = syncStatus.value) {
        qsTile?.apply {
            state = if (status is SyncStatus.Connected || status is SyncStatus.Connecting) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    private fun toggleSyncStatus() {
        if (syncStatus.value is SyncStatus.Connected) {
            Log.d(TAG, "Stopping sync")
            stopSyncService()
        } else {
            Log.d(TAG, "Starting sync")
            startSyncService()
        }
    }

    private fun startSyncService() {
        if (syncStatus.value is SyncStatus.Connecting || syncStatus.value is SyncStatus.Connected) {
            Log.d(TAG, "Service already started")
            return
        }
        Log.d(TAG, "Service starting")
        updateStatus(SyncStatus.Connecting)

        initializeSecurity()
        Backend.connect()

        // 注册剪贴板监听器
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        Log.d(TAG, "Service started")
    }

    private fun stopSyncService() {
        Log.d(TAG, "Service stopping")

        // 取消注册剪贴板监听器
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)

        // 停止MQTT连接
        Backend.disconnect()
    }

    private fun initializeSecurity() {
        mqttConfig = MqttSetting()

        // 初始化加密密钥
        val hash = MessageDigest.getInstance("SHA-256").digest(mqttConfig.secretKey.toByteArray())
        secretKeySpec = SecretKeySpec(hash, "AES")

        // 创建初始化向量
        val ivBytes = hash.copyOfRange(0, 16)
        // 使用异或操作增加安全性
        for (i in ivBytes.indices) {
            ivBytes[i] = (ivBytes[i] xor hash[hash.size - 16 + i])
        }
        Backend.ivParameterSpec = IvParameterSpec(ivBytes)
    }

    private fun syncClipboardContent() {
        val clipData = clipboardManager.primaryClip ?: return

        if (clipData.itemCount <= 0) return

        val item = clipData.getItemAt(0)
        val text = item.text?.toString() ?: return

        val syncMessage = SyncMessage(
            deviceID = Backend.deviceId,
            type = "text",
            content = text,
            timestamp = lastTimestamp
        )

        try {
            val encryptedMessage = encryptMessage(syncMessage)
            publishMessage(encryptedMessage)
        } catch (e: Exception) {
            Log.e(TAG, "加密或发送消息失败", e)
            updateStatus(SyncStatus.Error("加密或发送消息失败: ${e.message}"))
        }
    }

    private fun encryptMessage(syncMessage: SyncMessage): ByteArray {
        val messageJson = jsonProcessor.encodeToString(SyncMessage.serializer(), syncMessage)
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, Backend.ivParameterSpec)
        return cipher.doFinal(messageJson.encodeToByteArray())
    }

    private fun publishMessage(message: ByteArray, qos: Int = DEFAULT_QOS, retained: Boolean = false) {
        if (!Backend.isMqttInitialized() || !mqttClient.isConnected) {
            Log.d(TAG, "发送失败: MQTT未连接")
            return
        }

        try {
            val mqttMessage = org.eclipse.paho.mqttv5.common.MqttMessage().apply {
                payload = message
                this.qos = qos
                isRetained = retained
            }

            mqttClient.publish(mqttConfig.topic, mqttMessage)
            Log.d(TAG, "消息已发送到主题: ${mqttConfig.topic}")
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败", e)
            updateStatus(SyncStatus.Error("发送失败: ${e.message}"))
        }
    }
}

sealed class SyncStatus {
    object Disconnected : SyncStatus()
    object Connecting : SyncStatus()
    object Connected : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}