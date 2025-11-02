package com.android.bakchodai.ui.conversations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.User
import com.google.firebase.Firebase // Import
import com.google.firebase.auth.auth // Import
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit,
    users: List<User>,
    isLoading: Boolean,
    onClearChat: (String) -> Unit,
    onMuteConversation: (String, Long) -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }

    val currentUserId = Firebase.auth.currentUser?.uid

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (conversations.isEmpty()) {
            Text(
                "No conversations yet.",
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationListItem(
                        conversation = conversation,
                        users = users,
                        onConversationClick = onConversationClick,
                        onLongPress = {
                            selectedConversation = conversation
                            showOptionsDialog = true
                        }
                    )
                }
            }
        }

        if (showOptionsDialog && selectedConversation != null) {
            ConversationOptionsDialog(
                conversation = selectedConversation!!,
                users = users,
                currentUserId = currentUserId,
                onDismiss = { showOptionsDialog = false; selectedConversation = null },
                onMuteClick = { showOptionsDialog = false; showMuteDialog = true },
                onClearChatClick = { showOptionsDialog = false; showClearChatDialog = true }
            )
        }

        if (showMuteDialog && selectedConversation != null) {
            val isMuted =
                selectedConversation!!.mutedUntil == -1L || selectedConversation!!.mutedUntil > System.currentTimeMillis()
            MuteDialog(
                isMuted = isMuted,
                onDismiss = { showMuteDialog = false; selectedConversation = null },
                onMute = { duration ->
                    onMuteConversation(selectedConversation!!.id, duration)
                    showMuteDialog = false
                    selectedConversation = null
                }
            )
        }

        if (showClearChatDialog && selectedConversation != null) {
            AlertDialog(
                onDismissRequest = { showClearChatDialog = false; selectedConversation = null },
                title = { Text("Clear Chat") },
                text = { Text("Are you sure you want to clear this entire chat history? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearChat(selectedConversation!!.id)
                            showClearChatDialog = false
                            selectedConversation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Clear Chat") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showClearChatDialog = false; selectedConversation = null
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ConversationOptionsDialog(
    conversation: Conversation,
    users: List<User>,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onMuteClick: () -> Unit,
    onClearChatClick: () -> Unit
) {
    val displayName = if (conversation.group) {
        conversation.name
    } else {
        val otherUserId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val otherUser = users.find { it.uid == otherUserId }
        otherUser?.name ?: conversation.name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(displayName) },
        text = {
            Column {
                TextButton(onClick = onMuteClick, modifier = Modifier.fillMaxWidth()) {
                    val isMuted =
                        conversation.mutedUntil == -1L || conversation.mutedUntil > System.currentTimeMillis()
                    Text(
                        if (isMuted) "Unmute Notifications" else "Mute Notifications",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(onClick = onClearChatClick, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Clear Chat",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                TextButton(onClick = { onMute(0L) }) {
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