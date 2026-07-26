package com.nutriai.ui.move

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.ExerciseItem
import com.nutriai.data.remote.dto.ExerciseLogRequest
import com.nutriai.data.remote.dto.WeeklyWorkout
import com.nutriai.data.remote.dto.WorkoutDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Move pillar — mirrors the Diet tab's segmented switcher so everything active lives under one
 * tab: [Exercise | Meditation | Log]. Meditation reuses the mind-&-body content; Log shows what
 * you actually did, with the calories it burned.
 */
@Composable
fun MoveScreen(modifier: Modifier = Modifier, initialSection: Int = 0) {
    var section by remember { mutableIntStateOf(initialSection.coerceIn(0, 2)) }
    val labels = listOf("Exercise", "Meditation", "Log")

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                FilterChip(
                    selected = section == i,
                    onClick = { section = i },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = "$label section" },
                )
            }
        }
        when (section) {
            0 -> ExerciseTab(Modifier.fillMaxSize())
            1 -> com.nutriai.ui.wellness.WellnessScreen(Modifier.fillMaxSize())
            else -> MoveLogScreen(Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------
// Exercise tab
// ---------------------------------------------------------------------------

data class MoveState(
    val loading: Boolean = true,
    val plan: WeeklyWorkout? = null,
    val levelSuggestion: com.nutriai.data.remote.dto.LevelSuggestion? = null,
    val error: String? = null,
    val toast: String? = null,
    /** Running total of calories logged this session (warm-up + main + cardio + cool-down). */
    val sessionKcal: Int = 0,
)

@HiltViewModel
class MoveViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(MoveState())
    val state: StateFlow<MoveState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.exercisePlanFull()
            _state.value = if (r.isSuccess) {
                val env = r.getOrNull()
                _state.value.copy(loading = false, plan = env?.plan, levelSuggestion = env?.levelSuggestion, error = null)
            } else {
                _state.value.copy(loading = false, error = "Generate a plan first (Diet tab)")
            }
        }
    }

    /** Log a performed set, then reload the plan so the next-session suggestion updates. */
    fun logSet(name: String, focus: String?, weightKg: Double?, reps: Int?, sets: Int?) {
        viewModelScope.launch {
            val r = repository.logExercise(
                ExerciseLogRequest(exerciseName = name, focus = focus, weightKg = weightKg, reps = reps, sets = sets),
            )
            if (r.isSuccess) {
                val kcal = r.getOrNull()?.kcal ?: 0
                val env = repository.exercisePlanFull().getOrNull()
                _state.value = _state.value.copy(
                    plan = env?.plan ?: _state.value.plan,
                    levelSuggestion = env?.levelSuggestion ?: _state.value.levelSuggestion,
                    toast = "Logged $name · ~$kcal kcal 🔥",
                    sessionKcal = _state.value.sessionKcal + kcal,
                )
            } else {
                _state.value = _state.value.copy(toast = "Couldn't log — try again")
            }
        }
    }

    /**
     * Log a warm-up / cardio / cool-down item as "done". Timed items estimate minutes from their
     * reps string ("3 min", "45s", "2 × 30s", "15-20 min"); the server computes a MET-based calorie
     * burn from the duration, so it feeds the same burnedTodayKcal / net-calorie picture as Main —
     * the calorie TARGET is never inflated (burn is a separate signal).
     */
    fun logDone(name: String, section: String, reps: String, fallbackMin: Int) {
        val mins = estimateMinutes(reps, fallbackMin)
        viewModelScope.launch {
            val r = repository.logExercise(
                ExerciseLogRequest(exerciseName = name, focus = section, durationMin = mins),
            )
            _state.value = if (r.isSuccess) {
                val kcal = r.getOrNull()?.kcal ?: 0
                _state.value.copy(toast = "Logged $name · ~$kcal kcal 🔥", sessionKcal = _state.value.sessionKcal + kcal)
            } else {
                _state.value.copy(toast = "Couldn't log — try again")
            }
        }
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
}

/** Best-effort minutes from a reps/time string; falls back for non-timed dynamic moves. */
internal fun estimateMinutes(reps: String, fallback: Int): Int {
    val s = reps.lowercase()
    Regex("""(\d+)\s*[×x]\s*(\d+)\s*s""").find(s)?.let {
        val (k, sec) = it.destructured
        return maxOf(1, Math.round(k.toInt() * sec.toInt() / 60.0).toInt())
    }
    Regex("""(\d+)\s*s\b""").find(s)?.let { return maxOf(1, Math.round(it.groupValues[1].toInt() / 60.0).toInt()) }
    Regex("""(\d+)\s*-\s*(\d+)\s*min""").find(s)?.let { return it.groupValues[2].toInt() }
    Regex("""(\d+)\s*min""").find(s)?.let { return it.groupValues[1].toInt() }
    return fallback
}

