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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.ExerciseItem
import com.nutriai.data.remote.dto.ExerciseLogRequest
import com.nutriai.data.remote.dto.WeeklyWorkout
import com.nutriai.data.remote.dto.WorkoutDay
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.Status
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun MoveScreen(modifier: Modifier = Modifier, initialSection: Int = 0) {
    var section by remember { mutableIntStateOf(initialSection.coerceIn(0, 2)) }
    val labels = listOf("🏋️ Today" , "📚 Library", "📊 Log")

    Column(modifier.fillMaxSize().background(MaterialTheme.kaizenColors.pageBackground)) {
        ScreenHeader("🏃 Move", modifier = Modifier.padding(horizontal = Spacing.screenHorizontal))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                FilterChip(
                    selected = section == i,
                    onClick = { section = i },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (section == i) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.semantics { contentDescription = "$label section" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MovementColor,
                        selectedLabelColor = Color.White,
                        containerColor = MovementColor.copy(alpha = 0.08f),
                        labelColor = MovementColor,
                    ),
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
// ViewModel — unchanged business logic
// ---------------------------------------------------------------------------

data class MoveState(
    val loading: Boolean = true,
    val plan: WeeklyWorkout? = null,
    val levelSuggestion: com.nutriai.data.remote.dto.LevelSuggestion? = null,
    val error: String? = null,
    val toast: String? = null,
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

// ---------------------------------------------------------------------------
// Exercise tab
// ---------------------------------------------------------------------------

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
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.lg),
    ) {
        plan?.let { p ->
            item {
                val context = buildString {
                    append(p.blockLabel.ifBlank { "Training block" }).append(" · ").append(p.location).append(" · ").append(p.goal)
                    p.note?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
                }
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("🎯", fontSize = 20.sp)
                        Text(
                            context,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        state.toast?.let { msg ->
            item {
                StatusIndicator(text = msg, status = Status.Positive)
                LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.clearToast() }
            }
        }

        if (state.sessionKcal > 0) {
            item {
                FeatureCard(emoji = "🔥", title = "Session Burn", accentColor = KaizenCoral) {
                    Text(
                        "~${state.sessionKcal} kcal burned",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = KaizenCoral,
                    )
                }
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MovementColor) } }
        }
        state.error?.let { err ->
            item {
                EmptyState(title = err, emoji = "🏋️")
            }
        }

        state.levelSuggestion?.takeIf { it.direction == "up" || it.direction == "down" }?.let { ls ->
            item {
                FeatureCard(
                    emoji = if (ls.direction == "up") "🚀" else "⚠️",
                    title = if (ls.direction == "up") "Ready to Level Up!" else "Consider Easing Down",
                    accentColor = if (ls.direction == "up") BrandGreen else BrandAmber,
                ) {
                    Text(ls.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Today's workout hero
        shownDay?.let { day ->
            val hasContent = day.exercises.isNotEmpty() || day.warmup.isNotEmpty() || day.core.isNotEmpty() || day.cardio != null || day.cooldown.isNotEmpty()
            item {
                FeatureCard(
                    emoji = if (day.rest || !hasContent) "💤" else "💪",
                    title = "Today's Workout",
                    accentColor = MovementColor,
                ) {
                    Text(
                        if (day.rest || !hasContent) "Rest day 🧘" else day.focus,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (!day.rest && hasContent) {
                        val firstLoggable = day.warmup.firstOrNull() ?: day.exercises.firstOrNull() ?: day.core.firstOrNull() ?: day.cardio ?: day.cooldown.firstOrNull()
                        Button(
                            onClick = { firstLoggable?.let { logTarget = it } },
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).heightIn(min = 52.dp),
                            shape = RoundedCornerShape(Radius.md),
                            colors = ButtonDefaults.buttonColors(containerColor = MovementColor),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text(" 🏃 Start workout", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        plan?.days?.takeIf { it.isNotEmpty() }?.let { days ->
            item { SectionHeader(title = "This Week", emoji = "📅") }
            item {
                val current = selectedIdx ?: days.indexOfFirst { it === shownDay }
                WeekStrip(days, selectedIndex = current, onSelect = { selectedIdx = it })
            }
        }

        shownDay?.let { day ->
            val hasContent = day.exercises.isNotEmpty() || day.warmup.isNotEmpty() || day.core.isNotEmpty() || day.cardio != null || day.cooldown.isNotEmpty()
            if (day.rest || !hasContent) {
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("🧘", fontSize = 24.sp)
                            Text("Rest & recovery day - light movement, stretch, hydrate.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                if (day.warmup.isNotEmpty()) {
                    item { SectionHeader(title = "Warm-up", emoji = "🔥") }
                    items(day.warmup.size) { i ->
                        val w = day.warmup[i]
                        ExerciseRow(w, "Warm-up", onLog = { logTarget = w })
                        if (i != day.warmup.lastIndex) HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
                    }
                }
                if (day.exercises.isNotEmpty()) {
                    item { SectionHeader(title = "Main Workout", emoji = "💪") }
                    items(day.exercises.size) { i ->
                        val ex = day.exercises[i]
                        ExerciseRow(
                            ex = ex, section = "Main", index = i,
                            onLog = { logTarget = ex },
                            onSwap = if (ex.substitutions.isNotEmpty()) ({ swapTarget = ex }) else null,
                        )
                        if (i != day.exercises.lastIndex) HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
                    }
                }
                if (day.core.isNotEmpty()) {
                    item { SectionHeader(title = "Core & Abs", emoji = "🦾") }
                    items(day.core.size) { i ->
                        val cr = day.core[i]
                        ExerciseRow(cr, "Core", onLog = { logTarget = cr })
                        if (i != day.core.lastIndex) HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
                    }
                }
                day.cardio?.let { c ->
                    item { SectionHeader(title = "Cardio", emoji = "❤️") }
                    item { ExerciseRow(c, "Cardio", onLog = { logTarget = c }) }
                }
                if (day.cooldown.isNotEmpty()) {
                    item { SectionHeader(title = "Cool-down", emoji = "🧘") }
                    items(day.cooldown.size) { i ->
                        val cd = day.cooldown[i]
                        ExerciseRow(cd, "Cool-down", onLog = { logTarget = cd })
                        if (i != day.cooldown.lastIndex) HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
                    }
                }
                item { RestTimer(Modifier.padding(top = Spacing.xs)) }
            }
        }

        item { StrengthTrendSection(Modifier.padding(top = Spacing.xs)) }

        plan?.disclaimer?.takeIf { it.isNotBlank() }?.let { d ->
            item {
                Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs))
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    ex: ExerciseItem,
    section: String,
    index: Int? = null,
    onLog: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
) {
    val repsLabel = if (ex.sets > 1) "${ex.sets} x ${ex.reps}" else ex.reps
    GlassCard {
        Column(
            Modifier.fillMaxWidth()
                .semantics { contentDescription = "$section: ${ex.name}, $repsLabel" },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                ExerciseDemo(name = ex.name, muscleGroup = ex.muscleGroup, sizeDp = 40)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (index != null) "${index + 1}. ${ex.name}" else ex.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ex.muscleGroup?.takeIf { it.isNotBlank() }?.let { mg ->
                        Text(mg.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MovementColor)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                ) {
                    Text(
                        repsLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            ex.cue?.takeIf { it.isNotBlank() }?.let { cue ->
                Text("💡 $cue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            ex.nextSession?.let { ns ->
                val target = buildString {
                    append("➡️ Next: ")
                    if (ns.suggestedWeightKg != null) append("${trimKg(ns.suggestedWeightKg)} kg × ")
                    append("${ns.suggestedReps} × ${ns.suggestedSets}")
                    if (ns.deload) append(" · deload 💤")
                }
                Text(
                    target,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ns.deload) BrandAmber else BrandGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (onLog != null || onSwap != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onLog != null) {
                        Button(
                            onClick = onLog,
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(containerColor = MovementColor),
                        ) { Text("➕ Log", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    if (onSwap != null) {
                        TextButton(
                            onClick = onSwap,
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) { Text("🔄 Swap / alternatives", color = KaizenLavender) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapExerciseDialog(exercise: ExerciseItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔄 Swap · ${exercise.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Alternatives you can do instead - same muscle group, often equipment-free or easier on joints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.substitutions.forEach { sub ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(KaizenLavender.copy(alpha = 0.08f))
                            .padding(Spacing.sm)
                            .semantics { contentDescription = "Alternative: $sub" },
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
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        },
    )
}

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

    Column(modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title = "Exercise Library", emoji = "📚")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("🔍 e.g. bench press, squat, yoga") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.sm),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ExerciseCatalog.categories) { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text("${c.emoji} ${c.label}", fontWeight = if (category == c) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MovementColor,
                        selectedLabelColor = Color.White,
                        containerColor = MovementColor.copy(alpha = 0.08f),
                        labelColor = MovementColor,
                    ),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (typed.isNotEmpty() && !hasExactName) {
                item {
                    ExerciseGridCard(
                        ex = ExerciseCatalog.custom(typed),
                        labelOverride = "➕ Log \"$typed\"",
                        onClick = { logTarget = ExerciseCatalog.custom(typed) },
                    )
                }
            }
            gridItems(results) { ex -> ExerciseGridCard(ex = ex, onClick = { logTarget = ex }) }
            if (results.isEmpty() && typed.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyState(title = "No match - tap above to log it anyway", emoji = "🤷")
                }
            }
        }
    }
}

@Composable
private fun ExerciseGridCard(ex: ExerciseItem, onClick: () -> Unit, labelOverride: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(
                Brush.verticalGradient(
                    listOf(MovementColor.copy(alpha = 0.12f), MovementColor.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, MovementColor.copy(alpha = 0.2f), RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ExerciseDemo(name = ex.name, muscleGroup = ex.muscleGroup, sizeDp = 40)
        Text(labelOverride ?: ex.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
        ex.muscleGroup?.takeIf { it.isNotBlank() }?.let { mg ->
            Text(mg.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(MovementColor)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text("➕ Log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

private fun isTimed(ex: ExerciseItem): Boolean =
    Regex("""min|\d+\s*s\b""", RegexOption.IGNORE_CASE).containsMatchIn(ex.reps)

private fun isWeighted(ex: ExerciseItem): Boolean =
    !isTimed(ex) && ex.type == "strength" && ex.equipment != "bodyweight"

data class LoggedSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationMin: Int? = null,
    val note: String? = null,
)

private class SetRow(amount: String, weight: String) {
    var amount by mutableStateOf(amount)
    var weight by mutableStateOf(weight)
}

@Composable
private fun LogExerciseDialog(exercise: ExerciseItem, onDismiss: () -> Unit, onConfirm: (List<LoggedSet>) -> Unit) {
    val timed = isTimed(exercise)
    val weighted = isWeighted(exercise)
    val ns = exercise.nextSession
    val defaultReps = (ns?.suggestedReps ?: firstInt(exercise.reps))?.toString() ?: ""
    val defaultWeight = ns?.suggestedWeightKg?.let { trimKg(it) } ?: ""
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
        title = { Text("🏋️ Log · ${exercise.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        singleBlock -> "⏱️ How long did you go?"
                        timed -> "⏱️ Seconds held per set - they're rarely equal."
                        else -> "💪 Reps per set - edit any that differed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { i, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MovementColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (singleBlock) "•" else "${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
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
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove set ${i + 1}",
                                tint = KaizenCoral,
                                modifier = Modifier.clickable { rows.removeAt(i) }.padding(6.dp).size(18.dp),
                            )
                        }
                    }
                }
                if (!singleBlock) {
                    TextButton(onClick = { rows.add(SetRow(rows.lastOrNull()?.amount ?: "", rows.lastOrNull()?.weight ?: "")) }) {
                        Text("➕ Add set", color = MovementColor, fontWeight = FontWeight.Bold)
                    }
                }
                if (!timed) RestTimer(compact = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val out = rows.mapNotNull { r ->
                        val n = r.amount.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                        when {
                            singleBlock -> LoggedSet(durationMin = n, note = "$n min")
                            timed -> LoggedSet(durationMin = maxOf(1, Math.round(n / 60.0).toInt()), note = "${n}s")
                            else -> LoggedSet(weightKg = r.weight.toDoubleOrNull(), reps = n)
                        }
                    }
                    if (out.isNotEmpty()) onConfirm(out)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MovementColor),
                shape = RoundedCornerShape(Radius.sm),
            ) { Text("✅ Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun trimKg(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun firstInt(reps: String): Int? = Regex("\\d+").find(reps)?.value?.toIntOrNull()

@Composable
private fun WeekStrip(days: List<WorkoutDay>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.take(7).forEachIndexed { i, d ->
            val rest = d.rest
            val selected = i == selectedIndex
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.sm))
                    .background(
                        if (selected) MovementColor
                        else if (rest) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MovementColor.copy(alpha = 0.1f)
                    )
                    .then(if (selected) Modifier.border(2.dp, MovementColor, RoundedCornerShape(Radius.sm)) else Modifier)
                    .clickable { onSelect(i) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (rest) {
                    Text("💤", fontSize = 14.sp)
                } else {
                    Text("🏋️", fontSize = 14.sp)
                }
                Text(
                    d.label?.take(3) ?: "D${d.dayIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
