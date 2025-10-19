package com.android.bakchodai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    isAiTyping: Boolean, // Collect this from the ViewModel
    onSendMessage: (String) -> Unit,
    onEmojiReact: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val usersById = users.associateBy { it.uid }
    val currentUserId = Firebase.auth.currentUser?.uid
    val listState = rememberLazyListState()

    // This is for the group "typing..." simulation
    var isGroupTyping by remember { mutableStateOf(false) }
    LaunchedEffect(conversation.messages) {
        if (conversation.group && conversation.participants.keys.any { it.startsWith("ai_") }) {
            isGroupTyping = true
            delay(2000) // Simulate typing for 2 seconds
            isGroupTyping = false
        }
        // Scroll to bottom when a new message arrives
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(conversation.name)
                        Spacer(Modifier.width(8.dp))

                        // Use the correct typing indicator
                        val typingText = if (conversation.group) {
                            if (isGroupTyping) "Typing..." else "Online"
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
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
            LazyColumn(
                state = listState,
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
            }
            MessageInput(onSendMessage = onSendMessage)
        }
    }
}