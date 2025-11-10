package com.android.chatalystai.ui.add_ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.chatalystai.data.model.User
import com.android.chatalystai.data.remote.AiService
import com.android.chatalystai.data.remote.GoogleImageService
import com.android.chatalystai.data.repository.ConversationRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddAiCharacterViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val aiService: AiService,
    private val googleImageService: GoogleImageService,
    private val auth: FirebaseAuth
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
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _errorMessage.value = "You must be logged in to create an AI."
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
                val sanitizedName = name.trim().lowercase()
                    .replace("[^a-z0-9\\s]".toRegex(), "")
                    .replace("\\s+".toRegex(), "_")
                    .replace("__+".toRegex(), "_")
                    .trim('_')

                val aiUid = "ai_${sanitizedName}_${UUID.randomUUID().toString().substring(0, 4)}"

                var imageUrl: String? = null
                if (imageSearchQuery.isNotBlank()) {
                    Log.d("AddAiViewModel", "Searching for image with query: '$imageSearchQuery'")
                    imageUrl = googleImageService.searchImage(imageSearchQuery)

                    if (imageUrl.isNullOrBlank()) {
                        Log.w("AddAiViewModel", "Google Image Search failed for '$imageSearchQuery'")
                        val simplifiedQuery = "$name portrait"
                        Log.d("AddAiViewModel", "Trying simplified query: '$simplifiedQuery'")
                        imageUrl = googleImageService.searchImage(simplifiedQuery)
                    }
                }

                val finalAvatarUrl = if (!imageUrl.isNullOrBlank()) {
                    Log.d("AddAiViewModel", "Successfully found image: $imageUrl")
                    imageUrl
                } else {
                    Log.e("AddAiViewModel", "Image search failed for '$imageSearchQuery'. Character will be created without an avatar.")
                    null
                }

                val newAiUser = User(
                    uid = aiUid,
                    name = name.trim(),
                    personality = personality.trim(),
                    avatarUrl = finalAvatarUrl,
                    backgroundStory = background.trim(),
                    interests = interests.trim(),
                    speakingStyle = style.trim(),
                    creatorId = currentUserId
                )

                repository.addUser(newAiUser)
                _addSuccess.value = true

            } catch (e: Exception) {
                Log.e("AddAiViewModel", "Error saving AI character", e)
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