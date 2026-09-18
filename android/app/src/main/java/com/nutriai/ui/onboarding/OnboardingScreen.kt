package com.nutriai.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.remote.dto.ProfileUpsertRequest
import com.nutriai.data.remote.dto.SensitiveData
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors

private val SEX = listOf("male" to "Male", "female" to "Female")
private val GENDER = listOf(
    "male" to "Male",
    "female" to "Female",
    "nonbinary" to "Non-binary",
    "self_describe" to "Prefer to self-describe",
    "prefer_not" to "Prefer not to say",
)
private val OCCUPATION = listOf(
    "student" to "Student",
    "desk" to "Desk / office",
    "on_feet" to "On my feet",
    "homemaker" to "Homemaker",
    "other" to "Other",
)
private val BUDGET = listOf(
    "low" to "Tight budget (cheapest foods)",
    "medium" to "Moderate",
    "flexible" to "Flexible",
)
private val STRICTNESS = listOf(
    "relaxed" to "Relaxed",
    "standard" to "Standard",
    "strict" to "Strict",
)
private val KITCHEN = listOf(
    "stove" to "Full kitchen (stove/gas)",
    "microwave" to "Microwave only",
    "kettle" to "Electric kettle / hot water only",
    "none" to "No cooking (assemble only)",
)
private val LIVING = listOf(
    "home" to "Home",
    "pg" to "PG / rented room",
    "hostel" to "Hostel / mess",
    "travel" to "Travelling",
)
private val FITNESS_LEVEL = listOf(
    "beginner" to "Beginner",
    "intermediate" to "Intermediate",
    "advanced" to "Advanced",
)
private val INTENSITY = listOf(
    "easy" to "Easy",
    "standard" to "Standard",
    "hard" to "Hard",
    "beast" to "Beast",
)
private val ACTIVITY = listOf(
    "sedentary" to "Sedentary (desk job)",
    "light" to "Lightly active",
    "moderate" to "Moderately active",
    "active" to "Active",
    "veryactive" to "Very active",
)
private val GOAL = listOf("lose" to "Lose weight", "maintain" to "Maintain", "gain" to "Gain muscle")
// Timeframe options as (months, label). Months → weeks uses month × 4.345 (avg weeks/month), rounded.
private val TIMEFRAMES: List<Pair<Int, String>> = listOf(
    1 to "1 month", 2 to "2 months", 3 to "3 months", 6 to "6 months", 12 to "12 months",
)

