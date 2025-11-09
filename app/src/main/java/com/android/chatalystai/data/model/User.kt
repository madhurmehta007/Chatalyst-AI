// chatalystai/data/model/User.kt

package com.android.chatalystai.data.model

import com.google.firebase.database.IgnoreExtraProperties
import java.net.URLEncoder

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val personality: String = "",
    val backgroundStory: String = "",
    val interests: String = "",
    val speakingStyle: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val fcmToken: String = "",
    val bio: String = "",
    val premium: Boolean = false,
    val avatarUploadTimestamp: Long = 0L,
    val creatorId: String? = null
) {
    fun resolveAvatarUrl(): String {
        if (avatarUrl.isNullOrBlank()) {
            return ""
        }

        return if (avatarUploadTimestamp > 0) {
            if (avatarUrl.contains("?")) "$avatarUrl&t=$avatarUploadTimestamp"
            else "$avatarUrl?t=$avatarUploadTimestamp"
        } else {
            avatarUrl
        }
    }

}