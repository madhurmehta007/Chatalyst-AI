package com.android.bakchodai.ui.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.Message
import com.android.bakchodai.data.model.MessageType
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val aiService: AiService,
    private val storage: FirebaseStorage
) : ViewModel() {

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _typingUsers = MutableStateFlow<List<User>>(emptyList())
    val typingUsers: StateFlow<List<User>> = _typingUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _replyToMessage = MutableStateFlow<Message?>(null)
    val replyToMessage: StateFlow<Message?> = _replyToMessage.asStateFlow()

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
        viewModelScope.launch {
            repository.getConversationFlow(conversationId).collectLatest { conv ->
                _conversation.value = conv
                if (_isLoading.value) {
                    _isLoading.value = false
                    Log.d("ChatViewModel", "Conversation $conversationId loaded (exists: ${conv != null})")
                }

                if (conv != null) {
                    markMessagesAsRead(conv)
                }
            }
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        val replyingTo = _replyToMessage.value

        val userMessage = Message(
            senderId = currentUser.uid,
            content = text,
            timestamp = System.currentTimeMillis(),
            replyToMessageId = replyingTo?.id,
            replyPreview = replyingTo?.content,
            replySenderName = users.value.find { it.uid == replyingTo?.senderId }?.name
        )

        viewModelScope.launch {
            repository.addMessage(conversationId, userMessage)
            Log.d("ChatViewModel", "User message sent to repo: ${userMessage.content}")
            _replyToMessage.value = null
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

    fun setReplyToMessage(message: Message?) {
        _replyToMessage.value = message
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

    private fun markMessagesAsRead(conversation: Conversation) {
        val userId = currentUserId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val unreadMessageIds = conversation.messages.values
                .filter { it.senderId != userId && !it.readBy.containsKey(userId) }
                .map { it.id }

            if (unreadMessageIds.isNotEmpty()) {
                repository.markMessagesAsRead(conversation.id, unreadMessageIds, userId)
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

    fun sendImage(conversationId: String, imageUri: Uri) {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            _isUploading.value = true
            try {
                // Create a unique ID for the image
                val imageId = UUID.randomUUID().toString()
                val storageRef = storage.reference
                    .child("images/$conversationId/$imageId.jpg")

                // 1. Upload the file to Firebase Storage
                storageRef.putFile(imageUri).await()

                // 2. Get the download URL
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // 3. Create and send the message object
                val imageMessage = Message(
                    senderId = currentUser.uid,
                    content = downloadUrl, // The URL is the content
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.IMAGE // *** Set the type ***
                )
                repository.addMessage(conversationId, imageMessage)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error uploading image", e)
                // TODO: You could emit an error event here
            } finally {
                _isUploading.value = false
            }
        }
    }
}