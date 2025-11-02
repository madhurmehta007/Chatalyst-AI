package com.android.bakchodai.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewChatViewModel @Inject constructor(private val repository: ConversationRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val currentUserId = Firebase.auth.currentUser?.uid

    val users: StateFlow<List<User>> = repository.getUsersFlow()
        .map { users ->
            _isLoading.value = false
            users.filter { it.uid != currentUserId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // *** MODIFICATION: Expose isUserPremium state ***
    val isUserPremium: StateFlow<Boolean> = repository.getUsersFlow()
        .map { users ->
            users.find { it.uid == currentUserId }?.isPremium ?: false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _navigateToConversation = MutableStateFlow<String?>(null)
    val navigateToConversation: StateFlow<String?> = _navigateToConversation

    fun findOrCreateConversation(user: User) {
        viewModelScope.launch {
            val currentUser = Firebase.auth.currentUser ?: return@launch
            val currentUserId = currentUser.uid

            val existingConversations = repository.getConversations()
            val existingChat = existingConversations.firstOrNull {
                !it.group && it.participants.containsKey(currentUserId) && it.participants.containsKey(user.uid)
            }

            if (existingChat != null) {
                _navigateToConversation.value = existingChat.id
            } else {
                val newConversationId = repository.createGroup(
                    name = user.name,
                    participantIds = listOf(currentUserId, user.uid),
                    topic = "",
                    isGroup = false
                )
                _navigateToConversation.value = newConversationId
            }
        }
    }
}