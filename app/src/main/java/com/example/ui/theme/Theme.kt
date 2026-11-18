package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val SkylumeDarkColorScheme = darkColorScheme(
    primary = SkyCerulean,
    onPrimary = SkyDeepNavy,
    primaryContainer = SkyTwilight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = SunGold,
    onSecondary = SkyDeepNavy,
    secondaryContainer = ColorTokens.WarmAmberContainer,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = AuroraTeal,
    onTertiary = SkyDeepNavy,
    background = SkyDeepNavy,
    onBackground = TextPrimaryLight,
    surface = SkyMidnight,
    onSurface = TextPrimaryLight,
    surfaceVariant = StormSlate,
    onSurfaceVariant = TextSecondaryLight,
    outline = GlassBorderLight,
    error = CoralWarm,
    onError = SkyDeepNavy
)

private object ColorTokens {
    val WarmAmberContainer = androidx.compose.ui.graphics.Color(0xFF3E3018)
}

val SkylumeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun SkylumeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SkylumeDarkColorScheme,
        typography = SkylumeTypography,
        shapes = SkylumeShapes,
        content = content
    )
}
