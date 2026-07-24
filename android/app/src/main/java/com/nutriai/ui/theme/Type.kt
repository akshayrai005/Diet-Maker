package com.nutriai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.nutriai.R

/**
 * Kaizen type scale. Uses **Nunito** — a warm, rounded, friendly Google Font — downloaded via the
 * Google Fonts provider and cached, with an automatic system-font fallback so it works offline / on
 * devices without Play Services. Body/label text is a touch larger with more line height so screens
 * feel roomy, not cramped; headings are bold with slightly tighter tracking. Sizes are in sp, so the
 * user's font-size accessibility setting is fully respected.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val nunito = GoogleFont("Nunito")

private val WarmFamily = FontFamily(
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Bold),
)

private val Default = Typography()

val NutriTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Default.displayMedium.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = Default.displaySmall.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Default.headlineLarge.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = Default.headlineMedium.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = Default.headlineSmall.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold),
    titleLarge = Default.titleLarge.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Bold),
    titleMedium = Default.titleMedium.copy(fontFamily = WarmFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = WarmFamily, fontWeight = FontWeight.SemiBold),
    // Roomier body/label text for comfortable reading.
    bodyLarge = Default.bodyLarge.copy(fontFamily = WarmFamily, fontSize = 16.5.sp, lineHeight = 25.sp),
    bodyMedium = Default.bodyMedium.copy(fontFamily = WarmFamily, fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = Default.bodySmall.copy(fontFamily = WarmFamily, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = Default.labelLarge.copy(fontFamily = WarmFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = WarmFamily, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = WarmFamily),
)
