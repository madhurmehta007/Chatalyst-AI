package com.android.bakchodai.ui.creategroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateGroupViewModel(private val repository: ConversationRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val users: StateFlow<List<User>> = repository.getUsersFlow()
        .map { users ->
            _isLoading.value = false
            users.filter { it.uid.startsWith("ai_") }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groupCreated = MutableStateFlow<String?>(null)
    val groupCreated: StateFlow<String?> = _groupCreated

    fun createGroup(name: String, participantIds: List<String>, topic: String) {
        viewModelScope.launch {
            val currentUser = Firebase.auth.currentUser ?: return@launch
            val allParticipantIds = participantIds + currentUser.uid
            val conversationId = repository.createGroup(name, allParticipantIds, topic)
            _groupCreated.value = conversationId
        }
    }
}