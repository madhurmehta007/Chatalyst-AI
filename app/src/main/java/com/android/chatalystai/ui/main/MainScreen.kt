package com.android.chatalystai.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.chatalystai.ui.auth.AuthViewModel
import com.android.chatalystai.ui.conversations.ConversationListScreen
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onConversationClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val conversations by mainViewModel.conversations.collectAsState()
    val users by mainViewModel.otherUsers.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val tabs = listOf("Chats", "Groups")

    val selectedConversationIds = remember { mutableStateListOf<String>() }
    val isInSelectionMode = selectedConversationIds.isNotEmpty()
    var showMuteDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    val firstSelectedConvo = conversations.find { it.id == selectedConversationIds.firstOrNull() }

    BackHandler(enabled = isInSelectionMode) {
        selectedConversationIds.clear()
    }

    Scaffold(
        topBar = {
            if (isInSelectionMode) {
                ContextualTopBar(
                    selectedCount = selectedConversationIds.size,
                    onClose = { selectedConversationIds.clear() },
                    onMute = { showMuteDialog = true },
                    onClearChat = { showClearChatDialog = true }
                )
            } else {
                NormalTopBar(
                    currentUser = currentUser,
                    onProfileClick = onProfileClick
                )
            }
        },
        floatingActionButton = {
            if (!isInSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        if (pagerState.currentPage == 0) onNewChatClick() else onNewGroupClick()
                    },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    if (pagerState.currentPage == 0) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "New Chat", tint = Color.White)
                    } else {
                        Icon(Icons.Default.Groups, contentDescription = "New Group", tint = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (!isInSelectionMode) {
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
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isInSelectionMode
            ) { page ->
                val pagerConversations = if (page == 0) {
                    conversations.filter { !it.group }
                } else {
                    conversations.filter { it.group }
                }

                ConversationListScreen(
                    conversations = pagerConversations,
                    users = users,
                    isLoading = isLoading,
                    selectedConversationIds = selectedConversationIds,
                    onConversationTap = { convoId ->
                        if (isInSelectionMode) {
                            if (convoId in selectedConversationIds) {
                                selectedConversationIds.remove(convoId)
                            } else {
                                selectedConversationIds.add(convoId)
                            }
                        } else {
                            onConversationClick(convoId)
                        }
                    },
                    onConversationLongPress = { convoId ->
                        if (!isInSelectionMode) {
                            selectedConversationIds.add(convoId)
                        }
                    }
                )
            }
        }
    }

    if (showMuteDialog && firstSelectedConvo != null) {
        val isMuted = firstSelectedConvo.mutedUntil == -1L || firstSelectedConvo.mutedUntil > System.currentTimeMillis()
        MuteDialog(
            isMuted = isMuted,
            onDismiss = { showMuteDialog = false },
            onMute = { duration ->
                selectedConversationIds.forEach { convoId ->
                    mainViewModel.muteConversation(convoId, duration)
                }
                showMuteDialog = false
                selectedConversationIds.clear()
            }
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Clear ${selectedConversationIds.size} chat${if (selectedConversationIds.size > 1) "s" else ""}?") },
            text = { Text("Are you sure you want to clear this chat history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedConversationIds.forEach { convoId ->
                            mainViewModel.clearChat(convoId)
                        }
                        showClearChatDialog = false
                        selectedConversationIds.clear()
                    },
                ) { Text("Clear Chat", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(
    currentUser: com.android.chatalystai.data.model.User?,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = { Text("Chatalyst", fontWeight = FontWeight.Bold) }, // Renamed
        actions = {
            IconButton(onClick = onProfileClick) {
                if (currentUser != null) {
                    AsyncImage(
                        model = currentUser?.resolveAvatarUrl(),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Default.AccountCircle),
                        error = rememberVectorPainter(Icons.Default.AccountCircle)
                    )
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = Color.White)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onMute: () -> Unit,
    onClearChat: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Selection")
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Mute Notifications") },
                    onClick = {
                        onMute()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Clear Chat") },
                    onClick = {
                        onClearChat()
                        menuExpanded = false
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun MuteDialog(
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onMute: (Long) -> Unit
) {
    var selectedDuration by remember { mutableStateOf(TimeUnit.HOURS.toMillis(8)) }
    val options = mapOf(
        "8 Hours" to TimeUnit.HOURS.toMillis(8),
        "1 Week" to TimeUnit.DAYS.toMillis(7),
        "Always" to -1L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMuted) "Unmute notifications" else "Mute notifications") },
        text = {
            if (isMuted) {
                Text("This chat is muted. Do you want to unmute?")
            } else {
                Column(Modifier.selectableGroup()) {
                    options.forEach { (label, duration) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (duration == selectedDuration),
                                    onClick = { selectedDuration = duration }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (duration == selectedDuration),
                                onClick = { selectedDuration = duration }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            if (isMuted) {
                TextButton(onClick = { onMute(0L) }) { // 0L to unmute
                    Text("Unmute")
                }
            } else {
                TextButton(onClick = { onMute(selectedDuration) }) {
                    Text("OK")
                }
            }
        }
    )
}