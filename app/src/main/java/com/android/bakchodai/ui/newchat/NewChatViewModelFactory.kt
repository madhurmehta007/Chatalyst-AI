package com.android.bakchodai.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.bakchodai.data.repository.ConversationRepository

class NewChatViewModelFactory(private val repository: ConversationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NewChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NewChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
