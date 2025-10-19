// file: bakchodai/ui/theme/Theme.kt
package com.android.bakchodai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppGreen,
    secondary = WhatsAppTeal,
    tertiary = WhatsAppDarkSentBubble,
    background = WhatsAppDarkBackground,
    surface = WhatsAppDarkSurface,
    onPrimary = Color.White, // Text on app bar
    onSecondary = Color.White, // Text on FAB
    onTertiary = Color.White,
    onBackground = Color.White.copy(alpha = 0.9f),
    onSurface = Color.White.copy(alpha = 0.9f),
    surfaceVariant = WhatsAppDarkSurface, // Other user's bubble
    onSurfaceVariant = Color.White.copy(alpha = 0.8f), // Text on other user's bubble,
    tertiaryContainer = WhatsAppDarkChatBackground
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppGreen,
    secondary = WhatsAppLightGreen,
    tertiary = WhatsAppSentBubble,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF0F0F0), // Light grey for "other" bubble
    onSurfaceVariant = Color.Black,
    tertiaryContainer = WhatsAppChatBackground
)

@Composable
fun BakchodAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to enforce WhatsApp theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb() // Set status bar color
            // Use isAppearanceLightStatusBars = !darkTheme (light text on dark bar, dark text on light bar)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}