package com.h3110w0r1d.clipboardsync

import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val deviceID: String,
    val type: String,
    val content: String,
    val timestamp: Long,
)