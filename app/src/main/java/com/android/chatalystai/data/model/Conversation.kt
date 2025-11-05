package com.android.chatalystai.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Conversation(
    val id: String = "",
    val name: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val messages: Map<String, Message> = emptyMap(),
    val group: Boolean = false,
    val topic: String = "",
    val typing: Map<String, Boolean> = emptyMap(),
    val mutedUntil: Long = 0L
)
