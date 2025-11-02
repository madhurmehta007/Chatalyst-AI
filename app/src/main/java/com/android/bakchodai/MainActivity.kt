package com.android.bakchodai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

    @Inject lateinit var repository: ConversationRepository
    @Inject lateinit var auth: FirebaseAuth

    // *** MODIFICATION: Add permission launcher ***
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is already granted
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // *** MODIFICATION: Ask for permission on create ***
        askNotificationPermission()

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