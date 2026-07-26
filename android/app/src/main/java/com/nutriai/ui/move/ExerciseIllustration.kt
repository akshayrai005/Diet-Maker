package com.nutriai.ui.move

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Offline form illustrations. Instead of bundling image assets or fetching
// anything at runtime, each exercise gets a small BUNDLED line-art body diagram
// drawn on a Compose Canvas, with the trained muscle region highlighted. Fully
// offline, zero assets, theme-aware. Falls back to a neutral dumbbell glyph when
// the muscle group is unknown.
// ---------------------------------------------------------------------------

/** The muscle-group buckets we can draw. */
enum class MuscleGroup { CHEST, BACK, SHOULDERS, LEGS, ARMS, CORE, FULL_BODY, GENERIC }

/** Human-readable label for the diagram, used for the visible caption + a11y. */
fun MuscleGroup.label(): String = when (this) {
    MuscleGroup.CHEST -> "Chest"
    MuscleGroup.BACK -> "Back"
    MuscleGroup.SHOULDERS -> "Shoulders"
    MuscleGroup.LEGS -> "Legs"
    MuscleGroup.ARMS -> "Arms"
    MuscleGroup.CORE -> "Core"
    MuscleGroup.FULL_BODY -> "Full body"
    MuscleGroup.GENERIC -> "General"
}

/**
 * Maps a free-text muscleGroup (server-provided, may be null) onto one of our drawable buckets.
 * Tolerant of common synonyms; unknown/blank → GENERIC (neutral dumbbell).
 */
fun muscleGroupOf(raw: String?): MuscleGroup {
    val g = raw?.trim()?.lowercase() ?: return MuscleGroup.GENERIC
    return when {
        g.isBlank() -> MuscleGroup.GENERIC
        listOf("chest", "pec", "push").any { g.contains(it) } -> MuscleGroup.CHEST
        listOf("back", "lat", "row", "pull", "trap", "rhomboid").any { g.contains(it) } -> MuscleGroup.BACK
        listOf("shoulder", "delt", "overhead", "press").any { g.contains(it) } -> MuscleGroup.SHOULDERS
        listOf("leg", "quad", "hamstring", "glute", "calf", "squat", "lunge", "hip").any { g.contains(it) } -> MuscleGroup.LEGS
        listOf("arm", "bicep", "tricep", "curl", "forearm").any { g.contains(it) } -> MuscleGroup.ARMS
        listOf("core", "ab", "oblique", "plank", "trunk").any { g.contains(it) } -> MuscleGroup.CORE
        listOf("full", "total", "compound", "conditioning", "cardio").any { g.contains(it) } -> MuscleGroup.FULL_BODY
        else -> MuscleGroup.GENERIC
    }
}

/**
 * A small bundled diagram for an exercise. Draws a neutral body silhouette with the trained region
 * highlighted, or a dumbbell glyph for the generic fallback. Every diagram carries a
 * contentDescription so it is announced by TalkBack.
 */
@Composable
fun ExerciseIllustration(
    muscleGroup: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
) {
    val group = muscleGroupOf(muscleGroup)
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val desc = if (group == MuscleGroup.GENERIC) {
        "Exercise illustration: dumbbell"
    } else {
        "Body diagram highlighting ${group.label().lowercase()}"
    }

    Box(
        modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            if (group == MuscleGroup.GENERIC) {
                drawDumbbell(outline)
            } else {
                drawBody(outline = outline, highlight = highlight, fill = surface, group = group)
            }
        }
    }
}

