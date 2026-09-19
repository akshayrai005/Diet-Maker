package com.nutriai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalKaizenColors = staticCompositionLocalOf { kaizenColorTokens(dark = false) }

val MaterialTheme.kaizenColors: KaizenColorTokens
    @Composable get() = LocalKaizenColors.current

private val LightColors get() = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = CardGreenLight,
    onPrimaryContainer = BrandGreenDeep,
    secondary = KaizenBlue,
    onSecondary = Color.White,
    secondaryContainer = CardBlueLight,
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary = KaizenCoral,
    onTertiary = Color.White,
    tertiaryContainer = CardCoralLight,
    onTertiaryContainer = Color(0xFFB71C1C),
    background = AppBackgroundLight,
    onBackground = OnSurfaceLight,
    surface = AppSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFF5C6B7A),
    error = CriticalLight,
    onError = Color.White,
)

private val DarkColors get() = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF003322),
    primaryContainer = BrandGreenDeep,
    onPrimaryContainer = BrandMint,
    secondary = KaizenBlue,
    onSecondary = Color(0xFF0E2A52),
    secondaryContainer = Color(0xFF1A2D4B),
    onSecondaryContainer = CardBlueLight,
    tertiary = KaizenCoral,
    onTertiary = Color(0xFF4A1B0E),
    tertiaryContainer = Color(0xFF3D1F1A),
    onTertiaryContainer = CardCoralLight,
    background = AppBackgroundDark,
    onBackground = OnSurfaceDark,
    surface = AppSurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = Color(0xFF9E9EB8),
    outline = Color(0xFF2A2A45),
    error = CriticalDark,
    onError = Color(0xFF3A0A0A),
)

@Composable
fun NutriAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "spectrum",
    content: @Composable () -> Unit,
) {
    // One palette drives everything: pick the theme, then build the Material colours from it.
    AppPalette.apply(accent, darkTheme)
    val base = if (darkTheme) DarkColors else LightColors
    val colorScheme = base.copy(
        primary = AppPalette.primary,
        onPrimary = Color.White,
        primaryContainer = AppPalette.tint(AppPalette.primary, 0.18f),
        onPrimaryContainer = AppPalette.primaryDeep,
        secondary = AppPalette.stop(3),
        secondaryContainer = AppPalette.tint(AppPalette.stop(3), 0.16f),
        tertiary = AppPalette.stop(1),
        tertiaryContainer = AppPalette.tint(AppPalette.stop(1), 0.16f),
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NutriTypography,
    ) {
        CompositionLocalProvider(LocalKaizenColors provides kaizenColorTokens(darkTheme)) {
            androidx.compose.material3.ProvideTextStyle(
                value = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = Nunito),
                content = content,
            )
        }
    }
}
