package com.android.bakchodai.ui.conversations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit,
    users: List<User>,
    isLoading: Boolean,
    onClearChat: (String) -> Unit, // *** ADDED ***
    onMuteConversation: (String, Long) -> Unit // *** ADDED ***
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }

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
                        // *** MODIFICATION: Pass long press lambda ***
                        onLongPress = {
                            selectedConversation = conversation
                            showOptionsDialog = true
                        }
                    )
                }
            }
        }

        // *** MODIFICATION: Add dialogs ***

        // Main options dialog
        if (showOptionsDialog && selectedConversation != null) {
            ConversationOptionsDialog(
                conversation = selectedConversation!!,
                onDismiss = { showOptionsDialog = false; selectedConversation = null },
                onMuteClick = { showOptionsDialog = false; showMuteDialog = true },
                onClearChatClick = { showOptionsDialog = false; showClearChatDialog = true }
            )
        }

        // Mute dialog
        if (showMuteDialog && selectedConversation != null) {
            MuteDialog(
                onDismiss = { showMuteDialog = false; selectedConversation = null },
                onMute = { duration ->
                    onMuteConversation(selectedConversation!!.id, duration)
                    showMuteDialog = false
                    selectedConversation = null
                }
            )
        }

        // Clear chat dialog
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
                    TextButton(onClick = { showClearChatDialog = false; selectedConversation = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// *** ADDED: Options Dialog ***
@Composable
private fun ConversationOptionsDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onMuteClick: () -> Unit,
    onClearChatClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(conversation.name) },
        text = {
            Column {
                TextButton(onClick = onMuteClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Mute Notifications", modifier = Modifier.fillMaxWidth())
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

// *** ADDED: Mute Dialog (copied from ChatScreen) ***
@Composable
private fun MuteDialog(
    onDismiss: () -> Unit,
    onMute: (Long) -> Unit
) {
    val options = mapOf(
        "8 Hours" to TimeUnit.HOURS.toMillis(8),
        "1 Week" to TimeUnit.DAYS.toMillis(7),
        "Always" to -1L // Use -1 as a flag for "Always"
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