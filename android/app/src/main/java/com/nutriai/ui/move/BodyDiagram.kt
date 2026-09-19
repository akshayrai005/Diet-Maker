package com.nutriai.ui.move

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

/**
 * Real anatomy figure (male or female, front and back) drawn from vector paths bundled in assets/body_paths_male.json.
 * Path data: react-native-body-highlighter (MIT, (c) Hicham El Boussarghini) as redistributed by Anatome
 * (Apache-2.0, NextSolutions). See THIRD_PARTY_NOTICES.md.
 */
object BodyPaths {
    class Piece(val slug: String, val path: Path)
    class View(val pieces: List<Piece>, val bounds: Rect)

    @Volatile private var maleCache: Pair<View, View>? = null
    @Volatile private var femaleCache: Pair<View, View>? = null

    fun load(context: Context, female: Boolean = false): Pair<View, View> {
        (if (female) femaleCache else maleCache)?.let { return it }
        val text = context.assets.open(if (female) "body_paths_female.json" else "body_paths_male.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(text)
        val parser = PathParser()
        fun view(name: String): View {
            val arr = json.getJSONArray(name)
            val pieces = ArrayList<Piece>()
            var bounds: Rect? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val slug = o.getString("s")
                val ps = o.getJSONArray("p")
                for (j in 0 until ps.length()) {
                    val path = parser.parsePathString(ps.getString(j)).toPath()
                    val b = path.getBounds()
                    bounds = bounds?.let { r -> Rect(minOf(r.left, b.left), minOf(r.top, b.top), maxOf(r.right, b.right), maxOf(r.bottom, b.bottom)) } ?: b
                    pieces += Piece(slug, path)
                }
            }
            return View(pieces, bounds ?: Rect(0f, 0f, 1f, 1f))
        }
        return (view("front") to view("back")).also { if (female) femaleCache = it else maleCache = it }
    }
}

private val NON_MUSCLE = setOf("head", "hair", "hands", "feet", "ankles", "knees", "neck")
private val BodyDark = Color(0xFF3F4854)
private val BodyMid = Color(0xFF9AA3AF)

/**
 * Draws the front and back figure side by side. [highlight] maps a muscle slug (chest, abs, deltoids, ...) to its colour;
 * every other muscle is dark grey. Falls back to nothing if the asset is missing (the tile still has its label).
 */
@Composable
fun BodyDiagram(highlight: Map<String, Color>, modifier: Modifier = Modifier, female: Boolean = false, showFront: Boolean = true, showBack: Boolean = true, highlightAlpha: Float = 1f) {
    val context = LocalContext.current
    val views = remember(female) { runCatching { BodyPaths.load(context, female) }.getOrNull() } ?: return
    Canvas(modifier) {
        val shown = listOfNotNull(if (showFront) views.first else null, if (showBack) views.second else null)
        if (shown.isEmpty()) return@Canvas
        val slotW = size.width / shown.size
        shown.forEachIndexed { index, v ->
            val scale = minOf(slotW / v.bounds.width, size.height / v.bounds.height)
            val dx = index * slotW + (slotW - v.bounds.width * scale) / 2f
            val dy = (size.height - v.bounds.height * scale) / 2f
            withTransform({
                translate(dx, dy)
                scale(scale, scale, pivot = Offset.Zero)
                translate(-v.bounds.left, -v.bounds.top)
            }) {
                for (piece in v.pieces) {
                    val color = highlight[piece.slug]?.copy(alpha = highlightAlpha) ?: if (piece.slug in NON_MUSCLE) BodyMid else BodyDark
                    drawPath(piece.path, color)
                    // fine light line between muscles, like an anatomy chart
                    if (piece.slug !in NON_MUSCLE) drawPath(piece.path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.4f / scale))
                }
            }
        }
    }
}
