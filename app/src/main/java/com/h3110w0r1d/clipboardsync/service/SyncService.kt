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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
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

class SyncService : LifecycleService() {
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
        lifecycleScope.launch { _syncStatus.emit(status) }
    }

    private val _syncHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val syncHistory = _syncHistory.asStateFlow()
    private fun updateStatus(history: List<HistoryItem>) {
        lifecycleScope.launch { _syncHistory.emit(history) }
    }

    private var mqttClient: MqttAsyncClient? = null
    private lateinit var clipboardManager: ClipboardManager

    private var _config: MqttSetting = MqttSetting()

    inner class SyncBinder : Binder() {
        fun getDeviceId(): String = DEVICE_ID
        fun getStatusFlow(): Flow<SyncStatus> = syncStatus
        fun syncClipboard() {
            _syncClipboard()
        }
        fun startSync(config: MqttSetting) {
            Log.d(TAG, "try Start sync")
            _startSync(config)
        }
        fun stopSync() {
            _stopSync()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private var lastTimestamp: Long = 0
    val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        DEVICE_ID = Settings.System.getString(contentResolver, Settings.Secure.ANDROID_ID)
        // Get the ClipboardManager
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        Log.d("SyncService", "Service created")
        createNotificationChannel()
    }


    private fun _startSync(config: MqttSetting) {
        Log.d(TAG, "Service starting")
        updateStatus(SyncStatus.Connecting)
        _config = config
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 处理停止服务的意图
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Stopping service from notification")
            _stopSync()
        }
        return START_STICKY
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Clipboard content has changed
        val clipData = clipboardManager.primaryClip
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
            publish(message)
        }
    }

    private fun connect() {
        val persistence = MemoryPersistence()
        val scheme = if (_config.port == 8883) "ssl" else "tcp"
        mqttClient = MqttAsyncClient("$scheme://${_config.serverAddress}", DEVICE_ID, persistence)
        mqttClient!!.setCallback(object : MqttCallback {
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
                Log.d(TAG, "Receive message: $message")
                val syncMessage = json.decodeFromString<SyncMessage>(message.toString())
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
            password = _config.password.toByteArray()
            userName = _config.username
            maxReconnectDelay = 2000
            isAutomaticReconnect = true
            isCleanStart = true
        }
        try {
            mqttClient!!.connect(options, null, object : MqttActionListener {
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
            e.printStackTrace()
        }
    }

    private fun publish(msg: String, qos: Int = 1, retained: Boolean = false) {
        if (!mqttClient?.isConnected!!) {
            Log.d(TAG, "failed sync, not connected")
            return
        }
        try {
            val message = MqttMessage().apply {
                payload = msg.toByteArray()
                this.qos = qos
                isRetained = retained
            }

            mqttClient!!.publish(_config.topic, message)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun disconnect() {
        try {
            mqttClient!!.disconnect(null, object : MqttActionListener {
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
            val mqttSubscription = MqttSubscription(_config.topic, 1).apply {
                isNoLocal = true
            }
            mqttClient!!.subscribe(mqttSubscription)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        _stopSync()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
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
            publish(message)
        }
    }
}

sealed class SyncStatus {
    object Disconnected : SyncStatus()
    object Connecting : SyncStatus()
    object Connected : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}