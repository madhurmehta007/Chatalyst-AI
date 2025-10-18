package com.android.bakchodai.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.bakchodai.R
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversation: Conversation,
    users: List<User>,
    onSendMessage: (String) -> Unit,
    onEmojiReact: (String, String) -> Unit
) {
    val usersById = users.associateBy { it.uid }
    val currentUserId = Firebase.auth.currentUser?.uid
    var isTyping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Simulate typing indicator for AI users
    LaunchedEffect(conversation.messages) {
        if (conversation.group && conversation.participants.keys.any { it.startsWith("ai_") }) {
            isTyping = true
            delay(2000) // Simulate typing for 2 seconds
            isTyping = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(conversation.name)
                        if (conversation.group) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isTyping) "Typing..." else "Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTyping) Color.Gray else Color.Green
                            )
                        }
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val messages = conversation.messages.values.sortedByDescending { it.timestamp }
                items(messages, key = { it.id }) { message ->
                    val sender = usersById[message.senderId] ?: User(name = "Unknown")
                    MessageBubble(
                        message = message,
                        isFromMe = message.senderId == currentUserId,
                        sender = sender,
                        isGroup = conversation.group,
                        onEmojiReact = { emoji -> onEmojiReact(message.id, emoji) }
                    )
                }
                if (isTyping && conversation.group) {
                    item {
                        Text(
                            text = "Someone is typing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            MessageInput(onSendMessage = onSendMessage)
        }
    }
}