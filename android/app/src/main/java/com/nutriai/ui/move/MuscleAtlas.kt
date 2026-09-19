package com.nutriai.ui.move

import com.nutriai.ui.move.ExerciseCatalog.Category as C

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriai.ui.theme.Spacing

private val Sharp = RoundedCornerShape(12.dp)
private val MoveAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

private val ATLAS get() = BodyParts.tiles

/** One representative exercise per tile (dataset id): its GIF shows the working muscle highlighted on a realistic body. */
private val DEMO_ID = mapOf(
    C.CHEST to "pectorals/barbell-bench-press",
    C.LATS to "lats/cable-pulldown",
    C.UPPER_BACK to "upper-back/barbell-bent-over-row",
    C.TRAPS to "traps/barbell-shrug",
    C.NECK to "levator-scapulae/neck-side-stretch",
    C.LOWER_BACK to "spine/lever-back-extension",
    C.SHOULDERS to "delts/dumbbell-lateral-raise",
    C.BICEPS to "biceps/barbell-curl",
    C.TRICEPS to "triceps/cable-pushdown",
    C.FOREARMS to "forearms/barbell-wrist-curl",
    C.CORE to "abs/crunch-floor",
    C.GLUTES to "glutes/low-glute-bridge-on-floor",
    C.QUADS to "quads/barbell-bench-squat",
    C.HAMSTRINGS to "hamstrings/barbell-straight-leg-deadlift",
    C.CALVES to "calves/bodyweight-standing-calf-raise",
    C.INNER_THIGH to "adductors/lever-seated-hip-adduction",
    C.OUTER_THIGH to "abductors/lever-seated-hip-abduction",
    C.CARDIO to "cardio/jump-rope",
    C.MOBILITY to "hamstrings/world-greatest-stretch",
)

/** Which anatomy muscles (and colour) each tile lights up on the offline figure (used if a GIF cannot load). */
private fun highlightFor(cat: ExerciseCatalog.Category): Map<String, Color>? {
    fun paint(color: Color, vararg slugs: String) = slugs.associateWith { color }
    return when (cat) {
        C.CHEST -> paint(Color(0xFFE53935), "chest")
        C.LATS, C.UPPER_BACK -> paint(Color(0xFF1E88E5), "upper-back")
        C.TRAPS -> paint(Color(0xFF3949AB), "trapezius")
        C.NECK -> paint(Color(0xFF00897B), "neck")
        C.LOWER_BACK -> paint(Color(0xFF6D4C41), "lower-back")
        C.SHOULDERS -> paint(Color(0xFFFB8C00), "deltoids")
        C.BICEPS -> paint(Color(0xFF8E24AA), "biceps")
        C.TRICEPS -> paint(Color(0xFF5E35B1), "triceps")
        C.FOREARMS -> paint(Color(0xFFAB47BC), "forearm")
        C.CORE -> paint(Color(0xFF7CB342), "abs", "obliques")
        C.GLUTES -> paint(Color(0xFFEC407A), "gluteal")
        C.QUADS -> paint(Color(0xFF00ACC1), "quadriceps")
        C.HAMSTRINGS -> paint(Color(0xFF00897B), "hamstring")
        C.CALVES -> paint(Color(0xFF43A047), "calves", "tibialis")
        C.INNER_THIGH, C.OUTER_THIGH -> paint(Color(0xFF26A69A), "adductors")
        else -> null
    }
}

/** Which side of the body shows this muscle: (front, back). */
private fun viewsFor(cat: ExerciseCatalog.Category): Pair<Boolean, Boolean> = when (cat) {
    C.CHEST, C.CORE, C.BICEPS, C.QUADS, C.INNER_THIGH -> true to false
    C.LATS, C.UPPER_BACK, C.TRAPS, C.NECK, C.LOWER_BACK, C.GLUTES, C.HAMSTRINGS, C.TRICEPS, C.OUTER_THIGH -> false to true
    else -> true to true
}

