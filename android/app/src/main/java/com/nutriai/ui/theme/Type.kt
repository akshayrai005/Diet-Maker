package com.nutriai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nutriai.R

/**
 * Kaizen type scale. Built on the Material 3 metrics but with bolder headings/titles, slightly
 * tighter tracking on large text, and a touch MORE size + line-height on body/label text so screens
 * feel roomier and warmer rather than cramped.
 *
 * Font: **Nunito** (SIL Open Font License) - a warm, rounded humanist family - is BUNDLED locally in
 * `res/font/` (latin subset, ~150 KB total). No runtime download / downloadable-font provider, so it
 * renders fully offline. It falls back to the system font automatically for any missing glyph. Sizes
 * are in sp, so everything still scales with the user's font-size accessibility setting.
 */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

/** Apply one font family to every M3 text style so the whole scale uses Nunito. */
private fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

// Base M3 metrics with Nunito applied to every style; the overrides below preserve the family.
private val Default = Typography().withFamily(Nunito)

val NutriTypography = Default.copy(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    // Roomier body/label text - a little larger with more line height for comfortable reading.
    bodyLarge = Default.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 25.sp),
    bodyMedium = Default.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = Default.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Medium),
)
