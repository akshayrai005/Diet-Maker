package com.nutriai.data.remote.dto

import kotlinx.serialization.Serializable

// ---- Auth ----
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class VerifyIdentityRequest(val email: String, val dob: String)

@Serializable
data class VerifyIdentityResponse(val verified: Boolean = false)

@Serializable
data class ResetPasswordRequest(val email: String, val dob: String, val newPassword: String)

@Serializable
data class Tokens(val accessToken: String, val refreshToken: String, val expiresIn: Long)

@Serializable
data class PublicUser(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
)

@Serializable
data class AuthResponse(val user: PublicUser, val tokens: Tokens)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokensResponse(val tokens: Tokens)

@Serializable
data class MeResponse(val user: PublicUser)

// ---- Profile ----
@Serializable
data class SensitiveData(
    val sex: String,
    val gender: String? = null,
    val genderSelfDescribe: String? = null,
    val occupation: String? = null,
    val budgetTier: String? = null,
    val dietStrictness: String? = null,
    val pantryTags: List<String>? = null,
    val fitnessLevel: String? = null,
    val intensityPreference: String? = null,
    val dob: String,
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val targetDate: String? = null,
    val waistCm: Double? = null,
    val conditions: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val desiredWeeklyLossKg: Double? = null,
    val fastDayOfWeek: Int? = null,
    val exerciseLocation: String? = null,
    val bodyGoal: String? = null,
    val workoutRestDay: Int? = null,
    val smoking: String? = null,
    val alcohol: String? = null,
    val contraception: String? = null,
    // Phase 4: family history of chronic conditions (diabetes, heart_disease, hypertension,
    // stroke, cancer, thyroid). Optional/additive — older payloads simply omit it.
    val familyHistory: List<String> = emptyList(),
    // Physique/training preferences. Optional/additive — the server ignores them when absent.
    // physiqueGoal ∈ recomp | lean_bulk | cut | maintain (null = not specified).
    // priorityMuscles ⊆ shoulders, back, chest, arms, legs, glutes, core (max 4).
    val physiqueGoal: String? = null,
    val priorityMuscles: List<String> = emptyList(),
)

// ---- Workout / exercise plan ----
@Serializable
data class NextSession(
    val suggestedWeightKg: Double? = null,
    val suggestedReps: Int,
    val suggestedSets: Int,
    val deload: Boolean = false,
    val rationale: String = "",
)

@Serializable
data class ExerciseItem(
    val name: String,
    val sets: Int,
    val reps: String,
    val type: String,
    val cue: String? = null,
    val muscleGroup: String? = null,
    val equipment: String? = null,
    val nextSession: NextSession? = null,
)

@Serializable
data class WorkoutDay(
    val dayIndex: Int,
    val date: String? = null,
    val label: String? = null,
    val focus: String,
    val rest: Boolean = false,
    val exercises: List<ExerciseItem> = emptyList(),
)

@Serializable
data class WeeklyWorkout(
    val location: String,
    val goal: String,
    val days: List<WorkoutDay> = emptyList(),
    val block: Int = 0,
    val blockLabel: String = "",
    val note: String = "",
    val disclaimer: String = "",
)

@Serializable
data class LevelSuggestion(val direction: String = "hold", val reason: String = "")

@Serializable
data class WorkoutEnvelope(val plan: WeeklyWorkout, val levelSuggestion: LevelSuggestion? = null)

// ---- Workout logging (performed sets) ----
@Serializable
data class ExerciseLogRequest(
    val exerciseName: String,
    val focus: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationMin: Int? = null,
    val notes: String? = null,
    val performedAt: String? = null,
)

@Serializable
data class ExerciseLogDto(
    val id: String,
    val performedAt: String,
    val exerciseName: String,
    val focus: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationMin: Int? = null,
    val kcal: Int? = null,
    val notes: String? = null,
)

@Serializable
data class ExerciseLogEnvelope(val entry: ExerciseLogDto)

@Serializable
data class ExerciseLogsEnvelope(val entries: List<ExerciseLogDto> = emptyList())

@Serializable
data class LastPerformance(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val sets: Int? = null,
    val performedAt: String? = null,
)

@Serializable
data class LastPerformanceEnvelope(val last: Map<String, LastPerformance> = emptyMap())