@Composable
private fun ExerciseTab(modifier: Modifier = Modifier, viewModel: MoveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    val today = plan?.days?.firstOrNull { it.label == "Today" } ?: plan?.days?.firstOrNull { !it.rest }
    var selectedIdx by remember(plan) { mutableStateOf<Int?>(null) }
    val shownDay = selectedIdx?.let { i -> plan?.days?.getOrNull(i) } ?: today

    var logTarget by remember { mutableStateOf<ExerciseItem?>(null) }
    var swapTarget by remember { mutableStateOf<ExerciseItem?>(null) }

    logTarget?.let { ex ->
        LogSetDialog(
            exercise = ex,
            onDismiss = { logTarget = null },
            onConfirm = { w, reps, sets ->
                viewModel.logSet(ex.name, shownDay?.focus, w, reps, sets)
                logTarget = null
            },
        )
    }

    swapTarget?.let { ex ->
        SwapExerciseDialog(exercise = ex, onDismiss = { swapTarget = null })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item { Hero(plan) }

        // Plan note (e.g. "Prioritising shoulders — a little extra volume there…") from the server.
        plan?.note?.takeIf { it.isNotBlank() }?.let { note ->
            item {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .semantics { contentDescription = "Plan note: $note" },
                )
            }
        }

        state.toast?.let { msg ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(msg, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                }
                LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.clearToast() }
            }
        }

        if (state.sessionKcal > 0) {
            item {
                Card(
                    Modifier.fillMaxWidth().semantics { contentDescription = "This session: about ${state.sessionKcal} calories burned" },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(
                        "🔥 This session: ~${state.sessionKcal} kcal burned (warm-up + main + cardio + cool-down)",
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        state.error?.let { err ->
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Text(err, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        state.levelSuggestion?.takeIf { it.direction == "up" || it.direction == "down" }?.let { ls ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ls.direction == "up") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (ls.direction == "up") "⬆️ Ready to level up" else "🌱 Consider easing down",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (ls.direction == "up") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            ls.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = (if (ls.direction == "up") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer).copy(alpha = 0.9f),
                        )
                        Text(
                            "Change it in your profile (Me › Edit health profile).",
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (ls.direction == "up") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer).copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        plan?.days?.takeIf { it.isNotEmpty() }?.let { days ->
            item { Text("This week · tap a day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                val current = selectedIdx ?: days.indexOfFirst { it === shownDay }
                WeekStrip(days, selectedIndex = current, onSelect = { selectedIdx = it })
            }
        }

        shownDay?.let { day ->
            item { Text(if (day.label == "Today") "Today · ${day.focus}" else "${day.label ?: "Day ${day.dayIndex + 1}"} · ${day.focus}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            val hasContent = day.exercises.isNotEmpty() || day.warmup.isNotEmpty() || day.core.isNotEmpty() || day.cardio != null || day.cooldown.isNotEmpty()
            if (day.rest || !hasContent) {
                item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Text("Rest & recovery day — light movement, stretch, hydrate.", Modifier.padding(18.dp)) } }
            } else {
                // Warm-up.
                if (day.warmup.isNotEmpty()) {
                    item { SessionHeader("🔥 Warm-up") }
                    items(day.warmup.size) { i ->
                        val w = day.warmup[i]
                        SecondaryExerciseRow(w, "Warm-up", onDone = { viewModel.logDone(w.name, "Warm-up", w.reps, 2) })
                    }
                }

                // Main lifts — full cards with sets/reps/log/cue/swap.
                if (day.exercises.isNotEmpty()) {
                    item { SessionHeader("🏋️ Main") }
                    items(day.exercises.size) { i ->
                        val ex = day.exercises[i]
                        ExerciseCard(
                            index = i,
                            ex = ex,
                            onLog = { logTarget = ex },
                            onSwap = if (ex.substitutions.isNotEmpty()) ({ swapTarget = ex }) else null,
                        )
                    }
                }

                // Core / Abs — its own labeled section, loggable.
                if (day.core.isNotEmpty()) {
                    item { SessionHeader("🧱 Core & Abs") }
                    items(day.core.size) { i ->
                        val cr = day.core[i]
                        SecondaryExerciseRow(cr, "Core", onDone = { viewModel.logDone(cr.name, "Core", cr.reps, 3) })
                    }
                }

                // Cardio (single item, if any).
                day.cardio?.let { c ->
                    item { SessionHeader("🏃 Cardio") }
                    item { SecondaryExerciseRow(c, "Cardio", onDone = { viewModel.logDone(c.name, "Cardio", c.reps, 15) }) }
                }

                // Cool-down.
                if (day.cooldown.isNotEmpty()) {
                    item { SessionHeader("🧊 Cool-down") }
                    items(day.cooldown.size) { i ->
                        val cd = day.cooldown[i]
                        SecondaryExerciseRow(cd, "Cool-down", onDone = { viewModel.logDone(cd.name, "Cool-down", cd.reps, 2) })
                    }
                }

                // Rest timer between sets.
                item { RestTimer(Modifier.padding(top = 4.dp)) }
            }
        }

        // Strength trend (est-1RM over time). Own empty state + loading.
        item { StrengthTrendSection(Modifier.padding(top = 4.dp)) }

        plan?.disclaimer?.takeIf { it.isNotBlank() }?.let { d ->
            item {
                Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SessionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "$title section" },
    )
}

/** Compact row for warm-up / cardio / cool-down items: illustration + name + reps + "mark done". */
@Composable
private fun SecondaryExerciseRow(ex: ExerciseItem, section: String, onDone: (() -> Unit)? = null) {
    var done by remember(ex.name, section) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "$section: ${ex.name}, ${ex.sets} sets of ${ex.reps}${if (done) ", logged" else ""}" },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseIllustration(muscleGroup = ex.muscleGroup, sizeDp = 40)
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (ex.sets > 1) "${ex.sets} × ${ex.reps}" else ex.reps,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDone != null) {
                if (done) {
                    Text("✓ Done", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedButton(
                        onClick = { done = true; onDone() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "Mark ${ex.name} done and count its calories" },
                    ) { Text("Done") }
                }
            }
        }
    }
}

/** Read-only alternatives sheet for a main exercise (equipment-free / injury-friendly). */
@Composable
private fun SwapExerciseDialog(exercise: ExerciseItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap · ${exercise.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Alternatives you can do instead — same muscle group, often equipment-free or easier on joints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.substitutions.forEach { sub ->
                    Row(
                        Modifier.fillMaxWidth().semantics { contentDescription = "Alternative: $sub" },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseIllustration(muscleGroup = exercise.muscleGroup, sizeDp = 34)
                        Text(sub, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Close") }
        },
    )
}

@Composable
private fun ExerciseCard(index: Int, ex: ExerciseItem, onLog: () -> Unit, onSwap: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                // Bundled offline form diagram for the muscle group.
                ExerciseIllustration(muscleGroup = ex.muscleGroup, sizeDp = 40)
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ex.muscleGroup?.takeIf { it.isNotBlank() }?.let { mg ->
                        Text(mg.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "${ex.sets} × ${ex.reps}",
                    Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
            }

            ex.cue?.takeIf { it.isNotBlank() }?.let { cue ->
                Text(
                    "💡 $cue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ex.nextSession?.let { ns ->
                val target = buildString {
                    append("Next: ")
                    if (ns.suggestedWeightKg != null) append("${trimKg(ns.suggestedWeightKg)} kg × ")
                    append("${ns.suggestedReps} × ${ns.suggestedSets}")
                    if (ns.deload) append(" · deload")
                }
                Text(
                    target,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ns.deload) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onLog,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Log a set of ${ex.name}" },
                ) { Text("＋ Log set") }
                if (onSwap != null) {
                    TextButton(
                        onClick = onSwap,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Swap or see alternatives for ${ex.name}" },
                    ) { Text("⇄ Swap / alternatives") }
                }
            }
        }
    }
}

@Composable
private fun LogSetDialog(exercise: ExerciseItem, onDismiss: () -> Unit, onConfirm: (Double?, Int?, Int?) -> Unit) {
    val ns = exercise.nextSession
    var weight by remember { mutableStateOf(ns?.suggestedWeightKg?.let { trimKg(it) } ?: "") }
    var reps by remember { mutableStateOf((ns?.suggestedReps ?: firstInt(exercise.reps))?.toString() ?: "") }
    var sets by remember { mutableStateOf((ns?.suggestedSets ?: exercise.sets).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log · ${exercise.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight (kg) — optional") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = sets, onValueChange = { sets = it.filter { c -> c.isDigit() } },
                        label = { Text("Sets") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                // Rest timer for between sets, right where you log them.
                RestTimer(compact = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weight.toDoubleOrNull(), reps.toIntOrNull(), sets.toIntOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "42.5" → "42.5", "40.0" → "40". */
private fun trimKg(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** First integer in a reps string like "8-10" or "12". */
private fun firstInt(reps: String): Int? = Regex("\\d+").find(reps)?.value?.toIntOrNull()

@Composable
private fun Hero(plan: WeeklyWorkout?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("🏋️ Move", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                plan?.let { "${it.blockLabel.ifBlank { "Training block" }} · ${it.location} · ${it.goal}" } ?: "Your training plan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun WeekStrip(days: List<WorkoutDay>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.take(7).forEachIndexed { i, d ->
            val rest = d.rest
            val selected = i == selectedIndex
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (rest) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onSelect(i) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 10.dp)
                    .semantics { contentDescription = "${d.label ?: "Day ${d.dayIndex + 1}"}${if (rest) ", rest day" else ""}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(if (rest) "😴" else "💪", style = MaterialTheme.typography.bodyMedium)
                Text(
                    d.label?.take(3) ?: "D${d.dayIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
