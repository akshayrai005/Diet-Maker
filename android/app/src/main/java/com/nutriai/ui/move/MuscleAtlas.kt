package com.nutriai.ui.move

import androidx.compose.foundation.BorderStroke
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

/** Which anatomy muscles (and colour) each library category lights up on the figure. */
private fun highlightFor(cat: ExerciseCatalog.Category): Map<String, Color>? {
    fun paint(color: Color, vararg slugs: String) = slugs.associateWith { color }
    return when (cat) {
        ExerciseCatalog.Category.CHEST -> paint(Color(0xFFE53935), "chest")
        ExerciseCatalog.Category.BACK -> paint(Color(0xFF1E88E5), "upper-back", "lower-back", "trapezius")
        ExerciseCatalog.Category.SHOULDERS -> paint(Color(0xFFFB8C00), "deltoids")
        ExerciseCatalog.Category.ARMS -> paint(Color(0xFF8E24AA), "biceps", "triceps", "forearm")
        ExerciseCatalog.Category.CORE -> paint(Color(0xFF7CB342), "abs", "obliques")
        ExerciseCatalog.Category.LEGS -> paint(Color(0xFF00ACC1), "quadriceps", "hamstring", "calves", "adductors", "tibialis")
        ExerciseCatalog.Category.GLUTES -> paint(Color(0xFFEC407A), "gluteal")
        else -> null
    }
}

/**
 * Visual muscle picker: real anatomy figures (front and back) with the trained muscles coloured. Tap one to list its
 * exercises (each card shows its animated demo); tap it again to clear the filter.
 */
@Composable
fun MuscleAtlas(selected: ExerciseCatalog.Category, onSelect: (ExerciseCatalog.Category) -> Unit, modifier: Modifier = Modifier, female: Boolean = false) {
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
                                if (highlight != null) BodyDiagram(highlight, Modifier.fillMaxSize(), female = female)
                                else Text(cat.emoji, fontSize = 44.sp)
                            }
                            Text(cat.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color(0xFF1B1F23))
                        }
                    }
                }
                repeat(2 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
