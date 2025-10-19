package com.android.bakchodai.data.model

import com.google.firebase.database.IgnoreExtraProperties

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
)