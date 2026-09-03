package com.nutriai.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.ui.move.ExerciseCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/** A preset one-tap food for building a plan fast (name + total macros). */
data class PlanFoodPreset(val name: String, val kcal: Double, val proteinG: Double)

/** One day's card in the weekly planning grid. */
data class DaySummary(val date: String, val dayName: String, val kcal: Int, val proteinG: Int, val isFast: Boolean)

val PLAN_FOOD_PRESETS = listOf(
    PlanFoodPreset("4 boiled eggs", 248.0, 24.0),
    PlanFoodPreset("2 rotis", 200.0, 6.0),
    PlanFoodPreset("Dal (1 katori)", 180.0, 8.0),
    PlanFoodPreset("150g chicken breast", 248.0, 47.0),
    PlanFoodPreset("100g soya chunks (dry)", 345.0, 52.0),
    PlanFoodPreset("100g paneer", 265.0, 18.0),
    PlanFoodPreset("1 scoop whey", 120.0, 24.0),
    PlanFoodPreset("30g peanuts", 170.0, 8.0),
    PlanFoodPreset("1 banana", 89.0, 1.0),
    PlanFoodPreset("1 katori curd", 90.0, 8.0),
    PlanFoodPreset("40g oats + milk", 220.0, 10.0),
    PlanFoodPreset("Rohu fish (150g)", 145.0, 27.0),
)

data class PlanUiState(
    val plan: DayPlan = DayPlan(date = LocalDate.now().plusDays(1).toString()),
    val kcalTarget: Double = 1900.0,
    val proteinTarget: Double = 130.0,
    val actualKcal: Double? = null,      // only for today (plan-vs-actual)
    val actualProtein: Double? = null,
    val reviewing: Boolean = false,
    val toast: String? = null,
) {
    val isToday: Boolean get() = plan.date == LocalDate.now().toString()
    val exerciseAdherence: Int
        get() = if (plan.exercises.isEmpty()) 0 else (plan.exercises.count { it.done } * 100 / plan.exercises.size)
    val foodAdherence: Int
        get() {
            val target = plan.plannedProtein
            if (target <= 0 || actualProtein == null) return 0
            return (min(actualProtein / target, 1.0) * 100).roundToInt()
        }
    val adherence: Int
        get() {
            val parts = mutableListOf<Int>()
            if (plan.foods.isNotEmpty() && actualProtein != null) parts.add(foodAdherence)
            if (plan.exercises.isNotEmpty()) parts.add(exerciseAdherence)
            return if (parts.isEmpty()) 0 else parts.average().roundToInt()
        }
}

