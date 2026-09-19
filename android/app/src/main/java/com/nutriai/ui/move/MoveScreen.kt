package com.nutriai.ui.move

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.nutriai.data.remote.dto.NextSession
import com.nutriai.data.remote.dto.WeeklyWorkout
import com.nutriai.data.remote.dto.WorkoutDay
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

/** Accent color for the Move screen — follows the user's chosen accent. */
private val MoveAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

@Composable
fun MoveScreen(modifier: Modifier = Modifier, initialSection: Int = 0) {
    var section by remember { mutableIntStateOf(initialSection.coerceIn(0, 3)) }

    Column(modifier.fillMaxSize()) {
        // Purple gradient header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))))
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        ) {
            Text("🏃 Move", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        // Tab bar — same style as Nutrition
        val tabs = listOf("🏋️" to "Today", "📚" to "Library", "⭐" to "My Gym", "📊" to "Log")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            shape = Sharp,
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                tabs.forEachIndexed { idx, (emoji, label) ->
                    val selected = section == idx
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { section = idx }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(emoji, fontSize = 12.sp)
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        when (section) {
            0 -> ExerciseTab(Modifier.fillMaxSize())
            1 -> ExerciseLibraryTab(Modifier.fillMaxSize())
            2 -> MyGymTab(Modifier.fillMaxSize(), onGoToLibrary = { section = 1 })
            else -> MoveLogScreen(Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------
// ViewModel — unchanged business logic
// ---------------------------------------------------------------------------

/** One entry logged into the current mixed workout session (treadmill + bench press + ..., say). */
data class SessionEntry(val name: String, val detail: String, val kcal: Int)

data class MoveState(
    val loading: Boolean = true,
    val plan: WeeklyWorkout? = null,
    val levelSuggestion: com.nutriai.data.remote.dto.LevelSuggestion? = null,
    val movementStage: com.nutriai.data.remote.dto.MovementStageInfo? = null,
    val splitSuggestion: com.nutriai.data.remote.dto.SplitSuggestion? = null,
    val error: String? = null,
    val toast: String? = null,
    val sessionKcal: Int = 0,
    /** Non-null while a mixed workout session is in progress; shared by every exercise logged until "Complete Workout". */
    val activeSessionId: String? = null,
    val sessionEntries: List<SessionEntry> = emptyList(),
    /** Lower-cased names of exercises actually logged today - drives "planned vs actual" adherence. */
    val todayLoggedNames: Set<String> = emptySet(),
    /** Exercise names the user has starred into "My Gym" for quick-access logging. */
    val gymFavoriteNames: Set<String> = emptySet(),
    /** Local date (yyyy-MM-dd) the plan was last loaded for - lets [MoveViewModel.refreshIfNewDay]
     * detect a day boundary crossed while the app sat in the background, instead of silently
     * showing yesterday's "Today" plan until the process is killed and relaunched. */
    val loadedDayKey: String? = null,
)

@HiltViewModel
class MoveViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(MoveState())
    val state: StateFlow<MoveState> = _state.asStateFlow()

    init { load() }

    /** Re-fetches the plan only if the local date has moved on since it was last loaded - called
     * on every screen resume so leaving the app open overnight doesn't strand "Today" on
     * yesterday. Cheap no-op the rest of the time (same day = no network call). */
    fun refreshIfNewDay() {
        val today = java.time.LocalDate.now().toString()
        if (_state.value.loadedDayKey != today) load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.exercisePlanFull()
            _state.value = if (r.isSuccess) {
                val env = r.getOrNull()
                _state.value.copy(
                    loading = false, plan = env?.plan, levelSuggestion = env?.levelSuggestion,
                    movementStage = env?.movementStage, splitSuggestion = env?.splitSuggestion, error = null,
                    loadedDayKey = java.time.LocalDate.now().toString(),
                )
            } else {
                _state.value.copy(loading = false, error = "Generate a plan first (Diet tab)")
            }
        }
        refreshTodayLogged()
        loadGymFavorites()
    }

    /** Refreshes what's actually been logged today, for the planned-vs-actual adherence chip. */
    private fun refreshTodayLogged() {
        viewModelScope.launch {
            val names = repository.exerciseLogs(null).getOrDefault(emptyList()).map { it.exerciseName.lowercase().trim() }.toSet()
            _state.value = _state.value.copy(todayLoggedNames = names)
        }
    }

    private fun loadGymFavorites() {
        viewModelScope.launch {
            val names = repository.gymFavorites().getOrDefault(emptyList()).toSet()
            _state.value = _state.value.copy(gymFavoriteNames = names)
        }
    }

    /** Toggles an exercise in/out of "My Gym" - optimistic, so the star flips instantly. */
    fun toggleGymFavorite(exerciseName: String) {
        val currentlyFavorite = exerciseName in _state.value.gymFavoriteNames
        _state.value = _state.value.copy(
            gymFavoriteNames = if (currentlyFavorite) _state.value.gymFavoriteNames - exerciseName else _state.value.gymFavoriteNames + exerciseName,
        )
        viewModelScope.launch {
            val r = if (currentlyFavorite) repository.removeGymFavorite(exerciseName) else repository.addGymFavorite(exerciseName)
            if (r.isFailure) {
                // Revert on failure.
                _state.value = _state.value.copy(
                    gymFavoriteNames = if (currentlyFavorite) _state.value.gymFavoriteNames + exerciseName else _state.value.gymFavoriteNames - exerciseName,
                    toast = "Couldn't update My Gym - try again",
                )
            }
        }
    }

    /** Starts a new mixed workout session (treadmill + strength + stretching, all grouped). */
    fun startWorkout() {
        _state.value = _state.value.copy(
            activeSessionId = java.util.UUID.randomUUID().toString(),
            sessionKcal = 0,
            sessionEntries = emptyList(),
        )
    }

    /** Ensures a session is active before logging, so tapping any exercise "just works". */
    private fun ensureSession(): String =
        _state.value.activeSessionId ?: java.util.UUID.randomUUID().toString().also {
            _state.value = _state.value.copy(activeSessionId = it)
        }

    /** Ends the current session; the running total stays visible until the user starts a new one. */
    fun completeWorkout() {
        val n = _state.value.sessionEntries.size
        val kcal = _state.value.sessionKcal
        _state.value = _state.value.copy(
            activeSessionId = null,
            toast = if (n > 0) "Workout complete: $n activit${if (n == 1) "y" else "ies"} · ~$kcal kcal 🔥" else "Workout complete",
        )
    }

    fun logEntry(name: String, focus: String?, weightKg: Double?, reps: Int?, sets: Int?, durationMin: Int?, performedAtDate: String? = null) {
        viewModelScope.launch {
            val sessionId = ensureSession()
            val r = repository.logExercise(
                ExerciseLogRequest(
                    exerciseName = name, focus = focus, weightKg = weightKg, reps = reps, sets = sets,
                    durationMin = durationMin, sessionId = sessionId, performedAt = performedAtDate?.let { "${it}T12:00:00" },
                ),
            )
            if (r.isSuccess) {
                val kcal = r.getOrNull()?.kcal ?: 0
                val env = repository.exercisePlanFull().getOrNull()
                val detail = listOfNotNull(
                    weightKg?.let { "${trimKg(it)}kg" },
                    reps?.let { "${it} reps" },
                    durationMin?.let { "${it} min" },
                ).joinToString(" · ")
                _state.value = _state.value.copy(
                    plan = env?.plan ?: _state.value.plan,
                    levelSuggestion = env?.levelSuggestion ?: _state.value.levelSuggestion,
                    toast = "Logged $name - ~$kcal kcal 🔥",
                    sessionKcal = _state.value.sessionKcal + kcal,
                    sessionEntries = _state.value.sessionEntries + SessionEntry(name, detail, kcal),
                )
                refreshTodayLogged()
            } else {
                _state.value = _state.value.copy(toast = "Couldn't log - try again")
            }
        }
    }

    fun logSets(name: String, focus: String?, sets: List<LoggedSet>, performedAtDate: String? = null) {
        if (sets.isEmpty()) return
        viewModelScope.launch {
            val sessionId = ensureSession()
            var kcal = 0
            var ok = 0
            sets.forEach { s ->
                val r = repository.logExercise(
                    ExerciseLogRequest(
                        exerciseName = name,
                        focus = focus,
                        weightKg = s.weightKg,
                        reps = s.reps,
                        rir = s.rir,
                        sets = if (s.reps != null) 1 else null,
                        durationMin = s.durationMin,
                        notes = s.note,
                        sessionId = sessionId,
                        performedAt = performedAtDate?.let { "${it}T12:00:00" },
                        speedKmh = s.speedKmh,
                        inclinePct = s.inclinePct,
                        distanceKm = s.distanceKm,
                    ),
                )
                if (r.isSuccess) { ok++; kcal += r.getOrNull()?.kcal ?: 0 }
            }
            val env = repository.exercisePlanFull().getOrNull()
            val detail = listOfNotNull(
                if (ok > 0) "$ok set${if (ok == 1) "" else "s"}" else null,
                sets.firstOrNull()?.durationMin?.let { "${it} min" },
            ).joinToString(" · ")
            _state.value = _state.value.copy(
                plan = env?.plan ?: _state.value.plan,
                levelSuggestion = env?.levelSuggestion ?: _state.value.levelSuggestion,
                toast = if (ok > 0) "Logged $name · $ok set${if (ok == 1) "" else "s"} · ~$kcal kcal 🔥" else "Couldn't log - try again",
                sessionKcal = _state.value.sessionKcal + kcal,
                sessionEntries = if (ok > 0) _state.value.sessionEntries + SessionEntry(name, detail, kcal) else _state.value.sessionEntries,
            )
            if (ok > 0) refreshTodayLogged()
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
private fun ExerciseTab(modifier: Modifier = Modifier, viewModel: MoveViewModel = hiltViewModel(), planVm: com.nutriai.ui.plan.PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Catches the case where the app sat open/backgrounded across midnight - without this,
    // "Today" silently keeps showing whatever day it was when the ViewModel first loaded, since
    // a still-alive process never re-triggers init{}. Cheap: no-op unless the date actually moved.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshIfNewDay()
        onPauseOrDispose {}
    }
    val plan = state.plan
    // Prefix match, not equality: a period day relabels to "Today · Period" (still today).
    val today = plan?.days?.firstOrNull { it.label?.startsWith("Today") == true } ?: plan?.days?.firstOrNull { !it.rest }
    var selectedIdx by remember(plan) { mutableStateOf<Int?>(null) }
    val shownDay = selectedIdx?.let { i -> plan?.days?.getOrNull(i) } ?: today

    var logTarget by remember { mutableStateOf<ExerciseItem?>(null) }
    var swapTarget by remember { mutableStateOf<ExerciseItem?>(null) }

    logTarget?.let { ex ->
        LogExerciseDialog(
            exercise = ex,
            onDismiss = { logTarget = null },
            onConfirm = { sets ->
                // Logs against the day actually selected in the week strip (e.g. tapping back to
                // Thursday to record a workout done that day), not always "right now" - previously
                // every log silently got today's real timestamp regardless of which day was shown,
                // corrupting history for anyone training on a different day than the plan expected.
                viewModel.logSets(ex.name, shownDay?.focus, sets, performedAtDate = shownDay?.date)
                logTarget = null
            },
            onPlanTomorrow = { name ->
                planVm.addExerciseToDate(java.time.LocalDate.now().plusDays(1).toString(), name, ex.muscleGroup)
            },
        )
    }

    swapTarget?.let { ex ->
        SwapExerciseDialog(exercise = ex, onDismiss = { swapTarget = null })
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        // Toast
        state.toast?.let { msg ->
            item(span = { GridItemSpan(2) }) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.1f)),
                ) {
                    Text(msg, Modifier.padding(Spacing.sm), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = BrandGreen)
                }
                LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.clearToast() }
            }
        }

        // Mixed workout session — one session can hold treadmill + strength + stretching, etc.
        if (state.activeSessionId != null || state.sessionEntries.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text("🔥", fontSize = 20.sp)
                                Column {
                                    Text(
                                        if (state.activeSessionId != null) "Workout in progress" else "Last workout",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text("~${state.sessionKcal} kcal · ${state.sessionEntries.size} activit${if (state.sessionEntries.size == 1) "y" else "ies"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = KaizenCoral)
                                }
                            }
                            if (state.activeSessionId != null) {
                                Button(
                                    onClick = { viewModel.completeWorkout() },
                                    shape = Sharp,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) { Text("Complete", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                        }
                        if (state.sessionEntries.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.sm))
                            state.sessionEntries.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("${i + 1}. ${e.name}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        if (e.detail.isNotBlank()) {
                                            Text(e.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Text("${e.kcal} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.loading) {
            item(span = { GridItemSpan(2) }) { Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MoveAccent) } }
        }
        state.error?.let { err ->
            item(span = { GridItemSpan(2) }) { EmptyState(title = err, emoji = "🏋️") }
        }

        // Today's focus — a slim status strip (day name + adherence). Actually starting or
        // logging an exercise happens on its own row further down, so no button here.
        shownDay?.let { day ->
            val hasContent = day.exercises.isNotEmpty() || day.warmup.isNotEmpty() || day.core.isNotEmpty() || day.cardio != null || day.cooldown.isNotEmpty()
            item(span = { GridItemSpan(2) }) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(if (day.rest || !hasContent) "💤" else "💪", fontSize = 16.sp)
                        Text(
                            if (day.rest || !hasContent) "Rest day 🧘" else day.focus,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )

                        // Planned-vs-actual adherence - only meaningful for TODAY's own plan, not
                        // a different day the user is just previewing in the week strip.
                        if (!day.rest && hasContent && day === today) {
                            val planned = (day.warmup + day.exercises + day.core + listOfNotNull(day.cardio) + day.cooldown)
                                .distinctBy { it.name.lowercase().trim() }
                            val done = planned.count { it.name.lowercase().trim() in state.todayLoggedNames }
                            if (planned.isNotEmpty()) {
                                val adherencePct = (done * 100) / planned.size
                                Text("$done/${planned.size} · $adherencePct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Week strip
        plan?.days?.takeIf { it.isNotEmpty() }?.let { days ->
            item(span = { GridItemSpan(2) }) { Text("📅 This Week", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            item(span = { GridItemSpan(2) }) {
                val current = selectedIdx ?: days.indexOfFirst { it === shownDay }
                WeekStrip(days, selectedIndex = current, onSelect = { selectedIdx = it })
            }
        }

        // Exercises for shown day — grid cards, matching the Library look.
        shownDay?.let { day ->
            val hasContent = day.exercises.isNotEmpty() || day.warmup.isNotEmpty() || day.core.isNotEmpty() || day.cardio != null || day.cooldown.isNotEmpty()
            if (day.rest || !hasContent) {
                item(span = { GridItemSpan(2) }) {
                    Card(Modifier.fillMaxWidth(), shape = Sharp, elevation = CardDefaults.cardElevation(1.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("🧘", fontSize = 20.sp)
                            Text("Rest & recovery day - light movement, stretch, hydrate.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                if (day.warmup.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) { Text("🔥 Warm-up", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    exerciseCards(day.warmup, onLog = { logTarget = it })
                }
                if (day.exercises.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) { Text("💪 Main Workout", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    exerciseCards(day.exercises, onLog = { logTarget = it }, onSwap = { swapTarget = it })
                }
                if (day.core.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) { Text("🦾 Core & Abs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    exerciseCards(day.core, onLog = { logTarget = it })
                }
                day.cardio?.let { c ->
                    item(span = { GridItemSpan(2) }) { Text("❤️ Cardio", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    item(span = { GridItemSpan(2) }) { ExerciseGridCard(ex = c, onClick = { logTarget = c }) }
                }
                if (day.cooldown.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) { Text("🧘 Cool-down", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    exerciseCards(day.cooldown, onLog = { logTarget = it })
                }
                item(span = { GridItemSpan(2) }) { RestTimer(Modifier.padding(top = Spacing.xs)) }
            }
        }

        item(span = { GridItemSpan(2) }) { StrengthTrendSection(Modifier.padding(top = Spacing.xs)) }

        // Program info + coaching nudges - below the actual workout, not blocking it.
        plan?.let { p ->
            item(span = { GridItemSpan(2) }) {
                val context = buildString {
                    append(p.blockLabel.ifBlank { "Training block" }).append(" · ").append(p.location).append(" · ").append(p.goal)
                    // The server's note already opens with the block label ("Month 2 · Block B - exercises rotate..."); don't print it twice.
                    p.note?.removePrefix(p.blockLabel)?.trimStart(' ', '-', '·', '—')?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
                }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("🎯", fontSize = 16.sp)
                        Text(context, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Mobility staging - why the plan is diet-first / light-movement instead of standard.
        state.movementStage?.takeIf { it.stage != "full" }?.let { ms ->
            item(span = { GridItemSpan(2) }) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(if (ms.stage == "diet_first") "🍽️" else "🚶", fontSize = 16.sp)
                            Text(
                                if (ms.stage == "diet_first") "Diet-first phase" else "Light-movement phase",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(ms.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ms.resumeAroundWeightKg?.let {
                            Text(
                                "We'll ease in more movement as you approach ~${it.toInt()} kg.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MoveAccent,
                            )
                        }
                    }
                }
            }
        }

        // Split suggestion - nudge from mixed full-body sessions to a body-part split once you're past month 1.
        state.splitSuggestion?.takeIf { it.suggestBodyPartSplit }?.let { ss ->
            item(span = { GridItemSpan(2) }) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("🎯", fontSize = 16.sp)
                            Text("Ready for a body-part split?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        ss.reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        // Level suggestion
        state.levelSuggestion?.takeIf { it.direction == "up" || it.direction == "down" }?.let { ls ->
            item(span = { GridItemSpan(2) }) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(if (ls.direction == "up") "🚀" else "⚠️", fontSize = 16.sp)
                        Column {
                            Text(
                                if (ls.direction == "up") "Ready to Level Up!" else "Consider Easing Down",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                            )
                            Text(ls.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        plan?.disclaimer?.takeIf { it.isNotBlank() }?.let { d ->
            item(span = { GridItemSpan(2) }) {
                Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs))
            }
        }
    }
}

@Composable
private fun SwapExerciseDialog(exercise: ExerciseItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("🔄 Swap · ${exercise.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alternatives — same muscle group", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                exercise.substitutions.forEach { sub ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(Sharp)
                            .background(KaizenLavender.copy(alpha = 0.08f))
                            .padding(Spacing.sm)
                            .semantics { contentDescription = "Alternative: $sub" },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseDemo(name = exercise.name, muscleGroup = exercise.muscleGroup, sizeDp = 30)
                        Text(sub, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ExerciseLibraryTab(modifier: Modifier = Modifier, viewModel: MoveViewModel = hiltViewModel(), planVm: com.nutriai.ui.plan.PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExerciseCatalog.Category.ALL) }
    var equipment by remember { mutableStateOf(ExerciseCatalog.EquipmentFilter.ANY) }
    var logTarget by remember { mutableStateOf<ExerciseItem?>(null) }
    val results = remember(query, category, equipment) { ExerciseCatalog.search(query, category, equipment) }
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
            onPlanTomorrow = { name ->
                planVm.addExerciseToDate(java.time.LocalDate.now().plusDays(1).toString(), name, ex.muscleGroup)
            },
        )
    }

    Column(modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("📚 Exercise Library", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (category == ExerciseCatalog.Category.ALL) {
            // Step 1: just the body pictures. Nothing loads until a muscle is chosen.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MuscleAtlas(selected = category, onSelect = { category = it; query = "" })
            }
            return@Column
        }
        // Step 2: the chosen muscle, with search, equipment and its exercises.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { category = ExerciseCatalog.Category.ALL; query = "" }, shape = Sharp) { Text("← All muscles", style = MaterialTheme.typography.labelMedium) }
            Text("${category.emoji} ${category.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search exercises...", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = Sharp,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Text("🎒 Equipment today", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ExerciseCatalog.equipmentFilters) { eq ->
                FilterChip(
                    selected = equipment == eq,
                    onClick = { equipment = eq },
                    label = { Text("${eq.emoji} ${eq.label}", style = MaterialTheme.typography.labelSmall, fontWeight = if (equipment == eq) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandAmber,
                        selectedLabelColor = Color.White,
                        containerColor = BrandAmber.copy(alpha = 0.08f),
                        labelColor = BrandAmber,
                    ),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
            gridItems(results) { ex ->
                ExerciseGridCard(
                    ex = ex,
                    onClick = { logTarget = ex },
                    isFavorite = ex.name in state.gymFavoriteNames,
                    onToggleFavorite = { viewModel.toggleGymFavorite(ex.name) },
                )
            }
            if (results.isEmpty() && typed.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyState(title = "No match - tap above to log it anyway", emoji = "🤷")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// "My Gym" tab — the exercises the user starred in Library, one page, quick to log.
// ---------------------------------------------------------------------------

@Composable
private fun MyGymTab(
    modifier: Modifier = Modifier,
    onGoToLibrary: () -> Unit,
    viewModel: MoveViewModel = hiltViewModel(),
    planVm: com.nutriai.ui.plan.PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var logTarget by remember { mutableStateOf<ExerciseItem?>(null) }
    val favorites = remember(state.gymFavoriteNames) {
        ExerciseCatalog.all.filter { it.name in state.gymFavoriteNames }
    }

    logTarget?.let { ex ->
        LogExerciseDialog(
            exercise = ex,
            onDismiss = { logTarget = null },
            onConfirm = { sets ->
                viewModel.logSets(ex.name, null, sets)
                logTarget = null
            },
            onPlanTomorrow = { name ->
                planVm.addExerciseToDate(java.time.LocalDate.now().plusDays(1).toString(), name, ex.muscleGroup)
            },
        )
    }

    Column(modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("⭐ My Gym", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            "The equipment at your gym — starred once in Library, logged from here every time.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (favorites.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                EmptyState(title = "No equipment added yet", emoji = "⭐")
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    Modifier.clip(Sharp).background(MoveAccent).clickable(onClick = onGoToLibrary).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text("📚 Go star some in Library", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(favorites) { ex ->
                    ExerciseGridCard(
                        ex = ex,
                        onClick = { logTarget = ex },
                        isFavorite = true,
                        onToggleFavorite = { viewModel.toggleGymFavorite(ex.name) },
                    )
                }
            }
        }
    }
}

/**
 * Renders a list of exercises as 2-column grid cards. When the count is odd, the LAST card spans
 * both columns instead of leaving an empty cell beside it - a lone trailing card in a half-width
 * slot with blank space next to it reads as a layout bug.
 */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.exerciseCards(
    list: List<ExerciseItem>,
    onLog: (ExerciseItem) -> Unit,
    onSwap: ((ExerciseItem) -> Unit)? = null,
) {
    items(
        count = list.size,
        span = { i -> if (i == list.size - 1 && list.size % 2 == 1) GridItemSpan(2) else GridItemSpan(1) },
    ) { i ->
        val ex = list[i]
        ExerciseGridCard(
            ex = ex,
            onClick = { onLog(ex) },
            onSwap = if (onSwap != null && ex.substitutions.isNotEmpty()) ({ onSwap(ex) }) else null,
        )
    }
}

@Composable
private fun ExerciseGridCard(
    ex: ExerciseItem,
    onClick: () -> Unit,
    labelOverride: String? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo && ex.info != null) ExerciseInfoDialog(ex) { showInfo = false }
    Card(
        Modifier.fillMaxWidth().heightIn(min = 110.dp).clickable(onClick = onClick),
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExerciseDemo(name = ex.name, muscleGroup = ex.muscleGroup, sizeDp = 104, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(labelOverride ?: ex.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.padding(end = 20.dp))
                ex.muscleGroup?.takeIf { it.isNotBlank() }?.let { mg ->
                    Text(mg.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                ProgressionChip(ex.nextSession)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(Sharp)
                            .background(MoveAccent)
                            .padding(horizontal = Spacing.sm, vertical = 3.dp),
                    ) {
                        Text("+ Log", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (ex.info != null) {
                        Text(
                            "ℹ️",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clip(Sharp)
                                .clickable { showInfo = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .semantics { contentDescription = "Form guide for ${ex.name}" },
                        )
                    }
                    if (onSwap != null) {
                        Text(
                            "🔄",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clip(Sharp)
                                .clickable(onClick = onSwap)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .semantics { contentDescription = "Swap ${ex.name} for an alternative" },
                        )
                    }
                }
            }
            if (onToggleFavorite != null) {
                Text(
                    if (isFavorite) "⭐" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleFavorite)
                        .padding(6.dp)
                        .semantics { contentDescription = if (isFavorite) "Remove ${ex.name} from My Gym" else "Add ${ex.name} to My Gym" },
                )
            }
        }
    }
}

@Composable
private fun ExerciseInfoDialog(ex: ExerciseItem, onDismiss: () -> Unit) {
    val info = ex.info ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text(ex.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(
                        info.pattern.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
                        info.difficulty.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
                        info.secondary.takeIf { it.isNotEmpty() }?.let { "Also works: " + it.joinToString(", ") },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ex.cue?.takeIf { it.isNotBlank() }?.let { GuideSection("Key cue", it) }
                GuideSection("Setup", info.setup)
                if (info.mistakes.isNotEmpty()) GuideSection("Common mistakes", info.mistakes.joinToString("\n") { "• $it" })
                GuideSection("Safety", info.safety)
                info.regression?.let { GuideSection("Too hard? Try", it) }
                info.progression?.let { GuideSection("Ready for more?", it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun GuideSection(title: String, body: String) {
    if (body.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Surfaces the server's progressive-overload recommendation (already computed in
 * [com.nutriai.data.remote.dto.NextSession], previously only used to silently prefill the log
 * dialog's defaults) directly on the card, so the user actually sees "try 62.5kg × 9" or "deload
 * week" before tapping in - this is the concrete progression signal, not just another exercise.
 * Renders nothing when there's no logged history yet for this exercise.
 */
@Composable
private fun ProgressionChip(nextSession: NextSession?) {
    if (nextSession == null) return
    val label = when {
        nextSession.deload -> "🔄 Deload: ease off this week"
        nextSession.suggestedWeightKg != null -> "🎯 Try ${trimKg(nextSession.suggestedWeightKg)}kg × ${nextSession.suggestedReps}"
        else -> "🎯 Try ${nextSession.suggestedReps} reps × ${nextSession.suggestedSets}"
    }
    val color = if (nextSession.deload) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = Modifier.semantics { contentDescription = nextSession.rationale.ifBlank { label } },
    )
}

private fun isTimed(ex: ExerciseItem): Boolean =
    Regex("""min|\d+\s*s\b""", RegexOption.IGNORE_CASE).containsMatchIn(ex.reps)

/** Strength-style movements that merely contain a cardio word ("Walking Lunge", "Jump Squat", "Row"). */
private val STRENGTH_WORDS = Regex("""lunge|squat|press|curl|raise|deadlift|extension|fly|pulldown|pull-up|pullup|push-up|pushup|dip|shrug|crunch|kickback|thrust|bridge""", RegexOption.IGNORE_CASE)
private val CARDIO_ACTIVITY = Regex("""\b(walk|walking|run|running|jog|jogging|hike|hiking|cycle|cycling|bike|biking|spin|treadmill|elliptical|stepmill|stair ?climber|swim|swimming|skipping|jump rope|rowing machine|cross trainer)\b""", RegexOption.IGNORE_CASE)
private val STRETCH_ACTIVITY = Regex("""\b(stretch|stretching|yoga|pose|mobility|foam roll)\b""", RegexOption.IGNORE_CASE)

/**
 * A logged/typed exercise is judged by its NAME, not just its reps text: "walk" must be minutes (never
 * 3 sets x reps x kg), and a stretch is a hold in seconds. Exercises that already carry a time-based
 * reps value are left exactly as they are.
 */
private fun normalizeTimed(ex: ExerciseItem): ExerciseItem {
    if (isTimed(ex) || STRENGTH_WORDS.containsMatchIn(ex.name)) return ex
    return when {
        CARDIO_ACTIVITY.containsMatchIn(ex.name) -> ex.copy(sets = 1, reps = "20 min", type = "cardio")
        STRETCH_ACTIVITY.containsMatchIn(ex.name) -> ex.copy(sets = 2, reps = "45 s", type = "flexibility")
        else -> ex
    }
}

private fun isWeighted(ex: ExerciseItem): Boolean =
    !isTimed(ex) && ex.type == "strength" && ex.equipment != "bodyweight"

/** Treadmill/walk/run/cycle - exercises where speed+incline+distance sharpen the calorie estimate. */
private fun isSpeedBased(ex: ExerciseItem): Boolean =
    Regex("""treadmill|walk|run|jog|hike|cycl|bike|spin""", RegexOption.IGNORE_CASE).containsMatchIn(ex.name)

private fun isTreadmill(ex: ExerciseItem): Boolean =
    Regex("""treadmill""", RegexOption.IGNORE_CASE).containsMatchIn(ex.name)

data class LoggedSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    /** Reps in reserve (0 = failure); null = not reported. */
    val rir: Int? = null,
    val durationMin: Int? = null,
    val note: String? = null,
    val speedKmh: Double? = null,
    val inclinePct: Double? = null,
    val distanceKm: Double? = null,
)

private class SetRow(amount: String, weight: String) {
    var amount by mutableStateOf(amount)
    var weight by mutableStateOf(weight)
}

@Composable
private fun LogExerciseDialog(exercise: ExerciseItem, onDismiss: () -> Unit, onConfirm: (List<LoggedSet>) -> Unit, onPlanTomorrow: ((String) -> Unit)? = null) {
    val ex = normalizeTimed(exercise)
    val timed = isTimed(ex)
    val weighted = isWeighted(ex)
    val ns = ex.nextSession
    val defaultReps = (ns?.suggestedReps ?: firstInt(ex.reps))?.toString() ?: ""
    val defaultWeight = ns?.suggestedWeightKg?.let { trimKg(it) } ?: ""
    val singleBlock = timed && ex.sets <= 1
    val defaultCount = if (singleBlock) 1 else (ns?.suggestedSets ?: ex.sets).coerceIn(1, 10)
    val speedBased = singleBlock && isSpeedBased(ex)

    var addToPlan by remember { mutableStateOf(false) }
    var rir by remember { mutableStateOf<Int?>(null) }
    var speedKmh by remember(ex.name) { mutableStateOf("") }
    var inclinePct by remember(ex.name) { mutableStateOf("") }
    var distanceKm by remember(ex.name) { mutableStateOf("") }

    val rows = remember(ex.name) {
        mutableStateListOf<SetRow>().also { list ->
            val amount = if (timed && !singleBlock) "45" else if (timed) estimateMinutes(ex.reps, 2).toString() else defaultReps
            repeat(defaultCount) { list.add(SetRow(amount, defaultWeight)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("🏋️ Log · ${ex.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        singleBlock -> "⏱️ How long did you go?"
                        timed -> "⏱️ Seconds held per set"
                        else -> "💪 Reps per set - edit any that differed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { i, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(MoveAccent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (singleBlock) "•" else "${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 10.sp,
                            )
                        }
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                        )
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { v -> row.amount = v.filter { c -> c.isDigit() } },
                            label = { Text(if (singleBlock) "Min" else if (timed) "Sec" else "Reps", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = Sharp,
                            colors = fieldColors,
                        )
                        if (weighted) {
                            OutlinedTextField(
                                value = row.weight,
                                onValueChange = { v -> row.weight = v.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("kg", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = Sharp,
                                colors = fieldColors,
                            )
                        }
                        if (rows.size > 1) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove set ${i + 1}",
                                tint = KaizenCoral,
                                modifier = Modifier.clickable { rows.removeAt(i) }.padding(4.dp).size(16.dp),
                            )
                        }
                    }
                }
                if (!singleBlock) {
                    TextButton(onClick = { rows.add(SetRow(rows.lastOrNull()?.amount ?: "", rows.lastOrNull()?.weight ?: "")) }) {
                        Text("+ Add set", color = MoveAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Speed/incline/distance - only for treadmill/walk/run/cycle, sharpens the calorie
                // estimate instead of treating every pace the same (dynamic logging per ex type).
                if (speedBased) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                    Text("🏃 Speed & terrain (optional, sharpens kcal estimate)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = speedKmh,
                            onValueChange = { v -> speedKmh = v.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("km/h", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = Sharp,
                            colors = fieldColors,
                        )
                        if (isTreadmill(ex)) {
                            OutlinedTextField(
                                value = inclinePct,
                                onValueChange = { v -> inclinePct = v.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Incline (%)", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = Sharp,
                                colors = fieldColors,
                            )
                        }
                        OutlinedTextField(
                            value = distanceKm,
                            onValueChange = { v -> distanceKm = v.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Distance (km)", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = Sharp,
                            colors = fieldColors,
                        )
                    }
                }

                // How hard was it? Reps in reserve lets the app decide whether to add weight or hold it.
                if (weighted && !timed && !singleBlock) {
                    var rirOpen by remember { mutableStateOf(false) }
                    val rirOptions = listOf<Pair<Int?, String>>(null to "Not sure", 0 to "Failure (0 left)", 1 to "1 rep left", 2 to "2 reps left", 3 to "3+ reps left")
                    Box {
                        OutlinedButton(onClick = { rirOpen = true }, shape = Sharp, modifier = Modifier.fillMaxWidth()) {
                            Text("Effort: " + (rirOptions.first { it.first == rir }.second), style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = rirOpen, onDismissRequest = { rirOpen = false }) {
                            rirOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = { if (rir == value) Text("✓", fontWeight = FontWeight.Bold) },
                                    onClick = { rir = value; rirOpen = false },
                                )
                            }
                        }
                    }
                }

                if (!timed) RestTimer(compact = true)

                // Plan tomorrow option
                if (onPlanTomorrow != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { addToPlan = !addToPlan },
                    ) {
                        Checkbox(checked = addToPlan, onCheckedChange = { addToPlan = it })
                        Text("📅 Also add to tomorrow's plan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val out = rows.mapNotNull { r ->
                        val n = r.amount.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                        when {
                            singleBlock -> LoggedSet(
                                durationMin = n,
                                note = "$n min",
                                speedKmh = if (speedBased) speedKmh.toDoubleOrNull() else null,
                                inclinePct = if (speedBased) inclinePct.toDoubleOrNull() else null,
                                distanceKm = if (speedBased) distanceKm.toDoubleOrNull() else null,
                            )
                            timed -> LoggedSet(durationMin = maxOf(1, Math.round(n / 60.0).toInt()), note = "${n}s")
                            else -> LoggedSet(weightKg = r.weight.toDoubleOrNull(), reps = n, rir = if (weighted) rir else null)
                        }
                    }
                    if (out.isNotEmpty()) {
                        onConfirm(out)
                        if (addToPlan) onPlanTomorrow?.invoke(ex.name)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MoveAccent),
                shape = Sharp,
            ) { Text("✅ Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun trimKg(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun firstInt(reps: String): Int? = Regex("\\d+").find(reps)?.value?.toIntOrNull()

@Composable
/** Muscle-group emoji for a workout day's focus text (e.g. "Chest", "Back & Biceps", "Legs & Abs"). */
private fun focusEmoji(focus: String): String {
    val f = focus.lowercase()
    return when {
        "chest" in f -> "💥"
        "back" in f -> "🔙"
        "shoulder" in f -> "🎯"
        "bicep" in f || "tricep" in f || "arm" in f -> "💪"
        "leg" in f || "quad" in f || "hamstring" in f || "calf" in f -> "🦵"
        "glute" in f -> "🍑"
        "core" in f || "ab" in f -> "🧱"
        "cardio" in f || "hiit" in f || "condition" in f -> "🏃"
        "mobility" in f || "stretch" in f || "yoga" in f -> "🧘"
        "full" in f || "push" in f || "pull" in f -> "🔥"
        else -> "🏋️"
    }
}

private val WeekdayAbbrev = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
private fun WeekStrip(days: List<WorkoutDay>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.take(7).forEachIndexed { i, d ->
            val rest = d.rest
            val selected = i == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Sharp)
                    .background(
                        when {
                            selected -> MoveAccent
                            rest -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else -> MoveAccent.copy(alpha = 0.1f)
                        },
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    if (rest) "💤" else focusEmoji(d.focus),
                    fontSize = 13.sp,
                )
                Text(
                    WeekdayAbbrev.getOrElse(i) { "D${d.dayIndex + 1}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
