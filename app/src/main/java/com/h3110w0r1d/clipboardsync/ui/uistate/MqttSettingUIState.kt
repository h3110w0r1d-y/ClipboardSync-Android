package com.h3110w0r1d.clipboardsync.ui.uistate

import androidx.compose.runtime.mutableStateOf
import com.h3110w0r1d.clipboardsync.entity.MqttSetting

class MqttSettingUIState(mqttSetting: MqttSetting) {
	val serverAddress = mutableStateOf(mqttSetting.serverAddress)
	val port = mutableStateOf(mqttSetting.port.toString())
	val enableSSL = mutableStateOf(mqttSetting.enableSSL)
	val username = mutableStateOf(mqttSetting.username)
	val password = mutableStateOf(mqttSetting.password)
	val topic = mutableStateOf(mqttSetting.topic)
}