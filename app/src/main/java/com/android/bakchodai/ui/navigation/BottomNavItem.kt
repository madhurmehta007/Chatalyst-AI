package com.android.bakchodai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val icon: ImageVector, val title: String) {
    object Chats : BottomNavItem("chats", Icons.Filled.Chat, "Chats")
    object Groups : BottomNavItem("groups", Icons.Filled.Group, "Groups")
}
