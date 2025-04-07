package com.h3110w0r1d.clipboardsync.entity

import com.h3110w0r1d.clipboardsync.utils.MmkvUtils

data class MqttSetting(
	val serverAddress: String = MmkvUtils["serverAddress", ""],
	val port: Int = MmkvUtils["port", 8883],
	val enableSSL: Boolean = MmkvUtils["enableSSL", true],
	val username: String = MmkvUtils["username", ""],
	val password: String = MmkvUtils["password", ""],
	val secretKey: String = MmkvUtils["secretKey", ""],
	val topic: String = MmkvUtils["topic", "clipboard"],
)