/**
 * Visual muscle picker: real anatomy figures (front and back) with the trained muscles coloured. Tap one to list its
 * exercises (each card shows its animated demo); tap it again to clear the filter.
 */
@Composable
fun MuscleAtlas(selected: ExerciseCatalog.Category, onSelect: (ExerciseCatalog.Category) -> Unit, modifier: Modifier = Modifier, female: Boolean = false) {
    val counts = remember { ExerciseCatalog.entries.groupingBy { it.category }.eachCount() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Pick a muscle", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        ATLAS.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowItems.forEach { cat ->
                    val isSelected = selected == cat
                    val highlight = highlightFor(cat)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.05f)
                            .clickable { onSelect(if (isSelected) ExerciseCatalog.Category.ALL else cat) }
                            .semantics { contentDescription = "${cat.label} exercises" + if (isSelected) ", selected" else "" },
                        shape = Sharp,
                        border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) MoveAccent else MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                var failed by remember(cat) { mutableStateOf(false) }
                                val demo = DEMO_ID[cat]
                                when {
                                    // A real 3D render of a representative exercise: the working muscle glows red and moves.
                                    demo != null && !failed -> DemoPreview(ExerciseDemoMap.BASE + demo + ".gif", Modifier.fillMaxSize(), onError = { failed = true })
                                    highlight != null -> {
                                        val (front, back) = viewsFor(cat)
                                        BodyDiagram(highlight, Modifier.fillMaxSize(), female = female, showFront = front, showBack = back)
                                    }
                                    else -> Text(cat.emoji, fontSize = 44.sp)
                                }
                            }
                            Text(cat.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color(0xFF1B1F23))
                            Text((counts[cat] ?: 0).toString() + " exercises", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                        }
                    }
                }
                repeat(2 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Sentinel for "show every region of this body part". */
const val ALL_REGIONS = "*"

/**
 * Second step of the Library: one card per region of the chosen body part (Chest -> Upper / Middle / Lower, Shoulders -> Front /
 * Side / Rear ...). Each card shows a real exercise for that region, so you see what the region looks like before opening it.
 */
@Composable
fun SubPartTiles(category: ExerciseCatalog.Category, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val regions = remember(category) { SubParts.forCategory(category) }
    // representative exercise per region: the first one that has a real demo GIF
    val pictures = remember(category) {
        regions.associateWith { label ->
            ExerciseCatalog.entries.firstOrNull { e ->
                e.category == category && SubParts.classify(category, e.item.name) == label && ExerciseDemoMap.gifUrl(e.item.name) != null
            }?.item?.name
        }
    }
    val counts = remember(category) {
        regions.associateWith { label -> ExerciseCatalog.entries.count { it.category == category && SubParts.classify(category, it.item.name) == label } }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Which part of ${category.label.lowercase()}?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        (regions + ALL_REGIONS).chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowItems.forEach { label ->
                    val isAll = label == ALL_REGIONS
                    val demo = if (isAll) null else pictures[label]?.let { ExerciseDemoMap.gifUrl(it) }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.05f)
                            .clickable { onPick(label) }
                            .semantics { contentDescription = (if (isAll) "All ${category.label}" else "$label ${category.label}") + " exercises" },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (demo != null) DemoPreview(demo, Modifier.fillMaxSize())
                                else Text(category.emoji, fontSize = 44.sp)
                            }
                            Text(
                                if (isAll) "All" else label,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color(0xFF1B1F23),
                            )
                            Text(
                                (if (isAll) counts.values.sum() else counts[label] ?: 0).toString() + " exercises",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280),
                            )
                        }
                    }
                }
                repeat(2 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Header card for the Library's inner screens: the theme gradient with a white back arrow on the left and the body part /
 * region name as the title. Tapping anywhere on the arrow goes back one step.
 */
@Composable
fun LibraryHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(com.nutriai.ui.theme.SpectrumBrush)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { Text("←", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(end = 44.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
