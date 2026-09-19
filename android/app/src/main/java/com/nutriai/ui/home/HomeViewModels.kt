package com.nutriai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Dashboard
import com.nutriai.data.remote.dto.PlanDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---- Dashboard ----
data class DashboardState(
    val loading: Boolean = true,
    val dashboard: Dashboard? = null,
    val firstName: String? = null,
    val steps: Long = 0,
    val stepsKcal: Int = 0,
    val exerciseKcal: Int = 0,
    val stepsAvailable: Boolean = false,
    val stepsPermission: Boolean = false,
    val heartRate: Int? = null,
    val sleepHours: Double? = null,
    val manualSleepHours: Double? = null,
    val manualHeartRate: Int? = null,
    val bloodPressure: Pair<Int, Int>? = null,
    val oxygenSaturation: Int? = null,
    val bodyTemperature: Double? = null,
    val stress: Int? = null,
    val soreness: Int? = null,
    val safetyFlags: List<com.nutriai.data.remote.dto.Flag> = emptyList(),
    val riskFindings: List<com.nutriai.data.remote.dto.RiskFinding> = emptyList(),
    val weekDays: List<com.nutriai.data.remote.dto.ReportDay> = emptyList(),
    val weekKcalTarget: Double? = null,
    val maintenanceKcal: Double? = null, // TDEE - what the body burns/day
    val bmi: Double? = null,
    val bodyFatPct: Double? = null,
    val coach: com.nutriai.data.remote.dto.CoachBrief? = null,
    val rating: com.nutriai.data.remote.dto.RatingResult? = null,
    /** Today's training day from the workout plan (null if rest day / no plan yet). */
    val todayWorkout: com.nutriai.data.remote.dto.WorkoutDay? = null,
    /** Weekly adherence read (calories/protein/training/steps/weigh-ins), reasoned across dimensions. */
    val adherence: com.nutriai.data.remote.dto.AdherenceRead? = null,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AppRepository,
    private val healthConnect: com.nutriai.data.health.HealthConnectManager,
    private val vitalsStore: com.nutriai.data.local.VitalsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refresh()
        loadSteps()
        viewModelScope.launch {
            repository.me().getOrNull()?.let { u ->
                _state.value = _state.value.copy(firstName = u.firstName)
            }
        }
        // Manually-entered vitals (for watches that don't sync to Health Connect).
        viewModelScope.launch {
            vitalsStore.vitals.collect { v ->
                _state.value = _state.value.copy(manualHeartRate = v.heartRate, stress = v.stress, soreness = v.soreness, manualSleepHours = v.sleepHours)
            }
        }
    }

    fun saveManualVitals(heartRate: Int?, stress: Int?, soreness: Int? = null, sleepHours: Double? = null) {
        viewModelScope.launch {
            vitalsStore.save(heartRate, stress, soreness, sleepHours, System.currentTimeMillis())
            if (soreness != null && soreness >= 3) refresh()
        }
    }

    /** Reads today's steps from Health Connect (0 if unavailable / no permission). */
    fun loadSteps() {
        viewModelScope.launch {
            val available = healthConnect.isAvailable()
            val perm = if (available) healthConnect.hasStepPermission() else false
            val steps = if (perm) healthConnect.readTodaySteps() else 0L
            // Watch vitals (heart rate + sleep + BP) - null unless a band/watch syncs them to
            // Health Connect. Note: Health Connect has no "stress score" data type, so that stat
            // stays manual self-entry (see vitalsStore below) - no watch can push it here.
            val hr = if (available) healthConnect.readLatestHeartRate() else null
            val sleep = if (available) healthConnect.readLastSleepHours() else null
            val bp = if (available) healthConnect.readLatestBloodPressure() else null
            val spo2 = if (available) healthConnect.readLatestOxygenSaturation() else null
            val bodyTemp = if (available) healthConnect.readLatestBodyTemperature() else null
            _state.value = _state.value.copy(
                steps = steps,
                stepsKcal = (steps * 0.04).toInt(), // ~0.04 kcal/step
                stepsAvailable = available,
                stepsPermission = perm,
                heartRate = hr,
                sleepHours = sleep,
                bloodPressure = bp,
                oxygenSaturation = spo2,
                bodyTemperature = bodyTemp,
            )
            // Best-effort: persist today's step count server-side so real-activity detection
            // (TDEE) has history to work from, not just a fresh device read every time. Silently
            // ignored on failure - this must never block the dashboard from loading.
            if (perm && steps > 0) {
                val today = java.time.LocalDate.now().toString()
                repository.syncSteps(today, steps.toInt())
            }
            // Re-run the risk engine WITH device sleep + today's hydration so poor-sleep /
            // low-hydration signals appear alongside the profile-based ones. Only send hydration
            // in the afternoon+ - being "behind" on water first thing in the morning is normal,
            // so flagging it right after waking up is a false alarm.
            val afternoon = java.time.LocalTime.now().hour >= 14
            val hydrationPct = if (afternoon) _state.value.dashboard?.water?.percent else null
            if (sleep != null || hydrationPct != null) {
                repository.risk(sleepHours = sleep, hydrationPct = hydrationPct).getOrNull()?.let { risk ->
                    _state.value = _state.value.copy(riskFindings = risk.findings)
                }
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.dashboard()
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, dashboard = r.getOrNull(), error = null)
            } else {
                _state.value.copy(loading = false, error = r.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
        // Safety/medical advisories (guardrail flags) - shown as a card on the dashboard.
        // Drop the generic DISCLAIMER (already in the footer) so the card only appears
        // when there's a real condition/medication note to show.
        viewModelScope.launch {
            repository.latestCalc().getOrNull()?.let { calc ->
                _state.value = _state.value.copy(
                    safetyFlags = calc.flags.filter { it.code != "DISCLAIMER" },
                    maintenanceKcal = calc.tdee,
                    bmi = calc.bmi,
                    bodyFatPct = calc.bodyFatEstimate,
                )
            }
        }
        // Deterministic health-risk findings (central obesity, BP, glucose, sleep…).
        viewModelScope.launch {
            repository.risk().getOrNull()?.let { risk ->
                _state.value = _state.value.copy(riskFindings = risk.findings)
            }
        }
        // Last-7-days goal monitor (calorie adherence per day).
        viewModelScope.launch {
            repository.weeklyReport().getOrNull()?.let { rep ->
                _state.value = _state.value.copy(
                    weekDays = rep.days,
                    weekKcalTarget = rep.targets?.dailyKcal,
                )
            }
        }
        // Coach brief + health rating (server-computed) for the "Your Analysis" home card.
        viewModelScope.launch {
            repository.coachToday().getOrNull()?.let { c ->
                _state.value = _state.value.copy(coach = c.brief, rating = c.rating)
            }
        }
        // Today's workout (for the "Today: Chest + Triceps" card up top).
        viewModelScope.launch {
            // Prefix match, not equality: a period day relabels to "Today · Period" (still today).
            repository.exercisePlan().getOrNull()?.days?.firstOrNull { it.label?.startsWith("Today") == true }?.let { day ->
                _state.value = _state.value.copy(todayWorkout = day)
            }
        }
        // Exercise kcal burned today from the exercise log.
        viewModelScope.launch {
            val logs = repository.exerciseLogs(null).getOrDefault(emptyList())
            _state.value = _state.value.copy(exerciseKcal = logs.sumOf { it.kcal ?: 0 })
        }
        // Weekly adherence read - reasons across calories/protein/training/steps/weigh-ins instead
        // of a flat calorie pass/fail (e.g. "hold steady" instead of "cut more" when weight trend
        // already confirms the plan is working despite a light-protein week).
        viewModelScope.launch {
            repository.adherence().getOrNull()?.let { a ->
                _state.value = _state.value.copy(adherence = a)
            }
        }
    }

    fun logWater(ml: Int) {
        viewModelScope.launch {
            repository.logWater(ml)
            refresh()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onDone()
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAccount()
            repository.logout() // clear local tokens after server-side deletion
            onDone()
        }
    }
}

// ---- Plan ----
/** Server's PLAN_VERSION. Plans with a lower version auto-refresh on open. Keep in sync. */
private const val CURRENT_PLAN_VERSION = 7

data class PlanState(
    val loading: Boolean = true,
    val plan: PlanDto? = null,
    val error: String? = null,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.latestPlan()
            val plan = r.getOrNull()
            // Auto-refresh a plan generated by an older meal-plan engine (e.g. one that could put
            // two grains or a staple-less plate), so the user never has to notice and regenerate.
            val stale = plan != null && (plan.targets?.planVersion ?: 0) < CURRENT_PLAN_VERSION
            if (stale) {
                val fresh = repository.generatePlan()
                _state.value = if (fresh.isSuccess) PlanState(loading = false, plan = fresh.getOrNull())
                else PlanState(loading = false, plan = plan) // fall back to the old plan if regen fails
            } else {
                _state.value = if (r.isSuccess) PlanState(loading = false, plan = plan)
                else PlanState(loading = false, error = r.exceptionOrNull()?.message)
            }
        }
    }

    fun generate() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.generatePlan()
            _state.value = if (r.isSuccess) PlanState(loading = false, plan = r.getOrNull())
            else PlanState(loading = false, error = r.exceptionOrNull()?.message)
        }
    }
}

