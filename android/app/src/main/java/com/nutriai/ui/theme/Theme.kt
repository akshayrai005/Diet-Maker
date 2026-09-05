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

private val LightColors = lightColorScheme(
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
    outline = Color(0xFFE5E7EB),
    error = CriticalLight,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
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

private data class Accent(val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color)

private fun accentFor(name: String, dark: Boolean): Accent = when (name) {
    "pink" -> if (dark) Accent(Color(0xFFF3A9C6), Color(0xFF4A1229), Color(0xFF7A2947), Color(0xFFFAE0EA))
    else Accent(Color(0xFFD46A93), Color.White, Color(0xFFFAE0EA), Color(0xFF7A2947))
    "yellow" -> if (dark) Accent(Color(0xFFEBD07A), Color(0xFF3D2F05), Color(0xFF6B540A), Color(0xFFFAF0CE))
    else Accent(Color(0xFFC9A227), Color.White, Color(0xFFFAF0CE), Color(0xFF6B540A))
    else -> if (dark) Accent(BrandGreen, Color(0xFF003322), BrandGreenDeep, BrandMint)
    else Accent(BrandGreen, Color.White, CardGreenLight, BrandGreenDeep)
}

@Composable
fun NutriAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "green",
    content: @Composable () -> Unit,
) {
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
    ) {
        CompositionLocalProvider(LocalKaizenColors provides kaizenColorTokens(darkTheme)) {
            androidx.compose.material3.ProvideTextStyle(
                value = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = Nunito),
                content = content,
            )
        }
    }
}