@Serializable
data class ProfileUpsertRequest(
    val heightCm: Double,
    val activityLevel: String,
    val goal: String,
    val dietType: String,
    val reducedMobility: Boolean = false,
    val sensitive: SensitiveData,
)

@Serializable
data class ProfileEnvelope(val profile: ProfileDto?)

@Serializable
data class ProfileDto(
    val id: String,
    val heightCm: Double,
    val activityLevel: String,
    val goal: String,
    val dietType: String,
    val reducedMobility: Boolean,
    val sensitive: SensitiveData? = null,
)

// ---- Calc ----
@Serializable
data class Flag(val code: String, val severity: String, val message: String)

@Serializable
data class CalcResult(
    val bmi: Double,
    val bmiCategory: String,
    val bmr: Double,
    val tdee: Double,
    val idealWeightMinKg: Double,
    val idealWeightMaxKg: Double,
    val bodyFatEstimate: Double,
    val dailyKcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbG: Double,
    val fiberG: Double,
    val waterMl: Double,
    val safeWeeklyDeltaKg: Double,
    val requiresSupervision: Boolean,
    val weightLossBlocked: Boolean,
    val flags: List<Flag> = emptyList(),
)

@Serializable
data class CalcEnvelope(val result: CalcResult?)

// ---- Dashboard ----
@Serializable
data class DashCalories(
    val consumed: Double,
    val target: Double? = null,
    val remaining: Double? = null,
    val percent: Double? = null,
)

@Serializable
data class DashMetric(val consumed: Double? = null, val consumedMl: Double? = null, val target: Double? = null, val targetMl: Double? = null, val percent: Double? = null)

@Serializable
data class WeightTrend(val latestKg: Double? = null, val firstKg: Double? = null, val deltaKg: Double? = null)

@Serializable
data class DashMacros(
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
)

@Serializable
data class ProjectionPoint(
    val label: String,
    val weeks: Int,
    val weightKg: Double,
    val bmi: Double,
)

@Serializable
data class Dashboard(
    val date: String,
    val calories: DashCalories,
    val protein: DashMetric,
    val water: DashMetric,
    val macros: DashMacros = DashMacros(),
    val streakDays: Int,
    val bmi: Double? = null,
    val weight: WeightTrend,
    val projection: List<ProjectionPoint> = emptyList(),
    val micronutrients: Micronutrients? = null,
)

@Serializable
data class MicronutrientTarget(
    val key: String,
    val label: String,
    val rda: Double,
    val intake: Double? = null,
    val pct: Int? = null,
    val low: Boolean = false,
)

/** A deficiency → food-source fix suggestion (may be empty/absent on older payloads). */
@Serializable
data class FoodFix(
    val key: String = "",
    val label: String = "",
    val foods: List<String> = emptyList(),
    val note: String = "",
)

@Serializable
data class Micronutrients(
    val available: Boolean = false,
    val note: String = "",
    val coveragePct: Int? = null,
    val targets: List<MicronutrientTarget> = emptyList(),
    val tips: List<String> = emptyList(),
    val foodFixes: List<FoodFix> = emptyList(),
)

@Serializable
data class FoodLogEntry(
    val id: String,
    val foodName: String,
    val mealSlot: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
)

@Serializable
data class FoodLogsEnvelope(val entries: List<FoodLogEntry> = emptyList())

@Serializable
data class DashboardEnvelope(val dashboard: Dashboard)

// ---- Plan ----
@Serializable
data class MealItem(val foodId: String, val name: String, val grams: Double, val kcal: Double, val proteinG: Double)

@Serializable
data class Meal(
    val slot: String,
    val items: List<MealItem>,
    val kcal: Double,
    val proteinG: Double,
    val friendliness: MealFriendliness? = null,
)

@Serializable
data class MealFriendliness(
    val diabetes: Int = 0,
    val heart: Int = 0,
    val gut: Int = 0,
    val inflammation: Int = 0,
    val highlights: List<String> = emptyList(),
)

@Serializable
data class DayTotals(val kcal: Double, val proteinG: Double, val carbG: Double, val fatG: Double, val fiberG: Double, val sodiumMg: Double)

@Serializable
data class DayPlan(
    val dayIndex: Int,
    val date: String? = null,
    val label: String? = null,
    val meals: List<Meal>,
    val totals: DayTotals,
)

