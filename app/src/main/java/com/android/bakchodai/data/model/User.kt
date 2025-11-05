package com.android.bakchodai.data.model

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
    val avatarUploadTimestamp: Long = 0L
) {
    fun resolveAvatarUrl(): String {
        val baseUrl = if (!avatarUrl.isNullOrBlank()) {
            avatarUrl
        } else {
            val encodedName = try {
                URLEncoder.encode(name, "UTF-8")
            } catch (e: Exception) {
                name
            }
            "https://api.dicebear.com/7.x/avataaars/avif?seed=${encodedName}"
        }

        return if (avatarUploadTimestamp > 0) {
            if (baseUrl.contains("?")) "$baseUrl&t=$avatarUploadTimestamp"
            else "$baseUrl?t=$avatarUploadTimestamp"
        } else {
            baseUrl
        }
    }

}