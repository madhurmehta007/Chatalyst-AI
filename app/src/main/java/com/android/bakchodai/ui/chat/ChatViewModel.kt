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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ConversationRepository
) : ViewModel() {

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    // *** ADDED: Loading state ***
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getUsersFlow().collectLatest { userList ->
                _users.value = userList
            }
        }
    }

    fun loadConversation(conversationId: String) {
        _isLoading.value = true // Set loading to true when we start
        viewModelScope.launch {
            repository.getConversationFlow(conversationId).collectLatest { conv ->
                // Check if the latest message is from a human, if so, AI is done typing
                val lastMessage = conv?.messages?.values?.maxByOrNull { it.timestamp }
                if (lastMessage != null && !lastMessage.senderId.startsWith("ai_")) {
                    _isAiTyping.value = false
                }
                _conversation.value = conv
                _isLoading.value = false // Set loading to false after we get the first value
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
            // Check if this is a 1-to-1 AI chat and set typing status
            val convo = _conversation.value
            if (convo != null && !convo.group && convo.participants.keys.any { it.startsWith("ai_") }) {
                _isAiTyping.value = true
            }
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

    // --- New Functions ---

    fun editMessage(conversationId: String, messageId: String, newText: String) {
        viewModelScope.launch {
            repository.updateMessage(conversationId, messageId, newText)
        }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(conversationId, messageId)
        }
    }

    fun deleteGroup(conversationId: String) {
        viewModelScope.launch {
            repository.deleteGroup(conversationId)
        }
    }
}