@Serializable
data class PlanTargets(val dailyKcal: Double, val proteinG: Double, val planVersion: Int = 0)

@Serializable
data class PlanDto(val id: String? = null, val days: List<DayPlan> = emptyList(), val targets: PlanTargets? = null)

@Serializable
data class PlanEnvelope(val plan: PlanDto?)

@Serializable
data class SwapMealRequest(val dayIndex: Int, val slot: String)

// ---- Adaptive coaching ----
@Serializable
data class Adaptation(
    val status: String,
    val message: String,
    val suggestedKcalDelta: Int = 0,
    val avgLoggedKcal: Double? = null,
    val weeklyWeightChangeKg: Double? = null,
)

@Serializable
data class AdaptEnvelope(val adaptation: Adaptation)

@Serializable
data class AdaptApplyResponse(val applied: Boolean = false, val kcalDelta: Int = 0)

// ---- Personalized guidance (conditions / sex / lifestyle) ----
@Serializable
data class Guidance(
    val summary: String = "",
    val dietTips: List<String> = emptyList(),
    val exerciseTips: List<String> = emptyList(),
)

@Serializable
data class GuidanceEnvelope(val guidance: Guidance)

// ---- Menstrual cycle ----
@Serializable
data class CyclePhaseGuidance(
    val summary: String = "",
    val dietTips: List<String> = emptyList(),
    val exerciseTips: List<String> = emptyList(),
    val yogaTips: List<String> = emptyList(),
    val cravingTips: List<String> = emptyList(),
    val pmsTips: List<String> = emptyList(),
)

@Serializable
data class CycleFinding(val issue: String, val possibleCauses: String, val severity: String = "watch")

@Serializable
data class CycleHealthAdvice(
    val diet: List<String> = emptyList(),
    val sleep: List<String> = emptyList(),
    val lifestyle: List<String> = emptyList(),
    val exercise: List<String> = emptyList(),
)

@Serializable
data class CycleHealth(
    val status: String = "",
    val headline: String = "",
    val findings: List<CycleFinding> = emptyList(),
    val advice: CycleHealthAdvice = CycleHealthAdvice(),
    val seeDoctor: Boolean = false,
    val redFlags: List<String> = emptyList(),
    val disclaimer: String = "",
)

@Serializable
data class Cycle(
    val applicable: Boolean = false,
    val needsSetup: Boolean = false,
    val lastPeriodStart: String? = null,
    val cycleDay: Int? = null,
    val cycleLengthDays: Int? = null,
    val periodLengthDays: Int? = null,
    val periodOngoing: Boolean = false,
    val phase: String? = null,
    val phaseLabel: String? = null,
    val nextPeriodInDays: Int? = null,
    val shouldPromptPeriod: Boolean = false,
    val guidance: CyclePhaseGuidance? = null,
    val health: CycleHealth? = null,
)

@Serializable
data class CycleEnvelope(val cycle: Cycle)

@Serializable
data class LogPeriodRequest(val startDate: String? = null, val endDate: String? = null)

// ---- Wellness (yoga + meditation) ----
@Serializable
data class YogaPose(val name: String, val hold: String, val cue: String)

@Serializable
data class YogaFlow(
    val id: String,
    val name: String,
    val focus: String,
    val durationMin: Int,
    val level: String = "all",
    val poses: List<YogaPose> = emptyList(),
)

@Serializable
data class BreathPattern(
    val inhaleSec: Int = 0,
    val hold1Sec: Int = 0,
    val exhaleSec: Int = 0,
    val hold2Sec: Int = 0,
)

@Serializable
data class Meditation(
    val id: String,
    val name: String,
    val goal: String,
    val durationMin: Int,
    val steps: List<String> = emptyList(),
    val pattern: BreathPattern? = null,
)

@Serializable
data class Wellness(
    val yoga: List<YogaFlow> = emptyList(),
    val meditation: List<Meditation> = emptyList(),
)

@Serializable
data class WellnessRecommendation(
    val yoga: YogaFlow? = null,
    val meditation: Meditation? = null,
    val reason: String = "",
)

@Serializable
data class WellnessRecommendationEnvelope(val recommendation: WellnessRecommendation)

