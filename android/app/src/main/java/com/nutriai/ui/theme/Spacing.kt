package com.nutriai.ui.theme

import androidx.compose.ui.unit.dp

/** Deliberate spacing scale (8/12/16/24/32dp) — the only spacing values shared components use. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Standard page/screen horizontal padding. */
    val screenHorizontal = 16.dp

    /** Vertical gap between distinct sections on a screen. */
    val section = 24.dp

    /** Gap between an icon and adjacent text. */
    val iconGap = 8.dp

    /** Internal row padding (e.g. a ListRow's own content padding). */
    val rowPadding = 12.dp
}

/** Corner radii used by shared surfaces/components — small/medium/large, no ad-hoc values. */
object Radius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
}

/** Minimum interactive/component heights. */
object ComponentHeight {
    val touchTarget = 48.dp
}
