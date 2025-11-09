package com.android.chatalystai.ui.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.chatalystai.data.model.User
import com.android.chatalystai.data.repository.ConversationRepository
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AiProfileViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val storage: FirebaseStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val userId: String = savedStateHandle.get<String>("userId")!!

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    val user: StateFlow<User?> = repository.getUsersFlow()
        .map { users -> users.find { it.uid == userId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateUserAvatar(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val storageRef = storage.reference.child("avatars/$userId.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                val timestamp = System.currentTimeMillis()

                repository.updateUserAvatarUrl(userId, downloadUrl, timestamp)
            } catch (e: Exception) {
                Log.e("AiProfileViewModel", "Failed to upload avatar", e)
                _errorEvent.value = "Failed to update avatar."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserAvatarFromUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val timestamp = System.currentTimeMillis()
                repository.updateUserAvatarUrl(userId, url, timestamp)
            } catch (e: Exception) {
                Log.e("AiProfileViewModel", "Failed to update avatar from URL", e)
                _errorEvent.value = "Failed to update avatar."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorEvent.value = null
    }
}