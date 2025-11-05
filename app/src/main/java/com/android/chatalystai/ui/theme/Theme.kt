package com.android.chatalystai.ui.theme

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

// Dark Color Scheme based on Chatalyst palette
private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    secondary = NeonCoral,
    tertiary = CyanBlue,
    background = MidnightBlack,
    surface = DarkGraySurface,
    onPrimary = SoftWhite,
    onSecondary = SoftWhite,
    onTertiary = Color.Black, // Cyan is light, black text is better
    onBackground = SoftWhite,
    onSurface = SoftWhite,
    surfaceVariant = DarkGrayBubble, // "Other" message bubble
    onSurfaceVariant = SoftWhite,
    tertiaryContainer = MidnightBlack, // Chat background
    primaryContainer = ElectricPurple, // Contextual top bar
    onPrimaryContainer = SoftWhite
)

// Light Color Scheme based on Chatalyst palette
private val LightColorScheme = lightColorScheme(
    primary = ElectricPurple,
    secondary = NeonCoral,
    tertiary = CyanBlue,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = SoftWhite,
    onSecondary = SoftWhite,
    onTertiary = Color.Black,
    onBackground = LightText,
    onSurface = LightText,
    surfaceVariant = LightGrayBubble, // "Other" message bubble
    onSurfaceVariant = LightText,
    tertiaryContainer = LightSurface, // Chat background
    primaryContainer = ElectricPurple, // Contextual top bar
    onPrimaryContainer = SoftWhite
)

@Composable
fun ChatalystAITheme( // Renamed from BakchodAITheme
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to enforce Chatalyst theme
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
            // Set status bar to the main background color
            window.statusBarColor = colorScheme.background.toArgb()
            // Set nav bar color
            window.navigationBarColor = colorScheme.background.toArgb()

            // Icons on status bar and nav bar should be light/dark based on theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}