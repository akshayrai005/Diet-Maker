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
import androidx.compose.ui.graphics.drawscope.withTransform
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
                                if (cat in BODY_DIAGRAM) AtlasFigure(cat, Modifier.fillMaxHeight().aspectRatio(100f / 222f))
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

/** A proper anatomy-style figure (front view, or back view for Back and Glutes) with the trained muscle in colour. */
@Composable
private fun AtlasFigure(cat: ExerciseCatalog.Category, modifier: Modifier = Modifier) {
    val hi = muscleColor(cat)
    Canvas(modifier) {
        withTransform({ scale(size.width / 100f, size.height / 222f, pivot = Offset.Zero) }) {
            drawFigure(cat, hi)
        }
    }
}

private val BODY = Color(0xFFCBD0D8)
private val SHADE = Color(0xFFB0B7C1)
private val LINE = Color(0xFF7C8591)

private fun path(build: Path.() -> Unit) = Path().apply(build)

/** Draws [block] for the left half, then mirrored for the right half (the figure is drawn in a 100 x 222 space). */
private fun DrawScope.both(block: DrawScope.() -> Unit) {
    block()
    withTransform({ scale(-1f, 1f, pivot = Offset(50f, 0f)) }) { block() }
}

private fun DrawScope.part(p: Path, fill: Color) {
    drawPath(p, fill)
    drawPath(p, LINE, style = Stroke(width = 0.9f))
}

private fun DrawScope.drawFigure(cat: ExerciseCatalog.Category, hi: Color) {
    val back = cat == ExerciseCatalog.Category.BACK || cat == ExerciseCatalog.Category.GLUTES
    val armsHi = cat == ExerciseCatalog.Category.ARMS
    val legsHi = cat == ExerciseCatalog.Category.LEGS

    // legs (thigh + calf) and feet
    val leg = path {
        moveTo(35f, 122f); cubicTo(29f, 138f, 29f, 154f, 34f, 168f)
        cubicTo(31f, 180f, 32f, 194f, 36f, 207f); lineTo(45f, 207f)
        cubicTo(46f, 194f, 46f, 180f, 44f, 168f); cubicTo(49f, 154f, 50f, 138f, 50f, 124f); close()
    }
    both {
        part(leg, if (legsHi) hi else BODY)
        drawOval(if (legsHi) hi else SHADE, Offset(33f, 207f), Size(14f, 6f))
        drawLine(LINE, Offset(40f, 150f), Offset(40f, 166f), strokeWidth = 0.7f) // knee crease
    }

    // arms: shoulder cap, upper arm, forearm, hand
    val upperArm = path {
        moveTo(26f, 37f); cubicTo(16f, 38f, 11f, 50f, 11f, 62f); lineTo(11f, 76f)
        cubicTo(11f, 81f, 21f, 81f, 21f, 76f); lineTo(22f, 60f); cubicTo(22f, 52f, 26f, 46f, 30f, 44f); close()
    }
    val foreArm = path {
        moveTo(11f, 77f); cubicTo(8f, 88f, 8f, 100f, 10f, 108f); lineTo(18f, 108f)
        cubicTo(19f, 100f, 20f, 88f, 21f, 77f); close()
    }
    both {
        part(upperArm, if (armsHi) hi else BODY)
        part(foreArm, if (armsHi) hi.copy(alpha = 0.8f) else BODY)
        drawOval(BODY, Offset(9f, 108f), Size(9f, 10f))
    }

    // torso (V taper) + neck + head
    val torso = path {
        moveTo(44f, 25f); cubicTo(38f, 30f, 30f, 32f, 25f, 37f)
        cubicTo(21f, 46f, 23f, 60f, 28f, 72f); cubicTo(32f, 84f, 36f, 94f, 37f, 104f)
        cubicTo(34f, 110f, 33f, 116f, 35f, 124f); lineTo(65f, 124f)
        cubicTo(67f, 116f, 66f, 110f, 63f, 104f); cubicTo(64f, 94f, 68f, 84f, 72f, 72f)
        cubicTo(77f, 60f, 79f, 46f, 75f, 37f); cubicTo(70f, 32f, 62f, 30f, 56f, 25f); close()
    }
    part(torso, BODY)
    drawRect(BODY, Offset(44.5f, 18f), Size(11f, 9f))
    drawOval(BODY, Offset(41f, 3f), Size(18f, 22f))
    drawOval(LINE, Offset(41f, 3f), Size(18f, 22f), style = Stroke(width = 0.9f))

    if (!back) {
        drawLine(LINE, Offset(50f, 40f), Offset(50f, 118f), strokeWidth = 0.6f) // centre line
        drawLine(SHADE, Offset(30f, 67f), Offset(70f, 67f), strokeWidth = 0.5f)
    }

    when (cat) {
        ExerciseCatalog.Category.CHEST -> both {
            part(path {
                moveTo(30f, 42f); cubicTo(38f, 39f, 46f, 41f, 49f, 45f); lineTo(49f, 62f)
                cubicTo(41f, 67f, 32f, 62f, 28f, 54f); cubicTo(27f, 48f, 28f, 44f, 30f, 42f); close()
            }, hi)
        }
        ExerciseCatalog.Category.SHOULDERS -> both { part(path {
            moveTo(26f, 37f); cubicTo(16f, 38f, 12f, 48f, 12f, 56f); cubicTo(20f, 58f, 27f, 54f, 30f, 44f)
            cubicTo(30f, 41f, 28f, 38f, 26f, 37f); close()
        }, hi) }
        ExerciseCatalog.Category.CORE -> both {
            for (r in 0..3) part(path {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(43.5f, 72f + r * 12f, 49.2f, 82f + r * 12f, CornerRadius(2f, 2f)))
            }, hi)
        }
        ExerciseCatalog.Category.BACK -> {
            // trapezius diamond + two lats wings, spine down the middle
            part(path { moveTo(44f, 25f); cubicTo(38f, 30f, 30f, 32f, 25f, 37f); lineTo(50f, 62f); lineTo(75f, 37f); cubicTo(70f, 32f, 62f, 30f, 56f, 25f); close() }, Color(0xFF43A047))
            both { part(path {
                moveTo(27f, 50f); cubicTo(29f, 66f, 34f, 82f, 40f, 98f); lineTo(49f, 92f); lineTo(49f, 64f)
                cubicTo(40f, 62f, 31f, 58f, 27f, 50f); close()
            }, hi) }
            drawLine(LINE, Offset(50f, 26f), Offset(50f, 122f), strokeWidth = 0.9f)
        }
        ExerciseCatalog.Category.GLUTES -> both {
            part(path {
                moveTo(50f, 108f); cubicTo(40f, 106f, 32f, 112f, 33f, 124f); cubicTo(34f, 136f, 44f, 138f, 50f, 132f); close()
            }, hi)
        }
        else -> Unit // arms / legs are coloured with the limbs above
    }
}
