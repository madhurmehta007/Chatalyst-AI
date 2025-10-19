package com.android.bakchodai.ui.chat

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.theme.WhatsAppChatBackground
import com.android.bakchodai.ui.theme.WhatsAppDarkChatBackground
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversation: Conversation,
    users: List<User>,
    isAiTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onEmojiReact: (String, String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onNavigateToEditGroup: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onBack: () -> Unit
) {
    val usersById = users.associateBy { it.uid }
    val currentUserId = Firebase.auth.currentUser?.uid
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // --- State Management ---
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }

    // --- Group typing simulation ---
    var isGroupTyping by remember { mutableStateOf(false) }
    LaunchedEffect(conversation.messages) {
        if (conversation.group && conversation.participants.keys.any { it.startsWith("ai_") }) {
            isGroupTyping = true
            delay(2000)
            isGroupTyping = false
        }
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // --- Back Press Handler ---
    // If in selection mode, pressing back cancels selection mode.
    BackHandler(enabled = (selectedMessageId != null)) {
        selectedMessageId = null
    }

    Scaffold(
        topBar = {
            // *** Conditionally show the correct TopAppBar ***
            if (selectedMessageId == null) {
                NormalTopBar(
                    conversation = conversation,
                    usersById = usersById,
                    currentUserId = currentUserId,
                    isGroupTyping = isGroupTyping,
                    isAiTyping = isAiTyping,
                    onBack = onBack,
                    onNavigateToEditGroup = onNavigateToEditGroup,
                    onShowDeleteGroupDialog = { showDeleteGroupDialog = true }
                )
            } else {
                ContextualTopBar(
                    onClose = { selectedMessageId = null },
                    onDelete = { showDeleteDialog = true }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        val chatBgColor = if (androidx.compose.foundation.isSystemInDarkTheme())
            WhatsAppDarkChatBackground
        else
            WhatsAppChatBackground

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(chatBgColor)
        ) {
            val messages = conversation.messages.values.sortedByDescending { it.timestamp }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message -> // Use the messages list directly
                    val sender = usersById[message.senderId] ?: User(uid = message.senderId, name = "Unknown") // Handle unknown sender
                    if (message.id.isNotBlank()) { // Add a check for valid message ID
                        MessageBubble(
                            message = message,
                            isFromMe = message.senderId == currentUserId,
                            sender = sender,
                            isGroup = conversation.group,
                            isSelected = (selectedMessageId == message.id),
                            onLongPress = { selectedMessageId = message.id },
                            onEmojiReact = { emoji -> onEmojiReact(message.id, emoji) }
                        )
                    } else {
                        // Log or handle messages without an ID if necessary
                        Log.w("ChatScreen", "Message found without valid ID: ${message.content}")
                    }
                }
            }
            MessageInput(onSendMessage = onSendMessage)
        }
    }

    // --- Dialogs ---

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedMessageId?.let { onDeleteMessage(it) } // Delete the selected message
                        selectedMessageId = null // Exit selection mode
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("Delete Group") },
            text = { Text("Are you sure you want to delete this group? This action cannot be undone and will delete the group for all participants.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup()
                        showDeleteGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// --- Top App Bar Composables ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(
    conversation: Conversation,
    usersById: Map<String, User>,
    currentUserId: String?,
    isGroupTyping: Boolean,
    isAiTyping: Boolean,
    onNavigateToEditGroup: () -> Unit,
    onBack: () -> Unit,
    onShowDeleteGroupDialog: () -> Unit
) {
    val context = LocalContext.current
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatarUrl = if (!conversation.group) {
                    val otherUserId = conversation.participants.keys.firstOrNull { it != currentUserId }
                    usersById[otherUserId]?.resolveAvatarUrl() ?: "https://ui-avatars.com/api/?name=?"
                } else {
                    "https://ui-avatars.com/api/?name=${conversation.name.replace(" ", "+")}&background=random"
                }

                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(conversation.name)
                    val typingText = if (conversation.group) {
                        if (isGroupTyping) "Typing..." else "${conversation.participants.size} members"
                    } else {
                        if (isAiTyping) "Typing..." else "Online"
                    }
                    val typingColor = if (isGroupTyping || isAiTyping) Color.Gray else Color.Green

                    Text(
                        text = typingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = typingColor
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (conversation.group) {
                var isGroupMenuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { isGroupMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Group Options")
                }
                DropdownMenu(
                    expanded = isGroupMenuExpanded,
                    onDismissRequest = { isGroupMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Group") },
                        onClick = {
                            onNavigateToEditGroup()
                            isGroupMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Group", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onShowDeleteGroupDialog()
                            isGroupMenuExpanded = false
                        }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualTopBar(
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("1 selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Selection")
            }
        },
        actions = {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDelete()
                        menuExpanded = false
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}