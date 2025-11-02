package com.android.bakchodai.ui.chat

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.android.bakchodai.data.PlaybackState
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversation: Conversation,
    users: List<User>,
    isUploading: Boolean,
    isAiTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onSendMedia: (List<Uri>) -> Unit,
    onEmojiReact: (String, String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onNavigateToEditGroup: () -> Unit,
    onDeleteMessages: (List<String>) -> Unit,
    onMuteConversation: (Long) -> Unit,
    onClearChat: () -> Unit,
    onDeleteGroup: () -> Unit,
    onBack: () -> Unit,
    typingUsers: List<User>,
    replyToMessage: Message?,
    onSetReplyToMessage: (Message?) -> Unit,
    filteredMessages: List<Message>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    firstUnreadMessageId: String?,
    isRecording: Boolean,
    nowPlayingMessageId: String?,
    playbackState: PlaybackState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayAudio: (Message) -> Unit,
    onSeekAudio: (Message, Float) -> Unit,
    onStopAudio: () -> Unit
) {
    val usersById = users.associateBy { it.uid }
    val currentUserId = Firebase.auth.currentUser?.uid
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedMessageIds = remember { mutableStateListOf<String>() }
    val isInSelectionMode = selectedMessageIds.isNotEmpty()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartRecording()
        } else {
            Toast.makeText(context, "Mic permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(filteredMessages.lastOrNull()?.id) {
        if (filteredMessages.isNotEmpty() && !isInSelectionMode) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onStopAudio()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopAudio()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onSendMedia(uris)
            }
        }
    )

    BackHandler(enabled = isInSelectionMode) {
        selectedMessageIds.clear()
    }

    Scaffold(
        topBar = {
            if (isInSelectionMode) {
                ContextualTopBar(
                    selectedCount = selectedMessageIds.size,
                    onClose = { selectedMessageIds.clear() },
                    onDelete = { showDeleteDialog = true }
                )
            } else {
                if (isSearchActive) {
                    SearchTopBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        onClose = {
                            isSearchActive = false
                            onSearchQueryChanged("")
                        }
                    )
                } else {
                    NormalTopBar(
                        conversation = conversation,
                        usersById = usersById,
                        currentUserId = currentUserId,
                        typingUsers = typingUsers,
                        onBack = onBack,
                        onNavigateToEditGroup = onNavigateToEditGroup,
                        onShowDeleteGroupDialog = { showDeleteGroupDialog = true },
                        onShowClearChatDialog = { showClearChatDialog = true },
                        onShowMuteDialog = { showMuteDialog = true },
                        onSearchClick = { isSearchActive = true }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        val chatBgColor = MaterialTheme.colorScheme.tertiaryContainer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(chatBgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    reverseLayout = false,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(filteredMessages, key = { _, msg -> msg.id }) { index, message ->
                        if (message.id == firstUnreadMessageId) {
                            NewMessagesDivider()
                        }

                        val sender = usersById[message.senderId] ?: User(
                            uid = message.senderId,
                            name = "Unknown"
                        )
                        if (message.id.isNotBlank()) {
                            val isThisMessagePlaying = message.id == nowPlayingMessageId
                            val progress = if (isThisMessagePlaying && playbackState.durationMs > 0) {
                                (playbackState.progressMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            MessageBubble(
                                message = message,
                                isFromMe = message.senderId == currentUserId,
                                sender = sender,
                                isGroup = conversation.group,
                                isSelected = (message.id in selectedMessageIds),
                                isInSelectionMode = isInSelectionMode,
                                conversation = conversation,
                                currentUserId = currentUserId!!,

                                // *** MODIFICATION: Added check for current user ***
                                onLongPress = {
                                    if (!isInSelectionMode && message.senderId == currentUserId) {
                                        selectedMessageIds.add(message.id)
                                    }
                                },
                                // *** MODIFICATION: Added check for current user ***
                                onTap = {
                                    if (isInSelectionMode) {
                                        // Only allow toggling selection for your own messages
                                        if (message.senderId == currentUserId) {
                                            if (message.id in selectedMessageIds) {
                                                selectedMessageIds.remove(message.id)
                                            } else {
                                                selectedMessageIds.add(message.id)
                                            }
                                        }
                                    }
                                    // If not in selection mode, the bubble's internal
                                    // logic will handle showing the emoji picker.
                                },

                                onEmojiReact = { emoji -> onEmojiReact(message.id, emoji) },
                                onSwipeToReply = { onSetReplyToMessage(message) },
                                isPlaying = isThisMessagePlaying && playbackState.isPlaying,
                                playbackProgress = progress,
                                onPlayAudio = { onPlayAudio(message) },
                                onSeekAudio = { newProgress -> onSeekAudio(message, newProgress) }
                            )
                        } else {
                            Log.w(
                                "ChatScreen",
                                "Message found without valid ID: ${message.content}"
                            )
                        }
                    }
                }
                MessageInput(
                    onSendMessage = onSendMessage,
                    onSendMedia = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    replyToMessage = replyToMessage,
                    onCancelReply = { onSetReplyToMessage(null) },
                    isRecording = isRecording,
                    onStartRecording = {
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onStopRecording = onStopRecording
                )
            }
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 80.dp)
                )
            }
        }
    }


    if (showDeleteDialog) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message${if (count > 1) "s" else ""}") },
            text = { Text("Are you sure you want to delete $count message${if (count > 1) "s" else ""}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMessages(selectedMessageIds.toList())
                        selectedMessageIds.clear()
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

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Clear Chat") },
            text = { Text("Are you sure you want to clear this entire chat history? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearChat()
                        showClearChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear Chat") }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
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
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMuteDialog) {
        MuteDialog(
            onDismiss = { showMuteDialog = false },
            onMute = { duration ->
                onMuteConversation(duration)
                showMuteDialog = false
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(
    conversation: Conversation,
    usersById: Map<String, User>,
    currentUserId: String?,
    typingUsers: List<User>,
    onNavigateToEditGroup: () -> Unit,
    onBack: () -> Unit,
    onShowDeleteGroupDialog: () -> Unit,
    onShowClearChatDialog: () -> Unit,
    onShowMuteDialog: () -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val displayName: String
                val avatarUrl: String
                if (conversation.group) {
                    displayName = conversation.name
                    avatarUrl = "https://ui-avatars.com/api/?name=${
                        conversation.name.replace(" ", "+")
                    }&background=random"
                } else {
                    val otherUserId =
                        conversation.participants.keys.firstOrNull { it != currentUserId }
                    val otherUser = usersById[otherUserId]

                    displayName = otherUser?.name ?: "Unknown"
                    avatarUrl =
                        otherUser?.resolveAvatarUrl() ?: "https.api.dicebear.com/7.x/avataaars/avif?seed=?"
                }

                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val typingText = when {
                        typingUsers.isNotEmpty() -> {
                            if (typingUsers.size > 2) "several people are typing..."
                            else typingUsers.joinToString(separator = " and ") { it.name } + if (typingUsers.size == 1) " is typing..." else " are typing..."
                        }
                        conversation.group -> "${conversation.participants.size} members"
                        else -> "Online"
                    }

                    val typingColor = if (typingUsers.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    }

                    Text(
                        text = typingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = typingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search Chat")
            }
            val otherUserId =
                if (conversation.group) null else conversation.participants.keys.firstOrNull { it != currentUserId }
            val otherUser = otherUserId?.let { usersById[it] }

            var isMenuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }

            if (conversation.group) {
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Mute Notifications") },
                        onClick = {
                            onShowMuteDialog()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Group") },
                        onClick = {
                            onNavigateToEditGroup()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onShowClearChatDialog()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Group", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onShowDeleteGroupDialog()
                            isMenuExpanded = false
                        }
                    )
                }
            } else if (otherUser != null && !otherUser.uid.startsWith("ai_")) {
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Mute Notifications") },
                        onClick = {
                            onShowMuteDialog()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onShowClearChatDialog()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Block User", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            Toast.makeText(context, "User blocked (Not Implemented)", Toast.LENGTH_SHORT).show()
                            isMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Report User", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            Toast.makeText(context, "User reported (Not Implemented)", Toast.LENGTH_SHORT).show()
                            isMenuExpanded = false
                        }
                    )
                }
            } else { // 1-on-1 AI Chat
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onShowClearChatDialog()
                            isMenuExpanded = false
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun MuteDialog(
    onDismiss: () -> Unit,
    onMute: (Long) -> Unit
) {
    val options = mapOf(
        "8 Hours" to TimeUnit.HOURS.toMillis(8),
        "1 Week" to TimeUnit.DAYS.toMillis(7),
        "Always" to -1L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute notifications") },
        text = {
            Column {
                options.forEach { (label, duration) ->
                    TextButton(
                        onClick = { onMute(duration) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextualTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Selection")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search...", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                })
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close Search")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun NewMessagesDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "NEW MESSAGES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}