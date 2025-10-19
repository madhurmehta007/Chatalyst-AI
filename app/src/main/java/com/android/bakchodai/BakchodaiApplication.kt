package com.android.bakchodai

import android.app.Application
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.repository.OfflineFirstConversationRepository
import com.android.bakchodai.data.service.GroupChatService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BakchodaiApplication : Application() {

    @Inject
    lateinit var repository: OfflineFirstConversationRepository // Inject concrete class

    @Inject // Inject AiService
    lateinit var aiService: AiService

    private var groupChatService: GroupChatService? = null // Hold reference

    override fun onCreate() {
        super.onCreate()

        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                // User logged in: Start syncing and the group service
                repository.startSyncing()
                // Ensure service starts only once
                if (groupChatService == null) {
                    groupChatService = GroupChatService(repository, aiService)
                    // Launch service start in a coroutine to avoid blocking main thread
                    CoroutineScope(Dispatchers.Default).launch {
                        groupChatService?.start()
                    }
                }
            } else {
                // User logged out: Stop syncing
                repository.stopSyncing()
                groupChatService = null // Release reference
            }
        }
    }
}