@Serializable
data class NowSuggestion(val yoga: YogaFlow? = null, val meditation: Meditation? = null, val reason: String = "")

@Serializable
data class NowSuggestionEnvelope(val suggestion: NowSuggestion = NowSuggestion())

// ---- Health rating + coach brief ----
@Serializable
data class RatingPillar(
    val key: String,
    val label: String,
    val weight: Double = 0.0,
    val score: Double = 0.0,
)

@Serializable
data class BiggestLever(val pillar: String = "", val message: String = "")

@Serializable
data class RatingResult(
    val overall: Int = 0,
    val grade: String = "",
    val pillars: List<RatingPillar> = emptyList(),
    val biggestLever: BiggestLever = BiggestLever(),
)

@Serializable
data class RatingEnvelope(val rating: RatingResult = RatingResult())

@Serializable
data class CoachBrief(
    val greeting: String = "",
    val ratingSummary: String = "",
    val dietFocus: String = "",
    val moveOrRest: String = "",
    val mindNudge: String = "",
    val disciplineActions: List<String> = emptyList(),
    val streak: String = "",
    val prediction: String = "",
    val disclaimer: String = "",
)

@Serializable
data class CoachTodayEnvelope(val brief: CoachBrief = CoachBrief(), val rating: RatingResult = RatingResult())

// ---- Reminder preferences (server mirror) ----
@Serializable
data class ReminderPrefsDto(
    val mealsEnabled: Boolean = true,
    val waterEnabled: Boolean = true,
    val workoutEnabled: Boolean = false,
    val weighInEnabled: Boolean = true,
    val walkEnabled: Boolean = false,
    val workoutTime: String? = null,
    val waterIntervalMin: Int = 60,
)

@Serializable
data class ReminderPrefsEnvelope(val prefs: ReminderPrefsDto = ReminderPrefsDto())

// ---- Discipline habits ----
@Serializable
data class AdherenceComponent(
    val key: String = "",
    val label: String = "",
    val points: Double = 0.0,
    val maxPoints: Double = 0.0,
    val detail: String = "",
)

@Serializable
data class Adherence(
    val score: Int = 0,
    val components: List<AdherenceComponent> = emptyList(),
    val topActions: List<String> = emptyList(),
)

@Serializable
data class HabitView(
    val id: String,
    val key: String = "",
    val title: String = "",
    val icon: String? = null,
    val doneToday: Boolean = false,
    val streakDays: Int = 0,
)

@Serializable
data class DisciplineToday(
    val adherence: Adherence = Adherence(),
    val habits: List<HabitView> = emptyList(),
    val review: String = "",
    val nudges: List<String> = emptyList(),
)

@Serializable
data class HabitToggleRequest(val done: Boolean, val date: String? = null)

@Serializable
data class HabitCreateRequest(val title: String, val icon: String? = null)

@Serializable
data class HabitCreateEnvelope(val habit: HabitView)

// ---- Wellness session logging ----
@Serializable
data class WellnessSessionRequest(val refId: String)

@Serializable
data class WellnessSessionDto(
    val id: String,
    val type: String,
    val refName: String,
    val durationMin: Int,
    val kcal: Int = 0,
    val completedAt: String,
)

@Serializable
data class WellnessSessionEnvelope(val session: WellnessSessionDto)

@Serializable
data class WellnessHistory(
    val streakDays: Int = 0,
    val weekCount: Int = 0,
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val totalKcal: Int = 0,
    val sessions: List<WellnessSessionDto> = emptyList(),
)

// ---- AI vision: body assessment + meal photo ----
@Serializable
data class PhotoRequest(val imageBase64: String, val mimeType: String = "image/jpeg")

@Serializable
data class BodyAssessment(
    val available: Boolean = false,
    val refused: Boolean = false,
    val reason: String? = null,
    val bodyFatLow: Double? = null,
    val bodyFatHigh: Double? = null,
    val category: String? = null,
    val notes: String? = null,
    val confidence: String? = null,
    val formulaEstimatePct: Double? = null,
    val source: String? = null, // "ai" (photo) or "formula" (from your stats)
    val disclaimer: String = "",
)

@Serializable
data class BodyAssessEnvelope(val assessment: BodyAssessment)

