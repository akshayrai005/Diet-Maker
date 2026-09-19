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

    val themes: Map<String, Def> = linkedMapOf(
        "spectrum" to Def("Spectrum", Color(0xFFE91E63), listOf(Color(0xFFF57C00), Color(0xFFE91E63), Color(0xFF8E24AA), Color(0xFF3949AB))),
        "ocean" to Def("Ocean", Color(0xFF1E88E5), listOf(Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF3949AB), Color(0xFF7E57C2))),
        "sunset" to Def("Sunset", Color(0xFFF4511E), listOf(Color(0xFFFFA000), Color(0xFFF4511E), Color(0xFFD81B60), Color(0xFF8E24AA))),
        "forest" to Def("Forest", Color(0xFF00A67D), listOf(Color(0xFF7CB342), Color(0xFF00C896), Color(0xFF00897B), Color(0xFF0277BD))),
        "berry" to Def("Berry", Color(0xFFAB47BC), listOf(Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2), Color(0xFF5C6BC0))),
        "gold" to Def("Gold", Color(0xFFF59E0B), listOf(Color(0xFFFFCA28), Color(0xFFFFA000), Color(0xFFF57C00), Color(0xFFE64A19))),
        "graphite" to Def("Graphite", Color(0xFF546E7A), listOf(Color(0xFF90A4AE), Color(0xFF607D8B), Color(0xFF455A64), Color(0xFF263238))),
        // ---- single colours (four shades of one hue: bars/buttons fade light -> dark, cards use the shades) ----
        "blue" to Def("Classic Blue", Color(0xFF1E88E5), listOf(Color(0xFF64B5F6), Color(0xFF1E88E5), Color(0xFF1565C0), Color(0xFF0D47A1)), solid = true),
        "green" to Def("Classic Green", Color(0xFF00A67D), listOf(Color(0xFF69F0AE), Color(0xFF00C896), Color(0xFF00897B), Color(0xFF00695C)), solid = true),
        "red" to Def("Classic Red", Color(0xFFE53935), listOf(Color(0xFFEF5350), Color(0xFFE53935), Color(0xFFC62828), Color(0xFF8E0000)), solid = true),
        "purple" to Def("Royal Purple", Color(0xFF7E57C2), listOf(Color(0xFFB39DDB), Color(0xFF7E57C2), Color(0xFF5E35B1), Color(0xFF311B92)), solid = true),
        "orange" to Def("Bright Orange", Color(0xFFFB8C00), listOf(Color(0xFFFFB74D), Color(0xFFFB8C00), Color(0xFFEF6C00), Color(0xFFBF360C)), solid = true),
        "teal" to Def("Deep Teal", Color(0xFF00897B), listOf(Color(0xFF4DB6AC), Color(0xFF00897B), Color(0xFF00695C), Color(0xFF004D40)), solid = true),
        "pink" to Def("Hot Pink", Color(0xFFEC407A), listOf(Color(0xFFF48FB1), Color(0xFFEC407A), Color(0xFFC2185B), Color(0xFF880E4F)), solid = true),
        "black" to Def("Midnight", Color(0xFF37474F), listOf(Color(0xFF78909C), Color(0xFF455A64), Color(0xFF263238), Color(0xFF000000)), solid = true),
    )

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

    val primary: Color get() = def.primary
    val primaryDark: Color get() = lerp(def.primary, Color.Black, 0.18f)
    val primaryDeep: Color get() = lerp(def.primary, Color.Black, 0.38f)
    val primaryLight: Color get() = lerp(def.primary, Color.White, 0.45f)

    /** The four theme colours; [stop] wraps so any index is safe. */
    fun stop(i: Int): Color = def.stops[((i % 4) + 4) % 4]
    val stops: List<Color> get() = def.stops

    /** A soft card background from a theme colour: white-ish in light mode, dark-ish in dark mode. */
    fun tint(c: Color, strength: Float = 0.14f): Color =
        if (dark) lerp(Color(0xFF1A1A2E), c, strength * 1.5f) else lerp(Color.White, c, strength)

    val horizontal: Brush get() = Brush.horizontalGradient(def.stops)
    val vertical: Brush get() = Brush.verticalGradient(def.stops)
}
