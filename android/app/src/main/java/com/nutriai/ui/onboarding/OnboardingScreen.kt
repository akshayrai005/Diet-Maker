package com.nutriai.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
// Training split: value → plain-language, body-neutral label. null = auto (server picks from goal).
private val TRAINING_SPLIT: List<Pair<String?, String>> = listOf(
    null to "Auto (based on my goal)",
    "body_part" to "Body-part split - Chest / Back / Shoulders / Arms / Legs",
    "push_pull_legs" to "Push / Pull / Legs",
    "upper_lower" to "Upper / Lower",
    "full_body" to "Full-body (3 days/week)",
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
private const val MAX_PRIORITY_MUSCLES = 4
private val FREQ = listOf("no" to "No", "occasional" to "Occasionally", "regular" to "Regularly")
private val CONTRA = listOf(
    "none" to "None",
    "pill" to "The pill",
    "hormonal_iud" to "Hormonal IUD",
    "implant" to "Implant",
    "injection" to "Injection",
    "other" to "Other",
)

private val STEP_EMOJIS = listOf("👤", "🎯", "🏋️", "🏥", "🚀")
private val STEP_COLORS = listOf(KaizenBlue, BrandGreen, MovementColor, KaizenCoral, KaizenLavender)

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

    val steps = listOf("About you", "Your goals", "Movement", "Health", "Ready")
    var step by remember { mutableIntStateOf(0) }
    val lastStep = steps.lastIndex

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
                    ),
                ),
                onDone,
            )
        }
    }

    val stepColor = STEP_COLORS[step.coerceIn(0, STEP_COLORS.lastIndex)]
    val stepEmoji = STEP_EMOJIS[step.coerceIn(0, STEP_EMOJIS.lastIndex)]

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.kaizenColors.pageBackground).padding(Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Step header with emoji and bold color
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(stepColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(stepEmoji, fontSize = 22.sp)
            }
            Column {
                Text(
                    if (editing) "Edit your profile" else steps[step],
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = stepColor,
                )
                Text("Step ${step + 1} of ${steps.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Bold progress bar
        LinearProgressIndicator(
            progress = { (step + 1) / steps.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = stepColor,
            trackColor = stepColor.copy(alpha = 0.12f),
        )

        // Step dots
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            steps.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == step) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) STEP_COLORS[i] else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> {
                    FeatureCard(emoji = "📋", title = "A few basics", accentColor = KaizenBlue) {
                        Text("So everything is personalised and safe.", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Visual body-type selector (spec Section 4) - shape now → shape you're working toward.
                    SectionHeader(title = "Your body type", emoji = "🧍")
                    com.nutriai.ui.bodytype.BodyTypeInlinePicker(
                        currentId = bodyTypeCurrent,
                        goalId = bodyTypeGoal,
                        onCurrent = { bodyTypeCurrent = it },
                        onGoal = { bodyTypeGoal = it },
                    )
                    SectionHeader(title = "Measurements", emoji = "📏")
                    numberField(height, { height = it }, "Height (cm)")
                    numberField(weight, { weight = it }, "Current weight (kg)")
                    numberField(target, { target = it }, "Target weight (kg)")
                    GlassCard {
                        Text(
                            "📐 Body measurements (a measuring tape helps) - these let the coach see your shape, not just your weight, and focus the plan where it matters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    numberField(waist, { waist = it }, "Waist (cm) - at the navel")
                    numberField(neck, { neck = it }, "Neck (cm) - optional, for body-fat %")
                    if (sex == "female") numberField(hip, { hip = it }, "Hip (cm) - optional, for body-fat %")
                    SectionHeader(title = "Identity", emoji = "🪪")
                    DobPicker(dob) { dob = it }
                    Dropdown("Gender", GENDER, gender) { gender = it }
                    if (gender == "self_describe") {
                        OutlinedTextField(
                            value = genderSelfDescribe,
                            onValueChange = { genderSelfDescribe = it.take(40) },
                            label = { Text("Describe (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Dropdown("Sex for health calculations", SEX, sex) { sex = it }
                    Text(
                        "ℹ️ We ask sex separately only because BMR and body-fat formulas need it - it doesn't change how we address you.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                1 -> {
                    SectionHeader(title = "Goals & Preferences", emoji = "🎯")
                    Dropdown("Goal", GOAL, goal) { goal = it }

                    SectionHeader(title = "Timeframe", emoji = "⏰")
                    GlassCard {
                        Text(
                            "📅 How soon would you like to reach your target weight? We'll pace it safely - picking a shorter time won't rush your body past what's healthy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TimeframeChips(timeframeWeeks) { timeframeWeeks = it }
                    if (state.timeline != null) {
                        TimelinePreviewCard(state.timeline!!)
                    }

                    SectionHeader(title = "Diet & Lifestyle", emoji = "🍽️")
                    Dropdown("Diet", DIET, diet) { diet = it }
                    Dropdown("Eating pattern", EATING_PATTERN, eatingPattern) { eatingPattern = it }
                    Text(
                        "🕐 How your day is shaped - e.g. 'Morning + night only' plans 3 front-loaded meals instead of 5.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Dropdown("Activity level", ACTIVITY, activity) { activity = it }
                    Dropdown("Occupation", OCCUPATION, occupation) { occupation = it }
                    Dropdown("Food budget", BUDGET, budgetTier) { budgetTier = it }
                    Dropdown("Plan strictness", STRICTNESS, dietStrictness) { dietStrictness = it }
                    Dropdown("Living situation", LIVING, livingSituation) { livingSituation = it }
                    Dropdown("Kitchen access", KITCHEN, kitchen) { kitchen = it }
                }
                2 -> {
                    SectionHeader(title = "Exercise Setup", emoji = "🏋️")
                    Dropdown("Where do you exercise?", EX_LOC, exLocation) { exLocation = it }
                    Dropdown("Body goal", BODY_GOAL, bodyGoal) { bodyGoal = it }

                    SectionHeader(title = "Training Split", emoji = "📊")
                    TrainingSplitPicker(TRAINING_SPLIT, trainingSplit) { trainingSplit = it }
                    Text(
                        "💡 Your Move plan updates to this split.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SectionHeader(title = "Intensity & Rest", emoji = "⚡")
                    Dropdown("Fitness level", FITNESS_LEVEL, fitnessLevel) { fitnessLevel = it }
                    Dropdown("Workout intensity", INTENSITY, intensity) { intensity = it }
                    GlassCard {
                        Text(
                            "💪 Harder isn't always better - pick what you can keep up with. We cap intensity for safety.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Dropdown("Workout rest day", DAYS, workoutRest) { workoutRest = it }

                    // Gym membership (spec Section 5) → progressive-overload phase (Foundation→Peak).
                    SectionHeader(title = "Gym Membership", emoji = "🏢")
                    Dropdown("Membership duration", GYM_MONTHS, gymMonths) { gymMonths = it }
                    if (gymMonths != null) {
                        Text("📅 When did you join? Your plan's intensity phase is calculated from this.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DobPicker(gymJoinDate, label = "Gym join date") { gymJoinDate = it }
                    }
                }
                3 -> {
                    SectionHeader(title = "Health Conditions", emoji = "🏥")
                    Label("Conditions (optional)")
                    MultiChoiceChips(CONDITIONS, conditions)
                    SectionHeader(title = "Family History", emoji = "👨‍👩‍👧‍👦")
                    Text(
                        "Conditions that run in your close family - helps us flag risks earlier.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MultiChoiceChips(FAMILY_HISTORY, familyHistory)
                    SectionHeader(title = "Lifestyle", emoji = "🌿")
                    Dropdown("Weekly fasting day (optional)", DAYS, fastDay) { fastDay = it }
                    Dropdown("Do you smoke?", FREQ, smoking) { smoking = it }
                    Dropdown("Do you drink alcohol?", FREQ, alcohol) { alcohol = it }
                    if (sex == "female") {
                        Dropdown("Contraception (if any)", CONTRA, contraception) { contraception = it }
                    }

                    SectionHeader(title = "Physique Goal", emoji = "🎯")
                    GlassCard {
                        Text(
                            "What would you like your training to work towards? This only tunes your targets - every option is healthy. 💚",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    PhysiqueGoalPicker(physiqueOptions, physiqueGoal) { physiqueGoal = it }
                    if (isMinor) {
                        FeatureCard(emoji = "🛡️", title = "Under-18 Safety", accentColor = BrandAmber) {
                            Text(
                                "Because you're under 18, we'll keep this safe for your age - no aggressive cutting.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    SectionHeader(title = "Priority Muscles", emoji = "💪")
                    Text(
                        "Pick up to $MAX_PRIORITY_MUSCLES - we'll add a little extra volume there.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PriorityMusclesChips(PRIORITY_MUSCLES, priorityMuscles, MAX_PRIORITY_MUSCLES)
                }
                else -> {
                    Spacer(Modifier.height(Spacing.lg))
                    FeatureCard(emoji = "🎉", title = "You're All Set!", accentColor = BrandGreen) {
                        Text(
                            "We'll build your personalised plan across Diet, Movement, Mind and Discipline - with safe, explainable targets. Small habits, big results. 🚀",
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
        }

        state.error?.let {
            FeatureCard(emoji = "❌", title = "Error", accentColor = KaizenCoral) {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (step == 0 && !canSave) {
            GlassCard {
                Text(
                    "📝 Fill height, weight, target and date of birth to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(Radius.md),
                ) { Text("⬅️ Back", fontWeight = FontWeight.Bold) }
            }
            if (step < lastStep) {
                Button(
                    onClick = { if (step != 0 || canSave) step++ },
                    enabled = step != 0 || canSave,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(Radius.md),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = stepColor),
                ) { Text("Next ➡️", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { doSave() },
                    enabled = !state.loading && canSave,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(Radius.md),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) {
                    if (state.loading) CircularProgressIndicator(Modifier.padding(4.dp), color = Color.White)
                    else Text(if (editing) "✅ Save changes" else "🚀 Create my plan", fontWeight = FontWeight.Bold)
                }
            }
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

@Composable
private fun numberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, disp) ->
                DropdownMenuItem(text = { Text(disp) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChoiceChips(options: List<String>, selected: MutableList<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            )
        }
    }
}

/** Single-select physique goal as radio rows with plain-language descriptions, plus a null "skip". */
@Composable
private fun PhysiqueGoalPicker(
    options: List<Pair<String, Pair<String, String>>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, labelDesc) ->
            PhysiqueOptionRow(
                label = labelDesc.first,
                desc = labelDesc.second,
                selected = selected == value,
                onClick = { onSelect(value) },
            )
        }
        PhysiqueOptionRow(
            label = "Not sure / skip",
            desc = "We'll pick balanced, healthy targets for you.",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
    }
}

/**
 * Single-select training split as radio rows with plain-language, body-neutral labels.
 */
@Composable
private fun TrainingSplitPicker(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val isSel = selected == value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) MovementColor.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = if (isSel) "$label, selected" else label },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = isSel, onClick = { onSelect(value) })
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhysiqueOptionRow(label: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandGreen.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$label. $desc" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Multi-select muscle chips capped at [max]; chips past the cap are disabled until one is freed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityMusclesChips(options: List<String>, selected: MutableList<String>, max: Int) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            )
        }
    }
}

/** Single-select timeframe chips (1/2/3/6/12 months). Emits the chosen months converted to weeks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeframeChips(selectedWeeks: Int?, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
