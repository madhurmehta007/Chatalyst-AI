package com.android.bakchodai.ui.add_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.bakchodai.data.model.User
import com.android.bakchodai.data.repository.ConversationRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder
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

    private val currentUserId = Firebase.auth.currentUser?.uid
    val isUserPremium: StateFlow<Boolean> = repository.getUsersFlow()
        .map { users ->
            users.find { it.uid == currentUserId }?.isPremium ?: false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun addAiCharacter(
        name: String,
        personality: String,
        background: String,
        interests: String,
        style: String
    ) {
        if (!isUserPremium.value) {
            _errorMessage.value = "Upgrade to Premium to create custom AI characters."
            return
        }

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
                val encodedName = try {
                    URLEncoder.encode(name.trim(), "UTF-8")
                } catch (e: Exception) {
                    name.trim()
                }
                val avatarUrl = "https://api.dicebear.com/7.x/avataaars/avif?seed=${encodedName}"

                val newAiUser = User(
                    uid = aiUid,
                    name = name.trim(),
                    personality = personality.trim(),
                    avatarUrl = avatarUrl,
                    backgroundStory = background.trim(),
                    interests = interests.trim(),
                    speakingStyle = style.trim()
                )

                repository.addUser(newAiUser)
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