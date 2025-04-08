package com.h3110w0r1d.clipboardsync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.coroutineScope
import com.h3110w0r1d.clipboardsync.R
import com.h3110w0r1d.clipboardsync.activity.MainActivity
import com.h3110w0r1d.clipboardsync.entity.HistoryItem
import com.h3110w0r1d.clipboardsync.entity.MqttSetting
import com.h3110w0r1d.clipboardsync.entity.SyncMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.client.MqttAsyncClient
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.MqttSubscription
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor


class SyncService : TileService(), LifecycleOwner {
    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val  lifecycle: Lifecycle
        get() = dispatcher.lifecycle
    private val LifecycleOwner.lifecycleScope: LifecycleCoroutineScope
        get() = lifecycle.coroutineScope

    companion object {
        const val ACTION_STOP_SERVICE =
            "com.h3110w0r1d.clipboardsync.ACTION_STOP_SERVICE" // 停止服务的 Action
        const val TAG = "SyncService"

        private const val CHANNEL_ID = "SyncServiceChannel"
        private const val NOTIFICATION_ID = 1
        private lateinit var DEVICE_ID: String
    }

    private val binder = SyncBinder()

    private val _clipboardContent = MutableStateFlow("")
    val clipboardContent = _clipboardContent.asStateFlow()
    private fun updateClipboardContent(content: String) {
        lifecycleScope.launch { _clipboardContent.emit(content) }
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Disconnected)
    val syncStatus = _syncStatus.asStateFlow()
    private fun updateStatus(status: SyncStatus) {
        if (status is SyncStatus.Connected) {
            qsTile?.state = Tile.STATE_ACTIVE
        } else {
            qsTile?.state = Tile.STATE_INACTIVE
        }
        qsTile?.updateTile()
        lifecycleScope.launch { _syncStatus.emit(status) }
    }

    private val _syncHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val syncHistory = _syncHistory.asStateFlow()
    private fun updateHistory(history: List<HistoryItem>) {
        lifecycleScope.launch { _syncHistory.emit(history) }
    }

    private var mqttClient: MqttAsyncClient? = null
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var secretKeySpec: SecretKeySpec
    private lateinit var ivParameterSpec: IvParameterSpec

    private var config: MqttSetting = MqttSetting()

    private var lastTimestamp: Long = 0
    val json = Json { ignoreUnknownKeys = true }

