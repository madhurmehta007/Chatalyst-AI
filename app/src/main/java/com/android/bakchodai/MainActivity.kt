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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Read the dark mode preference from ThemeHelper
            val isDarkTheme by ThemeHelper.isDarkTheme(LocalContext.current)
                .collectAsState(initial = false)

            BakchodAITheme(darkTheme = isDarkTheme) { // Pass the preference to the theme
                AppNavigation()
            }
        }
    }
}