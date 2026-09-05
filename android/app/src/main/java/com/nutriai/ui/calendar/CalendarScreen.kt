package com.nutriai.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Adaptation
import com.nutriai.data.remote.dto.DayPlan
import com.nutriai.data.remote.dto.ExerciseLogDto
import com.nutriai.data.remote.dto.ExerciseLogRequest
import com.nutriai.data.remote.dto.Guidance
import com.nutriai.data.remote.dto.Meditation
import com.nutriai.data.remote.dto.Recipe
import com.nutriai.data.remote.dto.YogaFlow
import com.nutriai.data.remote.dto.LastPerformance
import com.nutriai.data.remote.dto.WorkoutDay
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.Status
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.RecoveryColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.kaizenColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---- State ----
data class CalendarState(
    val loading: Boolean = true,
    val dietError: String? = null,
    val workoutError: String? = null,
    val dietDays: List<DayPlan> = emptyList(),
    val workoutDays: List<WorkoutDay> = emptyList(),
    val workoutBlockLabel: String? = null,
    val selectedDate: String? = null,
    /** Most recent performed set per exercise name - progression hints. */
    val lastPerf: Map<String, LastPerformance> = emptyMap(),
    /** Exercise logs the user recorded on the selected day. */
    val selectedLogs: List<ExerciseLogDto> = emptyList(),
    /** Adaptive coaching insight from recent logging + weight trend. */
    val adaptation: Adaptation? = null,
    val applying: Boolean = false,
    val recipeLoading: Boolean = false,
    val recipe: com.nutriai.data.remote.dto.Recipe? = null,
    /** Yoga cool-down + breathing shown inline in the workout, like the exercises. */
    val yoga: com.nutriai.data.remote.dto.YogaFlow? = null,
    val meditation: com.nutriai.data.remote.dto.Meditation? = null,
    /** Today's mood (1 low .. 5 great) - drives the yoga/meditation pick alongside phase + health. */
    val mood: Int? = null,
    val wellnessReason: String? = null,
    /** Personalized diet + exercise guidance from conditions / sex / lifestyle. */
    val guidance: com.nutriai.data.remote.dto.Guidance? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val currentMood = _state.value.mood
        _state.value = CalendarState(loading = true, mood = currentMood)
        viewModelScope.launch {
            val plan = repository.latestPlan()
            val workout = repository.exercisePlan()
            val lastPerf = repository.lastPerformance().getOrDefault(emptyMap())
            val adaptation = repository.adaptation().getOrNull()
            val guidance = repository.guidance().getOrNull()
            // Phase + mood + health -> the day's yoga & meditation.
            val rec = repository.recommendWellness(currentMood).getOrNull()
            val yoga = rec?.yoga
            val meditation = rec?.meditation

            val dietDays = plan.getOrNull()?.days.orEmpty()
            val workoutPlan = workout.getOrNull()
            val workoutDays = workoutPlan?.days.orEmpty()

            // Default the selection to the "Today" diet day when present,
            // otherwise the first day, otherwise a "Today" workout day.
            val defaultDate = dietDays.firstOrNull { it.label == "Today" }?.date
                ?: dietDays.firstOrNull { it.date != null }?.date
                ?: workoutDays.firstOrNull { it.label == "Today" }?.date
                ?: workoutDays.firstOrNull { it.date != null }?.date

            val logs = defaultDate?.let { repository.exerciseLogs(it).getOrDefault(emptyList()) }.orEmpty()

            _state.value = CalendarState(
                loading = false,
                dietError = plan.exceptionOrNull()?.message,
                workoutError = workout.exceptionOrNull()?.message,
                dietDays = dietDays,
                workoutDays = workoutDays,
                workoutBlockLabel = workoutPlan?.blockLabel?.takeIf { it.isNotBlank() },
                selectedDate = defaultDate,
                lastPerf = lastPerf,
                selectedLogs = logs,
                adaptation = adaptation,
                guidance = guidance,
                yoga = yoga,
                meditation = meditation,
                mood = currentMood,
                wellnessReason = rec?.reason,
            )
        }
    }

    /** Sets today's mood and re-picks the yoga + meditation to match it. */
    fun setMood(m: Int) {
        _state.value = _state.value.copy(mood = m)
        viewModelScope.launch {
            val rec = repository.recommendWellness(m).getOrNull()
            _state.value = _state.value.copy(
                yoga = rec?.yoga ?: _state.value.yoga,
                meditation = rec?.meditation ?: _state.value.meditation,
                wellnessReason = rec?.reason ?: _state.value.wellnessReason,
            )
        }
    }

    fun loadRecipe(name: String, foodId: String?) {
        _state.value = _state.value.copy(recipeLoading = true, recipe = null)
        viewModelScope.launch {
            val r = repository.recipe(name, foodId).getOrNull()
            _state.value = _state.value.copy(recipeLoading = false, recipe = r)
        }
    }

    fun clearRecipe() { _state.value = _state.value.copy(recipe = null, recipeLoading = false) }

    /** Swaps a single meal in the plan for a different dish at similar calories. */
    fun swapMeal(dayIndex: Int, slot: String) {
        viewModelScope.launch {
            repository.swapMeal(dayIndex, slot).getOrNull()?.let { plan ->
                _state.value = _state.value.copy(dietDays = plan.days)
            }
        }
    }

    /** Applies the coach's recommendation: rebuilds the plan (at the adjusted target). */
    fun applyAdaptation() {
        _state.value = _state.value.copy(applying = true)
        viewModelScope.launch {
            repository.applyAdaptation()
            val plan = repository.latestPlan().getOrNull()
            val adaptation = repository.adaptation().getOrNull()
            _state.value = _state.value.copy(
                applying = false,
                dietDays = plan?.days.orEmpty(),
                adaptation = adaptation,
            )
        }
    }

    fun selectDate(date: String?) {
        _state.value = _state.value.copy(selectedDate = date, selectedLogs = emptyList())
        viewModelScope.launch {
            val logs = date?.let { repository.exerciseLogs(it).getOrDefault(emptyList()) }.orEmpty()
            if (_state.value.selectedDate == date) _state.value = _state.value.copy(selectedLogs = logs)
        }
    }

    /** Records a performed set against the selected day, then refreshes logs + progression. */
    fun logExercise(name: String, focus: String?, sets: Int?, reps: Int?, weightKg: Double?) {
        val date = _state.value.selectedDate
        val performedAt = date?.let { "${it}T12:00:00.000Z" }
        viewModelScope.launch {
            repository.logExercise(
                ExerciseLogRequest(
                    exerciseName = name,
                    focus = focus,
                    sets = sets,
                    reps = reps,
                    weightKg = weightKg,
                    performedAt = performedAt,
                ),
            )
            val logs = date?.let { repository.exerciseLogs(it).getOrDefault(emptyList()) }.orEmpty()
            val lastPerf = repository.lastPerformance().getOrDefault(emptyMap())
            _state.value = _state.value.copy(selectedLogs = logs, lastPerf = lastPerf)
        }
    }

    fun deleteLog(id: String) {
        val date = _state.value.selectedDate
        viewModelScope.launch {
            repository.deleteExerciseLog(id)
            val logs = date?.let { repository.exerciseLogs(it).getOrDefault(emptyList()) }.orEmpty()
            _state.value = _state.value.copy(selectedLogs = logs)
        }
    }

    fun regenerate() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            repository.generatePlan()
            load()
        }
    }
}

