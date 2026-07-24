package com.nutriai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Kaizen type scale. Built on the Material 3 metrics but with bolder headings/titles, slightly
 * tighter tracking on large text, and a touch MORE size + line-height on body/label text so screens
 * feel roomier and warmer rather than cramped. Uses the system font family (no bundled font yet);
 * scales correctly with the user's font-size setting because sizes are in sp.
 */
private val Default = Typography()

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
    // Roomier body/label text — a little larger with more line height for comfortable reading.
    bodyLarge = Default.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 25.sp),
    bodyMedium = Default.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = Default.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Medium),
)
