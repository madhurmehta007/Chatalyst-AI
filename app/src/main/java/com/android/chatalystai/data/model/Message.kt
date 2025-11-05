package com.android.chatalystai.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Message(
    val id: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val type: MessageType = MessageType.TEXT,
    val reactions: Map<String, String> = emptyMap(),
    val isEdited: Boolean = false,
    val readBy: Map<String, Long> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyPreview: String? = null,
    val replySenderName: String? = null,
    val audioDurationMs: Long = 0L,
    val isSent: Boolean = true
)