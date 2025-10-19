package com.android.bakchodai.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Message(
    val id: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val isEdited: Boolean = false // Added for edit feature
)