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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
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
 * The Move pillar - a segmented switcher so everything active lives under one tab:
 * [Today | Library | Log]. Today is the day's plan with sets/reps/weight logging built in; Library
 * is the full searchable exercise catalog for logging anything off-plan; Log is history. Meditation /
 * mind-&-body content lives in its own dedicated Mind tab, so it's intentionally NOT duplicated here.
 */
@Composable
fun MoveScreen(modifier: Modifier = Modifier, initialSection: Int = 0) {
    var section by remember { mutableIntStateOf(initialSection.coerceIn(0, 2)) }
    val labels = listOf("Today", "Library", "Log")

    Column(modifier.fillMaxSize()) {
        com.nutriai.ui.components.ScreenHeader("Move", modifier = Modifier.padding(horizontal = 12.dp))
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
            1 -> ExerciseLibraryTab(Modifier.fillMaxSize())
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

    /**
     * Log what you actually did for ANY item - weighted set, bodyweight reps/sets, or a timed piece
     * (duration). The server computes a MET/strength-based calorie burn from whatever's provided, so
     * it feeds the same burnedTodayKcal / net-calorie picture (the calorie TARGET is never inflated -
     * burn is a separate signal). Reloads the plan so the next-session suggestion updates.
     */
    fun logEntry(name: String, focus: String?, weightKg: Double?, reps: Int?, sets: Int?, durationMin: Int?) {
        viewModelScope.launch {
            val r = repository.logExercise(
                ExerciseLogRequest(exerciseName = name, focus = focus, weightKg = weightKg, reps = reps, sets = sets, durationMin = durationMin),
            )
            if (r.isSuccess) {
                val kcal = r.getOrNull()?.kcal ?: 0
                val env = repository.exercisePlanFull().getOrNull()
                _state.value = _state.value.copy(
                    plan = env?.plan ?: _state.value.plan,
                    levelSuggestion = env?.levelSuggestion ?: _state.value.levelSuggestion,
                    toast = "Logged $name - ~$kcal kcal 🔥",
                    sessionKcal = _state.value.sessionKcal + kcal,
                )
            } else {
                _state.value = _state.value.copy(toast = "Couldn't log - try again")
            }
        }
    }

    /**
     * Logs a REAL, non-uniform set list - sit-ups at 20/12/12, planks at 60s/40s/40s - as one entry
     * per set, so history keeps the actual numbers instead of an averaged "3 x 12". Reports the
     * combined burn in a single toast.
     */
    fun logSets(name: String, focus: String?, sets: List<LoggedSet>) {
        if (sets.isEmpty()) return
        viewModelScope.launch {
            var kcal = 0
            var ok = 0
            sets.forEach { s ->
                val r = repository.logExercise(
                    ExerciseLogRequest(
                        exerciseName = name,
                        focus = focus,
                        weightKg = s.weightKg,
                        reps = s.reps,
                        sets = if (s.reps != null) 1 else null,
                        durationMin = s.durationMin,
                        notes = s.note,
                    ),
                )
                if (r.isSuccess) { ok++; kcal += r.getOrNull()?.kcal ?: 0 }
            }
            val env = repository.exercisePlanFull().getOrNull()
            _state.value = _state.value.copy(
                plan = env?.plan ?: _state.value.plan,
                levelSuggestion = env?.levelSuggestion ?: _state.value.levelSuggestion,
                toast = if (ok > 0) "Logged $name · $ok set${if (ok == 1) "" else "s"} · ~$kcal kcal 🔥" else "Couldn't log - try again",
                sessionKcal = _state.value.sessionKcal + kcal,
            )
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
        LogExerciseDialog(
            exercise = ex,
            onDismiss = { logTarget = null },
            onConfirm = { sets ->
                viewModel.logSets(ex.name, shownDay?.focus, sets)
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
        // Plan context — one compact line, block/location/goal + note folded in (no separate paragraph).
        plan?.let { p ->
            item {
                val context = buildString {
                    append(p.blockLabel.ifBlank { "Training block" }).append(" · ").append(p.location).append(" · ").append(p.goal)
                    p.note?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
                }
                Text(
                    context,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics { contentDescription = "Plan: $context" },
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
                item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Text("Rest & recovery day - light movement, stretch, hydrate.", Modifier.padding(18.dp)) } }
            } else {
                // Warm-up.
                if (day.warmup.isNotEmpty()) {
                    item { SessionHeader("🔥 Warm-up") }
                    items(day.warmup.size) { i ->
                        val w = day.warmup[i]
                        ExerciseRowCard(w, "Warm-up", onLog = { logTarget = w })
                    }
                }

                // Main lifts - same card, with the numbered badge + sets/reps/log/cue/swap controls.
                if (day.exercises.isNotEmpty()) {
                    item { SessionHeader("🏋️ Main") }
                    items(day.exercises.size) { i ->
                        val ex = day.exercises[i]
                        ExerciseRowCard(
                            ex = ex,
                            section = "Main",
                            index = i,
                            onLog = { logTarget = ex },
                            onSwap = if (ex.substitutions.isNotEmpty()) ({ swapTarget = ex }) else null,
                        )
                    }
                }

                // Core / Abs - its own labeled section, loggable.
                if (day.core.isNotEmpty()) {
                    item { SessionHeader("🧱 Core & Abs") }
                    items(day.core.size) { i ->
                        val cr = day.core[i]
                        ExerciseRowCard(cr, "Core", onLog = { logTarget = cr })
                    }
                }

                // Cardio (single item, if any).
                day.cardio?.let { c ->
                    item { SessionHeader("🏃 Cardio") }
                    item { ExerciseRowCard(c, "Cardio", onLog = { logTarget = c }) }
                }

                // Cool-down.
                if (day.cooldown.isNotEmpty()) {
                    item { SessionHeader("🧊 Cool-down") }
                    items(day.cooldown.size) { i ->
                        val cd = day.cooldown[i]
                        ExerciseRowCard(cd, "Cool-down", onLog = { logTarget = cd })
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

/**
 * One shared card for EVERY Move section (warm-up, main, core, cardio, cool-down) so the whole tab
 * reads as a single design: same shape/padding, [ExerciseIllustration], and name + reps/target
 * styling. Section-specific controls are opt-in:
 *  - Main passes [index] (numbered badge) + optional [onSwap]; every item passes [onLog], which opens
 *    the adaptive log dialog (duration for timed pieces, reps/sets for the rest, weight only for lifts).
 * The cue and next-session target lines render only when the item carries them (i.e. Main).
 */
@Composable
private fun ExerciseRowCard(
    ex: ExerciseItem,
    section: String,
    index: Int? = null,
    onLog: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
) {
    val repsLabel = if (ex.sets > 1) "${ex.sets} x ${ex.reps}" else ex.reps
    Card(
        Modifier.fillMaxWidth()
            .semantics { contentDescription = "$section: ${ex.name}, $repsLabel" },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Animated demo (or muscle diagram). Kept first so every row across every section
                // lines up at the same left edge, numbered or not.
                ExerciseDemo(name = ex.name, muscleGroup = ex.muscleGroup, sizeDp = 40)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (index != null) "${index + 1}. ${ex.name}" else ex.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ex.muscleGroup?.takeIf { it.isNotBlank() }?.let { mg ->
                        Text(mg.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    repsLabel,
                    Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
            }

            ex.cue?.takeIf { it.isNotBlank() }?.let { cue ->
                Text("💡 $cue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            if (onLog != null || onSwap != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onLog != null) {
                        TextButton(
                            onClick = onLog,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Log what you did for ${ex.name}" },
                        ) { Text("+ Log") }
                    }
                    if (onSwap != null) {
                        TextButton(
                            onClick = onSwap,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Swap or see alternatives for ${ex.name}" },
                        ) { Text("Swap / alternatives") }
                    }
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
                    "Alternatives you can do instead - same muscle group, often equipment-free or easier on joints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.substitutions.forEach { sub ->
                    Row(
                        Modifier.fillMaxWidth().semantics { contentDescription = "Alternative: $sub" },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseDemo(name = exercise.name, muscleGroup = exercise.muscleGroup, sizeDp = 34)
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

/**
 * Full-screen exercise library (the "Library" sub-tab): search + category chips + a 2-column grid of
 * result cards, so browsing the whole catalog doesn't need a cramped dialog. Tapping a card opens the
 * same adaptive log dialog used everywhere else in Move. If what they did isn't in the catalog, a
 * "Log <what you typed>" card lets them log it by name anyway - nothing is off-limits.
 */
@Composable
private fun ExerciseLibraryTab(modifier: Modifier = Modifier, viewModel: MoveViewModel = hiltViewModel()) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExerciseCatalog.Category.ALL) }
    var logTarget by remember { mutableStateOf<ExerciseItem?>(null) }
    val results = remember(query, category) { ExerciseCatalog.search(query, category) }
    val typed = query.trim()
    val hasExactName = results.any { it.name.equals(typed, ignoreCase = true) }

    logTarget?.let { ex ->
        LogExerciseDialog(
            exercise = ex,
            onDismiss = { logTarget = null },
            onConfirm = { sets ->
                viewModel.logSets(ex.name, null, sets)
                logTarget = null
            },
        )
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("e.g. bench press, squat, yoga") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Exercise search field" },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ExerciseCatalog.categories) { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text("${c.emoji} ${c.label}") },
                    modifier = Modifier.semantics { contentDescription = "${c.label} filter" },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (typed.isNotEmpty() && !hasExactName) {
                item {
                    ExerciseGridCard(
                        ex = ExerciseCatalog.custom(typed),
                        labelOverride = "Log \"$typed\"",
                        onClick = { logTarget = ExerciseCatalog.custom(typed) },
                    )
                }
            }
            gridItems(results) { ex -> ExerciseGridCard(ex = ex, onClick = { logTarget = ex }) }
            if (results.isEmpty() && typed.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("No match in the library - tap \"Log \\\"$typed\\\"\" above to log it anyway.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One exercise in the Library's 2-column grid — compact card, ~120dp tall. */
@Composable
private fun ExerciseGridCard(ex: ExerciseItem, onClick: () -> Unit, labelOverride: String? = null) {
    Card(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Log ${labelOverride ?: ex.name}" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExerciseDemo(name = ex.name, muscleGroup = ex.muscleGroup, sizeDp = 40)
            Text(labelOverride ?: ex.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
            ex.muscleGroup?.takeIf { it.isNotBlank() }?.let { mg ->
                Text(mg.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("+ Log", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Is this a TIMED movement (cardio / stretch / plank), i.e. logged by duration not reps? */
private fun isTimed(ex: ExerciseItem): Boolean =
    Regex("""min|\d+\s*s\b""", RegexOption.IGNORE_CASE).containsMatchIn(ex.reps)

/** Does it make sense to enter a load? Only real weighted strength (not bodyweight / cardio / mobility). */
private fun isWeighted(ex: ExerciseItem): Boolean =
    !isTimed(ex) && ex.type == "strength" && ex.equipment != "bodyweight"

/** One performed set: reps (or a timed hold) plus an optional load. */
data class LoggedSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationMin: Int? = null,
    val note: String? = null,
)

/** One editable row in the multi-set log dialog. */
private class SetRow(amount: String, weight: String) {
    var amount by mutableStateOf(amount)
    var weight by mutableStateOf(weight)
}

/**
 * Multi-set log dialog. Real sessions are rarely uniform - sit-ups go 20/12/12, planks go 60s/40s/40s -
 * so every set gets its own row instead of forcing a single "sets x reps" pair. Timed holds enter
 * SECONDS per set; a single cardio block (a 16-min walk) is one row of minutes; weighted lifts get an
 * optional per-set load. Saving logs one entry per set, so the history keeps the real numbers.
 */
@Composable
private fun LogExerciseDialog(exercise: ExerciseItem, onDismiss: () -> Unit, onConfirm: (List<LoggedSet>) -> Unit) {
    val timed = isTimed(exercise)
    val weighted = isWeighted(exercise)
    val ns = exercise.nextSession
    val defaultReps = (ns?.suggestedReps ?: firstInt(exercise.reps))?.toString() ?: ""
    val defaultWeight = ns?.suggestedWeightKg?.let { trimKg(it) } ?: ""
    // A single cardio block (walk / cycle / stair climber) is one row of minutes, not a set list.
    val singleBlock = timed && exercise.sets <= 1
    val defaultCount = if (singleBlock) 1 else (ns?.suggestedSets ?: exercise.sets).coerceIn(1, 10)

    val rows = remember(exercise.name) {
        mutableStateListOf<SetRow>().also { list ->
            val amount = if (timed && !singleBlock) "45" else if (timed) estimateMinutes(exercise.reps, 2).toString() else defaultReps
            repeat(defaultCount) { list.add(SetRow(amount, defaultWeight)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log · ${exercise.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        singleBlock -> "How long did you go?"
                        timed -> "Seconds held per set - they're rarely equal."
                        else -> "Reps per set - edit any that differed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { i, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (singleBlock) "⏱" else "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(20.dp),
                        )
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { v -> row.amount = v.filter { c -> c.isDigit() } },
                            label = { Text(if (singleBlock) "Minutes" else if (timed) "Seconds" else "Reps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        if (weighted) {
                            OutlinedTextField(
                                value = row.weight,
                                onValueChange = { v -> row.weight = v.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("kg") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rows.size > 1) {
                            Text(
                                "✕",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { rows.removeAt(i) }
                                    .padding(6.dp)
                                    .semantics { contentDescription = "Remove set ${i + 1}" },
                            )
                        }
                    }
                }
                if (!singleBlock) {
                    TextButton(onClick = { rows.add(SetRow(rows.lastOrNull()?.amount ?: "", rows.lastOrNull()?.weight ?: "")) }) {
                        Text("+ Add set")
                    }
                }
                if (!timed) RestTimer(compact = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val out = rows.mapNotNull { r ->
                    val n = r.amount.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    when {
                        singleBlock -> LoggedSet(durationMin = n, note = "$n min")
                        timed -> LoggedSet(durationMin = maxOf(1, Math.round(n / 60.0).toInt()), note = "${n}s")
                        else -> LoggedSet(weightKg = r.weight.toDoubleOrNull(), reps = n)
                    }
                }
                if (out.isNotEmpty()) onConfirm(out)
            }) { Text("Save") }
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
