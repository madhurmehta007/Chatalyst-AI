package com.android.bakchodai.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.auth.AuthViewModel
import com.android.bakchodai.ui.conversations.ConversationListScreen
import com.android.bakchodai.ui.navigation.BottomNavItem
import com.android.bakchodai.ui.profile.ProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onConversationClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val conversations by mainViewModel.conversations.collectAsState()
    val currentUser by authViewModel.user.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bakchod AI") },
                navigationIcon = {
                    if (currentDestination?.route == "profile") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentDestination?.hierarchy?.any { it.route == BottomNavItem.Chats.route } == true) {
                FloatingActionButton(onClick = onNewChatClick) {
                    Icon(Icons.Default.Chat, contentDescription = "New Chat")
                }
            } else if (currentDestination?.hierarchy?.any { it.route == BottomNavItem.Groups.route } == true) {
                FloatingActionButton(onClick = onNewGroupClick) {
                    Icon(Icons.Default.Add, contentDescription = "New Group")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(BottomNavItem.Chats, BottomNavItem.Groups)
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = BottomNavItem.Chats.route,
            Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Chats.route) {
                ConversationListScreen(
                    conversations = conversations.filter { !it.group },
                    onConversationClick = onConversationClick,
                    title = "Chats"
                )
            }
            composable(BottomNavItem.Groups.route) {
                ConversationListScreen(
                    conversations = conversations.filter { it.group },
                    onConversationClick = onConversationClick,
                    title = "Groups"
                )
            }
            composable("profile") {
                val user = User(uid = currentUser?.uid ?: "", name = currentUser?.displayName ?: "")
                ProfileScreen(
                    user = user,
                    onSaveClick = onSaveProfile,
                    onLogoutClick = onLogout
                )
            }
        }
    }
}