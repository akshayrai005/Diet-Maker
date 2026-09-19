package com.nutriai.ui.move

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nutriai.ui.theme.Spacing

private val Sharp = RoundedCornerShape(8.dp)
private val MoveAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

private val ATLAS = listOf(
    ExerciseCatalog.Category.CHEST,
    ExerciseCatalog.Category.BACK,
    ExerciseCatalog.Category.SHOULDERS,
    ExerciseCatalog.Category.ARMS,
    ExerciseCatalog.Category.CORE,
    ExerciseCatalog.Category.LEGS,
    ExerciseCatalog.Category.GLUTES,
    ExerciseCatalog.Category.CARDIO,
    ExerciseCatalog.Category.MOBILITY,
)

private val BODY_DIAGRAM = setOf(
    ExerciseCatalog.Category.CHEST, ExerciseCatalog.Category.BACK, ExerciseCatalog.Category.SHOULDERS,
    ExerciseCatalog.Category.ARMS, ExerciseCatalog.Category.CORE, ExerciseCatalog.Category.LEGS, ExerciseCatalog.Category.GLUTES,
)

/**
 * Visual muscle picker: a grid of body diagrams with the trained muscle highlighted. Tap one to list
 * its exercises (each card shows its animated demo); tap it again to clear the filter.
 */
