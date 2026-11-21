package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PineBright,
    onPrimary = PineDeep,
    primaryContainer = PineForest,
    onPrimaryContainer = SageSurface,
    secondary = TerracottaBright,
    onSecondary = Color(0xFF3E1404),
    secondaryContainer = Color(0xFF6B2810),
    onSecondaryContainer = TerracottaSoft,
    tertiary = Color(0xFF9AC5AF),
    onTertiary = PineDeep,
    background = NightPineBg,
    onBackground = NightSageInk,
    surface = NightPineSurface,
    onSurface = NightSageInk,
    surfaceVariant = NightPineCard,
    onSurfaceVariant = NightMossInk,
    surfaceContainerLow = NightPineSurface,
    surfaceContainer = NightPineCard,
    surfaceContainerHigh = Color(0xFF22362D),
    outline = Color(0xFF364D42),
    outlineVariant = Color(0xFF26382F)
)

private val LightColorScheme = lightColorScheme(
    primary = PineDeep,
    onPrimary = Color.White,
    primaryContainer = SageSurface,
    onPrimaryContainer = PineDeep,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaSoft,
    onSecondaryContainer = TerracottaDark,
    tertiary = PineMuted,
    onTertiary = Color.White,
    background = SandstoneBg,
    onBackground = CharcoalInk,
    surface = SandstoneCard,
    onSurface = CharcoalInk,
    surfaceVariant = SandstoneVariant,
    onSurfaceVariant = MossInk,
    surfaceContainerLow = SandstoneBg,
    surfaceContainer = SandstoneCard,
    surfaceContainerHigh = SandstoneVariant,
    outline = SageBorder,
    outlineVariant = Color(0xFFE0DDD3)
)

val SingletrackShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SingletrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = SingletrackShapes,
        content = content
    )
}
