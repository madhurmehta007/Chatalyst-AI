package com.android.chatalystai.ui.edit_group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.chatalystai.data.model.Conversation
import com.android.chatalystai.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditGroupViewModel @Inject constructor(
    private val repository: ConversationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val conversationId: String = savedStateHandle.get<String>("conversationId")!!

    val conversation: StateFlow<Conversation?> = repository.getConversationFlow(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun saveGroupDetails(newName: String, newTopic: String) {
        if (newName.isBlank()) {
            _errorMessage.value = "Group name cannot be empty."
            return
        }
        if (conversation.value == null) {
             _errorMessage.value = "Cannot save, conversation data not loaded yet."
             return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _saveSuccess.value = false
            try {
                repository.updateGroupDetails(conversationId, newName.trim(), newTopic.trim())
                _saveSuccess.value = true // Signal success
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

     fun clearError() {
        _errorMessage.value = null
    }

    fun resetSuccess() {
        _saveSuccess.value = false
    }
}