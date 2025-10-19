package com.android.bakchodai.data.model

import com.google.firebase.database.IgnoreExtraProperties
import java.net.URLEncoder

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val avatarUrl: String = "\"https://ui-avatars.com/api/?name=${
        name.replace(
            " ",
            "+"
        )
    }&background=random\"",
    val personality: String = ""
){
    fun resolveAvatarUrl(): String {
        return avatarUrl.ifBlank {
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