/** Average-length month → whole weeks (month × 4.345, rounded): 1→4, 2→9, 3→13, 6→26, 12→52. */
private fun monthsToWeeks(months: Int): Int = Math.round(months * 4.345).toInt()
private val DIET = listOf(
    "veg" to "Vegetarian", "eggetarian" to "Eggetarian", "nonveg" to "Non-veg",
    "vegan" to "Vegan", "jain" to "Jain", "keto" to "Keto", "highprotein" to "High-protein",
)
private val EX_LOC = listOf("gym" to "Gym", "home" to "Home", "none" to "No workouts")
private val BODY_GOAL = listOf("fatloss" to "Fat loss", "athletic" to "Athletic / lean", "muscular" to "Muscular")
// Training split: value → plain-language, body-neutral label. null = auto (server picks: mixed
// full-body sessions for your first month at the gym, then a body-part split as you advance).
private val TRAINING_SPLIT: List<Pair<String?, String>> = listOf(
    null to "Auto (mixed for month 1, then body-part split as you advance)",
    "full_body" to "Full-body mixed session - machines, dumbbells, cardio in one visit (recommended for month 1)",
    "body_part" to "Body-part split - Chest / Back / Shoulders / Arms / Legs (once training feels routine, ~1 month+)",
    "push_pull_legs" to "Push / Pull / Legs",
    "upper_lower" to "Upper / Lower",
    "fat_loss" to "Fat-loss circuits",
)
private val DAYS: List<Pair<Int?, String>> = listOf(
    null to "None", 0 to "Sunday", 1 to "Monday", 2 to "Tuesday",
    3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday",
)
// Office/lifestyle eating pattern (spec Section 6) → drives server meal-slot distribution.
private val EATING_PATTERN: List<Pair<String?, String>> = listOf(
    null to "Not sure / skip",
    "morning_night" to "Morning + night only",
    "home" to "Home all day",
    "office_canteen" to "Office (with canteen)",
    "office_no_canteen" to "Office (no canteen)",
    "field" to "Field job / travelling",
    "night_shift" to "Night shift",
    "omad" to "One meal a day",
    "religious_fasting" to "Religious fasting",
)
// Gym membership duration (spec Section 5) → progressive-overload phase.
private val GYM_MONTHS: List<Pair<Int?, String>> = listOf(
    null to "Not a member", 1 to "1 month", 3 to "3 months", 6 to "6 months", 12 to "12 months",
)
private val CONDITIONS = listOf("diabetes", "hypertension", "kidney_disease", "thyroid", "pcos", "heart_disease", "fatty_liver", "gout")
private val FAMILY_HISTORY = listOf("diabetes", "heart_disease", "hypertension", "stroke", "cancer", "thyroid")
// Physique goal: value → (label, body-neutral, plain-language description). Never framed around appearance/shame.
private val PHYSIQUE_GOAL: List<Pair<String, Pair<String, String>>> = listOf(
    "recomp" to ("Recomp" to "Build muscle and lose fat at the same time (maintenance calories, high protein)."),
    "lean_bulk" to ("Lean bulk" to "Gain muscle slowly with a slight calorie surplus."),
    "cut" to ("Cut" to "Lose fat while keeping muscle (a safe calorie deficit)."),
    "maintain" to ("Maintain" to "Keep your current physique."),
)
private val PRIORITY_MUSCLES = listOf("shoulders", "back", "chest", "arms", "legs", "glutes", "core")
private const val MAX_PRIORITY_MUSCLES = 7
private val FREQ = listOf("no" to "No", "occasional" to "Occasionally", "regular" to "Regularly")
private val CONTRA = listOf(
    "none" to "None",
    "pill" to "The pill",
    "hormonal_iud" to "Hormonal IUD",
    "implant" to "Implant",
    "injection" to "Injection",
    "other" to "Other",
)

private val STEP_COLORS = listOf(KaizenBlue, BrandGreen, MovementColor, KaizenCoral, KaizenLavender)

/**
 * Group title as its own compact chip, separate from the content below it - explicit request:
 * "i want your body as a title in different card and then this body type card small.... similar
 * for all". Applies to every group the same way, so the whole page reads as title-chip → content
 * card, title-chip → content card, instead of one big tinted box holding both.
 */
@Composable
private fun GroupTitleChip(title: String, emoji: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 15.sp)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
    }
}

/**
 * Compact content card for the single-page layout - paired with [GroupTitleChip] above it. A
 * soft accent-tinted fill + a solid color spine on the left reads as one clean card instead of a
 * thin outlined box (explicit feedback: "i dont like this thin thin border").
 */
@Composable
private fun BorderedGroup(title: String, emoji: String, accent: Color = BrandGreen, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitleChip(title, emoji, accent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.03f)))),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * Stacks each field with a thin divider between rows - explicit feedback: "dont find border
 * between rows" (they want row separators inside a group, just not a thick box border around it).
 */
@Composable
private fun RowDivided(vararg rows: @Composable () -> Unit) {
    rows.forEachIndexed { i, row ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        row()
    }
}

// Fixed focus rotation the server uses for a "muscular" (body-part split) program block - same
// order at every gym-goer's mesocycle block, only the exercises inside each focus rotate every 4
// weeks (see workoutGenerator.ts PROGRAMS['muscular:*']). Shown as a read-only Day → Part preview
// so "body part split" answers "which day / which part" instead of staying a mystery.
private val BODY_PART_FOCUS_GYM = listOf("Chest", "Back", "Shoulders", "Biceps & Forearms", "Triceps & Core", "Legs & Abs")
private val BODY_PART_FOCUS_HOME = listOf("Push (Chest/Shoulders/Triceps)", "Pull (Back/Biceps)", "Legs & Abs")
private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Computes the Sun-Sat → focus (or "Rest") preview for a body-part split, matching server rotation. */
private fun bodyPartDayPreview(restDay: Int?, atGym: Boolean): List<Pair<String, String>> {
    val focuses = if (atGym) BODY_PART_FOCUS_GYM else BODY_PART_FOCUS_HOME
    var t = 0
    return (0..6).map { weekday ->
        val label = WEEKDAY_LABELS[weekday]
        if (weekday == restDay) label to "Rest" else {
            val focus = focuses[t % focuses.size]
            t++
            label to focus
        }
    }
}

