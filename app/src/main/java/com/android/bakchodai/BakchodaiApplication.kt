package com.android.bakchodai

import android.app.Application
import com.android.bakchodai.data.remote.AiService
import com.android.bakchodai.data.remote.GiphyService
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

    @Inject lateinit var repository: OfflineFirstConversationRepository
    @Inject lateinit var aiService: AiService
    @Inject lateinit var giphyService: GiphyService // NEW: Inject GiphyService
    private var groupChatService: GroupChatService? = null

    override fun onCreate() {
        super.onCreate()

        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                repository.startSyncing()
                if (groupChatService == null) {
                    // MODIFIED: Pass GiphyService to the constructor
                    groupChatService = GroupChatService(repository, aiService, giphyService)
                    // Launch service start
                    CoroutineScope(Dispatchers.IO).launch {
                        groupChatService?.start()
                    }
                }
            } else {
                repository.stopSyncing()
                groupChatService?.stop()
                groupChatService = null
            }
        }
    }
}