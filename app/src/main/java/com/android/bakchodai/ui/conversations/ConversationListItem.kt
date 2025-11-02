package com.android.bakchodai.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge // Import
import androidx.compose.material3.BadgedBox // Import
import androidx.compose.material3.ExperimentalMaterial3Api // Import
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class) // Add this annotation
@Composable
fun ConversationListItem(
    conversation: Conversation,
    users: List<User>,
    onConversationClick: (String) -> Unit
) {
    val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }
    val currentUserId = Firebase.auth.currentUser?.uid
    val displayName: String

    // *** MODIFICATION: Calculate unread count ***
    val unreadCount = if (currentUserId == null) 0 else {
        conversation.messages.values.count {
            it.senderId != currentUserId && !it.readBy.containsKey(currentUserId)
        }
    }

    val (avatarUrl, placeholderIcon) = if (conversation.group) {
        displayName = conversation.name
        Pair(
            "https://ui-avatars.com/api/?name=${conversation.name.replace(" ", "+")}&background=random",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConversationClick(conversation.id) }
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
            Text(
                text = lastMessage?.content ?: "No messages yet.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(16.dp))

        // *** MODIFICATION: Column for timestamp and unread badge ***
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
            if (unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}