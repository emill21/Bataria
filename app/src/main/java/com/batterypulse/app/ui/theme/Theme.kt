package com.batterypulse.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (Android 8–11) generated from a green seed; Android 12+ uses wallpaper colors.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6A3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB0F1B9),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF516351),
    secondaryContainer = Color(0xFFD4E8D1),
    onSecondaryContainer = Color(0xFF0F1F11),
    tertiary = Color(0xFF39656F),
    tertiaryContainer = Color(0xFFBCEAF6),
    onTertiaryContainer = Color(0xFF001F25),
    background = Color(0xFFF6FBF3),
    surface = Color(0xFFF6FBF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D59F),
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF125128),
    onPrimaryContainer = Color(0xFFB0F1B9),
    secondary = Color(0xFFB8CCB6),
    secondaryContainer = Color(0xFF3A4B3A),
    onSecondaryContainer = Color(0xFFD4E8D1),
    tertiary = Color(0xFFA1CED9),
    tertiaryContainer = Color(0xFF1F4D56),
    onTertiaryContainer = Color(0xFFBCEAF6),
    background = Color(0xFF0F1510),
    surface = Color(0xFF0F1510),
)

@Composable
fun BatteryPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
