package com.nutriai.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * THE one place that decides the app's colours. Every card, button, top bar, chart and tile reads from here (through the
 * named constants in Color.kt), so choosing a theme in Settings restyles the whole app at once.
 *
 * A theme is 4 gradient [Def.stops] (used left-to-right for bars/buttons and one-per-category for cards) plus a [Def.primary]
 * (tab pills, focus rings, progress). Status colours (green tick, red error, amber warning) are NOT part of the theme.
 */
object AppPalette {
    /** [solid] themes use four shades of ONE colour; the others blend four different colours. */
    class Def(val label: String, val primary: Color, val stops: List<Color>, val solid: Boolean = false)

    private val definitions: Map<String, Def> = linkedMapOf(
        "spectrum" to Def("Spectrum", Color(0xFFE91E63), listOf(Color(0xFFF57C00), Color(0xFFE91E63), Color(0xFF8E24AA), Color(0xFF3949AB))),
        "ocean" to Def("Ocean", Color(0xFF3AA0EA), listOf(Color(0xFF22B8CF), Color(0xFF3AA0EA), Color(0xFF5A84E6), Color(0xFF7B6CDC))),
        "sunset" to Def("Sunset", Color(0xFFF2705F), listOf(Color(0xFFF6A04D), Color(0xFFF2705F), Color(0xFFE0568A), Color(0xFFB45BB8))),
        "forest" to Def("Forest", Color(0xFF43B58A), listOf(Color(0xFF7CC46A), Color(0xFF43B58A), Color(0xFF2FA3A0), Color(0xFF3D8FBF))),
        "berry" to Def("Berry", Color(0xFFAB47BC), listOf(Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2), Color(0xFF5C6BC0))),
        "gold" to Def("Gold", Color(0xFFFFA23A), listOf(Color(0xFFFFC24B), Color(0xFFFFA23A), Color(0xFFF7833F), Color(0xFFEC6A4C))),
        "graphite" to Def("Graphite", Color(0xFF708496), listOf(Color(0xFF90A4B4), Color(0xFF708496), Color(0xFF566A7E), Color(0xFF3E5064))),
        // ---- single colours (four shades of one hue: bars/buttons fade light -> dark, cards use the shades) ----
        "blue" to Def("Soft Blue", Color(0xFF6C9BD2), listOf(Color(0xFF8DB4E2), Color(0xFF6C9BD2), Color(0xFF5A88BD), Color(0xFF4A74A6)), solid = true),
        "green" to Def("Soft Green", Color(0xFF5FB49C), listOf(Color(0xFF8CCFB9), Color(0xFF5FB49C), Color(0xFF4A9C85), Color(0xFF3B8571)), solid = true),
        "red" to Def("Soft Rose", Color(0xFFE58A8A), listOf(Color(0xFFF0A9A9), Color(0xFFE58A8A), Color(0xFFD06F6F), Color(0xFFB95A5A)), solid = true),
        "purple" to Def("Soft Lilac", Color(0xFF9C8FD6), listOf(Color(0xFFB9AEE6), Color(0xFF9C8FD6), Color(0xFF8577C4), Color(0xFF6F62AD)), solid = true),
        "orange" to Def("Soft Peach", Color(0xFFF0A26B), listOf(Color(0xFFF6BC91), Color(0xFFF0A26B), Color(0xFFDC8B55), Color(0xFFC57545)), solid = true),
        "teal" to Def("Soft Teal", Color(0xFF5FB3B3), listOf(Color(0xFF8AD0D0), Color(0xFF5FB3B3), Color(0xFF4A9999), Color(0xFF3A8080)), solid = true),
        "pink" to Def("Soft Pink", Color(0xFFE58FB0), listOf(Color(0xFFF1B0C9), Color(0xFFE58FB0), Color(0xFFD07497), Color(0xFFB85F80)), solid = true),
        "black" to Def("Soft Slate", Color(0xFF7D8CA3), listOf(Color(0xFFA2AEC0), Color(0xFF7D8CA3), Color(0xFF66768F), Color(0xFF526179)), solid = true),
    )

    /**
     * The themes shown to the user. A single-colour theme is ONE plain colour: all four "stops" are the same colour, so every
     * bar, button and header is flat (no fade from white). Blended themes keep their four different colours.
     */
    val themes: Map<String, Def> = definitions.mapValues { (_, d) -> if (d.solid) Def(d.label, d.primary, List(4) { d.primary }, true) else d }

    /** Saved themes used older names; they map onto the new ones. */
    private val legacy = mapOf("yellow" to "gold")

    /** Compose state: reading any getter below inside a composable recomposes it when the theme changes. */
    var themeKey by mutableStateOf("spectrum")
        private set
    var dark by mutableStateOf(false)
        private set

    fun apply(key: String, darkTheme: Boolean) {
        val k = if (key in themes) key else legacy[key] ?: "spectrum"
        if (themeKey != k) themeKey = k
        if (dark != darkTheme) dark = darkTheme
    }

    private val def: Def get() = themes[themeKey] ?: themes.getValue("spectrum")

    /** In dark mode blended themes use deeper shades so white text reads and nothing glares; single colours stay as chosen. */
    private fun forMode(c: Color): Color = if (dark && !def.solid) lerp(c, Color.Black, 0.30f) else c

    val primary: Color get() = if (dark && !def.solid) lerp(def.primary, Color.White, 0.10f) else def.primary
    val primaryDark: Color get() = lerp(def.primary, Color.Black, 0.18f)
    val primaryDeep: Color get() = lerp(def.primary, Color.Black, 0.38f)
    val primaryLight: Color get() = lerp(def.primary, Color.White, 0.45f)

    /** The four theme colours; [stop] wraps so any index is safe. */
    fun stop(i: Int): Color = forMode(def.stops[((i % 4) + 4) % 4])
    val stops: List<Color> get() = def.stops.map { forMode(it) }

    /** A soft card background from a theme colour: white-ish in light mode, dark-ish in dark mode. */
    fun tint(c: Color, strength: Float = 0.14f): Color =
        if (dark) lerp(Color(0xFF1A1A2E), c, strength * 1.5f) else lerp(Color.White, c, strength)

    val horizontal: Brush get() = Brush.horizontalGradient(stops)
    val vertical: Brush get() = Brush.verticalGradient(stops)
}
