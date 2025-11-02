package com.android.bakchodai.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.MessageType
import com.android.bakchodai.data.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListItem(
    conversation: Conversation,
    users: List<User>,
    onConversationClick: (String) -> Unit,
    onLongPress: () -> Unit
) {
    val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }
    val currentUserId = Firebase.auth.currentUser?.uid
    val displayName: String
    val hapticFeedback = LocalHapticFeedback.current

    val unreadCount = if (currentUserId == null) 0 else {
        conversation.messages.values.count {
            it.senderId != currentUserId && !it.readBy.containsKey(currentUserId)
        }
    }

    val (avatarUrl, placeholderIcon) = if (conversation.group) {
        displayName = conversation.name
        Pair(
            "https://ui-avatars.com/api/?name=${
                conversation.name.replace(
                    " ",
                    "+"
                )
            }&background=random",
            Icons.Filled.Group
        )
    } else {
        val otherUserId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val otherUser = users.find { it.uid == otherUserId }

        displayName = otherUser?.name ?: "Unknown User"
        Pair(
            otherUser?.resolveAvatarUrl() ?: "https://ui-avatars.com/api/?name=?",
            Icons.Filled.Person
        )
    }

    // *** MODIFICATION: Media-aware preview text logic ***
    val lastMessagePreview: @Composable () -> Unit = @Composable {
        val previewColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        if (lastMessage == null) {
            Text(
                text = "No messages yet.",
                color = previewColor,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val (icon, text) = when (lastMessage.type) {
                MessageType.IMAGE -> Icons.Default.Photo to "Photo"
                MessageType.AUDIO -> Icons.Default.Audiotrack to "Audio"
                MessageType.VIDEO -> Icons.Default.Photo to "Video" // Assuming
                MessageType.TEXT -> null to lastMessage.content
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = previewColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = text,
                    color = previewColor,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onConversationClick(conversation.id) },
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(placeholderIcon),
            error = rememberVectorPainter(placeholderIcon)
        )

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            lastMessagePreview()
        }

        Spacer(Modifier.width(16.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            if (lastMessage != null) {
                Text(
                    text = formatTimestamp(lastMessage.timestamp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            val isMuted =
                conversation.mutedUntil == -1L || conversation.mutedUntil > System.currentTimeMillis()

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMuted) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Text(
                            text = "$unreadCount",
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}