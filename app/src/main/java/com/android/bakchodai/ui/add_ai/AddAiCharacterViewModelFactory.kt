package com.android.bakchodai.ui.add_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.bakchodai.data.repository.ConversationRepository

class AddAiCharacterViewModelFactory(
    private val repository: ConversationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddAiCharacterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddAiCharacterViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for AddAiCharacterViewModel")
    }
}