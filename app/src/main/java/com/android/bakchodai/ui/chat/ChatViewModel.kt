package com.android.bakchodai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ConversationRepository
) : ViewModel() {

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    init {
        viewModelScope.launch {
            repository.getUsersFlow().collectLatest { userList ->
                _users.value = userList
            }
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            repository.getConversationFlow(conversationId).collectLatest { conv ->
                _conversation.value = conv
            }
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        val message = Message(
            senderId = currentUser.uid,
            content = text,
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.addMessage(conversationId, message)
        }
    }

    fun addEmojiReaction(conversationId: String, messageId: String, emoji: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            val ref = Firebase.database.reference
                .child("conversations/$conversationId/messages/$messageId/reactions/${currentUser.uid}")
            ref.setValue(emoji)
        }
    }
}