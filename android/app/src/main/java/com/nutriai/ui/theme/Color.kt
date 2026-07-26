package com.nutriai.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Kaizen pastel palette - matches the splash: soft green · coral · lavender · blue ----
// The Brand* names are kept for source compatibility with existing screens; their VALUES are
// retuned to the softer Kaizen identity, so the whole app shifts to pastel without a per-screen
// refactor. (The full "route everything through MaterialTheme.colorScheme" pass is Phase 6.)
val BrandGreen = Color(0xFF4CB08A)        // primary - Kaizen leaf green (soft, still legible on white)
val BrandGreenDark = Color(0xFF3E9B78)
val BrandGreenDeep = Color(0xFF2E7D5B)    // gradient base / deep green text
val BrandGreenLight = Color(0xFF8FD6B4)   // pastel light green (dark-theme primary / gradient top)
val BrandMint = Color(0xFFDDF2E7)         // light green container
val BrandLime = Color(0xFFA9DDC3)
val BrandAmber = Color(0xFFF0A08B)        // repurposed → Kaizen coral (accent / tertiary)
val BrandAmberContainer = Color(0xFFFBE4DD)

// New named pastels for the multi-accent identity (use going forward instead of hardcoding).
val KaizenLavender = Color(0xFF8B7FD0)
val KaizenLavenderContainer = Color(0xFFE8E2F8)
val KaizenCoral = Color(0xFFF0A08B)
val KaizenCoralContainer = Color(0xFFFBE4DD)
val KaizenBlue = Color(0xFF5B9BF6)
val KaizenBlueContainer = Color(0xFFE3EDFD)
val KaizenInk = Color(0xFF2B3A33)         // warm charcoal, matches the "Kaizen" wordmark

val AppBackgroundLight = Color(0xFFFAFBF9) // warm off-white
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppSurfaceVariantLight = Color(0xFFEFF3EF)
val OnSurfaceLight = Color(0xFF25332C)

val AppBackgroundDark = Color(0xFF0D130F)
val AppSurfaceDark = Color(0xFF141C17)
val AppSurfaceVariantDark = Color(0xFF20291F)
val OnSurfaceDark = Color(0xFFE9F1EC)
