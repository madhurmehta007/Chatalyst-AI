// ConversationListItem.kt
package com.android.bakchodai.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.bakchodai.data.model.Conversation

@Composable
fun ConversationListItem(
    conversation: Conversation,
    onConversationClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConversationClick(conversation.id) }
            .padding(16.dp)
    ) {
        Column {
            Text(text = conversation.name)
            val lastMessage = conversation.messages.values.maxByOrNull { it.timestamp }
            Text(text = lastMessage?.content ?: "No messages yet.")
        }
    }
}