@Serializable
data class VisionFoodItem(val name: String, val grams: Double, val per100g: FoodLogPer100g)

@Serializable
data class MealPhotoResult(
    val available: Boolean = false,
    val items: List<VisionFoodItem> = emptyList(),
    val note: String = "",
)

// ---- Recipes ----
@Serializable
data class Recipe(
    val title: String = "",
    val timeMin: Int? = null,
    val servings: Int? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val source: String = "",
    val note: String? = null,
)

@Serializable
data class RecipeEnvelope(val recipe: Recipe)

// ---- Chat history ----
@Serializable
data class ChatMessageDto(val id: String, val role: String, val content: String, val createdAt: String)

@Serializable
data class ChatHistoryEnvelope(val messages: List<ChatMessageDto> = emptyList())

// ---- Saved / recent foods ----
@Serializable
data class SavedFoodRequest(
    val name: String,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
)

@Serializable
data class SavedFood(
    val id: String,
    val name: String,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
)

@Serializable
data class SavedFoodsEnvelope(val foods: List<SavedFood> = emptyList())

@Serializable
data class SavedFoodEnvelope(val food: SavedFood)

@Serializable
data class RecentFood(val name: String, val per100g: FoodLogPer100g)

@Serializable
data class RecentFoodsEnvelope(val foods: List<RecentFood> = emptyList())

// ---- Chat ----
@Serializable
data class ChatRequest(val message: String)

@Serializable
data class ChatReply(val intent: String, val reply: String, val sources: List<String> = emptyList())

@Serializable
data class ChatEnvelope(val reply: ChatReply)

// ---- Food logging ----
@Serializable
data class FoodLogPer100g(
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
)

@Serializable
data class FoodLogRequest(
    val mealSlot: String,
    val grams: Double,
    val foodId: String? = null,
    val foodName: String? = null,
    val per100g: FoodLogPer100g? = null,
    val entryMethod: String = "text",
)

@Serializable
data class WaterLogRequest(val amountMl: Int)

@Serializable
data class FoodDto(
    val id: String,
    val name: String,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val typicalServingG: Double = 100.0,
    val source: String = "local",
)

@Serializable
data class FoodsEnvelope(val foods: List<FoodDto> = emptyList())

// ---- Weekly check-ins ----
@Serializable
data class CheckinRequest(
    val weightKg: Double,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val hipCm: Double? = null,
    val energy: Int? = null,
    val sleepHours: Double? = null,
    val mood: Int? = null,
    val pain: Int? = null,
    val notes: String? = null,
)

@Serializable
data class Measurements(val weightKg: Double, val waistCm: Double? = null)

@Serializable
data class CheckinDto(
    val id: String,
    val date: String,
    val measurements: Measurements? = null,
    val energy: Int? = null,
    val sleepHours: Double? = null,
    val mood: Int? = null,
    val pain: Int? = null,
    val notes: String? = null,
)

@Serializable
data class CheckinsEnvelope(val checkins: List<CheckinDto> = emptyList())

@Serializable
data class CheckinCreated(val id: String, val date: String)

@Serializable
data class CheckinCreatedEnvelope(val checkin: CheckinCreated)

// ---- Grocery ----
@Serializable
data class GroceryLine(val name: String, val meals: Int = 1, val perServing: Int = 0, val qty: Int = 0, val unit: String = "g", val kcal: Int = 0)

@Serializable
data class GroceryCategory(val category: String, val items: List<GroceryLine> = emptyList())

@Serializable
data class Grocery(
    val categories: List<GroceryCategory> = emptyList(),
    val totalItems: Int = 0,
    val weeklyKcal: Int = 0,
    val targetWeeklyKcal: Int? = null,
)

@Serializable
data class GroceryEnvelope(val grocery: Grocery)

// ---- Health risk ----
@Serializable
data class RiskFinding(
    val id: String = "",
    val label: String = "",
    val level: String = "low", // low | moderate | high
    val why: String = "",
    val recommendation: String = "",
    val nextAction: String = "",
)

@Serializable
data class RiskAssessment(
    val findings: List<RiskFinding> = emptyList(),
    val overallScore: Int = 0,
    val disclaimer: String = "",
)

@Serializable
data class RiskEnvelope(val risk: RiskAssessment)