// ---- Chat ----
data class ChatMessage(val fromUser: Boolean, val text: String)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val redFlagMessage: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val greeting = ChatMessage(false, "Hi! Ask me about a food, your targets, or a safe pace to lose weight.")
    private val _state = MutableStateFlow(ChatState(messages = listOf(greeting)))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init { loadHistory() }

    /** Loads persisted history so the coach remembers past conversations. */
    fun loadHistory() {
        viewModelScope.launch {
            val past = repository.chatHistory().getOrDefault(emptyList())
            if (past.isNotEmpty()) {
                _state.value = _state.value.copy(
                    messages = past.map { ChatMessage(fromUser = it.role == "user", text = it.content) },
                )
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        val history = _state.value.messages + ChatMessage(true, text)
        _state.value = _state.value.copy(messages = history, sending = true)
        // Red-flag safety net - runs alongside the normal reply, never suppresses it.
        viewModelScope.launch {
            repository.checkRedFlags(text).getOrNull()?.let { rf ->
                if (rf.urgent) _state.value = _state.value.copy(redFlagMessage = rf.message)
            }
        }
        viewModelScope.launch {
            val r = repository.chat(text)
            val reply = r.getOrNull()?.reply ?: (r.exceptionOrNull()?.message ?: "Sorry, I couldn't answer that.")
            _state.value = _state.value.copy(messages = history + ChatMessage(false, reply), sending = false)
        }
    }

    fun clearRedFlag() { _state.value = _state.value.copy(redFlagMessage = null) }

    /** Wipes the whole conversation - server-side too, so it stays cleared on next app open. */
    fun clearChat() {
        _state.value = _state.value.copy(messages = listOf(greeting))
        viewModelScope.launch { repository.clearChatHistory() }
    }
}

// ---- Food logging ----
data class LogState(
    val query: String = "",
    val results: List<com.nutriai.data.remote.dto.FoodDto> = emptyList(),
    val today: List<com.nutriai.data.remote.dto.FoodLogEntry> = emptyList(),
    val slot: String = "breakfast",
    val grams: String = "150",
    val loading: Boolean = false,
    val message: String? = null,
    val recents: List<com.nutriai.data.remote.dto.RecentFood> = emptyList(),
    val saved: List<com.nutriai.data.remote.dto.SavedFood> = emptyList(),
    val photoItems: List<com.nutriai.data.remote.dto.VisionFoodItem> = emptyList(),
    val analyzing: Boolean = false,
)

@HiltViewModel
class LogFoodViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LogState())
    val state: StateFlow<LogState> = _state.asStateFlow()

    val slots = listOf("breakfast", "midmorning", "lunch", "eveningsnack", "dinner", "bedtime")

    init {
        search("")
        loadToday()
        loadExtras()
    }

    fun loadToday() {
        viewModelScope.launch {
            val r = repository.todayLogs()
            r.getOrNull()?.let { _state.value = _state.value.copy(today = it) }
        }
    }

    fun loadExtras() {
        viewModelScope.launch {
            val recents = repository.recentFoods().getOrDefault(emptyList())
            val saved = repository.savedFoods().getOrDefault(emptyList())
            _state.value = _state.value.copy(recents = recents, saved = saved)
        }
    }

    /** Sends a meal photo to AI vision and shows the detected items to add. */
    fun analyzePhoto(imageBase64: String) {
        _state.value = _state.value.copy(analyzing = true, message = null, photoItems = emptyList())
        viewModelScope.launch {
            val r = repository.mealPhoto(imageBase64)
            val res = r.getOrNull()
            _state.value = _state.value.copy(
                analyzing = false,
                photoItems = res?.items ?: emptyList(),
                message = res?.note ?: "Couldn't analyse the photo.",
            )
        }
    }

    fun clearPhotoItems() { _state.value = _state.value.copy(photoItems = emptyList()) }

    fun logVisionItem(item: com.nutriai.data.remote.dto.VisionFoodItem) {
        viewModelScope.launch {
            val r = repository.logNamed(com.nutriai.util.MealSlot.now(), item.name, item.per100g, item.grams, "photo")
            if (r.isSuccess) {
                _state.value = _state.value.copy(
                    message = "✓ Logged ${item.name}",
                    photoItems = _state.value.photoItems.filter { it !== item },
                )
                loadToday(); loadExtras()
            }
        }
    }

    fun logRecent(recent: com.nutriai.data.remote.dto.RecentFood, grams: Double) {
        viewModelScope.launch {
            val r = repository.logNamed(com.nutriai.util.MealSlot.now(), recent.name, recent.per100g, grams)
            if (r.isSuccess) { _state.value = _state.value.copy(message = "✓ Logged ${recent.name}"); loadToday() }
        }
    }

    fun logSaved(saved: com.nutriai.data.remote.dto.SavedFood, grams: Double) {
        val per = com.nutriai.data.remote.dto.FoodLogPer100g(
            kcal = saved.kcal, proteinG = saved.proteinG, carbG = saved.carbG, fatG = saved.fatG,
            fiberG = saved.fiberG, sugarG = saved.sugarG, sodiumMg = saved.sodiumMg,
        )
        viewModelScope.launch {
            val r = repository.logNamed(com.nutriai.util.MealSlot.now(), saved.name, per, grams)
            if (r.isSuccess) { _state.value = _state.value.copy(message = "✓ Logged ${saved.name}"); loadToday() }
        }
    }

    fun saveCustom(req: com.nutriai.data.remote.dto.SavedFoodRequest) {
        viewModelScope.launch {
            if (repository.saveFood(req).isSuccess) { _state.value = _state.value.copy(message = "★ Saved ${req.name}"); loadExtras() }
        }
    }

    fun favorite(food: com.nutriai.data.remote.dto.FoodDto) {
        saveCustom(
            com.nutriai.data.remote.dto.SavedFoodRequest(
                name = food.name, kcal = food.kcal, proteinG = food.proteinG, carbG = food.carbG,
                fatG = food.fatG, fiberG = food.fiberG, sugarG = food.sugarG, sodiumMg = food.sodiumMg,
            ),
        )
    }

    fun deleteSaved(id: String) {
        viewModelScope.launch { if (repository.deleteSavedFood(id).isSuccess) loadExtras() }
    }

    fun onQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun onSlot(s: String) {
        _state.value = _state.value.copy(slot = s)
    }

    fun onGrams(g: String) {
        _state.value = _state.value.copy(grams = g.filter { it.isDigit() })
    }

    fun search(q: String) {
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = repository.searchFoods(q)
            _state.value = _state.value.copy(loading = false, results = r.getOrDefault(emptyList()))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val r = repository.deleteFoodLog(id)
            if (r.isSuccess) {
                loadToday()
            } else {
                _state.value = _state.value.copy(message = "Could not remove entry")
            }
        }
    }

    fun log(food: com.nutriai.data.remote.dto.FoodDto, gramsOverride: Double? = null) {
        if (com.nutriai.ui.components.LogFeedback.busy) return // a second tap while saving must not log the food again
        val grams = gramsOverride ?: _state.value.grams.toDoubleOrNull() ?: food.typicalServingG
        com.nutriai.ui.components.LogFeedback.start("Saving ${food.name}...")
        viewModelScope.launch {
            val r = repository.logFoodItem(com.nutriai.util.MealSlot.now(), food, grams)
            com.nutriai.ui.components.LogFeedback.done(r.isSuccess, "Logged")
            _state.value = _state.value.copy(
                message = if (r.isSuccess) {
                    "✓ Logged ${grams.toInt()} g of ${food.name} to ${com.nutriai.util.MealSlot.now()}"
                } else {
                    r.exceptionOrNull()?.message ?: "Could not log"
                },
            )
            if (r.isSuccess) loadToday()
        }
    }
}

// ---- Session (login state at the nav root) ----
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _loggedIn.asStateFlow()

    // null = still checking; true = logged in but profile incomplete → must finish onboarding first.
    private val _needsProfile = MutableStateFlow<Boolean?>(null)
    val needsProfile: StateFlow<Boolean?> = _needsProfile.asStateFlow()

    init {
        viewModelScope.launch {
            repository.isLoggedIn.collect { li ->
                _loggedIn.value = li
                _needsProfile.value = when (li) {
                    true -> repository.getProfile().getOrNull()?.sensitive == null
                    false -> false
                    null -> null
                }
            }
        }
    }
}
