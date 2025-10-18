package com.android.bakchodai.ui.creategroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.bakchodai.data.repository.ConversationRepository

class CreateGroupViewModelFactory(private val repository: ConversationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateGroupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateGroupViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
