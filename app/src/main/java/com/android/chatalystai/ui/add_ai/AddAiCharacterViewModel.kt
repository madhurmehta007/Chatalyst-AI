// chatalystai/ui/add_ai/AddAiCharacterViewModel.kt

package com.android.chatalystai.ui.add_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.chatalystai.data.model.User
import com.android.chatalystai.data.remote.AiService
import com.android.chatalystai.data.remote.GiphyService
import com.android.chatalystai.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddAiCharacterViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val aiService: AiService,
    private val giphyService: GiphyService
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _addSuccess = MutableStateFlow(false)
    val addSuccess: StateFlow<Boolean> = _addSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _generatedPersona = MutableStateFlow<Map<String, String>?>(null)
    val generatedPersona: StateFlow<Map<String, String>?> = _generatedPersona.asStateFlow()

    fun generatePersona(prompt: String) {
        if (prompt.isBlank()) {
            _errorMessage.value = "Please enter a prompt to generate a persona."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val personaMap = aiService.generateAiPersona(prompt)
                if (personaMap.isEmpty()) {
                    _errorMessage.value = "Failed to generate persona. Please try again."
                } else {
                    _generatedPersona.value = personaMap
                }
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveAiCharacter(
        name: String,
        personality: String,
        background: String,
        interests: String,
        style: String,
        imageSearchQuery: String
    ) {

        if (name.isBlank() || personality.isBlank() || background.isBlank() || style.isBlank()) {
            _errorMessage.value = "All fields except Interests are required."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _addSuccess.value = false
            try {
                val aiUid = "ai_${
                    name.trim().lowercase().replace("\\s+".toRegex(), "_")
                }_${UUID.randomUUID().toString().substring(0, 4)}"

                val imageUrl = if (imageSearchQuery.isNotBlank()) {
                    giphyService.searchGif(imageSearchQuery)
                } else {
                    null
                }

                // *** MODIFICATION: Removed Dicebear fallback ***
                // If imageUrl is blank (Giphy search failed or no query), finalAvatarUrl will be null.
                val finalAvatarUrl = if (!imageUrl.isNullOrBlank()) {
                    imageUrl
                } else {
                    null // This will store null in Firebase if Giphy fails
                }
                // *** END MODIFICATION ***

                val newAiUser = User(
                    uid = aiUid,
                    name = name.trim(),
                    personality = personality.trim(),
                    avatarUrl = finalAvatarUrl, // Will be null if Giphy failed
                    backgroundStory = background.trim(),
                    interests = interests.trim(),
                    speakingStyle = style.trim()
                )

                repository.addUser(newAiUser)
                _addSuccess.value = true

            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
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

    fun clearGeneratedPersona() {
        _generatedPersona.value = null
    }
}