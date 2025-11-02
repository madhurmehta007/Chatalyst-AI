package com.android.bakchodai.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.Conversation
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: ConversationRepository) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _otherUsers = MutableStateFlow<List<User>>(emptyList())
    val otherUsers: StateFlow<List<User>> = _otherUsers.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val auth: FirebaseAuth = Firebase.auth

    init {
        // ... (init block is unchanged) ...
        val userIdFlow = callbackFlow<String?> {
            val listener = FirebaseAuth.AuthStateListener {
                trySend(it.currentUser?.uid)
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }

        viewModelScope.launch {
            userIdFlow.flatMapLatest { userId ->
                _isLoading.value = true
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    repository.getConversationsFlow()
                }
            }.collectLatest { convos ->
                _conversations.value = convos
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            userIdFlow.combine(repository.getUsersFlow()) { userId, allUsers ->
                if (userId == null) {
                    Pair<User?, List<User>>(null, emptyList())
                } else {
                    val currentUser = allUsers.find { it.uid == userId }
                    val otherUsers = allUsers.filter { it.uid != userId }
                    Pair(currentUser, otherUsers)
                }
            }.collectLatest { (currentUser, otherUsers) ->
                _currentUser.value = currentUser
                _otherUsers.value = otherUsers
            }
        }
    }

    fun clearChat(conversationId: String) {
        viewModelScope.launch {
            repository.clearChat(conversationId)
        }
    }

    fun muteConversation(conversationId: String, durationMillis: Long) {
        viewModelScope.launch {
            val mutedUntil = if (durationMillis == -1L) {
                -1L // Flag for "Always"
            } else {
                System.currentTimeMillis() + durationMillis
            }
            repository.setConversationMuted(conversationId, mutedUntil)
        }
    }
}