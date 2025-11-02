package com.android.bakchodai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.MessageType
import com.android.bakchodai.data.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    sender: User,
    isGroup: Boolean,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    conversation: Conversation,
    currentUserId: String,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
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

    val hapticFeedback = LocalHapticFeedback.current
    var swipeOffset by remember { mutableStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        label = "SwipeOffsetAnimation"
    )

    val swipeThreshold = 150f
    val dragResistance = 0.25f

    val rowModifier = Modifier
        .fillMaxWidth()
        .background(selectionColor)
        .padding(horizontal = 8.dp, vertical = 2.dp)
        .graphicsLayer(translationX = animatedSwipeOffset)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val dragStart = awaitHorizontalDragOrCancellation(down.id)

                if (dragStart != null) {
                    if (dragStart.positionChange().x > 0) {
                        dragStart.consume()
                        var totalDrag = 0f
                        val dragSuccessful = drag(dragStart.id) { change ->
                            val horizontalChange = change.positionChange().x
                            if (horizontalChange > 0) {
                                change.consume()
                                totalDrag += horizontalChange
                                swipeOffset = (totalDrag * dragResistance).coerceIn(0f, swipeThreshold + 100f)
                            } else {
                                change.consume()
                            }
                        }
                        if (dragSuccessful && swipeOffset > swipeThreshold) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwipeToReply()
                        }
                    }
                }
                swipeOffset = 0f
            }
        }
        .pointerInput(Unit) {
            // This is the tap/long-press gesture
            detectTapGestures(
                onLongPress = {
                    onLongPress() // This starts multi-select
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onTap = {
                    // *** MODIFICATION: Handle tap based on selection mode ***
                    if (isInSelectionMode) {
                        onTap() // This toggles selection
                    } else {
                        // This is a simple tap, toggle the emoji picker
                        showEmojiPicker = !showEmojiPicker
                    }
                }
            )
        }

    Row(
        modifier = rowModifier,
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

            // *** MODIFICATION: Added Emoji Picker ***
            AnimatedVisibility(
                visible = showEmojiPicker,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        onEmojiReact(emoji)
                        showEmojiPicker = false
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            var isDropdownExpanded by remember { mutableStateOf(false) }

            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .shadow(1.dp, bubbleShape)
                        .clip(bubbleShape)
                        .background(bubbleColor)
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
                                    val previewText = message.replyPreview ?: ""
                                    val (icon, text) = when {
                                        previewText.startsWith("📷") -> Icons.Default.Photo to previewText
                                        previewText.startsWith("🎤") -> Icons.Default.Audiotrack to previewText
                                        else -> null to previewText
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (icon != null) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Media",
                                                modifier = Modifier.size(16.dp),
                                                tint = textColor.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = text,
                                            color = textColor.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
        }
    }
}

// *** MODIFICATION: Added EmojiPicker composable ***
@Composable
private fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clickable { onEmojiSelected(emoji) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
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