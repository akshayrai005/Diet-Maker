package com.nutriai.ui.calendar

import com.nutriai.ui.components.BorderedTable
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
import androidx.compose.ui.unit.sp
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
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDark
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
            // Prefix match, not equality: a period day relabels the workout day to "Today · Period" (still today).
            val defaultDate = dietDays.firstOrNull { it.label?.startsWith("Today") == true }?.date
                ?: dietDays.firstOrNull { it.date != null }?.date
                ?: workoutDays.firstOrNull { it.label?.startsWith("Today") == true }?.date
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
            repository.generatePlan(days = 2) // just today and tomorrow
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

/** Human-readable meal slot name (server sends compact slugs like "eveningsnack", "midmorning"). */
private fun mealSlotLabel(slot: String): String = when (slot.lowercase()) {
    "wakeup" -> "Wake-up"
    "breakfast" -> "Breakfast"
    "midmorning" -> "Mid-morning"
    "lunch" -> "Lunch"
    "eveningsnack" -> "Evening Snack"
    "dinner" -> "Dinner"
    "bedtime" -> "Bedtime"
    else -> slot.replaceFirstChar { it.uppercase() }
}

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
    var detailItem by remember { mutableStateOf<com.nutriai.data.remote.dto.MealItem?>(null) }

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

    detailItem?.let { mi ->
        FoodDetailDialog(item = mi, onRecipe = { viewModel.loadRecipe(mi.name, mi.foodId); detailItem = null }, onDismiss = { detailItem = null })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.kaizenColors.pageBackground)
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        // Regenerate button — only full-width when there's no plan yet; once a plan exists,
        // a compact action next to the Diet header (below) covers it instead.
        if (state.dietDays.isEmpty()) {
            item {
                PrimaryButton(
                    text = "✨ Generate my 7-day plan",
                    onClick = { viewModel.regenerate() },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = BrandGreen,
                )
            }
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

        // Selected day's combined report.
        val selected = state.selectedDate
        val dietDay = state.dietDays.firstOrNull { it.date != null && it.date == selected }
        val workoutDay = state.workoutDays.firstOrNull { it.date != null && it.date == selected }

        // Week strip + Diet header, grouped in one card instead of floating on the page background.
        if (state.dietDays.isNotEmpty() || (!state.loading && (dietDay != null || workoutDay != null))) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (state.dietDays.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                state.dietDays.filter { it.label == "Today" || it.label == "Tomorrow" }.forEach { day ->
                                    DayPill(
                                        label = day.label,
                                        date = day.date,
                                        isToday = day.label == "Today",
                                        isSelected = day.date != null && day.date == state.selectedDate,
                                        onClick = { day.date?.let { viewModel.selectDate(it) } },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        if (!state.loading && (dietDay != null || workoutDay != null)) {
                            // One tidy row: title on the left, a coloured Regenerate pill on the right.
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("🍲 Suggested Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text("What to eat - log real meals in Log", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(com.nutriai.ui.theme.SpectrumBrush)
                                        .clickable { viewModel.regenerate() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) { Text("🔄 Regenerate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White) }
                            }
                        }
                    }
                }
            }
        }

        if (!state.loading && (dietDay != null || workoutDay != null)) {
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
                    val mealEmoji = when (meal.slot.lowercase()) {
                        "breakfast" -> "🍳"; "lunch" -> "🍛"; "dinner" -> "🍝"
                        "snack", "snacks" -> "🍎"; else -> "🍽️"
                    }
                    val mealColor = when (meal.slot.lowercase()) {
                        "breakfast" -> BrandAmber; "lunch" -> NutritionColor
                        "dinner" -> KaizenLavender; "snack", "snacks" -> KaizenCoral; else -> KaizenBlue
                    }
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Text(mealEmoji, style = MaterialTheme.typography.titleMedium)
                                    Text(mealSlotLabel(meal.slot), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = mealColor)
                                }
                                Text("🔀 Swap", style = MaterialTheme.typography.labelMedium, color = KaizenCoral, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { viewModel.swapMeal(dietDay.dayIndex, meal.slot) })
                            }
                            val highlights = meal.friendliness?.highlights.orEmpty()
                            if (highlights.isNotEmpty()) {
                                Spacer(Modifier.height(Spacing.xs))
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    highlights.forEach { h -> StatusIndicator(text = h, status = Status.Positive) }
                                }
                            }
                            Spacer(Modifier.height(Spacing.sm))
                            // Table header — 3 columns only
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(mealColor.copy(alpha = 0.1f)).padding(horizontal = Spacing.sm, vertical = 6.dp)) {
                                Text("Item", Modifier.weight(3f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = mealColor)
                                Text("Amount", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = mealColor, textAlign = TextAlign.End)
                                Text("Cal", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = mealColor, textAlign = TextAlign.End)
                            }
                            meal.items.forEachIndexed { i, mi ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { detailItem = mi }.padding(horizontal = Spacing.sm, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(mi.name, Modifier.weight(3f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                                    Text("${mi.grams.toInt()} g", Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${mi.kcal.toInt()}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BrandGreen, textAlign = TextAlign.End)
                                }
                                if (i != meal.items.lastIndex) HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
                            }
                            // Meal total row
                            HorizontalDivider(color = mealColor.copy(alpha = 0.3f), thickness = 1.dp)
                            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp)) {
                                Text("Total", Modifier.weight(3f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1.2f))
                                Text("${meal.items.sumOf { it.kcal }.toInt()}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BrandGreen, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
                // Plan totals — compact row matching dashboard card style. These are the SUGGESTED
                // plan's totals, not what's actually been logged (see the note above).
                item {
                    Text("Planned total for today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DayTotalTile(Modifier.weight(1f), "🔥", "${dietDay.totals.kcal.toInt()}", "kcal", BrandGreen, com.nutriai.ui.theme.CardGreenLight)
                        DayTotalTile(Modifier.weight(1f), "💪", "${dietDay.totals.proteinG.toInt()}g", "Protein", NutritionColor, com.nutriai.ui.theme.CardGreenLight)
                        DayTotalTile(Modifier.weight(1f), "🌾", "${dietDay.totals.carbG.toInt()}g", "Carbs", BrandAmber, com.nutriai.ui.theme.CardAmberLight)
                        DayTotalTile(Modifier.weight(1f), "🥑", "${dietDay.totals.fatG.toInt()}g", "Fat", KaizenCoral, com.nutriai.ui.theme.CardCoralLight)
                        DayTotalTile(Modifier.weight(1f), "🥬", "${dietDay.totals.fiberG.toInt()}g", "Fibre", com.nutriai.ui.theme.KaizenTeal, com.nutriai.ui.theme.CardTealLight)
                    }
                }
            }
        }

        // Personalized guidance — shown BELOW diet content
        state.guidance?.takeIf { it.dietTips.isNotEmpty() || it.exerciseTips.isNotEmpty() }?.let { g ->
            item { GuidanceCard(g) }
        }

        // Coach insight lives at the bottom: the plan comes first, the advice after.
        state.adaptation?.takeIf { it.status != "insufficient_data" }?.let { adapt ->
            item {
                AdaptiveInsightCard(
                    adaptation = adapt,
                    applying = state.applying,
                    onApply = { viewModel.applyAdaptation() },
                )
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
        containerColor = androidx.compose.ui.graphics.Color(0xFFF3F5FA),
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
                    if (recipe.timeMin != null || recipe.servings != null) {
                        BorderedTable(
                            headers = listOf("Time", "Servings"),
                            rows = listOf(listOf(recipe.timeMin?.let { "$it min" } ?: "-", recipe.servings?.toString() ?: "-")),
                            weights = listOf(0.5f, 0.5f),
                            centeredColumns = setOf(0, 1),
                            title = "⏱ Time & servings", accent = com.nutriai.ui.theme.AppPalette.stop(2),
                        )
                    }
                    if (recipe.ingredients.isNotEmpty()) {
                        BorderedTable(
                            headers = listOf("#", "Quantity", "Ingredient"),
                            rows = recipe.ingredients.mapIndexed { i, line -> val (q, item) = splitIngredient(line); listOf("${i + 1}", q, item) },
                            weights = listOf(0.16f, 0.3f, 0.54f),
                            centeredColumns = setOf(0, 1),
                            title = "🧂 Ingredients", accent = com.nutriai.ui.theme.AppPalette.stop(3),
                        )
                    }
                    if (recipe.steps.isNotEmpty()) {
                        BorderedTable(
                            headers = listOf("Step", "What to do"),
                            rows = recipe.steps.mapIndexed { i, s -> listOf("${i + 1}", s.replace(Regex("^\\s*\\d+[.)]\\s*"), "")) },
                            weights = listOf(0.2f, 0.8f),
                            centeredColumns = setOf(0),
                            title = "👨‍🍳 Steps", accent = com.nutriai.ui.theme.AppPalette.stop(0),
                        )
                    }
                    recipe.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                else -> Text("Couldn't load a recipe. Try again.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private val INGREDIENT_QTY = Regex(
    "^([\\d\u00BC\u00BD\u00BE./-]+(?:\\s+(?:tablespoons?|tbsp|teaspoons?|tsp|cups?|grams?|g|kg|ml|litres?|liters?|l|cloves?|pieces?|slices?|sprigs?|pinch(?:es)?|inch|handful|large|medium|small))?)\\s+(.+)$",
    RegexOption.IGNORE_CASE,
)

/** Splits "2 tablespoons finely chopped onions" into ("2 tablespoons", "finely chopped onions"); "Salt to taste" stays whole. */
private fun splitIngredient(line: String): Pair<String, String> {
    val m = INGREDIENT_QTY.find(line.trim()) ?: return "-" to line.trim()
    return m.groupValues[1] to m.groupValues[2]
}

@Composable
private fun GuidanceCard(g: Guidance) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = com.nutriai.ui.theme.CardLavenderLight),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, KaizenLavender.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("💡", style = MaterialTheme.typography.titleMedium)
                Text("Personalised for you", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = KaizenLavender)
                Spacer(Modifier.weight(1f))
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = KaizenLavender)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(g.summary.ifBlank { "Diet & exercise tips for your profile" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Spacer(Modifier.height(Spacing.sm))
                if (g.dietTips.isNotEmpty()) {
                    Text("🥗 Diet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NutritionColor)
                    g.dietTips.forEach { tip ->
                        Text("  • $tip", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                if (g.exerciseTips.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("🏋️ Exercise", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MovementColor)
                    g.exerciseTips.forEach { tip ->
                        Text("  • $tip", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTotalTile(modifier: Modifier, emoji: String, value: String, label: String, color: Color, bgColor: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FoodDetailDialog(item: com.nutriai.data.remote.dto.MealItem, onRecipe: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
        ) {
            // colourful header: food name + amount
            Column(
                Modifier.fillMaxWidth().background(com.nutriai.ui.theme.SpectrumBrush).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🍽️ ${item.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("${item.grams.toInt()} g  ·  ${item.kcal.toInt()} kcal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.95f))
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayTotalTile(Modifier.weight(1f), "💪", "${item.proteinG.toInt()} g", "Protein", NutritionColor, NutritionColor.copy(alpha = 0.10f))
                    DayTotalTile(Modifier.weight(1f), "🌾", "${item.carbG.toInt()} g", "Carbs", BrandAmber, BrandAmber.copy(alpha = 0.12f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayTotalTile(Modifier.weight(1f), "🥑", "${item.fatG.toInt()} g", "Fat", KaizenCoral, KaizenCoral.copy(alpha = 0.10f))
                    DayTotalTile(Modifier.weight(1f), "🥬", "${"%.1f".format(item.fiberG)} g", "Fibre", com.nutriai.ui.theme.KaizenTeal, com.nutriai.ui.theme.KaizenTeal.copy(alpha = 0.10f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Close", fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(com.nutriai.ui.theme.SpectrumBrush).clickable { onRecipe() },
                        contentAlignment = Alignment.Center,
                    ) { Text("📖 Recipe", fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
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
    modifier: Modifier = Modifier,
) {
    val dayNumber = date?.substringAfterLast('-')?.trimStart('0')?.ifBlank { "0" } ?: "-"
    val weekday = date?.let {
        runCatching {
            java.time.LocalDate.parse(it)
                .dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        }.getOrNull()
    } ?: label
    // The two pills are just "Today" and "Tomorrow" (the date number stays underneath).
    val title = if (label == "Today" || label == "Tomorrow") label else weekday
    val container = when {
        isSelected -> BrandGreenDark
        isToday -> NutritionColor.copy(alpha = 0.15f)
        else -> MaterialTheme.kaizenColors.elevatedSurface
    }
    val content = when {
        isSelected -> Color.White
        isToday -> BrandGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                title ?: "-",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                dayNumber,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isToday) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else BrandGreen),
                )
            }
        }
    }
}
