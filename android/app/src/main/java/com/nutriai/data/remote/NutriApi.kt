package com.nutriai.data.remote

import com.nutriai.data.remote.dto.AuthResponse
import com.nutriai.data.remote.dto.BarcodeEnvelope
import com.nutriai.data.remote.dto.CalcEnvelope
import com.nutriai.data.remote.dto.ChatEnvelope
import com.nutriai.data.remote.dto.ChatRequest
import com.nutriai.data.remote.dto.CheckinCreatedEnvelope
import com.nutriai.data.remote.dto.CheckinRequest
import com.nutriai.data.remote.dto.CheckinsEnvelope
import com.nutriai.data.remote.dto.DashboardEnvelope
import com.nutriai.data.remote.dto.FamilyEnvelope
import com.nutriai.data.remote.dto.FamilyMemberCreatedEnvelope
import com.nutriai.data.remote.dto.FamilyMemberRequest
import com.nutriai.data.remote.dto.FoodLogRequest
import com.nutriai.data.remote.dto.FoodsEnvelope
import com.nutriai.data.remote.dto.GamificationEnvelope
import com.nutriai.data.remote.dto.GroceryEnvelope
import com.nutriai.data.remote.dto.LoginRequest
import com.nutriai.data.remote.dto.PlanEnvelope
import com.nutriai.data.remote.dto.ProfileEnvelope
import com.nutriai.data.remote.dto.ProfileUpsertRequest
import com.nutriai.data.remote.dto.RegisterRequest
import com.nutriai.data.remote.dto.ReportEnvelope
import com.nutriai.data.remote.dto.WaterLogRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NutriApi {
    /** Wakes the (cold-starting) backend. Any HTTP response means the server is up. */
    @GET
    suspend fun ping(@retrofit2.http.Url url: String): retrofit2.Response<okhttp3.ResponseBody>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/forgot-password/verify")
    suspend fun forgotVerify(@Body body: com.nutriai.data.remote.dto.VerifyIdentityRequest): com.nutriai.data.remote.dto.VerifyIdentityResponse

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: com.nutriai.data.remote.dto.ResetPasswordRequest): retrofit2.Response<Unit>

    @GET("profile")
    suspend fun getProfile(): ProfileEnvelope

    @PUT("profile")
    suspend fun putProfile(@Body body: ProfileUpsertRequest): ProfileEnvelope

    @POST("calc")
    suspend fun computeCalc(): CalcEnvelope

    @GET("calc/latest")
    suspend fun latestCalc(): CalcEnvelope

    @GET("dashboard")
    suspend fun dashboard(): DashboardEnvelope

    @POST("plan")
    suspend fun generatePlan(@Body body: Map<String, Int>): PlanEnvelope

    @GET("plan/latest")
    suspend fun latestPlan(): PlanEnvelope

    @POST("plan/swap")
    suspend fun swapMeal(@Body body: com.nutriai.data.remote.dto.SwapMealRequest): PlanEnvelope

    @POST("chat")
    suspend fun chat(@Body body: ChatRequest): ChatEnvelope

    @GET("adapt")
    suspend fun adaptation(): com.nutriai.data.remote.dto.AdaptEnvelope

    @GET("guidance")
    suspend fun guidance(): com.nutriai.data.remote.dto.GuidanceEnvelope

    @GET("cycle")
    suspend fun cycle(): com.nutriai.data.remote.dto.CycleEnvelope

    @POST("cycle/period")
    suspend fun logPeriod(@Body body: com.nutriai.data.remote.dto.LogPeriodRequest)

    @POST("cycle/end")
    suspend fun endPeriod(): Unit

    @GET("wellness")
    suspend fun wellness(): com.nutriai.data.remote.dto.Wellness

    @GET("wellness/recommend")
    suspend fun recommendWellness(@Query("mood") mood: Int?): com.nutriai.data.remote.dto.WellnessRecommendationEnvelope

    @POST("wellness/session")
    suspend fun logWellnessSession(@Body body: com.nutriai.data.remote.dto.WellnessSessionRequest): com.nutriai.data.remote.dto.WellnessSessionEnvelope

    @GET("wellness/history")
    suspend fun wellnessHistory(): com.nutriai.data.remote.dto.WellnessHistory

    @GET("wellness/suggest")
    suspend fun wellnessSuggest(
        @Query("mood") mood: Int?,
        @Query("energy") energy: Int?,
        @Query("sleep") sleep: Double?,
    ): com.nutriai.data.remote.dto.NowSuggestionEnvelope

    @GET("rating")
    suspend fun rating(): com.nutriai.data.remote.dto.RatingEnvelope

    @GET("coach/today")
    suspend fun coachToday(): com.nutriai.data.remote.dto.CoachTodayEnvelope

    @GET("reminders/prefs")
    suspend fun reminderPrefs(): com.nutriai.data.remote.dto.ReminderPrefsEnvelope

    @retrofit2.http.PUT("reminders/prefs")
    suspend fun putReminderPrefs(@Body body: com.nutriai.data.remote.dto.ReminderPrefsDto): com.nutriai.data.remote.dto.ReminderPrefsEnvelope

    @GET("discipline/today")
    suspend fun disciplineToday(): com.nutriai.data.remote.dto.DisciplineToday

    @POST("habits/{id}/toggle")
    suspend fun toggleHabit(
        @retrofit2.http.Path("id") id: String,
        @Body body: com.nutriai.data.remote.dto.HabitToggleRequest,
    ): com.nutriai.data.remote.dto.DisciplineToday

    // ---- AI vision ----
    @POST("body/assess-photo")
    suspend fun assessBodyPhoto(@Body body: com.nutriai.data.remote.dto.PhotoRequest): com.nutriai.data.remote.dto.BodyAssessEnvelope

    @POST("foods/photo")
    suspend fun mealPhoto(@Body body: com.nutriai.data.remote.dto.PhotoRequest): com.nutriai.data.remote.dto.MealPhotoResult

    // ---- Recipes ----
    @GET("recipe")
    suspend fun recipe(@Query("food") food: String, @Query("foodId") foodId: String?): com.nutriai.data.remote.dto.RecipeEnvelope

    // ---- Chat history ----
    @GET("chat/history")
    suspend fun chatHistory(): com.nutriai.data.remote.dto.ChatHistoryEnvelope

    // ---- Saved / recent foods ----
    @POST("foods/saved")
    suspend fun saveFood(@Body body: com.nutriai.data.remote.dto.SavedFoodRequest): com.nutriai.data.remote.dto.SavedFoodEnvelope

    @GET("foods/saved")
    suspend fun savedFoods(): com.nutriai.data.remote.dto.SavedFoodsEnvelope

    @retrofit2.http.DELETE("foods/saved/{id}")
    suspend fun deleteSavedFood(@Path("id") id: String)

    @GET("foods/recent")
    suspend fun recentFoods(): com.nutriai.data.remote.dto.RecentFoodsEnvelope

    @POST("adapt/apply")
    suspend fun applyAdaptation(): com.nutriai.data.remote.dto.AdaptApplyResponse

    @GET("foods")
    suspend fun foods(@Query("q") q: String?): FoodsEnvelope

    @GET("foods/search")
    suspend fun foodsSearch(@Query("q") q: String?): FoodsEnvelope

    @POST("logs/food")
    suspend fun logFood(@Body body: FoodLogRequest)

    @GET("logs/food")
    suspend fun todayLogs(): com.nutriai.data.remote.dto.FoodLogsEnvelope

    @retrofit2.http.DELETE("logs/food/{id}")
    suspend fun deleteFoodLog(@Path("id") id: String)

    @POST("logs/water")
    suspend fun logWater(@Body body: WaterLogRequest)

    @retrofit2.http.Streaming
    @GET("reports/weekly.pdf")
    suspend fun weeklyPdf(): okhttp3.ResponseBody

    // ---- Check-ins ----
    @POST("checkins")
    suspend fun createCheckin(@Body body: CheckinRequest): CheckinCreatedEnvelope

    @GET("checkins")
    suspend fun checkins(): CheckinsEnvelope

    // ---- Grocery ----
    @GET("grocery")
    suspend fun grocery(): GroceryEnvelope

    // ---- Reports ----
    @GET("reports/weekly.json")
    suspend fun weeklyReport(): ReportEnvelope

    @GET("report/view")
    suspend fun reportHtml(
        @retrofit2.http.Query("range") range: String,
        @retrofit2.http.Query("count") count: Int,
    ): com.nutriai.data.remote.dto.ReportHtmlResponse

    // ---- Gamification ----
    @GET("gamification")
    suspend fun gamification(): GamificationEnvelope

    // ---- Health risk ----
    @GET("risk")
    suspend fun risk(
        @retrofit2.http.Query("sleepHours") sleepHours: Double? = null,
        @retrofit2.http.Query("hydrationPct") hydrationPct: Double? = null,
    ): com.nutriai.data.remote.dto.RiskEnvelope

    // ---- Family ----
    @GET("family")
    suspend fun family(): FamilyEnvelope

    @POST("family")
    suspend fun addFamilyMember(@Body body: FamilyMemberRequest): FamilyMemberCreatedEnvelope

    @GET("family/{id}/calc")
    suspend fun familyCalc(@Path("id") id: String): CalcEnvelope

    // ---- Barcode ----
    @GET("barcode/{code}")
    suspend fun barcode(@Path("code") code: String): BarcodeEnvelope

    // ---- Workout ----
    @GET("exercise-plan")
    suspend fun exercisePlan(): com.nutriai.data.remote.dto.WorkoutEnvelope

    @POST("exercise-logs")
    suspend fun logExercise(@Body body: com.nutriai.data.remote.dto.ExerciseLogRequest): com.nutriai.data.remote.dto.ExerciseLogEnvelope

    @GET("exercise-logs")
    suspend fun exerciseLogs(@Query("date") date: String?): com.nutriai.data.remote.dto.ExerciseLogsEnvelope

    @GET("exercise-logs/last")
    suspend fun exerciseLast(): com.nutriai.data.remote.dto.LastPerformanceEnvelope

    @retrofit2.http.DELETE("exercise-logs/{id}")
    suspend fun deleteExerciseLog(@Path("id") id: String)

    // ---- Account ----
    @GET("auth/me")
    suspend fun me(): com.nutriai.data.remote.dto.MeResponse

    @retrofit2.http.DELETE("me")
    suspend fun deleteAccount()
}
