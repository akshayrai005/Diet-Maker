package com.nutriai.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.remote.dto.ProfileUpsertRequest
import com.nutriai.data.remote.dto.SensitiveData

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
private val DIET = listOf(
    "veg" to "Vegetarian", "eggetarian" to "Eggetarian", "nonveg" to "Non-veg",
    "vegan" to "Vegan", "jain" to "Jain", "keto" to "Keto", "highprotein" to "High-protein",
)
private val EX_LOC = listOf("gym" to "Gym", "home" to "Home", "none" to "No workouts")
private val BODY_GOAL = listOf("fatloss" to "Fat loss", "athletic" to "Athletic / lean", "muscular" to "Muscular")
private val DAYS: List<Pair<Int?, String>> = listOf(
    null to "None", 0 to "Sunday", 1 to "Monday", 2 to "Tuesday",
    3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday",
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

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("male") }
    var gender by remember { mutableStateOf("male") }
    var genderSelfDescribe by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("desk") }
    var budgetTier by remember { mutableStateOf("medium") }
    var dietStrictness by remember { mutableStateOf("standard") }
    var fitnessLevel by remember { mutableStateOf("beginner") }
    var intensity by remember { mutableStateOf("standard") }
    var activity by remember { mutableStateOf("moderate") }
    var goal by remember { mutableStateOf("lose") }
    var diet by remember { mutableStateOf("nonveg") }
    val conditions = remember { mutableStateListOf<String>() }
    val familyHistory = remember { mutableStateListOf<String>() }
    var fastDay by remember { mutableStateOf<Int?>(null) }
    var exLocation by remember { mutableStateOf("home") }
    var bodyGoal by remember { mutableStateOf("fatloss") }
    var workoutRest by remember { mutableStateOf<Int?>(0) }
    var smoking by remember { mutableStateOf("no") }
    var alcohol by remember { mutableStateOf("no") }
    var contraception by remember { mutableStateOf("none") }
    var physiqueGoal by remember { mutableStateOf<String?>(null) }
    val priorityMuscles = remember { mutableStateListOf<String>() }

    LaunchedEffect(state.prefillLoaded) {
        val p = state.prefill ?: return@LaunchedEffect
        height = p.heightCm.takeIf { it > 0 }?.let { fmt(it) } ?: height
        activity = p.activityLevel.ifBlank { activity }
        goal = p.goal.ifBlank { goal }
        diet = p.dietType.ifBlank { diet }
        p.sensitive?.let { s ->
            weight = fmt(s.currentWeightKg)
            target = fmt(s.targetWeightKg)
            dob = s.dob
            sex = s.sex.ifBlank { sex }
            gender = s.gender ?: s.sex.ifBlank { gender }
            genderSelfDescribe = s.genderSelfDescribe ?: genderSelfDescribe
            occupation = s.occupation ?: occupation
            budgetTier = s.budgetTier ?: budgetTier
            dietStrictness = s.dietStrictness ?: dietStrictness
            fitnessLevel = s.fitnessLevel ?: fitnessLevel
            intensity = s.intensityPreference ?: intensity
            conditions.clear(); conditions.addAll(s.conditions)
            familyHistory.clear(); familyHistory.addAll(s.familyHistory)
            fastDay = s.fastDayOfWeek
            exLocation = s.exerciseLocation ?: exLocation
            bodyGoal = s.bodyGoal ?: bodyGoal
            workoutRest = s.workoutRestDay ?: workoutRest
            smoking = s.smoking ?: smoking
            alcohol = s.alcohol ?: alcohol
            contraception = s.contraception ?: contraception
            physiqueGoal = s.physiqueGoal
            priorityMuscles.clear(); priorityMuscles.addAll(s.priorityMuscles.take(MAX_PRIORITY_MUSCLES))
        }
    }
    val age = remember(dob) { ageFromDob(dob) }
    val isMinor = age != null && age < 18
    // Don't surface aggressive cutting to minors; the server also downgrades a minor's cut.
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
                        fitnessLevel = fitnessLevel,
                        intensityPreference = intensity,
                        dob = dob.trim(),
                        currentWeightKg = w,
                        targetWeightKg = t,
                        conditions = conditions.toList(),
                        familyHistory = familyHistory.toList(),
                        fastDayOfWeek = fastDay,
                        exerciseLocation = exLocation,
                        bodyGoal = bodyGoal,
                        workoutRestDay = workoutRest,
                        smoking = smoking,
                        alcohol = alcohol,
                        contraception = if (sex == "female") contraception else null,
                        physiqueGoal = physiqueGoal,
                        priorityMuscles = priorityMuscles.toList(),
                    ),
                ),
                onDone,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (editing) "Edit your profile" else steps[step],
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(progress = { (step + 1) / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text("Step ${step + 1} of ${steps.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> {
                    Text("A few basics so everything is personalised and safe.", style = MaterialTheme.typography.bodyMedium)
                    numberField(height, { height = it }, "Height (cm)")
                    numberField(weight, { weight = it }, "Current weight (kg)")
                    numberField(target, { target = it }, "Target weight (kg)")
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
                        "We ask sex separately only because BMR and body-fat formulas need it — it doesn't change how we address you.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                1 -> {
                    Dropdown("Goal", GOAL, goal) { goal = it }
                    Dropdown("Diet", DIET, diet) { diet = it }
                    Dropdown("Activity level", ACTIVITY, activity) { activity = it }
                    Dropdown("Occupation", OCCUPATION, occupation) { occupation = it }
                    Dropdown("Food budget", BUDGET, budgetTier) { budgetTier = it }
                    Dropdown("Plan strictness", STRICTNESS, dietStrictness) { dietStrictness = it }
                }
                2 -> {
                    Dropdown("Where do you exercise?", EX_LOC, exLocation) { exLocation = it }
                    Dropdown("Body goal", BODY_GOAL, bodyGoal) { bodyGoal = it }
                    Dropdown("Fitness level", FITNESS_LEVEL, fitnessLevel) { fitnessLevel = it }
                    Dropdown("Workout intensity", INTENSITY, intensity) { intensity = it }
                    Text(
                        "Harder isn't always better — pick what you can keep up with. We cap intensity for safety.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Dropdown("Workout rest day", DAYS, workoutRest) { workoutRest = it }
                }
                3 -> {
                    Label("Conditions (optional)")
                    MultiChoiceChips(CONDITIONS, conditions)
                    Label("Family history (optional)")
                    Text(
                        "Conditions that run in your close family — helps us flag risks earlier.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MultiChoiceChips(FAMILY_HISTORY, familyHistory)
                    Dropdown("Weekly fasting day (optional)", DAYS, fastDay) { fastDay = it }
                    Dropdown("Do you smoke?", FREQ, smoking) { smoking = it }
                    Dropdown("Do you drink alcohol?", FREQ, alcohol) { alcohol = it }
                    if (sex == "female") {
                        Dropdown("Contraception (if any)", CONTRA, contraception) { contraception = it }
                    }

                    Label("Physique goal (optional)")
                    Text(
                        "What would you like your training to work towards? This only tunes your targets — every option is healthy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PhysiqueGoalPicker(physiqueOptions, physiqueGoal) { physiqueGoal = it }
                    if (isMinor) {
                        Text(
                            "Because you're under 18, we'll keep this safe for your age — no aggressive cutting.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Label("Muscles you'd like to bring up (optional)")
                    Text(
                        "Pick up to $MAX_PRIORITY_MUSCLES — we'll add a little extra volume there.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PriorityMusclesChips(PRIORITY_MUSCLES, priorityMuscles, MAX_PRIORITY_MUSCLES)
                }
                else -> {
                    Text("You're all set 🎉", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "We'll build your personalised plan across Diet, Movement, Mind and Discipline — with safe, explainable targets. Small habits, big results.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Goal: ${labelOf(GOAL, goal)}  ·  Diet: ${labelOf(DIET, diet)}\nLevel: ${labelOf(FITNESS_LEVEL, fitnessLevel)}  ·  Intensity: ${labelOf(INTENSITY, intensity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (step == 0 && !canSave) {
            Text(
                "Fill height, weight, target and date of birth to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            if (step < lastStep) {
                Button(
                    onClick = { if (step != 0 || canSave) step++ },
                    enabled = step != 0 || canSave,
                    modifier = Modifier.weight(1f),
                ) { Text("Next") }
            } else {
                Button(
                    onClick = { doSave() },
                    enabled = !state.loading && canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.loading) CircularProgressIndicator(Modifier.padding(4.dp))
                    else Text(if (editing) "Save changes" else "Create my plan")
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
private fun DobPicker(dob: String, onDob: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = dob,
            onValueChange = {},
            readOnly = true,
            label = { Text("Date of birth") },
            placeholder = { Text("Tap to pick 📅") },
            trailingIcon = { Text("📅", modifier = Modifier.padding(end = 12.dp)) },
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
    Text(text, style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun PhysiqueOptionRow(label: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$label. $desc" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
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
