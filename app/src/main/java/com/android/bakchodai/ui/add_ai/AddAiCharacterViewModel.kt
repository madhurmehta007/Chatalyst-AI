package com.android.bakchodai.ui.add_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddAiCharacterViewModel @Inject constructor(private val repository: ConversationRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _addSuccess = MutableStateFlow(false)
    val addSuccess: StateFlow<Boolean> = _addSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addAiCharacter(
        name: String,
        personality: String,
        background: String, // New
        interests: String, // New
        style: String // New
    ) {
        // Basic validation
        if (name.isBlank() || personality.isBlank() || background.isBlank() || style.isBlank()) {
            _errorMessage.value = "All fields except Interests are required."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _addSuccess.value = false
            try {
                val aiUid = "ai_${name.trim().lowercase().replace("\\s+".toRegex(), "_")}_${UUID.randomUUID().toString().substring(0, 4)}"
                val avatarUrl = "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}&background=random"

                val newAiUser = User(
                    uid = aiUid,
                    name = name.trim(),
                    personality = personality.trim(), // Short summary
                    avatarUrl = avatarUrl,
                    // Save new fields
                    backgroundStory = background.trim(),
                    interests = interests.trim(), // Can be blank
                    speakingStyle = style.trim()
                )

                repository.addUser(newAiUser) // addUser in repo needs to handle the User model
                _addSuccess.value = true

            } catch (e: Exception) {
            }
            finally { _isLoading.value = false }

        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun resetSuccess() {
        _addSuccess.value = false
    }
}