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

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val auth: FirebaseAuth = Firebase.auth

    init {
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
            userIdFlow.combine(repository.getUsersFlow()) { userId, userList ->
                if (userId == null) {
                    emptyList()
                } else {
                    userList.filter { it.uid != userId }
                }
            }.collectLatest { filteredUsers ->
                _users.value = filteredUsers
            }
        }
    }

    fun createGroup(name: String, participantIds: List<String>, topic: String) {
        viewModelScope.launch {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                val allParticipantIds = participantIds + currentUserId
                repository.createGroup(name, allParticipantIds, topic)
            }
        }
    }
}