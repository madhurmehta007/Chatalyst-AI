package com.android.bakchodai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.theme.WhatsAppDarkSentBubble
import com.android.bakchodai.ui.theme.WhatsAppSentBubble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    sender: User,
    isGroup: Boolean,
    onEmojiReact: (String) -> Unit
) {
    val horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start

    // WhatsApp-style bubble shape
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    }

    val bubbleColor = if (isFromMe) {
        if (androidx.compose.foundation.isSystemInDarkTheme()) WhatsAppDarkSentBubble else WhatsAppSentBubble
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    var showEmojiPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        // Removed the old profile pic + name label that was outside the bubble

        Box(
            modifier = Modifier
                .shadow(1.dp, bubbleShape)
                .clip(bubbleShape)
                .background(bubbleColor)
                .clickable { showEmojiPicker = !showEmojiPicker }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // *** UI IMPROVEMENT ***
                // Add sender's name inside the bubble for groups
                if (isGroup && !isFromMe) {
                    Text(
                        text = sender.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, // Use a distinct color
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(text = message.content, color = textColor)

                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )

                // Display emoji reactions (simplified)
                if (message.reactions?.isNotEmpty() == true) {
                    Row {
                        message.reactions.values.forEach { emoji ->
                            Text(
                                text = emoji,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (showEmojiPicker) {
            Row {
                listOf("😄", "😂", "👍", "🔥").forEach { emoji ->
                    Text(
                        text = emoji,
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable {
                                onEmojiReact(emoji)
                                showEmojiPicker = false
                            }
                    )
                }
            }
        }
    }
}