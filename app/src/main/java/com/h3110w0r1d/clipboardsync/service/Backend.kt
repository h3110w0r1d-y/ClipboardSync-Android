package com.h3110w0r1d.clipboardsync.service

import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import com.h3110w0r1d.clipboardsync.entity.MqttSetting
import com.h3110w0r1d.clipboardsync.entity.SyncMessage
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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Backend {
    private const val TAG = "ClipboardSync"
    private const val SENTINEL_TIMESTAMP = -1L
    private const val DEFAULT_QOS = 1
    private const val RECONNECT_DELAY_MS = 2000

    var lastTimestamp: Long = 0
    var deviceId: String = "android"
    private val jsonProcessor = Json { ignoreUnknownKeys = true }
    var mqttConfig: MqttSetting = MqttSetting()

    var syncService: SyncService? = null

    lateinit var mqttClient: MqttAsyncClient
    lateinit var clipboardManager: ClipboardManager
    lateinit var secretKeySpec: SecretKeySpec
    lateinit var ivParameterSpec: IvParameterSpec

    val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    private fun handleClipboardChange() {
        val clipData = clipboardManager.primaryClip ?: return

        if (lastTimestamp == SENTINEL_TIMESTAMP) {
            lastTimestamp = clipData.description?.timestamp ?: 0
            return
        }

        if (lastTimestamp == clipData.description?.timestamp) {
            return
        }

        lastTimestamp = clipData.description?.timestamp ?: 0

        if (clipData.itemCount > 0) {
            val item: ClipData.Item = clipData.getItemAt(0)
            val text = item.text?.toString() ?: return

            Log.d(TAG, "Clipboard changed: $text")
            val syncMessage = SyncMessage(
                deviceID = deviceId,
                type = "text",
                content = text,
                timestamp = lastTimestamp
            )

            try {
                val encryptedMessage = encryptMessage(syncMessage)
                publishMessage(encryptedMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing clipboard change", e)
            }
        }
    }

    private fun encryptMessage(syncMessage: SyncMessage): ByteArray {
        val messageJson = jsonProcessor.encodeToString(SyncMessage.serializer(), syncMessage)
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        return cipher.doFinal(messageJson.encodeToByteArray())
    }

    fun disconnect() {
        if (!isMqttInitialized()) return

        try {
            mqttClient.disconnect(null, object : MqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT连接已断开")
                    syncService?.updateStatus(SyncStatus.Disconnected)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT断开连接失败", exception)
                    syncService?.updateStatus(SyncStatus.Disconnected)
                }
            })
            mqttClient.close(true)
        } catch (e: MqttException) {
            Log.e(TAG, "断开MQTT连接时发生异常", e)
        } finally {
            syncService?.updateStatus(SyncStatus.Disconnected)
        }
    }

    fun connect() {
        val persistence = MemoryPersistence()
        val scheme = if (mqttConfig.enableSSL) "ssl" else "tcp"
        val serverUri = "$scheme://${mqttConfig.serverAddress}"

        try {
            closeExistingConnection()

            mqttClient = MqttAsyncClient(serverUri, deviceId, persistence)
            setupMqttCallbacks()

            val connectionOptions = createConnectionOptions()

            mqttClient.connect(connectionOptions, null, object : MqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT连接成功")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT连接失败", exception)
                    syncService?.updateStatus(SyncStatus.Error("连接失败: ${exception?.message}"))
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "创建MQTT连接时发生异常", e)
            syncService?.updateStatus(SyncStatus.Error("连接异常: ${e.message}"))
        }
    }

    private fun closeExistingConnection() {
        if (isMqttInitialized()) {
            try {
                mqttClient.disconnect()
                mqttClient.close(true)
            } catch (_: MqttException) { }
        }
    }

    private fun setupMqttCallbacks() {
        mqttClient.setCallback(object : MqttCallback {
            override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
                Log.d(TAG, "MQTT连接断开: ${disconnectResponse?.reasonString}")
                syncService?.updateStatus(SyncStatus.Connecting)
            }

            override fun mqttErrorOccurred(exception: MqttException?) {
                Log.e(TAG, "MQTT错误", exception)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (message == null) return

                try {
                    val decryptedMessage = decryptMessage(message.payload)
                    processIncomingMessage(decryptedMessage)
                } catch (e: Exception) {
                    Log.e(TAG, "处理接收消息时发生异常", e)
                }
            }

            override fun deliveryComplete(token: IMqttToken?) {
                Log.d(TAG, "消息发送完成")
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                val connectionType = if (reconnect) "重新连接" else "首次连接"
                Log.d(TAG, "MQTT $connectionType 完成: $serverURI")
                subscribeToTopic()
                syncService?.updateStatus(SyncStatus.Connected)
            }

            override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
                Log.d(TAG, "收到认证包: $reasonCode")
            }
        })
    }

    private fun createConnectionOptions(): MqttConnectionOptions {
        return MqttConnectionOptions().apply {
            password = mqttConfig.password.toByteArray()
            userName = mqttConfig.username
            maxReconnectDelay = RECONNECT_DELAY_MS
            isAutomaticReconnect = true
            isCleanStart = true
        }
    }

    private fun decryptMessage(payload: ByteArray): SyncMessage {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        val messageData = cipher.doFinal(payload)
        return jsonProcessor.decodeFromString(SyncMessage.serializer(), messageData.decodeToString())
    }

    private fun processIncomingMessage(syncMessage: SyncMessage) {
        Log.d(TAG, "收到消息: $syncMessage")
        if (syncMessage.type == "text") {
            val clipData = ClipData.newPlainText(null, syncMessage.content)
            lastTimestamp = SENTINEL_TIMESTAMP
            Log.d(TAG, "设置剪贴板内容，lastTimestamp: $lastTimestamp")
            clipboardManager.setPrimaryClip(clipData)
        }
    }

    private fun subscribeToTopic() {
        try {
            val mqttSubscription = MqttSubscription(mqttConfig.topic, DEFAULT_QOS).apply {
                isNoLocal = true
            }
            mqttClient.subscribe(mqttSubscription)
            Log.d(TAG, "已订阅主题: ${mqttConfig.topic}")
        } catch (e: MqttException) {
            Log.e(TAG, "订阅主题失败", e)
            syncService?.updateStatus(SyncStatus.Error("订阅失败: ${e.message}"))
        }
    }

    private fun publishMessage(message: ByteArray, qos: Int = DEFAULT_QOS, retained: Boolean = false) {
        if (!mqttClient.isConnected) {
            Log.d(TAG, "发送失败: 未连接")
            return
        }

        try {
            val mqttMessage = MqttMessage().apply {
                payload = message
                this.qos = qos
                isRetained = retained
            }

            mqttClient.publish(mqttConfig.topic, mqttMessage)
            Log.d(TAG, "消息已发送到主题: ${mqttConfig.topic}")
        } catch (e: MqttException) {
            Log.e(TAG, "发送消息失败", e)
            syncService?.updateStatus(SyncStatus.Error("发送失败: ${e.message}"))
        }
    }

    fun isMqttInitialized(): Boolean = ::mqttClient.isInitialized

    fun isClipboardManagerInitialized(): Boolean = ::clipboardManager.isInitialized
}