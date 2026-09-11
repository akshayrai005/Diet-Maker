package com.nutriai.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.nutriai.ui.move.ExerciseCatalog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.roundToInt

private val Sharp = RoundedCornerShape(8.dp)

@Composable
fun PlanScreen(modifier: Modifier = Modifier, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    val tomorrow = LocalDate.now().plusDays(1).toString()

    var showAddFood by remember { mutableStateOf(false) }
    var showAddExercise by remember { mutableStateOf(false) }

    if (showAddFood) AddFoodDialog(viewModel, onDismiss = { showAddFood = false })
    if (showAddExercise) AddExerciseDialog(viewModel, onDismiss = { showAddExercise = false })

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.lg),
    ) {
        item {
            Text(
                "📋 Plan Ahead",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Forward planning only - tomorrow, not a week-long review.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = true, onClick = {}, label = { Text("📅 Tomorrow") })
                FilterChip(selected = plan.isFast, onClick = { viewModel.toggleFast() }, label = { Text(if (plan.isFast) "🚫 Fasting" else "Mark fasting") })
            }
        }

        // Live totals vs target
        item { TotalsCard(state) }

        // Trainer notes
        item {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("🗣️ Trainer Notes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = plan.trainerNotes,
                        onValueChange = { viewModel.setTrainerNotes(it) },
                        placeholder = { Text("What did your trainer say? (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                    )
                }
            }
        }

        // Workout duration
        item {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("⏱️ Workout Duration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(30, 45, 60, 90).forEach { mins ->
                            FilterChip(
                                selected = plan.workoutMinutes == mins,
                                onClick = { viewModel.setWorkoutMinutes(mins) },
                                label = { Text("${mins}m") },
                            )
                        }
                    }
                }
            }
        }

        // Food plan
        item {
            Text(
                "🍽️ Food Plan",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        itemsIndexed(plan.foods) { i, f ->
            PlanRow(
                title = f.name,
                subtitle = "${f.kcal.roundToInt()} kcal · ${f.proteinG.roundToInt()}g protein",
                emoji = "🥗",
                onRemove = { viewModel.removeFood(i) },
            )
        }
        item {
            PrimaryButton(
                text = "＋ Add Food",
                onClick = { showAddFood = true },
                modifier = Modifier.fillMaxWidth(),
                containerColor = NutritionColor,
            )
        }

        // Workout plan
        item {
            Text(
                "💪 Workout Plan",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        itemsIndexed(plan.exercises) { i, e ->
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isToday) {
                        Checkbox(checked = e.done, onCheckedChange = { viewModel.toggleExerciseDone(i) })
                    }
                    com.nutriai.ui.move.ExerciseDemo(name = e.name, muscleGroup = e.muscleGroup, sizeDp = 44)
                    Spacer(Modifier.padding(start = Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${e.sets} × ${e.reps}${e.muscleGroup?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val catalogEntry = ExerciseCatalog.search(e.name).firstOrNull()
                        catalogEntry?.breathingCue?.takeIf { it.isNotBlank() }?.let { cue ->
                            Text("🫁 $cue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        if ((i + 1) % 3 == 0) {
                            Text("💧 Hydrate — take a sip", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = KaizenCoral,
                        modifier = Modifier.clickable { viewModel.removeExercise(i) },
                    )
                }
            }
        }
        item {
            PrimaryButton(
                text = "＋ Add Exercise",
                onClick = { showAddExercise = true },
                modifier = Modifier.fillMaxWidth(),
                containerColor = MovementColor,
            )
        }

        // Sleep
        item {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("😴 Sleep Schedule", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    SleepRow(plan.bedtime, plan.waketime) { b, w -> viewModel.setSleep(b, w) }
                }
            }
        }

    }
}

@Composable
private fun TotalsCard(state: PlanUiState) {
    val kcalPct = (state.plan.plannedKcal / state.kcalTarget).coerceIn(0.0, 1.0).toFloat()
    val proteinPct = (state.plan.plannedProtein / state.proteinTarget).coerceIn(0.0, 1.0).toFloat()
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("🎯 Planned vs Target", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🔥 Calories", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("${state.plan.plannedKcal.roundToInt()} / ${state.kcalTarget.roundToInt()} kcal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            KaizenProgressBar(progress = kcalPct, color = BrandAmber)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("💪 Protein", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("${state.plan.plannedProtein.roundToInt()} / ${state.proteinTarget.roundToInt()} g", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            KaizenProgressBar(progress = proteinPct, color = NutritionColor)
        }
    }
}

@Composable
private fun PlanRow(title: String, subtitle: String, emoji: String = "🥗", onRemove: () -> Unit) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onRemove),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("✕", style = MaterialTheme.typography.titleMedium, color = KaizenCoral)
        }
    }
}

@Composable
private fun SleepRow(bedtime: String, waketime: String, onChange: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(value = bedtime, onValueChange = { onChange(it, waketime) }, label = { Text("🌙 Sleep by") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = waketime, onValueChange = { onChange(bedtime, it) }, label = { Text("☀️ Wake at") }, singleLine = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AddFoodDialog(viewModel: PlanViewModel, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🍽️ Add Food") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("⭐ Presets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                items(viewModel.foodPresets) { p ->
                    Card(
                        Modifier.fillMaxWidth().clickable { viewModel.addFood(p.name, p.kcal, p.proteinG); onDismiss() },
                        shape = Sharp,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${p.kcal.roundToInt()} kcal · ${p.proteinG.roundToInt()}g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Text("✏️ Or add custom", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(custom, { custom = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(kcal, { kcal = it.filter { c -> c.isDigit() } }, label = { Text("kcal") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(protein, { protein = it.filter { c -> c.isDigit() } }, label = { Text("protein g") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (custom.isNotBlank()) {
                    viewModel.addFood(custom.trim(), kcal.toDoubleOrNull() ?: 0.0, protein.toDoubleOrNull() ?: 0.0)
                }
                onDismiss()
            }) { Text("Add custom") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AddExerciseDialog(viewModel: PlanViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExerciseCatalog.Category.ALL) }
    val results = remember(query, category) { ExerciseCatalog.search(query, category) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💪 Add Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search, or pick a body part below") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ExerciseCatalog.categories) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text("${c.emoji} ${c.label}") },
                        )
                    }
                }
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results) { ex ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { viewModel.addExercise(ex.name, ex.muscleGroup); onDismiss() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            com.nutriai.ui.move.ExerciseDemo(name = ex.name, muscleGroup = ex.muscleGroup, sizeDp = 34)
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                ex.muscleGroup?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
                        }
                    }
                    if (results.isEmpty()) item { Text("No matches — type a name to add it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
