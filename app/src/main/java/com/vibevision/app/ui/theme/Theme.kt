package com.vibevision.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VibeCyan,
    onPrimary = VibeBackground,
    secondary = VibeAmber,
    onSecondary = VibeBackground,
    tertiary = VibeGreen,
    background = VibeBackground,
    surface = VibeSurface,
    onSurface = VibeTextPrimary,
    surfaceVariant = VibeSurfaceElevated,
    onSurfaceVariant = VibeTextSecondary,
    outline = VibeBorder,
    error = VibeRed,
    onError = VibeBackground
)

@Composable
fun VibeVisionTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = VibeBackground.toArgb()
            window.navigationBarColor = VibeBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
