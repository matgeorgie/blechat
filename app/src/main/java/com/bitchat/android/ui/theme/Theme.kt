package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Colors that match the iOS bitchat theme
// Colors that match the updated BLEChat theme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2196F3),        // Blue
    onPrimary = Color.Black,
    secondary = Color(0xFF03A9F4),      // Light Blue
    onSecondary = Color.Black,
    background = Color(0xFF0A0A0A),     // Very dark
    onBackground = Color(0xFF2196F3),   // Blue on black
    surface = Color(0xFF1E1E1E),        // Dark gray surface
    onSurface = Color(0xFFE0E0E0),      // Light text
    error = Color(0xFFFF5555),          // Red for errors
    onError = Color.Black,
    tertiary = Color(0x99EBEBF5),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),        // Dark Blue
    onPrimary = Color.White,
    secondary = Color(0xFF1565C0),      // Darker Blue
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1976D2),   // Dark blue on white
    surface = Color(0xFFF5F7FA),        // Soft light blue/gray
    onSurface = Color(0xFF333333),      // Dark text
    error = Color(0xFFCC0000),          // Dark red for errors
    onError = Color.White,
    tertiary = Color(0x993C3C43)
)

@Composable
fun BitchatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