/** Draws a simple front-facing body silhouette and shades the trained region. */
private fun DrawScope.drawBody(outline: Color, highlight: Color, fill: Color, group: MuscleGroup) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val stroke = Stroke(width = (w * 0.045f).coerceAtLeast(2f), cap = StrokeCap.Round)

    // Proportions (fractions of the canvas).
    val headR = w * 0.11f
    val headCy = h * 0.15f
    val shoulderY = h * 0.30f
    val hipY = h * 0.58f
    val torsoHalf = w * 0.18f
    val footY = h * 0.92f

    // Torso as a rounded rect.
    val torso = RoundRect(
        rect = Rect(cx - torsoHalf, shoulderY, cx + torsoHalf, hipY),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
    )

    // Highlight region drawn UNDER the outline so the silhouette stays crisp.
    val hl = highlight.copy(alpha = 0.35f)
    when (group) {
        MuscleGroup.CHEST -> drawRoundRectRegion(cx - torsoHalf * 0.85f, shoulderY + h * 0.02f, cx + torsoHalf * 0.85f, shoulderY + (hipY - shoulderY) * 0.42f, hl, w)
        MuscleGroup.CORE -> drawRoundRectRegion(cx - torsoHalf * 0.7f, shoulderY + (hipY - shoulderY) * 0.45f, cx + torsoHalf * 0.7f, hipY - h * 0.01f, hl, w)
        MuscleGroup.BACK -> {
            // Front-view proxy: shade the whole upper torso with a spine hint.
            drawRoundRectRegion(cx - torsoHalf * 0.85f, shoulderY + h * 0.02f, cx + torsoHalf * 0.85f, shoulderY + (hipY - shoulderY) * 0.6f, hl, w)
            drawLine(highlight, Offset(cx, shoulderY + h * 0.03f), Offset(cx, hipY - h * 0.04f), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
        }
        MuscleGroup.SHOULDERS -> {
            drawCircle(hl, radius = w * 0.09f, center = Offset(cx - torsoHalf, shoulderY + h * 0.01f))
            drawCircle(hl, radius = w * 0.09f, center = Offset(cx + torsoHalf, shoulderY + h * 0.01f))
        }
        MuscleGroup.ARMS -> Unit // arms highlighted via limb colour below
        MuscleGroup.LEGS -> Unit // legs highlighted via limb colour below
        MuscleGroup.FULL_BODY -> drawRoundRectRegion(cx - torsoHalf * 0.85f, shoulderY + h * 0.02f, cx + torsoHalf * 0.85f, hipY - h * 0.01f, hl, w)
        MuscleGroup.GENERIC -> Unit
    }

    val armColor = if (group == MuscleGroup.ARMS) highlight else outline
    val legColor = if (group == MuscleGroup.LEGS || group == MuscleGroup.FULL_BODY) highlight else outline

    // Head.
    drawCircle(color = outline, radius = headR, center = Offset(cx, headCy), style = stroke)
    // Neck.
    drawLine(outline, Offset(cx, headCy + headR), Offset(cx, shoulderY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Torso outline.
    val torsoPath = Path().apply { addRoundRect(torso) }
    drawPath(torsoPath, color = outline, style = stroke)

    // Arms (shoulder → hand), angled slightly out.
    drawLine(armColor, Offset(cx - torsoHalf, shoulderY + h * 0.01f), Offset(cx - torsoHalf - w * 0.14f, hipY - h * 0.04f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(armColor, Offset(cx + torsoHalf, shoulderY + h * 0.01f), Offset(cx + torsoHalf + w * 0.14f, hipY - h * 0.04f), strokeWidth = stroke.width, cap = StrokeCap.Round)

    // Legs (hip → foot).
    drawLine(legColor, Offset(cx - torsoHalf * 0.5f, hipY), Offset(cx - torsoHalf * 0.6f, footY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(legColor, Offset(cx + torsoHalf * 0.5f, hipY), Offset(cx + torsoHalf * 0.6f, footY), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawRoundRectRegion(l: Float, t: Float, r: Float, b: Float, color: Color, w: Float) {
    val path = Path().apply {
        addRoundRect(RoundRect(Rect(l, t, r, b), CornerRadius(w * 0.05f, w * 0.05f)))
    }
    drawPath(path, color = color)
}

/** Neutral fallback glyph: a simple dumbbell. */
private fun DrawScope.drawDumbbell(color: Color) {
    val w = size.width
    val h = size.height
    val cy = h / 2f
    val sw = w * 0.06f
    // Bar.
    drawLine(color, Offset(w * 0.28f, cy), Offset(w * 0.72f, cy), strokeWidth = sw, cap = StrokeCap.Round)
    // Plates (two on each end).
    val plateH = h * 0.34f
    listOf(0.22f, 0.30f, 0.70f, 0.78f).forEach { fx ->
        val x = w * fx
        drawLine(color, Offset(x, cy - plateH / 2f), Offset(x, cy + plateH / 2f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