// ---- Reports ----
@Serializable
data class ReportDay(val date: String, val kcal: Double, val proteinG: Double)

@Serializable
data class ReportTargets(val dailyKcal: Double, val proteinG: Double, val waterMl: Double)

@Serializable
data class WeeklyReport(
    val name: String,
    val generatedAt: String,
    val targets: ReportTargets? = null,
    val bmi: Double? = null,
    val latestWeightKg: Double? = null,
    val weightDeltaKg: Double? = null,
    val days: List<ReportDay> = emptyList(),
    val avgKcal: Double? = null,
    val adherencePct: Double? = null,
    val disclaimer: String,
)

@Serializable
data class ReportEnvelope(val report: WeeklyReport)

@Serializable
data class ReportHtmlResponse(val html: String = "")

// ---- Gamification ----
@Serializable
data class Badge(val code: String, val title: String, val description: String, val earned: Boolean)

@Serializable
data class Gamification(val earnedCount: Int, val total: Int, val badges: List<Badge> = emptyList())

@Serializable
data class GamificationEnvelope(val gamification: Gamification)

// ---- Family ----
@Serializable
data class FamilyMemberRequest(
    val firstName: String,
    val relation: String? = null,
    val heightCm: Double,
    val activityLevel: String,
    val goal: String,
    val dietType: String,
    val sensitive: SensitiveData,
)

@Serializable
data class FamilyMemberDto(
    val id: String,
    val firstName: String,
    val relation: String? = null,
    val heightCm: Double? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
    val dietType: String? = null,
)

@Serializable
data class FamilyEnvelope(val members: List<FamilyMemberDto> = emptyList())

@Serializable
data class FamilyMemberCreated(val id: String, val firstName: String, val relation: String? = null)

@Serializable
data class FamilyMemberCreatedEnvelope(val member: FamilyMemberCreated)

// ---- Barcode ----
@Serializable
data class BarcodePer100g(
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
)

@Serializable
data class BarcodeFood(val code: String, val name: String, val per100g: BarcodePer100g)

@Serializable
data class BarcodeEnvelope(val food: BarcodeFood)

// ---- Vitals & labs ----
// A single logged reading. `value` and (`systolic`,`diastolic`) are mutually exclusive per type,
// so all three are nullable — blood pressure uses systolic/diastolic, everything else uses value.
@Serializable
data class VitalPoint(
    val id: String = "",
    val measuredAt: String = "",
    val value: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val note: String? = null,
)

/** Educational classification for a reading (band + severity + message + guideline citation). */
@Serializable
data class VitalClassification(
    val band: String = "",
    val severity: String = "", // normal | watch | elevated | high | urgent
    val message: String = "",
    val guideline: String = "",
)

@Serializable
data class VitalTrend(
    val direction: String = "insufficient", // rising | falling | stable | insufficient
    val change: Double = 0.0,
    val summary: String = "",
)

/** Classification fields plus the point they describe (server's `latest` on a series). */
@Serializable
data class VitalLatest(
    val band: String = "",
    val severity: String = "",
    val message: String = "",
    val guideline: String = "",
    val point: VitalPoint = VitalPoint(),
)

@Serializable
data class VitalSeries(
    val type: String = "",
    val label: String = "",
    val unit: String = "",
    val points: List<VitalPoint> = emptyList(),
    val trend: VitalTrend = VitalTrend(),
    val latest: VitalLatest? = null,
    val disclaimer: String = "",
)

@Serializable
data class VitalSummaryItem(
    val type: String = "",
    val label: String = "",
    val unit: String = "",
    val latest: VitalPoint = VitalPoint(),
    val classification: VitalClassification? = null,
)

@Serializable
data class VitalsSummaryResponse(
    val items: List<VitalSummaryItem> = emptyList(),
    val disclaimer: String = "",
)

@Serializable
data class LogVitalRequest(
    val type: String,
    val value: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val note: String? = null,
    val measuredAt: String? = null,
)

@Serializable
data class LogVitalResponse(
    val point: VitalPoint = VitalPoint(),
    val classification: VitalClassification? = null,
)

