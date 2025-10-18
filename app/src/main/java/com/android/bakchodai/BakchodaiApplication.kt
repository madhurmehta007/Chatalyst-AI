package com.android.bakchodai

import android.app.Application
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.repository.FirebaseConversationRepository
import com.android.bakchodai.data.service.GroupChatService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class BakchodaiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val repository = FirebaseConversationRepository()
        val aiService = AiService()

        // Wait for Firebase Auth to initialize before starting GroupChatService
        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                val groupChatService = GroupChatService(repository, aiService)
                groupChatService.start() // Start autonomous group chat service
            }
        }
    }
}