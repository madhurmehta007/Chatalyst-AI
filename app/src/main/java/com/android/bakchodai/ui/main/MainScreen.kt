package com.android.bakchodai.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.auth.AuthViewModel
import com.android.bakchodai.ui.conversations.ConversationListScreen
import com.android.bakchodai.ui.profile.ProfileScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    // This NavController is for the "Profile" screen overlay
    val navController = rememberNavController()
    val conversations by mainViewModel.conversations.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val currentUser by authViewModel.user.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val tabs = listOf("Chats", "Groups")

    NavHost(navController = navController, startDestination = "main_tabs") {
        composable("main_tabs") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Bakchod AI", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { navController.navigate("profile") }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            if (pagerState.currentPage == 0) onNewChatClick() else onNewGroupClick()
                        },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        if (pagerState.currentPage == 0) {
                            Icon(Icons.Default.Chat, contentDescription = "New Chat", tint = Color.White)
                        } else {
                            Icon(Icons.Default.Groups, contentDescription = "New Group", tint = Color.White)
                        }
                    }
                }
            ) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(title) },
                                selectedContentColor = Color.White,
                                unselectedContentColor = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> ConversationListScreen(
                                conversations = conversations.filter { !it.group },
                                onConversationClick = onConversationClick,
                                isLoading = isLoading
                            )
                            1 -> ConversationListScreen(
                                conversations = conversations.filter { it.group },
                                onConversationClick = onConversationClick,
                                isLoading = isLoading
                            )
                        }
                    }
                }
            }
        }
        composable("profile") {
            val user = User(uid = currentUser?.uid ?: "", name = currentUser?.displayName ?: "")
            // ProfileScreen now needs its own Scaffold to show a TopAppBar with a back button
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Profile") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                ProfileScreen(
                    user = user,
                    onSaveClick = onSaveProfile,
                    onLogoutClick = onLogout,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}