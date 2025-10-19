package com.android.bakchodai.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val profilePictureUrl: String? = null,
    val personality: String = ""
) {
    /**
     * Gets a displayable avatar URL.
     * Returns the profilePictureUrl if it exists, otherwise generates a default
     * placeholder avatar based on the user's name.
     */
    fun getAvatarUrl(): String {
        return profilePictureUrl ?: "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}&background=random"
    }
}