package com.nutriai.ui.plan

import androidx.compose.foundation.clickable
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
    val today = LocalDate.now().toString()
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
                "📋 Plan & Review",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Weekly grid
        item {
            val week by viewModel.week.collectAsStateWithLifecycle()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(week) { d ->
                    WeekDayCard(d, selected = d.date == plan.date) { viewModel.switchTo(d.date) }
                }
            }
        }

        // Day switch
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = plan.date == tomorrow, onClick = { viewModel.switchTo(tomorrow) }, label = { Text("📅 Tomorrow") })
                FilterChip(selected = plan.date == today, onClick = { viewModel.switchTo(today) }, label = { Text("🎯 Today (track)") })
            }
        }

        // Live totals vs target
        item { TotalsCard(state) }

        // Plan-vs-actual
        if (state.isToday && (plan.foods.isNotEmpty() || plan.exercises.isNotEmpty())) {
            item { AdherenceCard(state) }
        }

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
                    Text("🏋️", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(start = Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${e.sets} × ${e.reps}${e.muscleGroup?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!state.isToday) {
                        Text(
                            "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = KaizenCoral,
                            modifier = Modifier.clickable { viewModel.removeExercise(i) },
                        )
                    }
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

        // AI review
        item {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = if (!state.reviewing) Modifier.clickable { viewModel.requestReview() } else Modifier,
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        if (state.reviewing) "🤖 Coach is reviewing…" else "🤖 Tap for Coach Review",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.reviewing) {
                        CircularProgressIndicator(Modifier.heightIn(max = 18.dp), strokeWidth = 2.dp, color = KaizenBlue)
                    }
                }
            }
        }
        if (plan.aiReview.isNotBlank()) {
            item {
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("💬 Coach Review", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(plan.aiReview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayCard(d: DaySummary, selected: Boolean, onClick: () -> Unit) {
    val container = when {
        d.isFast -> KaizenCoral.copy(alpha = 0.15f)
        selected -> KaizenBlue.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        d.isFast -> KaizenCoral
        selected -> KaizenBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier
            .heightIn(min = 78.dp)
            .clickable(onClick = onClick),
        shape = Sharp,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(d.dayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
            if (d.isFast) {
                Text("🚫 Fast", style = MaterialTheme.typography.labelSmall, color = textColor)
            } else if (d.kcal > 0) {
                Text("🔥 ${d.kcal} kcal", style = MaterialTheme.typography.labelSmall, color = textColor)
                Text("💪 ${d.proteinG}g P", style = MaterialTheme.typography.labelSmall, color = textColor)
            } else {
                Text("—", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
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
private fun AdherenceCard(state: PlanUiState) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("⚡ Plan Adherence - Today", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                "${state.adherence}% followed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = BrandGreen,
            )
            state.actualProtein?.let {
                Text(
                    "💪 Protein: ${it.roundToInt()}g actual / ${state.plan.plannedProtein.roundToInt()}g planned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.plan.exercises.isNotEmpty()) {
                Text(
                    "🏋️ Workout: ${state.plan.exercises.count { it.done }}/${state.plan.exercises.size} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
