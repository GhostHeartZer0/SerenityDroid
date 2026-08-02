package com.example.serenitydroid.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SerenityColorScheme = darkColorScheme(
    primary = SerenityAccent,
    secondary = SerenityInference,
    background = SerenityBackground,
    surface = SerenitySurface,
    onPrimary = SerenityBackground,
    onSecondary = SerenityBackground,
    onBackground = SerenityText,
    onSurface = SerenityText,
    error = SerenityError
)

@Composable
fun SerenityDroidTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SerenityBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = SerenityColorScheme,
        typography = Typography,
        content = content
    )
}