/** Which exercise the log dialog is currently open for, with sensible pre-filled values. */
private data class PendingLog(
    val name: String,
    val focus: String?,
    val weight: String,
    val reps: String,
    val sets: String,
)

// ---- UI ----
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingLog?>(null) }
    var showWellness by remember { mutableStateOf(false) }

    var activeMed by remember { mutableStateOf<com.nutriai.data.remote.dto.Meditation?>(null) }

    // System back closes an open session/library overlay instead of leaving the Plan tab.
    androidx.activity.compose.BackHandler(enabled = activeMed != null || showWellness) {
        if (activeMed != null) activeMed = null else showWellness = false
    }

    // A guided breathing session opens in-place (voice-guided), then returns to the plan.
    activeMed?.let { med ->
        com.nutriai.ui.wellness.MeditationSession(med) { activeMed = null }
        return
    }

    // Full yoga/meditation library (optional "see all"), opened in-place from the Plan tab.
    if (showWellness) {
        Column(modifier.fillMaxSize()) {
            TextButton(onClick = { showWellness = false }, modifier = Modifier.padding(4.dp)) { Text("← Back to plan") }
            com.nutriai.ui.wellness.WellnessScreen(Modifier.fillMaxSize())
        }
        return
    }

    pending?.let { p ->
        LogDialog(
            pending = p,
            onDismiss = { pending = null },
            onConfirm = { weight, reps, sets ->
                viewModel.logExercise(p.name, p.focus, sets, reps, weight)
                pending = null
            },
        )
    }

    if (state.recipeLoading || state.recipe != null) {
        RecipeDialog(loading = state.recipeLoading, recipe = state.recipe, onDismiss = { viewModel.clearRecipe() })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.kaizenColors.pageBackground)
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        // Day navigation: prev / next steps through the same dates the week strip below shows.
        if (state.dietDays.isNotEmpty() || state.workoutDays.isNotEmpty()) {
            item {
                val dates = (state.dietDays.mapNotNull { it.date } + state.workoutDays.mapNotNull { it.date }).distinct().sorted()
                val idx = dates.indexOf(state.selectedDate)
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (idx > 0) viewModel.selectDate(dates[idx - 1]) }, enabled = idx > 0) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = BrandGreen)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("📅", style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.dietDays.firstOrNull { it.date == state.selectedDate }?.label
                                    ?: state.workoutDays.firstOrNull { it.date == state.selectedDate }?.label
                                    ?: "Today",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandGreen,
                            )
                        }
                        IconButton(onClick = { if (idx in 0 until dates.lastIndex) viewModel.selectDate(dates[idx + 1]) }, enabled = idx in 0 until dates.lastIndex) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day", tint = BrandGreen)
                        }
                    }
                }
            }
        }
        item {
            PrimaryButton(
                text = if (state.dietDays.isEmpty()) "✨ Generate my 7-day plan" else "🔄 Regenerate week",
                onClick = { viewModel.regenerate() },
                modifier = Modifier.fillMaxWidth(),
                containerColor = BrandGreen,
            )
        }

        state.adaptation?.takeIf { it.status != "insufficient_data" }?.let { adapt ->
            item {
                AdaptiveInsightCard(
                    adaptation = adapt,
                    applying = state.applying,
                    onApply = { viewModel.applyAdaptation() },
                )
            }
        }

        state.guidance?.takeIf { it.dietTips.isNotEmpty() || it.exerciseTips.isNotEmpty() }?.let { g ->
            item { GuidanceCard(g) }
        }

        // Menstrual-cycle section (renders only for female profiles).
        item { com.nutriai.ui.cycle.CycleSection() }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }
        }

        if (!state.loading && state.dietDays.isEmpty() && state.workoutDays.isEmpty()) {
            item {
                val hasError = state.dietError != null || state.workoutError != null
                EmptyState(
                    title = "Nothing scheduled yet",
                    emoji = "📅",
                    message = if (hasError) {
                        "First finish your health profile (Home tab → Complete profile). Then tap Generate to build your week."
                    } else {
                        "Tap Generate to build your personalised 7-day plan."
                    },
                )
            }
        }

        // Week strip built from the diet-plan days.
        if (state.dietDays.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(state.dietDays) { day ->
                        DayPill(
                            label = day.label,
                            date = day.date,
                            isToday = day.label == "Today",
                            isSelected = day.date != null && day.date == state.selectedDate,
                            onClick = { day.date?.let { viewModel.selectDate(it) } },
                        )
                    }
                }
            }
        }

        // Selected day's combined report.
        val selected = state.selectedDate
        val dietDay = state.dietDays.firstOrNull { it.date != null && it.date == selected }
        val workoutDay = state.workoutDays.firstOrNull { it.date != null && it.date == selected }

        if (!state.loading && (dietDay != null || workoutDay != null)) {
            item {
                // Full weekday + date (e.g. "Wednesday, 22 Jul 2026"), plus any Fasting note.
                val header = selected?.let {
                    runCatching {
                        java.time.LocalDate.parse(it).format(
                            java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", java.util.Locale.getDefault()),
                        )
                    }.getOrNull()
                } ?: (dietDay?.label ?: workoutDay?.label ?: "Selected day")
                val fasting = (dietDay?.label ?: workoutDay?.label)?.contains("Fasting", ignoreCase = true) == true
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(if (fasting) "🌙" else "☀️", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (fasting) "$header · Fasting day" else header,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandGreen,
                        )
                    }
                }
            }

            // Diet section.
            item { SectionHeader(title = "Diet", emoji = "🍲") }
            if (dietDay == null || dietDay.meals.isEmpty()) {
                item {
                    Text(
                        state.dietError ?: "No meals planned for this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(dietDay.meals) { meal ->
                    FeatureCard(
                        emoji = when (meal.slot.lowercase()) {
                            "breakfast" -> "🍳"
                            "lunch" -> "🍛"
                            "dinner" -> "🍝"
                            "snack", "snacks" -> "🍎"
                            else -> "🍽️"
                        },
                        title = meal.slot.replaceFirstChar { it.uppercase() },
                        accentColor = when (meal.slot.lowercase()) {
                            "breakfast" -> BrandAmber
                            "lunch" -> NutritionColor
                            "dinner" -> KaizenLavender
                            "snack", "snacks" -> KaizenCoral
                            else -> KaizenBlue
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    "🔀 Swap",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KaizenCoral,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { viewModel.swapMeal(dietDay.dayIndex, meal.slot) },
                                )
                            }
                            // Condition-friendliness highlights (Diabetes-friendly, Gut-friendly...).
                            val highlights = meal.friendliness?.highlights.orEmpty()
                            if (highlights.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    highlights.forEach { h ->
                                        StatusIndicator(text = h, status = Status.Positive)
                                    }
                                }
                            }
                            meal.items.forEachIndexed { i, mealItem ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    EmojiBadge(emoji = "🍚", bgColor = NutritionColor.copy(alpha = 0.15f), size = 32.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(mealItem.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("${mealItem.grams.toInt()} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "${mealItem.kcal.toInt()} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandGreen,
                                    )
                                    Icon(
                                        Icons.Filled.MenuBook,
                                        contentDescription = "View recipe for ${mealItem.name}",
                                        tint = KaizenBlue,
                                        modifier = Modifier.size(22.dp).clickable { viewModel.loadRecipe(mealItem.name, mealItem.foodId) },
                                    )
                                }
                                if (i != meal.items.lastIndex) HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
                            }
                        }
                    }
                }
                item {
                    GlassCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            com.nutriai.ui.components.MetricBlock(
                                label = "Calories",
                                value = "${dietDay.totals.kcal.toInt()}",
                                unit = "kcal",
                                color = BrandGreen,
                            )
                            com.nutriai.ui.components.MetricBlock(
                                label = "Protein",
                                value = "${dietDay.totals.proteinG.toInt()}",
                                unit = "g",
                                color = KaizenBlue,
                            )
                        }
                    }
                }
            }
        }


        item {
            Text(
                "Educational guidance, not medical advice – consult a professional.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun RecipeDialog(loading: Boolean, recipe: Recipe?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("📖", style = MaterialTheme.typography.titleMedium)
                Text(recipe?.title?.ifBlank { "Recipe" } ?: "Recipe", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            when {
                loading -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = BrandGreen) }
                recipe != null -> Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val meta = listOfNotNull(
                        recipe.timeMin?.let { "⏱ $it min" },
                        recipe.servings?.let { "🍽 $it servings" },
                    ).joinToString("   ·   ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (recipe.ingredients.isNotEmpty()) {
                        SectionHeader(title = "Ingredients", emoji = "🧂")
                        recipe.ingredients.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (recipe.steps.isNotEmpty()) {
                        SectionHeader(title = "Steps", emoji = "👨‍🍳")
                        recipe.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
                    }
                    recipe.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                else -> Text("Couldn't load a recipe. Try again.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun GuidanceCard(g: Guidance) {
    var expanded by remember { mutableStateOf(false) }
    FeatureCard(
        emoji = "💡",
        title = "Personalized for you",
        accentColor = KaizenLavender,
        onClick = { expanded = !expanded },
    ) {
        Text(
            g.summary.ifBlank { "Diet & exercise tips for your profile" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Spacer(Modifier.height(Spacing.sm))
            if (g.dietTips.isNotEmpty()) {
                SectionHeader(title = "Diet", emoji = "🥗")
                g.dietTips.forEach { tip ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(emoji = "✅", bgColor = NutritionColor.copy(alpha = 0.15f), size = 24.dp)
                        Text(tip, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (g.exerciseTips.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                SectionHeader(title = "Exercise", emoji = "🏋️")
                g.exerciseTips.forEach { tip ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(emoji = "💪", bgColor = MovementColor.copy(alpha = 0.15f), size = 24.dp)
                        Text(tip, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Educational guidance, not medical advice.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Tap to see your diet & exercise tips ▼",
                style = MaterialTheme.typography.labelSmall,
                color = KaizenLavender,
            )
        }
    }
}

@Composable
private fun AdaptiveInsightCard(
    adaptation: Adaptation,
    applying: Boolean,
    onApply: () -> Unit,
) {
    val onTrack = adaptation.status == "on_track"
    FeatureCard(
        emoji = if (onTrack) "🎯" else "🧭",
        title = if (onTrack) "Coach: on track" else "Coach insight",
        accentColor = if (onTrack) NutritionColor else BrandAmber,
    ) {
        Text(adaptation.message, style = MaterialTheme.typography.bodyMedium)
        if (adaptation.status == "adjust_target") {
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(
                text = if (applying) "Applying..." else run {
                    val sign = if (adaptation.suggestedKcalDelta >= 0) "+" else ""
                    "Apply ($sign${adaptation.suggestedKcalDelta} kcal) & rebuild plan"
                },
                onClick = onApply,
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
                containerColor = BrandAmber,
            )
        }
    }
}

@Composable
private fun WorkoutWellnessCard(
    yoga: YogaFlow?,
    meditation: Meditation?,
    mood: Int?,
    reason: String?,
    onMood: (Int) -> Unit,
    onStartMeditation: (Meditation) -> Unit,
) {
    FeatureCard(
        emoji = "🧘",
        title = "Yoga & Meditation",
        accentColor = RecoveryColor,
    ) {
        // Mood selector - tune the session to how you feel.
        Text("How's your mood today?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val faces = listOf(1 to "😣", 2 to "😕", 3 to "😐", 4 to "🙂", 5 to "😄")
            faces.forEach { (value, face) ->
                val selected = mood == value
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (selected) RecoveryColor else RecoveryColor.copy(alpha = 0.08f))
                        .clickable { onMood(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(face, style = MaterialTheme.typography.titleMedium) }
            }
        }
        reason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        yoga?.let { flow ->
            Spacer(Modifier.height(Spacing.sm))
            SectionHeader(title = "Cool-down · ${flow.name}", emoji = "🧘‍♀️")
            flow.poses.take(5).forEach { p ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(emoji = "🌿", bgColor = RecoveryColor.copy(alpha = 0.15f), size = 24.dp)
                    Text("${p.name} – ${p.hold}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        meditation?.let { med ->
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(med.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${med.durationMin} min guided breathing (voice)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { onStartMeditation(med) }) { Text("▶ Start") }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    index: Int,
    name: String,
    prescription: String,
    last: LastPerformance?,
    done: Boolean,
    onLog: () -> Unit,
) {
    GlassCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Number / done badge.
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (done) BrandGreen else MovementColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✓" else "$index",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (done) Color.White else MovementColor,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                // Sets x reps pill + optional last-performance hint.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        prescription,
                        Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(MovementColor.copy(alpha = 0.14f))
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MovementColor,
                    )
                    last?.weightKg?.let {
                        val hint = buildString {
                            append("last ${fmtNum(it)} kg")
                            last.reps?.let { r -> append(" × $r") }
                        }
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = onLog, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text(if (done) "Again" else "📝 Log")
            }
        }
    }
}

@Composable
private fun LogDialog(
    pending: PendingLog,
    onDismiss: () -> Unit,
    onConfirm: (weightKg: Double?, reps: Int?, sets: Int?) -> Unit,
) {
    var weight by remember(pending) { mutableStateOf(pending.weight) }
    var reps by remember(pending) { mutableStateOf(pending.reps) }
    var sets by remember(pending) { mutableStateOf(pending.sets) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("🏋️", style = MaterialTheme.typography.titleMedium)
                Text("Log ${pending.name}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("What did you actually do? Leave blank what doesn't apply.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Weight (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it.filter { c -> c.isDigit() } },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter { c -> c.isDigit() } },
                    label = { Text("Reps (per set)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(weight.toDoubleOrNull(), reps.toIntOrNull(), sets.toIntOrNull())
            }) { Text("✅ Save", color = BrandGreen, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Formats a Double without a trailing ".0" (40 not 40.0). */
private fun fmtNum(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
private fun DayPill(
    label: String?,
    date: String?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val dayNumber = date?.substringAfterLast('-')?.trimStart('0')?.ifBlank { "0" } ?: "-"
    // Always show the real weekday (Mon/Tue...) from the date, not Yesterday/Today/Tomorrow.
    val weekday = date?.let {
        runCatching {
            java.time.LocalDate.parse(it)
                .dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        }.getOrNull()
    } ?: label
    val container = when {
        isSelected -> BrandGreen
        isToday -> NutritionColor.copy(alpha = 0.15f)
        else -> MaterialTheme.kaizenColors.elevatedSurface
    }
    val content = when {
        isSelected -> Color.White
        isToday -> BrandGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.width(74.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                weekday ?: "-",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = content,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                dayNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isToday) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else BrandGreen),
                )
            }
        }
    }
}
