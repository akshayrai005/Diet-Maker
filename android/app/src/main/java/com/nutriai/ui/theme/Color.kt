package com.nutriai.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Kaizen v4 — Light-first, bold & vibrant health-app palette ----

// Primary brand — vivid coral-orange (warm, energetic)
val BrandGreen = Color(0xFF00C896)
val BrandGreenDark = Color(0xFF00A67D)
val BrandGreenDeep = Color(0xFF007A5C)
val BrandGreenLight = Color(0xFF5DFFC2)
val BrandMint = Color(0xFFE0FFF5)
val BrandLime = Color(0xFFF0FFF8)

// Hero section — warm gradient
val HeroGradientTop = Color(0xFF667EEA)
val HeroGradientBottom = Color(0xFF764BA2)

// Accent palette — bold & saturated
val BrandAmber = Color(0xFFFFB547)
val BrandAmberContainer = Color(0xFFFFF3E0)

val KaizenLavender = Color(0xFF7C4DFF)
val KaizenLavenderContainer = Color(0xFFEDE7F6)
val KaizenCoral = Color(0xFFFF6B6B)
val KaizenCoralContainer = Color(0xFFFFEBEE)
val KaizenBlue = Color(0xFF2196F3)
val KaizenBlueContainer = Color(0xFFE3F2FD)
val KaizenInk = Color(0xFF1A1A2E)

// Surface system — LIGHT mode first
val AppBackgroundLight = Color(0xFFF5F6FA)
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppSurfaceVariantLight = Color(0xFFF0F2F8)
val OnSurfaceLight = Color(0xFF1A1A2E)

val AppBackgroundDark = Color(0xFF0F0F1A)
val AppSurfaceDark = Color(0xFF1A1A2E)
val AppSurfaceVariantDark = Color(0xFF252540)
val OnSurfaceDark = Color(0xFFF0F0F5)

// Card colors — bold pastels for light, rich tones for dark
val CardGreenLight = Color(0xFFE8F5E9)
val CardBlueLight = Color(0xFFE3F2FD)
val CardCoralLight = Color(0xFFFFEBEE)
val CardAmberLight = Color(0xFFFFF8E1)
val CardLavenderLight = Color(0xFFEDE7F6)
val CardMintLight = Color(0xFFE0F7FA)

// ---- Semantic status roles ----
val PositiveLight = Color(0xFF00C896)
val PositiveDark = Color(0xFF00E5A8)
val CautionLight = Color(0xFFFF9800)
val CautionDark = Color(0xFFFFB74D)
val CriticalLight = Color(0xFFFF5252)
val CriticalDark = Color(0xFFFF6B6B)
val InformationLight = Color(0xFF2196F3)
val InformationDark = Color(0xFF64B5F6)
val DisabledLight = Color(0xFFBDBDBD)
val DisabledDark = Color(0xFF616161)

// Semantic domain colors — vivid
val NutritionColor = Color(0xFF00C896)
val MovementColor = Color(0xFF2196F3)
val RecoveryColor = Color(0xFF7C4DFF)
val HydrationColor = Color(0xFF00BCD4)
val CoralAccent = Color(0xFFFF6B6B)

// Gradient endpoints
val GradientStart = Color(0xFF667EEA)
val GradientMid = Color(0xFF764BA2)
val GradientEnd = Color(0xFFF093FB)
val GradientAccentStart = Color(0xFF00C896)
val GradientAccentEnd = Color(0xFF00E5A8)

val ChartColors = listOf(
    BrandGreen,
    KaizenBlue,
    KaizenCoral,
    KaizenLavender,
    BrandAmber,
)

data class KaizenColorTokens(
    val pageBackground: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val divider: Color,
    val positive: Color,
    val caution: Color,
    val critical: Color,
    val information: Color,
    val disabled: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val glass: Color,
    val glassBorder: Color,
)

fun kaizenColorTokens(dark: Boolean): KaizenColorTokens = if (dark) {
    KaizenColorTokens(
        pageBackground = AppBackgroundDark,
        surface = AppSurfaceDark,
        elevatedSurface = AppSurfaceVariantDark,
        primaryText = OnSurfaceDark,
        secondaryText = Color(0xFF9E9EB8),
        divider = Color(0xFF2A2A45),
        positive = PositiveDark,
        caution = CautionDark,
        critical = CriticalDark,
        information = InformationDark,
        disabled = DisabledDark,
        cardGradientStart = Color(0xFF1E1E35),
        cardGradientEnd = Color(0xFF252545),
        glass = Color(0x1AFFFFFF),
        glassBorder = Color(0x1AFFFFFF),
    )
} else {
    KaizenColorTokens(
        pageBackground = AppBackgroundLight,
        surface = AppSurfaceLight,
        elevatedSurface = AppSurfaceVariantLight,
        primaryText = OnSurfaceLight,
        secondaryText = Color(0xFF6B7280),
        divider = Color(0xFFE5E7EB),
        positive = PositiveLight,
        caution = CautionLight,
        critical = CriticalLight,
        information = InformationLight,
        disabled = DisabledLight,
        cardGradientStart = Color(0xFFFFFFFF),
        cardGradientEnd = Color(0xFFF8F9FE),
        glass = Color(0x0D000000),
        glassBorder = Color(0x0D000000),
    )
}