    inner class SyncBinder : Binder() {
        fun getDeviceId(): String = DEVICE_ID
        fun getStatusFlow(): Flow<SyncStatus> = syncStatus
        fun syncClipboard() {
            _syncClipboard()
        }
        fun startSync() {
            _startSync()
        }
        fun stopSync() {
            _stopSync()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        Log.d(TAG, "Service bound")
        Log.d(TAG, "Intent action: ${intent.action}")
        if (intent.action == null) {
            return binder
        }
        return super.onBind(intent)
//        return binder
    }

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        DEVICE_ID = Settings.System.getString(contentResolver, Settings.Secure.ANDROID_ID)
        // Get the ClipboardManager
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        Log.d("SyncService", "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 处理停止服务的意图
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Stopping service from notification")
            _stopSync()
        }
        return START_STICKY
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile is listening")
        // Update the tile state
        if (syncStatus.value is SyncStatus.Connected) {
            qsTile?.state = Tile.STATE_ACTIVE
        } else {
            qsTile?.state = Tile.STATE_INACTIVE
        }
        qsTile?.updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (syncStatus.value is SyncStatus.Connected) {
            qsTile?.state = Tile.STATE_ACTIVE
        } else {
            qsTile?.state = Tile.STATE_INACTIVE
        }
        qsTile?.updateTile()
    }

    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        _stopSync()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        if (!isLocked) {
            Log.d(TAG, "Tile clicked")
            // Toggle the sync status
            if (syncStatus.value is SyncStatus.Connected) {
                Log.d(TAG, "Stopping sync")
                _stopSync()
            } else {
                _startSync()
                Log.d(TAG, "Starting sync")
            }
        } else {
            unlockAndRun {
                Log.d(TAG, "Tile clicked and unlocked")
                // Toggle the sync status
                if (syncStatus.value is SyncStatus.Connected) {
                    Log.d(TAG, "Stopping sync")
                    _stopSync()
                } else {
                    _startSync()
                    Log.d(TAG, "Starting sync")
                }
            }
        }
    }

    private fun _startSync() {
        Log.d(TAG, "Service starting")
        updateStatus(SyncStatus.Connecting)
        config = MqttSetting()

        // 初始化密钥
        val hash = MessageDigest.getInstance("SHA-256").digest(config.secretKey.toByteArray())
        secretKeySpec = SecretKeySpec(hash, "AES")
        val ivBytes = hash.copyOfRange(0, 16)
        // xor ivBytes with the last 16 bytes of the hash
        for (i in ivBytes.indices) {
            ivBytes[i] = (ivBytes[i] xor hash[hash.size - 16 + i])
        }
        ivParameterSpec = IvParameterSpec(ivBytes)

        // MQTT连接逻辑
        connect()
        // Register the listener
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        // 创建通知
        val notification = createNotification()
        // 将服务设置为前台服务
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        Log.d(TAG, "Service started")
    }

    private fun _stopSync() {
        Log.d(TAG, "Service stopping")
        // Unregister the listener
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        // 停止MQTT连接
        disconnect()
        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Clipboard content has changed
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item: ClipData.Item = clipData.getItemAt(0)
            updateClipboardContent(item.text.toString())
        }

        if (lastTimestamp == (-1).toLong()) {
            lastTimestamp = clipData?.description?.timestamp ?: 0
            return@OnPrimaryClipChangedListener
        }
        if (lastTimestamp == clipData?.description?.timestamp) {
            return@OnPrimaryClipChangedListener
        }
        lastTimestamp = clipData?.description?.timestamp ?: 0
        if (clipData != null && clipData.itemCount > 0) {
            val item: ClipData.Item = clipData.getItemAt(0)
            val text = item.text
            Log.d(TAG, "Clipboard changed: $text")
            val syncMessage = SyncMessage(
                deviceID = DEVICE_ID,
                type = "text",
                content = text.toString(),
                timestamp = lastTimestamp
            )
            val message = json.encodeToString(SyncMessage.serializer(), syncMessage)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            val encryptedMessage = cipher.doFinal(message.encodeToByteArray())

            publish(encryptedMessage)
        }
    }

    private fun connect() {
        val persistence = MemoryPersistence()
        val scheme = if (config.enableSSL) "ssl" else "tcp"
        mqttClient = MqttAsyncClient("$scheme://${config.serverAddress}", DEVICE_ID, persistence)
        mqttClient?.setCallback(object : MqttCallback {
            override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
                Log.d(TAG, "Disconnected")
                updateStatus(SyncStatus.Disconnected)
            }

            override fun mqttErrorOccurred(exception: MqttException?) {
                Log.d(TAG, "Error")
                Log.d(TAG, exception.toString())
                updateStatus(SyncStatus.Error(exception.toString()))
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (message == null) {
                    return
                }
                val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
                cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
                val messageData = cipher.doFinal(message.payload)

                val syncMessage = json.decodeFromString<SyncMessage>(messageData.decodeToString())
                Log.d(TAG, syncMessage.toString())
                if (syncMessage.type == "text") {
                    val clipData = ClipData.newPlainText(null, syncMessage.content)
                    lastTimestamp = -1
                    Log.d(TAG, "set lastTimestamp: $lastTimestamp")
                    clipboardManager.setPrimaryClip(clipData)
                }
            }

            override fun deliveryComplete(token: IMqttToken?) {
                TODO("Not yet implemented")
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "mqtt Connected")
                updateStatus(SyncStatus.Connected)
            }

            override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
                TODO("Not yet implemented")
            }
        })
        val options = MqttConnectionOptions().apply {
            password = config.password.toByteArray()
            userName = config.username
            maxReconnectDelay = 2000
            isAutomaticReconnect = true
            isCleanStart = true
        }
        try {
            mqttClient?.connect(options, null, object : MqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
                    subscribe()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure")
                    Log.d(TAG, exception.toString())
                    updateStatus(SyncStatus.Error(exception.toString()))
                }
            })
        } catch (e: MqttException) {
            Log.d(TAG, "Connection failure")
            Log.d(TAG, e.toString())
            updateStatus(SyncStatus.Error(e.toString()))
        }
    }

    private fun publish(msg: ByteArray, qos: Int = 1, retained: Boolean = false) {
        if (mqttClient?.isConnected != true) {
            Log.d(TAG, "failed sync, not connected")
            return
        }
        try {
            val message = MqttMessage().apply {
                payload = msg
                this.qos = qos
                isRetained = retained
            }

            mqttClient?.publish(config.topic, message)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun disconnect() {
        try {
            mqttClient?.disconnect(null, object : MqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Disconnected")
                    updateStatus(SyncStatus.Disconnected)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to disconnect")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun subscribe() {
        try {
            val mqttSubscription = MqttSubscription(config.topic, 1).apply {
                isNoLocal = true
            }
            mqttClient?.subscribe(mqttSubscription)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sync Service Channel",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        // 创建停止服务的 PendingIntent
        val stopIntent = Intent(this, SyncService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync Service")
            .setContentText("Service is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 替换为你的图标
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // 添加停止服务的 Action
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun _syncClipboard() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item: ClipData.Item = clipData.getItemAt(0)
            val text = item.text
            val syncMessage = SyncMessage(
                deviceID = DEVICE_ID,
                type = "text",
                content = text.toString(),
                timestamp = lastTimestamp
            )
            val message = json.encodeToString(SyncMessage.serializer(), syncMessage)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            val encryptedMessage = cipher.doFinal(message.encodeToByteArray())
            publish(encryptedMessage)
        }
    }
}

sealed class SyncStatus {
    object Disconnected : SyncStatus()
    object Connecting : SyncStatus()
    object Connected : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}