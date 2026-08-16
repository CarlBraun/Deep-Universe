package com.deepuniverse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpaceBlack = Color(0xFF07060E)
val DeepIndigo = Color(0xFF141126)
val PanelIndigo = Color(0xFF1D1934)
val DriftViolet = Color(0xFF7B5CC4)
val DriftGlow = Color(0xFFB79BFF)
val Starlight = Color(0xFFF2E9FF)
val MutedStar = Color(0xFFA79FC0)
val EmberRose = Color(0xFFE85D75)

private val DarkColors = darkColorScheme(
    primary = DriftViolet,
    onPrimary = Starlight,
    primaryContainer = PanelIndigo,
    onPrimaryContainer = Starlight,
    secondary = EmberRose,
    onSecondary = Starlight,
    background = SpaceBlack,
    onBackground = Starlight,
    surface = DeepIndigo,
    onSurface = Starlight,
    surfaceVariant = PanelIndigo,
    onSurfaceVariant = MutedStar,
    outline = Color(0xFF3A3358),
)

/**
 * The game is dark by design — it is set in deep space and the art is lit for it. The light scheme
 * exists only so that a device forced to light mode still renders readable text rather than
 * inheriting Material defaults that clash with the palette.
 */
private val LightColors = lightColorScheme(
    primary = DriftViolet,
    onPrimary = Color.White,
    background = Color(0xFFF6F3FF),
    onBackground = Color(0xFF1A1730),
    surface = Color.White,
    onSurface = Color(0xFF1A1730),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun DeepUniverseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
