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
    primary = Color(0xFF2B6A3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAEF2BA),
    onPrimaryContainer = Color(0xFF00210D),
    inversePrimary = Color(0xFF93D69F),
    secondary = Color(0xFF506352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D3),
    onSecondaryContainer = Color(0xFF0E1F12),
    tertiary = Color(0xFF3A646F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEE9F6),
    onTertiaryContainer = Color(0xFF001F26),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceTint = Color(0xFF2B6A3F),
    inverseSurface = Color(0xFF2D322D),
    inverseOnSurface = Color(0xFFEEF2EA),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceDim = Color(0xFFD7DBD4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93D69F),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF0F5229),
    onPrimaryContainer = Color(0xFFAEF2BA),
    inversePrimary = Color(0xFF2B6A3F),
    secondary = Color(0xFFB7CCB7),
    onSecondary = Color(0xFF233526),
    secondaryContainer = Color(0xFF394B3B),
    onSecondaryContainer = Color(0xFFD3E8D3),
    tertiary = Color(0xFFA2CDDA),
    onTertiary = Color(0xFF03363F),
    tertiaryContainer = Color(0xFF214C56),
    onTertiaryContainer = Color(0xFFBEE9F6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF93D69F),
    inverseSurface = Color(0xFFDFE4DC),
    inverseOnSurface = Color(0xFF2D322D),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A35),
    surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
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
