package com.nutriai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBrandGradient: Brush = Brush.verticalGradient(
    listOf(
        Color(0xFF041E14),
        Color(0xFF0E4D36),
        Color(0xFF176B4D),
    ),
)

val LightHeroGradient: Brush = Brush.verticalGradient(
    listOf(
        Color(0xFF176B4D),
        Color(0xFF1A7A57),
        Color(0xFF43B77A),
    ),
)

@Composable
fun BrandGradientBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBrandGradient),
        content = content,
    )
}

/**
 * The app's signature multi-colour blend (orange -> hot pink -> violet -> indigo), used on every top bar and primary
 * action so the theme flows through several hues instead of a single flat accent. White text reads on every stop.
 */
val SpectrumBrush: Brush
    get() = AppPalette.horizontal
