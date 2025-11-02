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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import java.util.concurrent.TimeUnit
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
    onSwipeToReply: () -> Unit,
    isPlaying: Boolean,
    playbackProgress: Float,
    onPlayAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit
) {
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    }
    val bubbleColor = if (isFromMe) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFromMe) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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
            .background(selectionColor)
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
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Filled.Person),
                error = rememberVectorPainter(Icons.Filled.Person)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {

            var isDropdownExpanded by remember { mutableStateOf(false) }

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

                        if (message.type == MessageType.IMAGE) {
                            AsyncImage(
                                model = message.content,
                                contentDescription = "Image from ${sender.name}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                                placeholder = rememberVectorPainter(Icons.Filled.Image),
                                error = rememberVectorPainter(Icons.Default.BrokenImage)
                            )
                        }else if (message.type == MessageType.AUDIO) {
                            AudioMessagePlayer(
                                isPlaying = isPlaying,
                                progress = playbackProgress,
                                durationMs = message.audioDurationMs,
                                onPlayClick = onPlayAudio,
                                onSeek = onSeekAudio
                            )
                        }
                        else {
                            Text(text = message.content, color = textColor)
                        }


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
                                    MaterialTheme.colorScheme.primary
                                else
                                    textColor.copy(alpha = 0.7f)

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
            }


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
}

@Composable
private fun AudioMessagePlayer(
    isPlaying: Boolean,
    progress: Float,
    durationMs: Long,
    onPlayClick: () -> Unit,
    onSeek: (Float) -> Unit
) {
    var localSliderPosition by remember { mutableStateOf(0f) }

    var isDragging by remember { mutableStateOf(false) }

    val onSeekState = rememberUpdatedState(onSeek)

    LaunchedEffect(progress, isDragging) {
        if (!isDragging) {
            localSliderPosition = progress
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause Audio"
            )
        }

        Slider(
            value = localSliderPosition,
            onValueChange = { newPosition ->
                localSliderPosition = newPosition
                isDragging = true
            },
            onValueChangeFinished = {
                onSeekState.value(localSliderPosition)
                isDragging = false
            },
            modifier = Modifier.weight(1f)
        )

        val currentTimeMs = (localSliderPosition * durationMs).toLong()
        Text(
            text = "${formatDuration(currentTimeMs)} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}