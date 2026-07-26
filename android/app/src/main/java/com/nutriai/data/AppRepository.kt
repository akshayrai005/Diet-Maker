package com.nutriai.data

import com.nutriai.data.local.TokenStore
import com.nutriai.data.local.cache.CacheDao
import com.nutriai.data.local.cache.CacheEntry
import com.nutriai.data.remote.NutriApi
import com.nutriai.data.remote.dto.CalcResult
import com.nutriai.data.remote.dto.ChatReply
import com.nutriai.data.remote.dto.ChatRequest
import com.nutriai.data.remote.dto.Dashboard
import com.nutriai.data.remote.dto.FoodLogRequest
import com.nutriai.data.remote.dto.LoginRequest
import com.nutriai.data.remote.dto.PlanDto
import com.nutriai.data.remote.dto.ProfileUpsertRequest
import com.nutriai.data.remote.dto.RegisterRequest
import com.nutriai.data.remote.dto.WaterLogRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val api: NutriApi,
    private val tokenStore: TokenStore,
    private val cacheDao: CacheDao,
    private val json: Json,
) {
    val isLoggedIn: Flow<Boolean> = tokenStore.accessTokenFlow.map { !it.isNullOrBlank() }

    suspend fun register(email: String, password: String, first: String, last: String): Result<Unit> =
        runCatching {
            val res = api.register(RegisterRequest(email, password, first, last))
            tokenStore.save(res.tokens.accessToken, res.tokens.refreshToken)
        }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val res = api.login(LoginRequest(email, password))
        tokenStore.save(res.tokens.accessToken, res.tokens.refreshToken)
    }

    /** Forgot-password step 1: verify email + DOB. Returns true if they match an account. */
    suspend fun forgotVerify(email: String, dob: String): Result<Boolean> = runCatching {
        api.forgotVerify(com.nutriai.data.remote.dto.VerifyIdentityRequest(email.trim(), dob)).verified
    }

    /** Forgot-password step 2: set a new password (server re-verifies email + DOB). */
    suspend fun resetPassword(email: String, dob: String, newPassword: String): Result<Unit> = runCatching {
        val res = api.resetPassword(com.nutriai.data.remote.dto.ResetPasswordRequest(email.trim(), dob, newPassword))
        if (!res.isSuccessful) error("We couldn't verify your details. Check your email and date of birth.")
        Unit
    }

    suspend fun logout() = tokenStore.clear()

    suspend fun saveProfile(body: ProfileUpsertRequest): Result<Unit> = runCatching {
        api.putProfile(body)
        api.computeCalc() // refresh the CalcResult snapshot (calorie + macro targets)
        // Regenerate the meal plan to match the new profile/targets so the Diet tab updates
        // automatically - no manual "Regenerate week" needed. Best-effort: a plan failure must not
        // fail the whole save. (The exercise plan already regenerates live from the profile.)
        runCatching { api.generatePlan(mapOf("days" to 7)) }
        // Drop stale cached copies so the next Diet/Home fetch shows the freshly-generated data.
        runCatching { cacheDao.delete("plan"); cacheDao.delete("dashboard") }
        Unit
    }

    /** Loads the saved profile so onboarding/settings can pre-fill instead of starting blank. */
    suspend fun getProfile(): Result<com.nutriai.data.remote.dto.ProfileDto?> =
        runCatching { api.getProfile().profile }

    suspend fun latestCalc(): Result<CalcResult?> = runCatching { api.latestCalc().result }

    /** Safe-pace preview for reaching [targetWeightKg] in [weeks] (server uses stored current weight). */
    suspend fun goalTimeline(targetWeightKg: Double, weeks: Int): Result<com.nutriai.data.remote.dto.GoalTimeline> =
        runCatching { api.goalTimeline(com.nutriai.data.remote.dto.GoalTimelineRequest(targetWeightKg, weeks)) }

    /** Fetches the dashboard; on network failure falls back to the last cached copy. */
    suspend fun dashboard(): Result<Dashboard> {
        val r = runCatching { api.dashboard().dashboard }
        return if (r.isSuccess) {
            val d = r.getOrThrow()
            runCatching { cacheDao.put(CacheEntry("dashboard", json.encodeToString(Dashboard.serializer(), d), System.currentTimeMillis())) }
            Result.success(d)
        } else {
            val cached = runCatching {
                cacheDao.get("dashboard")?.let { json.decodeFromString(Dashboard.serializer(), it.json) }
            }.getOrNull()
            if (cached != null) Result.success(cached) else r
        }
    }

    /** Fetches the latest plan; on network failure falls back to the last cached copy. */
    suspend fun latestPlan(): Result<PlanDto?> {
        val r = runCatching { api.latestPlan().plan }
        return if (r.isSuccess) {
            val p = r.getOrThrow()
            if (p != null) {
                runCatching { cacheDao.put(CacheEntry("plan", json.encodeToString(PlanDto.serializer(), p), System.currentTimeMillis())) }
            }
            Result.success(p)
        } else {
            val cached = runCatching {
                cacheDao.get("plan")?.let { json.decodeFromString(PlanDto.serializer(), it.json) }
            }.getOrNull()
            if (cached != null) Result.success(cached) else r
        }
    }

    suspend fun generatePlan(): Result<PlanDto?> = runCatching { api.generatePlan(mapOf("days" to 7)).plan }

    suspend fun swapMeal(dayIndex: Int, slot: String): Result<PlanDto?> = runCatching {
        api.swapMeal(com.nutriai.data.remote.dto.SwapMealRequest(dayIndex, slot)).plan
    }

    suspend fun chat(message: String): Result<ChatReply> = runCatching { api.chat(ChatRequest(message)).reply }

    suspend fun adaptation(): Result<com.nutriai.data.remote.dto.Adaptation> =
        runCatching { api.adaptation().adaptation }

    suspend fun guidance(): Result<com.nutriai.data.remote.dto.Guidance> =
        runCatching { api.guidance().guidance }

    // ---- Cycle ----
    suspend fun cycle(): Result<com.nutriai.data.remote.dto.Cycle> =
        runCatching { api.cycle().cycle }

    suspend fun logPeriod(startDate: String? = null): Result<Unit> =
        runCatching { api.logPeriod(com.nutriai.data.remote.dto.LogPeriodRequest(startDate)) }

    suspend fun endPeriod(): Result<Unit> = runCatching { api.endPeriod() }

    // ---- Wellness ----
    suspend fun wellness(): Result<com.nutriai.data.remote.dto.Wellness> =
        runCatching { api.wellness() }

    suspend fun recommendWellness(mood: Int?): Result<com.nutriai.data.remote.dto.WellnessRecommendation> =
        runCatching { api.recommendWellness(mood).recommendation }

    suspend fun wellnessSuggest(mood: Int? = null, energy: Int? = null, sleep: Double? = null): Result<com.nutriai.data.remote.dto.NowSuggestion> =
        runCatching { api.wellnessSuggest(mood, energy, sleep).suggestion }

    suspend fun logWellnessSession(refId: String): Result<com.nutriai.data.remote.dto.WellnessSessionDto> =
        runCatching { api.logWellnessSession(com.nutriai.data.remote.dto.WellnessSessionRequest(refId)).session }

    suspend fun wellnessHistory(): Result<com.nutriai.data.remote.dto.WellnessHistory> =
        runCatching { api.wellnessHistory() }

    // ---- Health rating + coach brief ----
    suspend fun rating(): Result<com.nutriai.data.remote.dto.RatingResult> =
        runCatching { api.rating().rating }

    suspend fun coachToday(): Result<com.nutriai.data.remote.dto.CoachTodayEnvelope> =
        runCatching { api.coachToday() }

    /**
     * Trigger the (cold-starting) backend and poll until it answers OK, so the splash can hold
     * until the server is actually up. Bounded (~18s) so it never blocks forever.
     */
    suspend fun warmup(): Boolean {
        val root = com.nutriai.BuildConfig.API_BASE_URL.substringBefore("/api/v1")
        // Gentle poll - the first ping triggers the cold boot; a few light retries catch when it's up.
        repeat(6) {
            val up = runCatching { api.ping("$root/health").isSuccessful }.getOrDefault(false)
            if (up) return true
            kotlinx.coroutines.delay(2500)
        }
        return false
    }

    // ---- Reminder prefs (server mirror) ----
    suspend fun reminderPrefs(): Result<com.nutriai.data.remote.dto.ReminderPrefsDto> =
        runCatching { api.reminderPrefs().prefs }

    suspend fun putReminderPrefs(prefs: com.nutriai.data.remote.dto.ReminderPrefsDto): Result<com.nutriai.data.remote.dto.ReminderPrefsDto> =
        runCatching { api.putReminderPrefs(prefs).prefs }

    // ---- Discipline habits ----
    suspend fun disciplineToday(): Result<com.nutriai.data.remote.dto.DisciplineToday> =
        runCatching { api.disciplineToday() }

    suspend fun toggleHabit(id: String, done: Boolean): Result<com.nutriai.data.remote.dto.DisciplineToday> =
        runCatching { api.toggleHabit(id, com.nutriai.data.remote.dto.HabitToggleRequest(done)) }

    suspend fun createHabit(title: String): Result<Unit> =
        runCatching { api.createHabit(com.nutriai.data.remote.dto.HabitCreateRequest(title)); Unit }

    suspend fun deleteHabit(id: String): Result<Unit> =
        runCatching { api.deleteHabit(id); Unit }

    // ---- AI vision ----
    suspend fun assessBodyPhoto(imageBase64: String): Result<com.nutriai.data.remote.dto.BodyAssessment> =
        runCatching { api.assessBodyPhoto(com.nutriai.data.remote.dto.PhotoRequest(imageBase64)).assessment }

    suspend fun mealPhoto(imageBase64: String): Result<com.nutriai.data.remote.dto.MealPhotoResult> =
        runCatching { api.mealPhoto(com.nutriai.data.remote.dto.PhotoRequest(imageBase64)) }

    // ---- Recipes ----
    suspend fun recipe(food: String, foodId: String?): Result<com.nutriai.data.remote.dto.Recipe> =
        runCatching { api.recipe(food, foodId).recipe }

    // ---- Chat history ----
    suspend fun chatHistory(): Result<List<com.nutriai.data.remote.dto.ChatMessageDto>> =
        runCatching { api.chatHistory().messages }

    // ---- Saved / recent foods ----
    suspend fun saveFood(body: com.nutriai.data.remote.dto.SavedFoodRequest): Result<Unit> =
        runCatching { api.saveFood(body); Unit }

    suspend fun savedFoods(): Result<List<com.nutriai.data.remote.dto.SavedFood>> =
        runCatching { api.savedFoods().foods }

    suspend fun deleteSavedFood(id: String): Result<Unit> = runCatching { api.deleteSavedFood(id) }

    suspend fun recentFoods(): Result<List<com.nutriai.data.remote.dto.RecentFood>> =
        runCatching { api.recentFoods().foods }

    suspend fun applyAdaptation(): Result<com.nutriai.data.remote.dto.AdaptApplyResponse> =
        runCatching { api.applyAdaptation() }

    suspend fun searchFoods(q: String): Result<List<com.nutriai.data.remote.dto.FoodDto>> = runCatching {
        api.foodsSearch(if (q.isBlank()) null else q).foods
    }

    suspend fun logFood(slot: String, foodId: String, grams: Double): Result<Unit> = runCatching {
        api.logFood(FoodLogRequest(mealSlot = slot, grams = grams, foodId = foodId))
    }

    /** Logs any food (local or USDA) by name + per-100g so no local DB row is required. */
    suspend fun logFoodItem(
        slot: String,
        food: com.nutriai.data.remote.dto.FoodDto,
        grams: Double,
    ): Result<Unit> = runCatching {
        api.logFood(
            FoodLogRequest(
                mealSlot = slot,
                grams = grams,
                // Link local catalog foods by id so the server can estimate vitamins & minerals.
                // USDA results have no local Food row, so leave foodId null for them.
                foodId = if (food.source == "usda") null else food.id,
                foodName = food.name,
                per100g = com.nutriai.data.remote.dto.FoodLogPer100g(
                    kcal = food.kcal,
                    proteinG = food.proteinG,
                    carbG = food.carbG,
                    fatG = food.fatG,
                    fiberG = food.fiberG,
                    sugarG = food.sugarG,
                    sodiumMg = food.sodiumMg,
                ),
                entryMethod = if (food.source == "usda") "barcode" else "text",
            ),
        )
    }

    /** Logs any food by name + per-100g (used by recents, saved foods and photo detection). */
    suspend fun logNamed(
        slot: String,
        name: String,
        per100g: com.nutriai.data.remote.dto.FoodLogPer100g,
        grams: Double,
        method: String = "text",
    ): Result<Unit> = runCatching {
        api.logFood(
            FoodLogRequest(mealSlot = slot, grams = grams, foodName = name, per100g = per100g, entryMethod = method),
        )
    }

    suspend fun logWater(ml: Int): Result<Unit> = runCatching { api.logWater(WaterLogRequest(ml)) }

    suspend fun todayLogs(): Result<List<com.nutriai.data.remote.dto.FoodLogEntry>> =
        runCatching { api.todayLogs().entries }

    suspend fun deleteFoodLog(id: String): Result<Unit> = runCatching { api.deleteFoodLog(id) }

    suspend fun reportPdfBytes(): Result<ByteArray> = runCatching { api.weeklyPdf().bytes() }

    // ---- Check-ins ----
    suspend fun createCheckin(body: com.nutriai.data.remote.dto.CheckinRequest): Result<Unit> =
        runCatching { api.createCheckin(body); Unit }

    suspend fun checkins(): Result<List<com.nutriai.data.remote.dto.CheckinDto>> =
        runCatching { api.checkins().checkins }

    // ---- Grocery ----
    suspend fun grocery(): Result<com.nutriai.data.remote.dto.Grocery> =
        runCatching { api.grocery().grocery }

    // ---- Reports ----
    suspend fun weeklyReport(): Result<com.nutriai.data.remote.dto.WeeklyReport> =
        runCatching { api.weeklyReport().report }

    /** Premium HTML report over a consolidated span (range = weekly|monthly, count = periods). */
    suspend fun reportHtml(range: String, count: Int): Result<String> =
        runCatching { api.reportHtml(range, count).html }

    // ---- Gamification ----
    suspend fun gamification(): Result<com.nutriai.data.remote.dto.Gamification> =
        runCatching { api.gamification().gamification }

    // ---- Health risk ----
    suspend fun risk(sleepHours: Double? = null, hydrationPct: Double? = null): Result<com.nutriai.data.remote.dto.RiskAssessment> =
        runCatching { api.risk(sleepHours, hydrationPct).risk }

    // ---- Family ----
    suspend fun family(): Result<List<com.nutriai.data.remote.dto.FamilyMemberDto>> =
        runCatching { api.family().members }

    suspend fun addFamilyMember(body: com.nutriai.data.remote.dto.FamilyMemberRequest): Result<Unit> =
        runCatching { api.addFamilyMember(body); Unit }

    suspend fun familyCalc(id: String): Result<com.nutriai.data.remote.dto.CalcResult?> =
        runCatching { api.familyCalc(id).result }

    // ---- Workout ----
    suspend fun exercisePlan(): Result<com.nutriai.data.remote.dto.WeeklyWorkout> =
        runCatching { api.exercisePlan().plan }

    /** Plan plus the auto level-up/ease-down suggestion. */
    suspend fun exercisePlanFull(): Result<com.nutriai.data.remote.dto.WorkoutEnvelope> =
        runCatching { api.exercisePlan() }

    // ---- Workout logging ----
    suspend fun logExercise(body: com.nutriai.data.remote.dto.ExerciseLogRequest): Result<com.nutriai.data.remote.dto.ExerciseLogDto> =
        runCatching { api.logExercise(body).entry }

    suspend fun exerciseLogs(date: String?): Result<List<com.nutriai.data.remote.dto.ExerciseLogDto>> =
        runCatching { api.exerciseLogs(date).entries }

    suspend fun lastPerformance(): Result<Map<String, com.nutriai.data.remote.dto.LastPerformance>> =
        runCatching { api.exerciseLast().last }

    suspend fun deleteExerciseLog(id: String): Result<Unit> =
        runCatching { api.deleteExerciseLog(id) }

    /** Estimated 1-rep-max over time, per exercise (from logged weighted sets). */
    suspend fun strengthTrend(): Result<List<com.nutriai.data.remote.dto.StrengthTrend>> =
        runCatching { api.strengthTrend().trends }

    // ---- Vitals & labs ----
    suspend fun logVital(
        type: String,
        value: Double? = null,
        systolic: Int? = null,
        diastolic: Int? = null,
        note: String? = null,
        measuredAt: String? = null,
    ): Result<com.nutriai.data.remote.dto.LogVitalResponse> = runCatching {
        api.logVital(
            com.nutriai.data.remote.dto.LogVitalRequest(
                type = type,
                value = value,
                systolic = systolic,
                diastolic = diastolic,
                note = note,
                measuredAt = measuredAt,
            ),
        )
    }

    suspend fun vitalSeries(type: String, rangeDays: Int? = null): Result<com.nutriai.data.remote.dto.VitalSeries> =
        runCatching { api.vitalSeries(type, rangeDays) }

    suspend fun vitalsSummary(): Result<com.nutriai.data.remote.dto.VitalsSummaryResponse> =
        runCatching { api.vitalsSummary() }

    // ---- Medications & supplements ----
    suspend fun medications(): Result<com.nutriai.data.remote.dto.MedicationsEnvelope> =
        runCatching { api.medications() }

    suspend fun createMedication(body: com.nutriai.data.remote.dto.MedicationRequest): Result<com.nutriai.data.remote.dto.MedicationDto> =
        runCatching { api.createMedication(body).medication }

    suspend fun updateMedication(id: String, body: com.nutriai.data.remote.dto.MedicationRequest): Result<com.nutriai.data.remote.dto.MedicationDto> =
        runCatching { api.updateMedication(id, body).medication }

    suspend fun setMedicationActive(id: String, active: Boolean): Result<Unit> = runCatching {
        val res = api.setMedicationActive(id, com.nutriai.data.remote.dto.MedicationActiveRequest(active))
        if (!res.isSuccessful) error("Couldn't update")
        Unit
    }

    suspend fun deleteMedication(id: String): Result<Unit> = runCatching {
        val res = api.deleteMedication(id)
        if (!res.isSuccessful) error("Couldn't delete")
        Unit
    }

    suspend fun logMedication(id: String): Result<Unit> = runCatching {
        val res = api.logMedication(id)
        if (!res.isSuccessful) error("Couldn't log")
        Unit
    }

    // ---- Progress / body (physique tracking) ----
    suspend fun logBodyMetric(
        body: com.nutriai.data.remote.dto.BodyMetricRequest,
    ): Result<com.nutriai.data.remote.dto.BodyMetricResponse> =
        runCatching { api.logBodyMetric(body) }

    suspend fun bodyMetrics(range: Int? = null): Result<com.nutriai.data.remote.dto.BodySeries> =
        runCatching { api.bodyMetrics(range) }

    suspend fun addBodyPhoto(localRef: String, caption: String? = null): Result<com.nutriai.data.remote.dto.BodyPhotoDto> =
        runCatching { api.addBodyPhoto(com.nutriai.data.remote.dto.BodyPhotoRequest(localRef, caption)).photo }

    suspend fun bodyPhotos(): Result<List<com.nutriai.data.remote.dto.BodyPhotoDto>> =
        runCatching { api.bodyPhotos().photos }

    suspend fun deleteBodyPhoto(id: String): Result<Unit> = runCatching {
        val res = api.deleteBodyPhoto(id)
        if (!res.isSuccessful) error("Couldn't delete")
        Unit
    }

    // ---- Mood / stress / sleep check-in ----
    suspend fun saveMoodCheckin(
        mood: Int? = null,
        stress: Int? = null,
        sleepQuality: Int? = null,
        notes: String? = null,
    ): Result<Unit> = runCatching {
        api.createMoodCheckin(
            com.nutriai.data.remote.dto.MoodCheckinRequest(
                mood = mood, stress = stress, sleepQuality = sleepQuality, notes = notes,
            ),
        )
        Unit
    }

    suspend fun moodCheckins(): Result<com.nutriai.data.remote.dto.MoodSeriesResponse> =
        runCatching { api.moodCheckins() }

    // ---- Red-flag safety net ----
    suspend fun checkRedFlags(text: String): Result<com.nutriai.data.remote.dto.RedFlagResponse> =
        runCatching { api.redFlags(com.nutriai.data.remote.dto.RedFlagRequest(text)) }

    suspend fun deleteAccount(): Result<Unit> = runCatching { api.deleteAccount() }

    suspend fun me(): Result<com.nutriai.data.remote.dto.PublicUser> = runCatching { api.me().user }

    // ---- Barcode ----
    suspend fun barcode(code: String): Result<com.nutriai.data.remote.dto.BarcodeFood> =
        runCatching { api.barcode(code).food }

    /** Logs a scanned barcode food by name + per-100g (no local DB row needed). */
    suspend fun logBarcodeFood(
        slot: String,
        food: com.nutriai.data.remote.dto.BarcodeFood,
        grams: Double,
    ): Result<Unit> = runCatching {
        api.logFood(
            FoodLogRequest(
                mealSlot = slot,
                grams = grams,
                foodName = food.name,
                per100g = com.nutriai.data.remote.dto.FoodLogPer100g(
                    kcal = food.per100g.kcal,
                    proteinG = food.per100g.proteinG,
                    carbG = food.per100g.carbG,
                    fatG = food.per100g.fatG,
                    fiberG = food.per100g.fiberG,
                    sugarG = food.per100g.sugarG,
                    sodiumMg = food.per100g.sodiumMg,
                ),
                entryMethod = "barcode",
            ),
        )
    }
}