// ---- Medications & supplements ----
@Serializable
data class MedicationDto(
    val id: String = "",
    val type: String = "med", // "med" | "supplement"
    val active: Boolean = true,
    val name: String = "",
    val dose: String? = null,
    val times: List<String> = emptyList(), // "HH:mm"
    val notes: String? = null,
    val takenToday: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class MedicationsEnvelope(
    val medications: List<MedicationDto> = emptyList(),
    val disclaimer: String = "",
)

@Serializable
data class MedicationEnvelope(val medication: MedicationDto = MedicationDto())

@Serializable
data class MedicationRequest(
    val type: String,
    val name: String,
    val dose: String? = null,
    val times: List<String>? = null,
    val notes: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class MedicationActiveRequest(val active: Boolean)

// ---- Mood / stress / sleep check-in (Mind pillar) ----
@Serializable
data class MoodCheckinRequest(
    val date: String? = null,
    val mood: Int? = null,
    val stress: Int? = null,
    val sleepQuality: Int? = null,
    val notes: String? = null,
)

@Serializable
data class MoodPoint(
    val date: String = "",
    val mood: Int? = null,
    val stress: Int? = null,
    val sleepQuality: Int? = null,
)

@Serializable
data class MoodCheckinEnvelope(val checkin: MoodPoint = MoodPoint())

@Serializable
data class MoodInsightDto(
    val entryCount: Int = 0,
    val avgMood: Double? = null,
    val avgStress: Double? = null,
    val avgSleepQuality: Double? = null,
    val direction: String = "",
    val message: String = "",
    val professionalNudge: Boolean = false,
    val disclaimer: String = "",
)

@Serializable
data class MoodSeriesResponse(
    val series: List<MoodPoint> = emptyList(),
    val insight: MoodInsightDto = MoodInsightDto(),
)

// ---- Red-flag safety net (free-text symptom triage) ----
@Serializable
data class RedFlagRequest(val text: String)

@Serializable
data class RedFlagMatch(val id: String = "", val label: String = "")

@Serializable
data class RedFlagResponse(
    val urgent: Boolean = false,
    val matched: List<RedFlagMatch> = emptyList(),
    val message: String = "",
)

// ---- Progress / body (physique tracking) ----
// A single logged set of body measurements. Every measurement is optional (nullable) — the user
// may log only what they measured that day. `bodyFatPct` is hidden for minors on the client.
@Serializable
data class BodyPoint(
    val id: String = "",
    val measuredAt: String = "",
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val neckCm: Double? = null,
    val bodyFatPct: Double? = null,
)

/** First→last change for one measurement across the loaded range. */
@Serializable
data class BodyTrend(
    val key: String = "",
    val first: Double = 0.0,
    val last: Double = 0.0,
    val delta: Double = 0.0,
)

/** Waist-to-hip ratio with an educational band + note (never framed as an "ideal"). */
@Serializable
data class Whr(
    val ratio: Double = 0.0,
    val risk: String = "",
    val band: String = "",
    val note: String = "",
)

/** Latest body-fat percentage with its descriptive band. */
@Serializable
data class BodyFat(
    val pct: Double = 0.0,
    val band: String = "",
)

@Serializable
data class BodySeries(
    val points: List<BodyPoint> = emptyList(),
    val trends: List<BodyTrend> = emptyList(),
    val latestBodyFat: BodyFat? = null,
    val whr: Whr? = null,
    val isMinor: Boolean = false,
    val disclaimer: String = "",
)

/** Response of POST body/metrics: the saved point plus a freshly derived waist-to-hip ratio. */
@Serializable
data class BodyMetricResponse(
    val point: BodyPoint = BodyPoint(),
    val whr: Whr? = null,
)

/** Progress-photo metadata. The image bytes live ON DEVICE only (referenced by `localRef`). */
@Serializable
data class BodyPhotoDto(
    val id: String = "",
    val takenAt: String = "",
    val localRef: String = "",
    val caption: String? = null,
)

@Serializable
data class BodyPhotoEnvelope(val photo: BodyPhotoDto = BodyPhotoDto())

@Serializable
data class BodyPhotosEnvelope(val photos: List<BodyPhotoDto> = emptyList())

@Serializable
data class BodyMetricRequest(
    val measuredAt: String? = null,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val neckCm: Double? = null,
    val bodyFatPct: Double? = null,
)

@Serializable
data class BodyPhotoRequest(
    val localRef: String,
    val caption: String? = null,
)
