package com.android.bakchodai.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val profilePictureUrl: String? = null,
    val personality: String = ""
)