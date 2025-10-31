package com.android.bakchodai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.android.bakchodai.data.repository.ConversationRepository
import com.android.bakchodai.ui.navigation.AppNavigation
import com.android.bakchodai.ui.theme.BakchodAITheme
import com.android.bakchodai.ui.theme.ThemeHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: ConversationRepository // NEW
    @Inject lateinit var auth: FirebaseAuth // NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme by ThemeHelper.isDarkTheme(LocalContext.current)
                .collectAsState(initial = false)

            BakchodAITheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Set user to ONLINE when app comes to foreground
        auth.currentUser?.uid?.let { uid ->
            Log.d("MainActivity", "Setting user ONLINE: $uid")
            lifecycleScope.launch {
                repository.updateUserPresence(uid, true)
            }
            // Set the onDisconnect hook
            repository.setOfflineOnDisconnect(uid)
        }
    }

    override fun onStop() {
        super.onStop()
        // Set user to OFFLINE when app goes to background
        auth.currentUser?.uid?.let { uid ->
            Log.d("MainActivity", "Setting user OFFLINE: $uid")
            lifecycleScope.launch {
                repository.updateUserPresence(uid, false)
            }
        }
    }
}