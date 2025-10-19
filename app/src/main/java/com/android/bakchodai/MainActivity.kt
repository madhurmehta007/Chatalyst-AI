package com.android.bakchodai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.android.bakchodai.ui.navigation.AppNavigation
import com.android.bakchodai.ui.theme.BakchodAITheme
import com.android.bakchodai.ui.theme.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint // Add this annotation
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme by ThemeHelper.isDarkTheme(LocalContext.current)
                .collectAsState(initial = false)

            BakchodAITheme(darkTheme = isDarkTheme) {
                AppNavigation()
            }
        }
    }
}