/**
 * Editable 7-row Day → Body part table shown once "Body-part split" is chosen - each row is a
 * tick-dropdown the user can actually set (explicit feedback: "i am not able to set" / "why the
 * hell am i not able to change as per my plan"). Defaults to the server's normal auto-rotation
 * until the user overrides a day; overrides are saved in `overrides` and sent to the server, which
 * uses them instead of the auto-rotation for that weekday (see workoutGenerator.ts dayFocusOverride).
 */
@Composable
private fun BodyPartDayTable(restDay: Int?, atGym: Boolean, overrides: MutableMap<Int, String>, onOverride: (Int, String) -> Unit) {
    val defaults = remember(restDay, atGym) { bodyPartDayPreview(restDay, atGym) }
    val focusChoices = remember(atGym) { (if (atGym) BODY_PART_FOCUS_GYM else BODY_PART_FOCUS_HOME) + "Rest" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        defaults.forEachIndexed { weekday, (day, autoFocus) ->
            val focus = overrides[weekday] ?: autoFocus
            val isRest = focus == "Rest"
            if (weekday > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (isRest) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MovementColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isRest) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                }
                Box(Modifier.weight(1f)) {
                    TickDropdown(
                        label = day,
                        options = focusChoices.map { it to it },
                        selected = focus,
                        onSelect = { onOverride(weekday, it) },
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var targetWaist by remember { mutableStateOf("") }
    var targetChest by remember { mutableStateOf("") }
    var targetArm by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("male") }
    var gender by remember { mutableStateOf("male") }
    var genderSelfDescribe by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("desk") }
    var budgetTier by remember { mutableStateOf("medium") }
    var dietStrictness by remember { mutableStateOf("standard") }
    var kitchen by remember { mutableStateOf("stove") }
    var livingSituation by remember { mutableStateOf("home") }
    var fitnessLevel by remember { mutableStateOf("beginner") }
    var intensity by remember { mutableStateOf("standard") }
    var activity by remember { mutableStateOf("moderate") }
    var goal by remember { mutableStateOf("lose") }
    var timeframeWeeks by remember { mutableStateOf<Int?>(null) }
    var diet by remember { mutableStateOf("nonveg") }
    val conditions = remember { mutableStateListOf<String>() }
    val familyHistory = remember { mutableStateListOf<String>() }
    var fastDay by remember { mutableStateOf<Int?>(null) }
    var exLocation by remember { mutableStateOf("home") }
    var bodyGoal by remember { mutableStateOf("fatloss") }
    var trainingSplit by remember { mutableStateOf<String?>(null) }
    var workoutRest by remember { mutableStateOf<Int?>(0) }
    var smoking by remember { mutableStateOf("no") }
    var alcohol by remember { mutableStateOf("no") }
    var contraception by remember { mutableStateOf("none") }
    var physiqueGoal by remember { mutableStateOf<String?>(null) }
    val priorityMuscles = remember { mutableStateListOf<String>() }
    var eatingPattern by remember { mutableStateOf<String?>(null) }
    var bodyTypeCurrent by remember { mutableStateOf<String?>(null) }
    var bodyTypeGoal by remember { mutableStateOf<String?>(null) }
    var gymJoinDate by remember { mutableStateOf("") }
    var gymMonths by remember { mutableStateOf<Int?>(null) }
    val bodyPartDayFocus = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(state.prefillLoaded) {
        val p = state.prefill ?: return@LaunchedEffect
        height = p.heightCm.takeIf { it > 0 }?.let { fmt(it) } ?: height
        activity = p.activityLevel.ifBlank { activity }
        goal = p.goal.ifBlank { goal }
        diet = p.dietType.ifBlank { diet }
        p.sensitive?.let { s ->
            weight = fmt(s.currentWeightKg)
            target = fmt(s.targetWeightKg)
            waist = s.waistCm?.let { fmt(it) } ?: waist
            neck = s.neckCm?.let { fmt(it) } ?: neck
            hip = s.hipCm?.let { fmt(it) } ?: hip
            chest = s.chestCm?.let { fmt(it) } ?: chest
            arm = s.armCm?.let { fmt(it) } ?: arm
            targetWaist = s.targetWaistCm?.let { fmt(it) } ?: targetWaist
            targetChest = s.targetChestCm?.let { fmt(it) } ?: targetChest
            targetArm = s.targetArmCm?.let { fmt(it) } ?: targetArm
            dob = s.dob
            sex = s.sex.ifBlank { sex }
            gender = s.gender ?: s.sex.ifBlank { gender }
            genderSelfDescribe = s.genderSelfDescribe ?: genderSelfDescribe
            occupation = s.occupation ?: occupation
            budgetTier = s.budgetTier ?: budgetTier
            dietStrictness = s.dietStrictness ?: dietStrictness
            kitchen = s.kitchen ?: kitchen
            livingSituation = s.livingSituation ?: livingSituation
            fitnessLevel = s.fitnessLevel ?: fitnessLevel
            intensity = s.intensityPreference ?: intensity
            conditions.clear(); conditions.addAll(s.conditions)
            familyHistory.clear(); familyHistory.addAll(s.familyHistory)
            fastDay = s.fastDayOfWeek
            exLocation = s.exerciseLocation ?: exLocation
            bodyGoal = s.bodyGoal ?: bodyGoal
            trainingSplit = s.trainingSplit
            workoutRest = s.workoutRestDay ?: workoutRest
            smoking = s.smoking ?: smoking
            alcohol = s.alcohol ?: alcohol
            contraception = s.contraception ?: contraception
            physiqueGoal = s.physiqueGoal
            priorityMuscles.clear(); priorityMuscles.addAll(s.priorityMuscles.take(MAX_PRIORITY_MUSCLES))
            timeframeWeeks = s.targetTimeframeWeeks ?: timeframeWeeks
            eatingPattern = s.eatingPattern ?: eatingPattern
            bodyTypeCurrent = s.bodyTypeCurrent ?: bodyTypeCurrent
            bodyTypeGoal = s.bodyTypeGoal ?: bodyTypeGoal
            gymJoinDate = s.gymJoinDate ?: gymJoinDate
            gymMonths = s.gymMembershipMonths ?: gymMonths
            bodyPartDayFocus.clear()
            s.bodyPartDayFocus?.forEach { (k, v) -> k.toIntOrNull()?.let { bodyPartDayFocus[it] = v } }
        }
    }

    // Live, debounced safe-pace preview: whenever a valid target + timeframe are set, ask the server.
    LaunchedEffect(target, timeframeWeeks) {
        val t = target.toDoubleOrNull()
        val w = timeframeWeeks
        if (t != null && w != null) {
            kotlinx.coroutines.delay(450)
            viewModel.previewTimeline(t, w)
        } else {
            viewModel.clearTimeline()
        }
    }
    val age = remember(dob) { ageFromDob(dob) }
    val isMinor = age != null && age < 18
    val physiqueOptions = remember(isMinor) {
        if (isMinor) PHYSIQUE_GOAL.filterNot { it.first == "cut" } else PHYSIQUE_GOAL
    }
    val editing = state.prefill != null
    val canSave = height.toDoubleOrNull() != null && weight.toDoubleOrNull() != null &&
        target.toDoubleOrNull() != null && dob.isNotBlank()

    fun doSave() {
        val h = height.toDoubleOrNull(); val w = weight.toDoubleOrNull(); val t = target.toDoubleOrNull()
        if (h != null && w != null && t != null && dob.isNotBlank()) {
            viewModel.save(
                ProfileUpsertRequest(
                    heightCm = h,
                    activityLevel = activity,
                    goal = goal,
                    dietType = diet,
                    sensitive = SensitiveData(
                        sex = sex,
                        gender = gender,
                        genderSelfDescribe = if (gender == "self_describe") genderSelfDescribe.ifBlank { null } else null,
                        occupation = occupation,
                        budgetTier = budgetTier,
                        dietStrictness = dietStrictness,
                        kitchen = kitchen,
                        livingSituation = livingSituation,
                        fitnessLevel = fitnessLevel,
                        intensityPreference = intensity,
                        dob = dob.trim(),
                        currentWeightKg = w,
                        targetWeightKg = t,
                        waistCm = waist.toDoubleOrNull(),
                        neckCm = neck.toDoubleOrNull(),
                        hipCm = hip.toDoubleOrNull(),
                        chestCm = chest.toDoubleOrNull(),
                        armCm = arm.toDoubleOrNull(),
                        targetWaistCm = targetWaist.toDoubleOrNull(),
                        targetChestCm = targetChest.toDoubleOrNull(),
                        targetArmCm = targetArm.toDoubleOrNull(),
                        conditions = conditions.toList(),
                        familyHistory = familyHistory.toList(),
                        fastDayOfWeek = fastDay,
                        exerciseLocation = exLocation,
                        bodyGoal = bodyGoal,
                        trainingSplit = trainingSplit,
                        workoutRestDay = workoutRest,
                        smoking = smoking,
                        alcohol = alcohol,
                        contraception = if (sex == "female") contraception else null,
                        physiqueGoal = physiqueGoal,
                        priorityMuscles = priorityMuscles.toList(),
                        targetTimeframeWeeks = timeframeWeeks,
                        eatingPattern = eatingPattern,
                        bodyTypeCurrent = bodyTypeCurrent,
                        bodyTypeGoal = bodyTypeGoal,
                        gymJoinDate = gymJoinDate.trim().ifBlank { null },
                        gymMembershipMonths = gymMonths,
                        bodyPartDayFocus = if (bodyPartDayFocus.isEmpty()) null else bodyPartDayFocus.mapKeys { it.key.toString() },
                    ),
                ),
                onDone,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.kaizenColors.pageBackground).padding(Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Single header - no step count, no progress bar, no dots. One page, scroll through it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(BrandGreen, MovementColor)))
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👤", fontSize = 26.sp)
                }
                Column {
                    Text(
                        if (editing) "Edit your profile" else "Complete your profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Text(
                        "One page, scroll to fill in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- About you ----
            run {
                    // Visual body-type selector (spec Section 4) - shape now → shape you're working toward.
                    BorderedGroup("Your body type", "🧍", accent = STEP_COLORS[0]) {
                        com.nutriai.ui.bodytype.BodyTypeInlinePicker(
                            currentId = bodyTypeCurrent,
                            goalId = bodyTypeGoal,
                            onCurrent = { bodyTypeCurrent = it },
                            onGoal = { bodyTypeGoal = it },
                        )
                    }
                    BorderedGroup("Measurements - where you are now", "📏", accent = STEP_COLORS[0]) {
                        RowDivided(
                            { numberField(height, { height = it }, "Height (cm)") },
                            { numberField(weight, { weight = it }, "Current weight (kg)") },
                            { numberField(waist, { waist = it }, "Waist (cm) - navel") },
                            { numberField(chest, { chest = it }, "Chest (cm) - optional") },
                            { numberField(arm, { arm = it }, "Arm / bicep (cm) - optional") },
                            { numberField(neck, { neck = it }, "Neck (cm) - optional") },
                        )
                        if (sex == "female") numberField(hip, { hip = it }, "Hip (cm) - optional")
                    }
                    BorderedGroup("Desired - where you want to be", "🎯", accent = STEP_COLORS[0]) {
                        RowDivided(
                            { numberField(target, { target = it }, "Target weight (kg)") },
                            { numberField(targetWaist, { targetWaist = it }, "Target waist (cm) - optional") },
                            { numberField(targetChest, { targetChest = it }, "Target chest (cm) - optional") },
                            { numberField(targetArm, { targetArm = it }, "Target arm / bicep (cm) - optional") },
                        )
                    }
                    BorderedGroup("Identity", "🪪", accent = STEP_COLORS[0]) {
                        RowDivided(
                            { DobPicker(dob) { dob = it } },
                            { Dropdown("Gender", GENDER, gender) { gender = it } },
                            { Dropdown("Sex for health calculations", SEX, sex) { sex = it } },
                        )
                        if (gender == "self_describe") {
                            OutlinedTextField(
                                value = genderSelfDescribe,
                                onValueChange = { genderSelfDescribe = it.take(40) },
                                label = { Text("Describe (optional)") },
                                singleLine = true,
                                colors = fieldBorderColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
            }

            // ---- Your goals ----
            run {
                    BorderedGroup("Goals & Preferences", "🎯", accent = STEP_COLORS[1]) {
                        Dropdown("Goal", GOAL, goal) { goal = it }
                    }

                    BorderedGroup("Timeframe", "⏰", accent = STEP_COLORS[1]) {
                        TimeframeChips(timeframeWeeks) { timeframeWeeks = it }
                        if (state.timeline != null) {
                            TimelinePreviewCard(state.timeline!!)
                        }
                        Text(
                            "📅 We'll pace it safely.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    BorderedGroup("Diet & Lifestyle", "🍽️", accent = STEP_COLORS[1]) {
                        RowDivided(
                            { Dropdown("Diet", DIET, diet) { diet = it } },
                            { Dropdown("Eating pattern", EATING_PATTERN, eatingPattern) { eatingPattern = it } },
                            { Dropdown("Activity level", ACTIVITY, activity) { activity = it } },
                            { Dropdown("Occupation", OCCUPATION, occupation) { occupation = it } },
                            { Dropdown("Food budget", BUDGET, budgetTier) { budgetTier = it } },
                            { Dropdown("Plan strictness", STRICTNESS, dietStrictness) { dietStrictness = it } },
                            { Dropdown("Living situation", LIVING, livingSituation) { livingSituation = it } },
                            { Dropdown("Kitchen access", KITCHEN, kitchen) { kitchen = it } },
                        )
                    }
            }

            // ---- Movement ----
            run {
                    BorderedGroup("Exercise Setup", "🏋️", accent = STEP_COLORS[2]) {
                        RowDivided(
                            { Dropdown("Where do you exercise?", EX_LOC, exLocation) { exLocation = it } },
                            { Dropdown("Body goal", BODY_GOAL, bodyGoal) { bodyGoal = it } },
                        )
                    }

                    BorderedGroup("Training Split", "📊", accent = STEP_COLORS[2]) {
                        TickDropdown("Training split", TRAINING_SPLIT, trainingSplit) { trainingSplit = it }
                        if (trainingSplit == "body_part") {
                            Text(
                                "Tap any day to set its part:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MovementColor,
                            )
                            BodyPartDayTable(
                                restDay = workoutRest,
                                atGym = exLocation != "home" && exLocation != "none",
                                overrides = bodyPartDayFocus,
                                onOverride = { day, focus -> bodyPartDayFocus[day] = focus },
                            )
                        }
                        Text(
                            "💡 Your Move plan updates to this.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    BorderedGroup("Intensity & Rest", "⚡", accent = STEP_COLORS[2]) {
                        RowDivided(
                            { Dropdown("Fitness level", FITNESS_LEVEL, fitnessLevel) { fitnessLevel = it } },
                            { Dropdown("Workout intensity", INTENSITY, intensity) { intensity = it } },
                            { Dropdown("Workout rest day", DAYS, workoutRest) { workoutRest = it } },
                        )
                    }

                    // Gym membership (spec Section 5) → progressive-overload phase (Foundation→Peak).
                    BorderedGroup("Gym Membership", "🏢", accent = STEP_COLORS[2]) {
                        Dropdown("Membership duration", GYM_MONTHS, gymMonths) { gymMonths = it }
                        if (gymMonths != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            DobPicker(gymJoinDate, label = "Gym join date") { gymJoinDate = it }
                        }
                    }
            }

            // ---- Health ----
            run {
                    BorderedGroup("Health Conditions", "🏥", accent = STEP_COLORS[3]) {
                        Label("Conditions (optional)")
                        MultiChoiceChips(CONDITIONS, conditions)
                    }
                    BorderedGroup("Family History", "👨‍👩‍👧‍👦", accent = STEP_COLORS[3]) {
                        MultiChoiceChips(FAMILY_HISTORY, familyHistory)
                        Text(
                            "Runs in your close family?",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BorderedGroup("Lifestyle", "🌿", accent = STEP_COLORS[3]) {
                        RowDivided(
                            { Dropdown("Weekly fasting day (optional)", DAYS, fastDay) { fastDay = it } },
                            { Dropdown("Do you smoke?", FREQ, smoking) { smoking = it } },
                            { Dropdown("Do you drink alcohol?", FREQ, alcohol) { alcohol = it } },
                            { if (sex == "female") Dropdown("Contraception (if any)", CONTRA, contraception) { contraception = it } },
                        )
                    }

                    BorderedGroup("Physique Goal", "🎯", accent = STEP_COLORS[3]) {
                        TickDropdown(
                            label = "Physique goal",
                            options = physiqueOptions.map { (value, ld) -> value as String? to ld.first } + (null to "Not sure / skip"),
                            selected = physiqueGoal,
                            descriptions = physiqueOptions.associate { (value, ld) -> (value as String?) to ld.second } + (null to "We'll pick balanced, healthy targets for you."),
                        ) { physiqueGoal = it }
                        if (isMinor) {
                            FeatureCard(emoji = "🛡️", title = "Under-18 Safety", accentColor = BrandAmber) {
                                Text(
                                    "Kept safe for your age - no aggressive cutting.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(
                            "💚 Only tunes your targets - every option is healthy.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    BorderedGroup("Priority Muscles", "💪", accent = STEP_COLORS[3]) {
                        PriorityMusclesChips(PRIORITY_MUSCLES, priorityMuscles, MAX_PRIORITY_MUSCLES)
                        Text(
                            "Pick any for extra focus.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }

            // ---- Ready ----
            run {
                    FeatureCard(emoji = "🎉", title = "You're All Set!", accentColor = BrandGreen) {
                        Text(
                            "Small habits, big results. 🚀",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("📊 Your Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandGreen)
                            Text(
                                "🎯 Goal: ${labelOf(GOAL, goal)}  ·  🍽️ Diet: ${labelOf(DIET, diet)}\n💪 Level: ${labelOf(FITNESS_LEVEL, fitnessLevel)}  ·  ⚡ Intensity: ${labelOf(INTENSITY, intensity)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
            }
        }

        state.error?.let {
            FeatureCard(emoji = "❌", title = "Error", accentColor = KaizenCoral) {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!canSave) {
            GlassCard {
                Text(
                    "📝 Fill height, weight, target and date of birth to continue - scroll up to \"About you\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (!state.loading && canSave) Brush.horizontalGradient(listOf(BrandGreen, MovementColor))
                    else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)),
                )
                .clickable(enabled = !state.loading && canSave) { doSave() },
            contentAlignment = Alignment.Center,
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(4.dp).size(24.dp), color = Color.White)
            else Text(
                if (editing) "✅ Save changes" else "🚀 Create my plan",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun <T> labelOf(options: List<Pair<T, String>>, value: T): String =
    options.firstOrNull { it.first == value }?.second ?: ""

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Date-of-birth field backed by a calendar picker (no manual typing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DobPicker(dob: String, label: String = "Date of birth", onDob: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = dob,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("📅 Tap to pick") },
            trailingIcon = { androidx.compose.material3.Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) },
            colors = fieldBorderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole (read-only) field opens the calendar.
        Box(Modifier.matchParentSize().clickable { show = true })
    }
    if (show) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDob(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

/** Border colors shared by every field box - explicit feedback: "not getting proper border of
 * each boxes inside the card" (Material3's default unfocused border is too faint to read). */
@Composable
private fun fieldBorderColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color(0xFF5C6B7A),
    focusedBorderColor = BrandGreen,
    unfocusedLabelColor = Color(0xFF5C6B7A),
)

@Composable
private fun numberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldBorderColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

/** A labelled dropdown (Material3 exposed menu) for single-choice selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Select"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = fieldBorderColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, disp) ->
                DropdownMenuItem(text = { Text(disp) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

private val chipBorderColor = Color(0xFF5C6B7A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChoiceChips(options: List<String>, selected: MutableList<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val isSel = selected.contains(opt)
            FilterChip(
                selected = isSel,
                onClick = { if (isSel) selected.remove(opt) else selected.add(opt) },
                label = { Text(opt.replace('_', ' ')) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaizenCoral,
                    selectedLabelColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSel, borderColor = chipBorderColor, selectedBorderColor = chipBorderColor),
            )
        }
    }
}

/**
 * Dropdown-with-checkmark for a single choice, optionally with a one-line description per option -
 * explicit request: "give this multiple choices in dropdown also give tick to select" (was a tall
 * radio-button list before). Used for Training Split and Physique Goal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> TickDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    descriptions: Map<T, String> = emptyMap(),
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Select"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = fieldBorderColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, disp) ->
                val isSel = value == selected
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(disp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            descriptions[value]?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    leadingIcon = if (isSel) {
                        { androidx.compose.material3.Icon(Icons.Filled.Check, contentDescription = "Selected", tint = BrandGreen) }
                    } else null,
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

/** Multi-select muscle chips capped at [max]; chips past the cap are disabled until one is freed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityMusclesChips(options: List<String>, selected: MutableList<String>, max: Int) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val isSel = selected.contains(opt)
            val atLimit = selected.size >= max
            val display = opt.replaceFirstChar { it.uppercase() }
            FilterChip(
                selected = isSel,
                enabled = isSel || !atLimit,
                onClick = { if (isSel) selected.remove(opt) else if (!atLimit) selected.add(opt) },
                label = { Text(display) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = if (isSel) "$display, selected" else display },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MovementColor,
                    selectedLabelColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSel, borderColor = chipBorderColor, selectedBorderColor = chipBorderColor),
            )
        }
    }
}

/** Single-select timeframe chips (1/2/3/6/12 months). Emits the chosen months converted to weeks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeframeChips(selectedWeeks: Int?, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TIMEFRAMES.forEach { (months, label) ->
            val weeks = monthsToWeeks(months)
            val isSel = selectedWeeks == weeks
            FilterChip(
                selected = isSel,
                onClick = { onSelect(weeks) },
                label = { Text(label) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = if (isSel) "$label, selected timeframe" else "$label timeframe"
                    },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandGreen,
                    selectedLabelColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSel, borderColor = chipBorderColor, selectedBorderColor = chipBorderColor),
            )
        }
    }
}

/**
 * Body-neutral preview of the server's safe-pace assessment.
 */
@Composable
private fun TimelinePreviewCard(timeline: com.nutriai.data.remote.dto.GoalTimeline) {
    val blocked = timeline.blocked
    val accentColor = if (blocked) BrandAmber else BrandGreen

    // Realistic target date = today + the server's realistic weeks.
    val monthYear = remember(timeline.realisticWeeks) {
        java.time.LocalDate.now()
            .plusWeeks(timeline.realisticWeeks.toLong())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    val kcal = kotlin.math.abs(timeline.dailyKcalDelta)
    val kcalWord = if (timeline.dailyKcalDelta > 0) "deficit" else "surplus"

    val describe = buildString {
        append(timeline.message)
        if (!blocked) {
            append(" On track for around ").append(monthYear).append('.')
            if (timeline.dailyKcalDelta != 0) {
                append(" About ").append(kcal).append(" kilocalories per day ").append(kcalWord).append('.')
            }
        }
        if (timeline.disclaimer.isNotBlank()) append(' ').append(timeline.disclaimer)
    }

    FeatureCard(
        emoji = if (blocked) "⚠️" else "📈",
        title = if (blocked) "Pace Review" else "Your Timeline",
        accentColor = accentColor,
        modifier = Modifier.semantics { contentDescription = describe },
    ) {
        if (timeline.message.isNotBlank()) {
            Text(timeline.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (!blocked) {
            Text(
                "📅 On track for around $monthYear.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
            )
            if (timeline.dailyKcalDelta != 0) {
                Text(
                    "🔥 ≈ $kcal kcal/day $kcalWord",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (timeline.disclaimer.isNotBlank()) {
            Text(
                timeline.disclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Age in whole years from an ISO `yyyy-MM-dd` date of birth, or null if blank/unparseable. */
private fun ageFromDob(dob: String): Int? = try {
    dob.trim().takeIf { it.isNotBlank() }?.let {
        java.time.Period.between(java.time.LocalDate.parse(it), java.time.LocalDate.now()).years
    }
} catch (e: Exception) {
    null
}
