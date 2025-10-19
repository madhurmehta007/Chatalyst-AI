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
            .fillMaxWidth() // The parent column takes full width
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = horizontalAlignment // This pushes the children to the start or end
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp) // <-- THE FIX: Wrap content, but with a max width
                .shadow(1.dp, bubbleShape)
                .clip(bubbleShape)
                .background(bubbleColor)
                .clickable { showEmojiPicker = !showEmojiPicker }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (isGroup && !isFromMe) {
                    Text(
                        text = sender.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
            }
        }

        // Emoji Reactions Display
        if (message.reactions.isNotEmpty()) {
            val groupedReactions = message.reactions.values.groupingBy { it }.eachCount()

            Row(
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                groupedReactions.forEach { (emoji, count) ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Emoji Picker
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