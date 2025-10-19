// ConversationListItem.kt
package com.android.bakchodai.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationListItem(
    conversation: Conversation,
    onConversationClick: (String) -> Unit
) {
    val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }
    val placeholderIcon = if (conversation.group) Icons.Filled.Group else Icons.Filled.Person
    val avatarUrl = "https://ui-avatars.com/api/?name=${conversation.name.replace(" ", "+")}&background=random"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConversationClick(conversation.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Picture
        AsyncImage(
            model = avatarUrl, // Placeholder URL for now
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(placeholderIcon), // Use icon based on chat type
            error = rememberVectorPainter(placeholderIcon) // Use icon based on chat type
        )

        Spacer(Modifier.width(16.dp))

        // Name and Last Message
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = conversation.name,
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

        // Timestamp
        if (lastMessage != null) {
            Text(
                text = formatTimestamp(lastMessage.timestamp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}