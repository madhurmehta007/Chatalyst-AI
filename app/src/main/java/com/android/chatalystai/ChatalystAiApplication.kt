package com.android.chatalystai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.android.chatalystai.data.remote.AiService
import com.android.chatalystai.data.remote.GiphyService
import com.android.chatalystai.data.remote.GoogleImageService
import com.android.chatalystai.data.repository.OfflineFirstConversationRepository
import com.android.chatalystai.data.service.GroupChatService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChatalystAiApplication : Application(), Application.ActivityLifecycleCallbacks {

    @Inject lateinit var repository: OfflineFirstConversationRepository
    @Inject lateinit var aiService: AiService
    @Inject lateinit var googleImageService: GoogleImageService
    @Inject lateinit var giphyService: GiphyService
    private var groupChatService: GroupChatService? = null

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this) // Register the callbacks

        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                repository.startSyncing()
                if (groupChatService == null) {
                    groupChatService = GroupChatService(repository, aiService, googleImageService, giphyService)
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


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        isAppInForeground = true
    }

    override fun onActivityResumed(activity: Activity) {
        isAppInForeground = true
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        isAppInForeground = false
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}