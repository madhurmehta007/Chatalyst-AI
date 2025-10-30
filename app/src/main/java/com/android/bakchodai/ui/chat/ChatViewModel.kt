package com.android.bakchodai.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val aiService: AiService
) : ViewModel() {

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _typingUsers = MutableStateFlow<List<User>>(emptyList())
    val typingUsers: StateFlow<List<User>> = _typingUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val currentUserId = Firebase.auth.currentUser?.uid

    init {
        viewModelScope.launch {
            repository.getUsersFlow().collectLatest { userList ->
                _users.value = userList
            }
        }

        viewModelScope.launch {
            conversation.combine(users) { conversation, userList ->
                if (conversation == null) {
                    emptyList<User>()
                } else {
                    val usersById = userList.associateBy { it.uid }
                    conversation.typing.keys
                        .filter { it != currentUserId }
                        .mapNotNull { usersById[it] }
                }
            }.collectLatest { typingUserList ->
                _typingUsers.value = typingUserList
            }
        }
    }

    fun loadConversation(conversationId: String) {
        _isLoading.value = true
        _conversation.value = null // Clear old conversation immediately

        viewModelScope.launch {
            repository.getConversationFlow(conversationId).collectLatest { conv ->
                _conversation.value = conv
                // Only set loading false *after* the first emission, even if null
                if (_isLoading.value) { // Avoid setting false repeatedly
                    _isLoading.value = false
                    Log.d("ChatViewModel", "Conversation $conversationId loaded (exists: ${conv != null})")
                }
            }
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        val userMessage = Message(
            senderId = currentUser.uid,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.addMessage(conversationId, userMessage)
            Log.d("ChatViewModel", "User message sent to repo: ${userMessage.content}")

            val currentConvoState = _conversation.value
            val isOneToOneAiChat = currentConvoState != null && !currentConvoState.group && currentConvoState.participants.keys.any { it.startsWith("ai_") }
            Log.d("ChatViewModel", "Is 1-to-1 AI chat? $isOneToOneAiChat")


            if (isOneToOneAiChat) {
                val aiParticipantId = currentConvoState!!.participants.keys.first { it.startsWith("ai_") }

                Log.d("ChatViewModel", "AI typing started")
                repository.setTypingIndicator(conversationId, aiParticipantId, true)

                try {
                    val currentMessages = currentConvoState.messages.values.toList()
                    val historyForAI = (currentMessages + userMessage).sortedBy { it.timestamp } // Add new message and sort

                    Log.d("ChatViewModel", "History size for AI: ${historyForAI.size}, Last User Msg: ${userMessage.content}")

                    // 4. Generate AI response
                    val aiResponseText = aiService.getResponse(historyForAI) // Pass the constructed history
                    Log.d("ChatViewModel", "AI response received: $aiResponseText")

                    // 5. Create and save the AI's message
                    if (aiResponseText.isNotBlank()) {
                        val aiMessage = Message(
                            senderId = aiParticipantId,
                            content = aiResponseText,
                            timestamp = System.currentTimeMillis() + 1 // Ensure slightly later timestamp
                        )
                        repository.addMessage(conversationId, aiMessage) // Write AI message to Firebase
                        Log.d("ChatViewModel", "AI message sent to repo: ${aiMessage.content}")

                    } else {
                        Log.w("ChatViewModel", "AI response was blank.")
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error generating/sending AI response", e)
                }
                finally {
                    // MODIFIED: Ensure typing indicator stops
                    repository.setTypingIndicator(conversationId, aiParticipantId, false)
                    Log.d("ChatViewModel", "AI typing stopped")
                }
            }
        }
    }

    fun addEmojiReaction(conversationId: String, messageId: String, emoji: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            val ref = Firebase.database.reference
                .child("conversations/$conversationId/messages/$messageId/reactions/${currentUser.uid}")

            try {
                val snapshot = ref.get().await()
                val currentEmoji = snapshot.getValue(String::class.java)

                if (currentEmoji == emoji) {
                    ref.removeValue().await()
                } else {
                    ref.setValue(emoji).await()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to update emoji reaction", e)
            }
        }
    }

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