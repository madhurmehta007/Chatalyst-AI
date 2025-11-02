package com.android.bakchodai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User


@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val avatarUrl: String?,
    val personality: String,
    val backgroundStory: String,
    val interests: String,
    val speakingStyle: String,
    val isOnline: Boolean,
    val lastSeen: Long,
    val fcmToken: String = "",
    val bio: String = "",
    val isPremium: Boolean = false,
    val avatarUploadTimestamp: Long = 0L
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val participants: Map<String, Boolean>,
    val messages: Map<String, Message>,
    val isGroup: Boolean,
    val topic: String,
    val typing: Map<String, Boolean>,
    val mutedUntil: Long = 0L
)

data class ConversationWithUsers(
    val conversation: ConversationEntity,
    val userList: List<UserEntity>
)