/**
 * Drives the "Plan Tomorrow" screen (spec Section 21): build a next-day plan (foods, exercises,
 * sleep, trainer notes), see live totals vs target, get an AI review via the coach chat, and — on the
 * planned day itself — compare planned vs actual and score adherence.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: AppRepository,
    private val planStore: PlanStore,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    val foodPresets = PLAN_FOOD_PRESETS

    fun exerciseSuggestions(query: String) = ExerciseCatalog.search(query).take(12)

    init {
        loadTargets()
        load(LocalDate.now().plusDays(1).toString())
        loadWeek()
    }

    /** Mon–Sun of the current week, each with its saved plan's totals (for the week grid). */
    private val _week = MutableStateFlow<List<DaySummary>>(emptyList())
    val week: StateFlow<List<DaySummary>> = _week.asStateFlow()

    fun loadWeek() {
        viewModelScope.launch {
            val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
            val summaries = (0..6).map { offset ->
                val d = monday.plusDays(offset.toLong())
                val iso = d.toString()
                val saved = planStore.load(iso)
                DaySummary(
                    date = iso,
                    dayName = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                    kcal = saved?.plannedKcal?.roundToInt() ?: 0,
                    proteinG = saved?.plannedProtein?.roundToInt() ?: 0,
                    isFast = d.dayOfWeek == java.time.DayOfWeek.TUESDAY,
                )
            }
            _week.value = summaries
        }
    }

    private fun loadTargets() {
        viewModelScope.launch {
            repository.dashboard().getOrNull()?.let { d ->
                _state.value = _state.value.copy(
                    kcalTarget = d.calories.target ?: _state.value.kcalTarget,
                    proteinTarget = d.protein.target ?: _state.value.proteinTarget,
                )
            }
        }
    }

    fun load(date: String) {
        viewModelScope.launch {
            val saved = planStore.load(date)
            val plan = saved ?: DayPlan(date = date)
            _state.value = _state.value.copy(plan = plan, actualKcal = null, actualProtein = null)
            if (date == LocalDate.now().toString()) refreshActual()
        }
    }

    fun switchTo(date: String) = load(date)

    private fun refreshActual() {
        viewModelScope.launch {
            val logs = repository.todayLogs().getOrNull().orEmpty()
            _state.value = _state.value.copy(
                actualKcal = logs.sumOf { it.kcal },
                actualProtein = logs.sumOf { it.proteinG },
            )
        }
    }

    private fun update(block: (DayPlan) -> DayPlan) {
        val next = block(_state.value.plan)
        _state.value = _state.value.copy(plan = next)
        viewModelScope.launch { planStore.save(next); loadWeek() }
    }

    fun setTrainerNotes(v: String) = update { it.copy(trainerNotes = v) }
    fun setSleep(bed: String, wake: String) = update { it.copy(bedtime = bed, waketime = wake) }
    fun addFood(name: String, kcal: Double, proteinG: Double) = update { it.copy(foods = it.foods + PlanFood(name, kcal, proteinG)) }
    fun removeFood(index: Int) = update { it.copy(foods = it.foods.filterIndexed { i, _ -> i != index }) }
    fun addExercise(name: String, muscleGroup: String?) = update { it.copy(exercises = it.exercises + PlanExercise(name = name, muscleGroup = muscleGroup)) }
    fun removeExercise(index: Int) = update { it.copy(exercises = it.exercises.filterIndexed { i, _ -> i != index }) }
    fun toggleExerciseDone(index: Int) = update {
        it.copy(exercises = it.exercises.mapIndexed { i, e -> if (i == index) e.copy(done = !e.done) else e })
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }

    /** Builds a structured prompt from the plan and asks the coach (Claude API) to review it. */
    fun requestReview() {
        val s = _state.value
        val p = s.plan
        _state.value = s.copy(reviewing = true)
        viewModelScope.launch {
            val prompt = buildString {
                append("Review my plan for ${p.date} like a supportive gym coach. ")
                append("My daily targets: ${s.kcalTarget.roundToInt()} kcal, ${s.proteinTarget.roundToInt()}g protein. ")
                if (p.trainerNotes.isNotBlank()) append("My trainer said: \"${p.trainerNotes}\". Cross-check it against my targets. ")
                append("Planned food (total ${p.plannedKcal.roundToInt()} kcal, ${p.plannedProtein.roundToInt()}g protein): ")
                append(p.foods.joinToString(", ") { "${it.name} (${it.kcal.roundToInt()}kcal/${it.proteinG.roundToInt()}g)" }.ifBlank { "none yet" })
                append(". Planned exercises: ")
                append(p.exercises.joinToString(", ") { "${it.name} ${it.sets}x${it.reps}" }.ifBlank { "none yet" })
                append(". Sleep ${p.bedtime}-${p.waketime}. ")
                append("Tell me if protein/calories are on target, suggest specific Indian food swaps to fix gaps, and whether the workout looks right. Keep it short and friendly.")
            }
            val reply = repository.chat(prompt)
            val text = reply.getOrNull()?.reply ?: "Couldn't reach the coach right now - check your connection and try again."
            update { it.copy(aiReview = text) }
            _state.value = _state.value.copy(reviewing = false)
        }
    }
}
