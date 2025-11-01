package com.android.bakchodai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.MessageType
import com.android.bakchodai.data.model.User
import com.android.bakchodai.ui.theme.WhatsAppDarkSentBubble
import com.android.bakchodai.ui.theme.WhatsAppSentBubble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    sender: User,
    isGroup: Boolean,
    isSelected: Boolean,
    conversation: Conversation,
    currentUserId: String,
    onLongPress: () -> Unit,
    onEmojiReact: (String) -> Unit,
    onSwipeToReply: () -> Unit
) {
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    }
    val bubbleColor = if (isFromMe) {
        MaterialTheme.colorScheme.tertiary // Use theme-defined "sent" color
    } else {
        MaterialTheme.colorScheme.surfaceVariant // Use theme-defined "received" color
    }

    val textColor = if (isFromMe) {
        MaterialTheme.colorScheme.onSecondaryContainer // Text for "sent" bubble
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant // Text for "received" bubble
    }

    var showEmojiPicker by remember { mutableStateOf(false) }

    val selectionColor =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent

    var swipeOffset by remember { mutableStateOf(0f) }
    val swipeThreshold = 150f

    val bubbleModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress() },
                onTap = { showEmojiPicker = !showEmojiPicker }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { swipeOffset = 0f },
                onDragEnd = {
                    if (abs(swipeOffset) > swipeThreshold) {
                        onSwipeToReply()
                    }
                    swipeOffset = 0f
                },
                onDrag = { change, dragAmount ->
                    // Only allow swiping right (positive drag)
                    if (dragAmount.x > 0) {
                        swipeOffset =
                            (swipeOffset + dragAmount.x).coerceIn(0f, swipeThreshold + 50f)
                        change.consume()
                    }
                }
            )
        }
        .graphicsLayer(translationX = swipeOffset)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionColor) // Apply selection highlight
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {

        if (!isFromMe) {
            AsyncImage(
                model = sender.resolveAvatarUrl(),
                contentDescription = "Sender Avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant), // Background for placeholder/error
                contentScale = ContentScale.Crop, // Crop image to fit circle
                placeholder = rememberVectorPainter(Icons.Filled.Person), // Show person icon while loading
                error = rememberVectorPainter(Icons.Filled.Person) // Show person icon on error
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {

            var isDropdownExpanded by remember { mutableStateOf(false) } // Moved here

            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .shadow(1.dp, bubbleShape)
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .then(bubbleModifier)
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

                        if (message.replyToMessageId != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bubbleColor.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Column {
                                    Text(
                                        text = message.replySenderName ?: "Unknown",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = message.replyPreview ?: "",
                                        color = textColor.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // --- MODIFIED: Show Image or Text ---
                        if (message.type == MessageType.IMAGE) {
                            // Use Coil's AsyncImage to load the GIF/Image from the URL
                            AsyncImage(
                                model = message.content, // This is the image URL
                                contentDescription = "Image from ${sender.name}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp) // Give the image a fixed height
                                    .clip(RoundedCornerShape(12.dp)), // Clip the image corners
                                contentScale = ContentScale.Crop,
                                placeholder = rememberVectorPainter(Icons.Filled.Image), // Placeholder icon
                                error = rememberVectorPainter(Icons.Default.BrokenImage) // Error icon
                            )
                        } else {
                            // This is the original Text composable
                            Text(text = message.content, color = textColor)
                        }
                        // --- END MODIFICATION ---


                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp)
                        ) {
                            if (message.isEdited) {
                                Text(
                                    text = "Edited",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(
                                    Date(
                                        message.timestamp
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )

                            if (isFromMe) {
                                val otherParticipants = conversation.participants.keys
                                    .filter { it != currentUserId }

                                val isReadByAll = otherParticipants.isNotEmpty() &&
                                        message.readBy.keys.containsAll(otherParticipants)

                                val statusIcon =
                                    if (isReadByAll) Icons.Default.DoneAll else Icons.Default.Done
                                val iconTint = if (isReadByAll)
                                    MaterialTheme.colorScheme.primary // Blue check
                                else
                                    textColor.copy(alpha = 0.7f) // Gray check

                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = "Message Status",
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = { isDropdownExpanded = false }
                    )
                }
            } // End Box


            if (message.reactions.isNotEmpty()) {
                val groupedReactions = message.reactions.values.groupingBy { it }.eachCount()
                Row(modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
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

            // --- 5. Emoji Picker (popup) ---
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
        } // End Bubble + Reactions Column
    } // End Main Row
}