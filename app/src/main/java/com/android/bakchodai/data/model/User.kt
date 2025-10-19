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
    val speakingStyle: String = ""
){
    fun resolveAvatarUrl(): String {
        return if (!avatarUrl.isNullOrBlank()) {
            avatarUrl // Use the URL from Firebase if it exists
        } else {
            // Generate a default URL if Firebase doesn't have one
            val encodedName = try {
                URLEncoder.encode(name, "UTF-8")
            } catch (e: Exception) {
                name // Fallback to raw name if encoding fails
            }
            "https://ui-avatars.com/api/?name=${encodedName}&background=random"
        }
    }
}