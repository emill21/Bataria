package com.batterypulse.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batterypulse.app.data.Appearance

// Fallback palette (Android 8–11, or dynamic color off), from a green seed.
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
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE4EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
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
    surfaceContainerLowest = Color(0xFF0A0F0B),
    surfaceContainerLow = Color(0xFF171D18),
    surfaceContainer = Color(0xFF1B211C),
    surfaceContainerHigh = Color(0xFF252B26),
    surfaceContainerHighest = Color(0xFF303630),
)

private val Tabular = TextStyle(fontFeatureSettings = "tnum")
private val base = Typography()

val AppTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-2).sp).merge(Tabular),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp).merge(Tabular),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp).merge(Tabular),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp).merge(Tabular),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold).merge(Tabular),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold).merge(Tabular),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold).merge(Tabular),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold).merge(Tabular),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun BatteryPulseTheme(
    appearance: Appearance,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var colors = when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && appearance.pureBlack) {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF232323),
        )
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
