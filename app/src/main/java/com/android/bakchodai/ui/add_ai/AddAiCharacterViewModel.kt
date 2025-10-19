package com.android.bakchodai.ui.add_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AddAiCharacterViewModel(private val repository: ConversationRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _addSuccess = MutableStateFlow(false)
    val addSuccess: StateFlow<Boolean> = _addSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addAiCharacter(name: String, personality: String) {
        if (name.isBlank() || personality.isBlank()) {
            _errorMessage.value = "Name and Personality cannot be empty."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _addSuccess.value = false
            try {
                // Generate unique ID (simple approach, consider checking for duplicates in production)
                val aiUid = "ai_${name.trim().lowercase().replace("\\s+".toRegex(), "_")}_${UUID.randomUUID().toString().substring(0, 4)}"
                val avatarUrl = "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}&background=random"

                val newAiUser = User(
                    uid = aiUid,
                    name = name.trim(),
                    personality = personality.trim(),
                    avatarUrl = avatarUrl
                )

                repository.addUser(newAiUser)
                _addSuccess.value = true // Signal success for navigation

            } catch (e: Exception) {
                _errorMessage.value = "Failed to add AI character: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun resetSuccess() {
        _addSuccess.value = false
    }
}