package com.nutriai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nutriai.R

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

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

private val Default = Typography().withFamily(Nunito)

val NutriTypography = Default.copy(
    // Hero numbers: 40sp ExtraBold
    displayLarge = Default.displayLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, lineHeight = 48.sp),
    // Large display: 34sp Bold
    displayMedium = Default.displayMedium.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, lineHeight = 42.sp),
    // Screen-level metric: 28sp Bold
    displaySmall = Default.displaySmall.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 36.sp),
    // Screen titles: 28sp Bold
    headlineLarge = Default.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 36.sp),
    // Large section titles: 22sp Bold
    headlineMedium = Default.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp, lineHeight = 30.sp),
    // Section headings: 18sp Bold
    headlineSmall = Default.headlineSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    // Sub-section: 20sp Bold
    titleLarge = Default.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    // Component titles: 16sp SemiBold
    titleMedium = Default.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    // Small titles: 14sp SemiBold
    titleSmall = Default.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    // Body: 16sp Regular
    bodyLarge = Default.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    // Secondary body: 15sp Regular
    bodyMedium = Default.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    // Small body: 13sp
    bodySmall = Default.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    // Buttons: 15sp SemiBold
    labelLarge = Default.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    // Secondary labels: 13sp Medium
    labelMedium = Default.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    // Metadata: 12sp
    labelSmall = Default.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
)
