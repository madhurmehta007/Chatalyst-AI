package com.android.bakchodai.ui.chat

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.AudioPlayer
import com.android.bakchodai.data.AudioRecorder
import com.android.bakchodai.data.PlaybackState
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val aiService: AiService,
    private val storage: FirebaseStorage,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _nowPlayingMessageId = MutableStateFlow<String?>(null)
    val nowPlayingMessageId: StateFlow<String?> = _nowPlayingMessageId.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // *** MODIFICATION: For "New Messages" divider ***
    private val _firstUnreadMessageId = MutableStateFlow<String?>(null)
    val firstUnreadMessageId: StateFlow<String?> = _firstUnreadMessageId.asStateFlow()

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

        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _playbackState.value = state
                if (!state.isPlaying && state.progressMs == 0 && state.durationMs == 0) {
                    _nowPlayingMessageId.value = null
                }
            }
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            // *** MODIFICATION: Set first unread ID *before* collecting ***
            val initialConvo = repository.getConversationFlow(conversationId).first() // Get initial state
            if (initialConvo != null) {
                _firstUnreadMessageId.value = findFirstUnreadMessageId(initialConvo)
            }

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

    // *** MODIFICATION: Helper to get message preview text ***
    private fun getMessagePreview(message: Message): String {
        return when (message.type) {
            MessageType.TEXT -> message.content
            MessageType.IMAGE -> "📷 Photo"
            MessageType.AUDIO -> "🎤 Audio"
            MessageType.VIDEO -> "▶️ Video"
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
            // *** MODIFICATION: Use preview helper ***
            replyPreview = replyingTo?.let { getMessagePreview(it) },
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
                    val historyForAI = (currentMessages + userMessage).sortedBy { it.timestamp }

                    Log.d("ChatViewModel", "History size for AI: ${historyForAI.size}, Last User Msg: ${userMessage.content}")

                    val aiResponseText = aiService.getResponse(historyForAI)
                    Log.d("ChatViewModel", "AI response received: $aiResponseText")

                    if (aiResponseText.isNotBlank()) {
                        val aiMessage = Message(
                            senderId = aiParticipantId,
                            content = aiResponseText,
                            timestamp = System.currentTimeMillis() + 1
                        )
                        repository.addMessage(conversationId, aiMessage)
                        Log.d("ChatViewModel", "AI message sent to repo: ${aiMessage.content}")

                    } else {
                        Log.w("ChatViewModel", "AI response was blank.")
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error generating/sending AI response", e)
                }
                finally {
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

    val filteredMessages: StateFlow<List<Message>> =
        combine(_conversation, _searchQuery) { conversation, query ->
            if (conversation == null) {
                emptyList()
            } else {
                val allMessages = conversation.messages.values.sortedBy { it.timestamp } // *** MODIFICATION: Sort by ascending for divider logic ***
                if (query.isBlank()) {
                    allMessages
                } else {
                    allMessages.filter {
                        it.content.contains(query, ignoreCase = true)
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // *** MODIFICATION: Helper to find first unread message ***
    private fun findFirstUnreadMessageId(conversation: Conversation): String? {
        val userId = currentUserId ?: return null
        return conversation.messages.values
            .sortedBy { it.timestamp }
            .firstOrNull { it.senderId != userId && !it.readBy.containsKey(userId) }
            ?.id
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

    // *** MODIFICATION: Renamed to deleteMessages for multi-delete ***
    fun deleteMessages(conversationId: String, messageIds: List<String>) {
        viewModelScope.launch {
            repository.deleteMessages(conversationId, messageIds)
        }
    }

    // *** MODIFICATION: Added clearChat ***
    fun clearChat(conversationId: String) {
        viewModelScope.launch {
            repository.clearChat(conversationId)
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
                val imageId = UUID.randomUUID().toString()
                val storageRef = storage.reference
                    .child("images/$conversationId/$imageId.jpg")

                storageRef.putFile(imageUri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val imageMessage = Message(
                    senderId = currentUser.uid,
                    content = downloadUrl,
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.IMAGE
                )
                repository.addMessage(conversationId, imageMessage)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error uploading image", e)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun startRecording() {
        audioRecorder.start()
        _isRecording.value = true
    }

    fun stopAndSendRecording(conversationId: String) {
        val audioFile = audioRecorder.stop()
        _isRecording.value = false
        if (audioFile != null) {
            sendAudio(conversationId, audioFile)
        }
    }

    private fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to get audio duration", e)
            0L
        }
    }

    private fun sendAudio(conversationId: String, audioFile: File) {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val audioId = UUID.randomUUID().toString()
                val storageRef = storage.reference
                    .child("audio/$conversationId/$audioId.m4a")

                val audioDuration = getAudioDuration(audioFile)

                storageRef.putFile(Uri.fromFile(audioFile)).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val audioMessage = Message(
                    senderId = currentUser.uid,
                    content = downloadUrl,
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.AUDIO,
                    audioDurationMs = audioDuration
                )
                repository.addMessage(conversationId, audioMessage)
                audioFile.delete()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error uploading audio", e)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun playAudio(message: Message) {
        val currentState = _playbackState.value

        if (message.id == _nowPlayingMessageId.value) {
            if (currentState.isPlaying) {
                audioPlayer.pause()
            } else {
                audioPlayer.resume()
            }
        } else {
            _nowPlayingMessageId.value = message.id
            audioPlayer.play(message.content)
        }
    }

    fun onSeekAudio(message: Message, progress: Float) {
        if (message.id == _nowPlayingMessageId.value) {
            val targetMs = (message.audioDurationMs * progress).toInt()
            audioPlayer.seekTo(targetMs)
        }
    }

    fun stopAudioOnDispose() {
        audioPlayer.stop()
        _nowPlayingMessageId.value = null
    }
}