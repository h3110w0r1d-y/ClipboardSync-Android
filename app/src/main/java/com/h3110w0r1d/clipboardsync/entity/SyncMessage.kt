package com.h3110w0r1d.clipboardsync.entity

import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val deviceID: String,
    val type: String,
    val content: String,
    val timestamp: Long,
)