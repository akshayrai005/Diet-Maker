package com.nutriai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandMint,
    onPrimaryContainer = BrandGreenDeep,
    secondary = KaizenLavender,
    onSecondary = Color.White,
    secondaryContainer = KaizenLavenderContainer,
    onSecondaryContainer = Color(0xFF3A2F63),
    tertiary = KaizenCoral,
    onTertiary = Color.White,
    tertiaryContainer = KaizenCoralContainer,
    onTertiaryContainer = Color(0xFF6B2E1E),
    background = AppBackgroundLight,
    onBackground = OnSurfaceLight,
    surface = AppSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF4A574F),
    outline = Color(0xFFB0C3B7),
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenLight,
    onPrimary = Color(0xFF0A2E1D),
    primaryContainer = BrandGreenDeep,
    onPrimaryContainer = BrandMint,
    secondary = Color(0xFFC3B8F2),
    onSecondary = Color(0xFF241A4D),
    secondaryContainer = Color(0xFF3A2F63),
    onSecondaryContainer = KaizenLavenderContainer,
    tertiary = Color(0xFFF6B7A6),
    onTertiary = Color(0xFF4A1B0E),
    tertiaryContainer = Color(0xFF6B2E1E),
    onTertiaryContainer = KaizenCoralContainer,
    background = AppBackgroundDark,
    onBackground = OnSurfaceDark,
    surface = AppSurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB9CFC0),
    outline = Color(0xFF3E5A49),
)

/** Selectable accent palettes (primary colour). Calm Green is the brand default. */
private data class Accent(val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color)

private fun accentFor(name: String, dark: Boolean): Accent = when (name) {
    "pink" -> if (dark) Accent(Color(0xFFF3A9C6), Color(0xFF4A1229), Color(0xFF7A2947), Color(0xFFFAE0EA))
    else Accent(Color(0xFFD46A93), Color.White, Color(0xFFFAE0EA), Color(0xFF7A2947))
    "yellow" -> if (dark) Accent(Color(0xFFEBD07A), Color(0xFF3D2F05), Color(0xFF6B540A), Color(0xFFFAF0CE))
    else Accent(Color(0xFFC9A227), Color.White, Color(0xFFFAF0CE), Color(0xFF6B540A))
    else -> if (dark) Accent(BrandGreenLight, Color(0xFF0A2E1D), BrandGreenDeep, BrandMint)
    else Accent(BrandGreen, Color.White, BrandMint, BrandGreenDeep)
}

@Composable
fun NutriAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "green",
    content: @Composable () -> Unit,
) {
    // Fixed brand palette (no dynamic color) so every screen looks consistent + colorful.
    val base = if (darkTheme) DarkColors else LightColors
    val a = accentFor(accent, darkTheme)
    val colorScheme = base.copy(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NutriTypography,
        content = content,
    )
}