@Composable
fun MuscleAtlas(selected: ExerciseCatalog.Category, onSelect: (ExerciseCatalog.Category) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Pick a muscle", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        ATLAS.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowItems.forEach { cat ->
                    val isSelected = selected == cat
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .clickable { onSelect(if (isSelected) ExerciseCatalog.Category.ALL else cat) }
                            .semantics { contentDescription = "${cat.label} exercises" + if (isSelected) ", selected" else "" },
                        shape = Sharp,
                        border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) MoveAccent else MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MoveAccent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Transparent), contentAlignment = Alignment.Center) {
                                if (cat in BODY_DIAGRAM) AtlasFigure(cat, Modifier.fillMaxHeight().aspectRatio(0.62f))
                                else Text(cat.emoji, fontSize = 34.sp)
                            }
                            Text(cat.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
                // keep the last row's tiles the same width if it has fewer than 3
                repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Bold anatomy-style colour for each trained muscle (so tiles are recognisable at a glance). */
private fun muscleColor(cat: ExerciseCatalog.Category): Color = when (cat) {
    ExerciseCatalog.Category.CHEST -> Color(0xFFE53935)
    ExerciseCatalog.Category.BACK -> Color(0xFF1E88E5)
    ExerciseCatalog.Category.SHOULDERS -> Color(0xFFFB8C00)
    ExerciseCatalog.Category.ARMS -> Color(0xFF8E24AA)
    ExerciseCatalog.Category.CORE -> Color(0xFF7CB342)
    ExerciseCatalog.Category.LEGS -> Color(0xFF00ACC1)
    ExerciseCatalog.Category.GLUTES -> Color(0xFFEC407A)
    else -> Color(0xFF8E24AA)
}

/** A filled body figure with the trained muscle painted in a strong colour (back view for Back and Glutes). */
@Composable
private fun AtlasFigure(cat: ExerciseCatalog.Category, modifier: Modifier = Modifier) {
    val body = Color(0xFFB9BFC7)
    val outline = Color(0xFF6B7480)
    Canvas(modifier) { drawAtlasFigure(cat, body, outline, muscleColor(cat)) }
}

private fun DrawScope.drawAtlasFigure(cat: ExerciseCatalog.Category, body: Color, outline: Color, hi: Color) {
    val w = size.width
    val h = size.height
    fun x(f: Float) = f * w
    fun y(f: Float) = f * h
    val cap = StrokeCap.Round

    // Limbs first (thick round strokes), then torso on top.
    fun limb(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, c: Color) =
        drawLine(c, Offset(x(x1), y(y1)), Offset(x(x2), y(y2)), strokeWidth = width * w, cap = cap)

    val armsHi = cat == ExerciseCatalog.Category.ARMS
    val legsHi = cat == ExerciseCatalog.Category.LEGS
    // arms
    limb(0.25f, 0.27f, 0.16f, 0.47f, 0.13f, if (armsHi) hi else body)
    limb(0.75f, 0.27f, 0.84f, 0.47f, 0.13f, if (armsHi) hi else body)
    limb(0.16f, 0.47f, 0.12f, 0.66f, 0.10f, if (armsHi) hi.copy(alpha = 0.75f) else body)
    limb(0.84f, 0.47f, 0.88f, 0.66f, 0.10f, if (armsHi) hi.copy(alpha = 0.75f) else body)
    // legs
    limb(0.42f, 0.60f, 0.40f, 0.80f, 0.17f, if (legsHi) hi else body)
    limb(0.58f, 0.60f, 0.60f, 0.80f, 0.17f, if (legsHi) hi else body)
    limb(0.40f, 0.80f, 0.40f, 0.96f, 0.12f, body)
    limb(0.60f, 0.80f, 0.60f, 0.96f, 0.12f, body)

    // torso
    val torso = Path().apply {
        moveTo(x(0.28f), y(0.24f)); lineTo(x(0.72f), y(0.24f))
        lineTo(x(0.64f), y(0.46f)); lineTo(x(0.62f), y(0.62f))
        lineTo(x(0.38f), y(0.62f)); lineTo(x(0.36f), y(0.46f)); close()
    }
    drawPath(torso, body)
    // neck + head
    drawRect(body, Offset(x(0.45f), y(0.17f)), Size(x(0.10f), y(0.08f)))
    drawCircle(body, radius = w * 0.11f, center = Offset(x(0.5f), y(0.11f)))

    // trained muscle
    fun blob(l: Float, t: Float, r: Float, b: Float, rad: Float = 0.06f) =
        drawRoundRect(hi, Offset(x(l), y(t)), Size(x(r - l), y(b - t)), CornerRadius(w * rad, w * rad))
    when (cat) {
        ExerciseCatalog.Category.CHEST -> { blob(0.31f, 0.26f, 0.49f, 0.39f); blob(0.51f, 0.26f, 0.69f, 0.39f) }
        ExerciseCatalog.Category.BACK -> {
            drawPath(Path().apply { moveTo(x(0.33f), y(0.28f)); lineTo(x(0.47f), y(0.32f)); lineTo(x(0.45f), y(0.52f)); lineTo(x(0.38f), y(0.48f)); close() }, hi)
            drawPath(Path().apply { moveTo(x(0.67f), y(0.28f)); lineTo(x(0.53f), y(0.32f)); lineTo(x(0.55f), y(0.52f)); lineTo(x(0.62f), y(0.48f)); close() }, hi)
            drawLine(outline, Offset(x(0.5f), y(0.26f)), Offset(x(0.5f), y(0.60f)), strokeWidth = 0.02f * w, cap = cap)
        }
        ExerciseCatalog.Category.SHOULDERS -> { drawCircle(hi, w * 0.10f, Offset(x(0.27f), y(0.27f))); drawCircle(hi, w * 0.10f, Offset(x(0.73f), y(0.27f))) }
        ExerciseCatalog.Category.CORE -> {
            blob(0.42f, 0.40f, 0.58f, 0.60f, 0.04f)
            for (i in 1..2) drawLine(body, Offset(x(0.42f), y(0.40f + 0.067f * i)), Offset(x(0.58f), y(0.40f + 0.067f * i)), strokeWidth = 0.012f * w)
            drawLine(body, Offset(x(0.5f), y(0.40f)), Offset(x(0.5f), y(0.60f)), strokeWidth = 0.012f * w)
        }
        ExerciseCatalog.Category.GLUTES -> { drawCircle(hi, w * 0.12f, Offset(x(0.44f), y(0.62f))); drawCircle(hi, w * 0.12f, Offset(x(0.56f), y(0.62f))) }
        else -> Unit // arms / legs are painted with the limbs above
    }
    // simple outline so the figure reads on light and dark cards
    drawPath(torso, outline, style = Stroke(width = 0.012f * w))
    drawCircle(outline, radius = w * 0.11f, center = Offset(x(0.5f), y(0.11f)), style = Stroke(width = 0.012f * w))
}
