package com.nutriai.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Kaizen v4 — Light-first, bold & vibrant health-app palette ----

// Primary brand — vivid coral-orange (warm, energetic)
val BrandGreen: Color get() = AppPalette.primary
val BrandGreenDark: Color get() = AppPalette.primaryDark
val BrandGreenDeep: Color get() = AppPalette.primaryDeep
val BrandGreenLight: Color get() = AppPalette.primaryLight
val BrandMint: Color get() = AppPalette.tint(AppPalette.primary, 0.16f)
val BrandLime: Color get() = AppPalette.tint(AppPalette.primary, 0.08f)
// Hero section — warm gradient
val HeroGradientTop: Color get() = AppPalette.stop(0)
val HeroGradientBottom: Color get() = AppPalette.stop(3)
// Accent palette — bold & saturated
val BrandAmber: Color get() = AppPalette.stop(0)
val BrandAmberContainer: Color get() = AppPalette.tint(AppPalette.stop(0), 0.16f)
val KaizenLavender: Color get() = AppPalette.stop(2)
val KaizenLavenderContainer: Color get() = AppPalette.tint(AppPalette.stop(2), 0.16f)
val KaizenCoral: Color get() = AppPalette.stop(1)
val KaizenCoralContainer: Color get() = AppPalette.tint(AppPalette.stop(1), 0.16f)
val KaizenBlue: Color get() = AppPalette.stop(3)
val KaizenBlueContainer: Color get() = AppPalette.tint(AppPalette.stop(3), 0.16f)
val KaizenTeal: Color get() = AppPalette.primaryDeep
val CardTealLight: Color get() = AppPalette.tint(AppPalette.primary, 0.14f)
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
val CardGreenLight: Color get() = AppPalette.tint(AppPalette.primary, 0.14f)
val CardBlueLight: Color get() = AppPalette.tint(AppPalette.stop(3), 0.14f)
val CardCoralLight: Color get() = AppPalette.tint(AppPalette.stop(1), 0.14f)
val CardAmberLight: Color get() = AppPalette.tint(AppPalette.stop(0), 0.16f)
val KaizenRose: Color get() = AppPalette.stop(1)
val CardRoseLight: Color get() = AppPalette.tint(AppPalette.stop(1), 0.16f)
val CardLavenderLight: Color get() = AppPalette.tint(AppPalette.stop(2), 0.14f)
val CardMintLight: Color get() = AppPalette.tint(AppPalette.primary, 0.12f)
// ---- "Your Day" grid — 6 cards, deliberately spread across the wheel (not the muted
// pastel brand tokens above, which cluster too close together at low saturation) ----
val GridGreen: Color get() = AppPalette.primaryDeep
val GridGreenLight: Color get() = AppPalette.tint(AppPalette.primaryDeep, 0.14f)
val GridOrange: Color get() = AppPalette.stop(0)
val GridOrangeLight: Color get() = AppPalette.tint(AppPalette.stop(0), 0.16f)
val GridPurple: Color get() = AppPalette.stop(2)
val GridPurpleLight: Color get() = AppPalette.tint(AppPalette.stop(2), 0.14f)
val GridBlue: Color get() = AppPalette.stop(3)
val GridBlueLight: Color get() = AppPalette.tint(AppPalette.stop(3), 0.14f)
val GridPink: Color get() = AppPalette.stop(1)
val GridPinkLight: Color get() = AppPalette.tint(AppPalette.stop(1), 0.14f)
val GridRed: Color get() = AppPalette.primaryDark
val GridRedLight: Color get() = AppPalette.tint(AppPalette.primaryDark, 0.14f)
// ---- Semantic status roles ----
val PositiveLight = Color(0xFF00C896)
val PositiveDark = Color(0xFF00E5A8)
val CautionLight = Color(0xFFD9642A)
val CautionDark = Color(0xFFE8875A)
val CriticalLight = Color(0xFFFF5252)
val CriticalDark = Color(0xFFFF6B6B)
val InformationLight = Color(0xFF2196F3)
val InformationDark = Color(0xFF64B5F6)
val DisabledLight = Color(0xFFBDBDBD)
val DisabledDark = Color(0xFF616161)

// Semantic domain colors — vivid
val NutritionColor: Color get() = AppPalette.stop(0)
val MovementColor: Color get() = AppPalette.stop(3)
val RecoveryColor: Color get() = AppPalette.stop(2)
val HydrationColor: Color get() = AppPalette.stop(1)
val CoralAccent: Color get() = AppPalette.stop(1)
// Gradient endpoints
val GradientStart: Color get() = AppPalette.stop(0)
val GradientMid: Color get() = AppPalette.stop(1)
val GradientEnd: Color get() = AppPalette.stop(3)
val GradientAccentStart: Color get() = AppPalette.primary
val GradientAccentEnd: Color get() = AppPalette.primaryLight
val ChartColors: List<Color> get() = listOf(
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
