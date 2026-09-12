package com.nutriai.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriai.data.remote.dto.Dashboard
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.ListRow
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.Status
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.CardAmberLight
import com.nutriai.ui.theme.CardBlueLight
import com.nutriai.ui.theme.CardCoralLight
import com.nutriai.ui.theme.CardGreenLight
import com.nutriai.ui.theme.CardLavenderLight
import com.nutriai.ui.theme.CardMintLight
import com.nutriai.ui.theme.CardRoseLight
import com.nutriai.ui.theme.KaizenRose
import com.nutriai.ui.theme.CoralAccent
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.CardTealLight
import com.nutriai.ui.theme.GridGreen
import com.nutriai.ui.theme.GridGreenLight
import com.nutriai.ui.theme.GridOrange
import com.nutriai.ui.theme.GridOrangeLight
import com.nutriai.ui.theme.GridPurple
import com.nutriai.ui.theme.GridPurpleLight
import com.nutriai.ui.theme.GridBlue
import com.nutriai.ui.theme.GridBlueLight
import com.nutriai.ui.theme.GridPink
import com.nutriai.ui.theme.GridPinkLight
import com.nutriai.ui.theme.GridRed
import com.nutriai.ui.theme.GridRedLight
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenTeal
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.RecoveryColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val SharpRadius = 8.dp

@Composable
fun PremiumDashboard(
    dashboard: Dashboard,
    greetingName: String?,
    onAddWater: () -> Unit,
    onCompleteProfile: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    steps: Long = 0,
    stepsKcal: Int = 0,
    exerciseKcal: Int = 0,
    stepsPermission: Boolean = true,
    stepsAvailable: Boolean = false,
    onConnectSteps: () -> Unit = {},
    heartRate: Int? = null,
    sleepHours: Double? = null,
    manualHeartRate: Int? = null,
    bloodPressure: Pair<Int, Int>? = null,
    oxygenSaturation: Int? = null,
    stress: Int? = null,
    onSaveVitals: (Int?, Int?, Int?) -> Unit = { _, _, _ -> },
    soreness: Int? = null,
    safetyFlags: List<com.nutriai.data.remote.dto.Flag> = emptyList(),
    riskFindings: List<com.nutriai.data.remote.dto.RiskFinding> = emptyList(),
    weekDays: List<com.nutriai.data.remote.dto.ReportDay> = emptyList(),
    weekKcalTarget: Double? = null,
    maintenanceKcal: Double? = null,
    bodyFatPct: Double? = null,
    coach: com.nutriai.data.remote.dto.CoachBrief? = null,
    rating: com.nutriai.data.remote.dto.RatingResult? = null,
    todayWorkout: com.nutriai.data.remote.dto.WorkoutDay? = null,
    onOpenVitals: () -> Unit = {},
    onOpenMove: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenDietLog: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val d = dashboard
    var showFullAnalysis by remember { mutableStateOf(false) }
    var showVitamins by remember { mutableStateOf(false) }
    var editingVitals by remember { mutableStateOf(false) }
    var showMoreInsights by remember { mutableStateOf(false) }

    if (editingVitals) {
        VitalsEntryDialog(
            initialHr = manualHeartRate,
            initialStress = stress,
            initialSoreness = soreness,
            onDismiss = { editingVitals = false },
            onSave = { newHr, newStress, newSoreness -> onSaveVitals(newHr, newStress, newSoreness); editingVitals = false },
        )
    }

    val sectionPadding = Modifier.padding(horizontal = Spacing.screenHorizontal)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xxl),
    ) {
        // Hero greeting
        item { HeroSection(greetingName = greetingName, streakDays = d.streakDays, dashboard = d) }

        // Calorie ring card
        item {
            Column(sectionPadding) {
                CalorieSummaryCard(dashboard = d, steps = steps, stepsKcal = stepsKcal, exerciseKcal = exerciseKcal, stepsPermission = stepsPermission, maintenanceKcal = maintenanceKcal)
            }
        }

        // Phase plan — which stage of the stated timeline you're in (fat-loss/shape/muscle).
        d.phasePlan?.let { phase ->
            item { Column(sectionPadding) { PhasePlanCard(phase) } }
        }

        // Domain cards — 2x2 grid, NO scrolling
        item {
            Column(sectionPadding) {
                SectionHeader("Your Day", emoji = "📊")
                DomainCardsGrid(
                    dashboard = d,
                    steps = steps,
                    stepsPermission = stepsPermission,
                    stepsKcal = stepsKcal,
                    sleepHours = sleepHours,
                    heartRate = heartRate ?: manualHeartRate,
                    bodyFatPct = bodyFatPct,
                    bloodPressure = bloodPressure,
                    oxygenSaturation = oxygenSaturation,
                    onAddWater = onAddWater,
                    onOpenDietLog = onOpenDietLog,
                    onOpenMove = onOpenMove,
                    onOpenProgress = onOpenProgress,
                    onOpenVitals = onOpenVitals,
                )
            }
        }

        // Priorities
        item {
            Column(sectionPadding) {
                SectionHeader("Priorities", emoji = "🎯")
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(SharpRadius),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PrioritiesContent(dashboard = d, todayWorkout = todayWorkout, onOpenMove = onOpenMove)
                    }
                }
            }
        }

        // Plan Tomorrow shortcut
        item {
            Column(sectionPadding) {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenPlan),
                    shape = RoundedCornerShape(SharpRadius),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("📅 Plan Tomorrow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Set workout + diet adapts automatically",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Vitamins (above Insight)
        d.micronutrients?.let { mn ->
            if (mn.targets.isNotEmpty()) {
                item {
                    Column(sectionPadding) {
                        VitaminsRow(mn = mn, expanded = showVitamins, onToggle = { showVitamins = !showVitamins })
                    }
                }
            }
        }

        // Vitals (above Insight)
        item {
            Column(sectionPadding) {
                VitalsRow(sleepHours = sleepHours, bloodPressure = bloodPressure, onEdit = { editingVitals = true })
            }
        }

        // Show more / less toggle — everything below is secondary detail, collapsed by default.
        item {
            Column(sectionPadding) {
                Row(
                    Modifier.fillMaxWidth().clickable { showMoreInsights = !showMoreInsights }.padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showMoreInsights) "Show less ▲" else "Show more ▼",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (showMoreInsights) {
            // Insight
            if (rating != null || coach != null) {
                item {
                    Column(sectionPadding) {
                        InsightSection(rating = rating, coach = coach, expanded = showFullAnalysis, onToggle = { showFullAnalysis = !showFullAnalysis })
                    }
                }
            }

            // 7-day goal
            if (weekDays.isNotEmpty() && weekKcalTarget != null && weekKcalTarget > 0) {
                item {
                    Column(sectionPadding) {
                        GoalMonitorSection(days = weekDays, target = weekKcalTarget)
                    }
                }
            }

            // Journey
            if (d.projection.size > 1) {
                item { Column(sectionPadding) { JourneySummaryRow(dashboard = d) } }
            }

            // Safety
            if (safetyFlags.isNotEmpty()) {
                item { Column(sectionPadding) { SafetyRows(flags = safetyFlags) } }
            }
            if (riskFindings.isNotEmpty()) {
                item { Column(sectionPadding) { RiskRows(findings = riskFindings) } }
            }

            item { Column(sectionPadding) { WhyTrustThisCard() } }
        }

        item {
            Text(
                "Educational guidance, not medical advice - consult a professional.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
    }
}

/**
 * Honest answer to "why should I follow this app / is my goal guaranteed": no target here is
 * a promise, but the plan is grounded in your real data and adjusts as your real behavior
 * changes - not a generic template.
 */
@Composable
private fun WhyTrustThisCard() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Why this plan, not a guarantee", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "No app can promise you'll hit an exact number by an exact date - bodies respond differently to the same plan. " +
                    "What this one does: computes your targets from your real height, weight, age and medical flags (not a generic template), " +
                    "adjusts your calorie budget as your logged workouts actually change, stages your exercise plan around real limitations " +
                    "like mobility or medical conditions instead of pushing a one-size routine, and keeps every target inside safety guardrails. " +
                    "Follow it consistently and it will move you in the right direction - the pace is the part that varies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero — warm gradient with greeting
// ---------------------------------------------------------------------------

@Composable
private fun HeroSection(greetingName: String?, streakDays: Int, dashboard: Dashboard) {
    val now = remember { LocalTime.now() }
    val today = remember { java.time.LocalDate.now() }
    val greetEmoji = when { now.hour < 12 -> "🌅"; now.hour < 17 -> "☀️"; else -> "🌙" }
    val greeting = when { now.hour < 12 -> "Good morning"; now.hour < 17 -> "Good afternoon"; else -> "Good evening" }

    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))))
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(vertical = Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "$greetEmoji $greeting, ${greetingName ?: "there"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
            if (streakDays > 0) {
                Card(
                    shape = RoundedCornerShape(SharpRadius),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔥", fontSize = 16.sp)
                        Text("${streakDays}d", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Calorie Summary — sharp card with ring + macros
// ---------------------------------------------------------------------------

@Composable
private fun CalorieSummaryCard(dashboard: Dashboard, steps: Long, stepsKcal: Int, exerciseKcal: Int = 0, stepsPermission: Boolean, maintenanceKcal: Double? = null) {
    val cal = dashboard.calories
    val hasTarget = cal.target != null && cal.target > 0
    val consumed = cal.consumed.toInt()
    val target = cal.target?.toInt() ?: 0
    val bodyNeed = maintenanceKcal?.toInt() ?: target
    val burned = stepsKcal + exerciseKcal
    val totalBudget = bodyNeed + burned
    val remaining = if (hasTarget) (target - consumed).coerceAtLeast(0) else 0
    // Body Need (TDEE) is an activity-level AVERAGE, not a per-day number - a day that clearly
    // exceeds that average (e.g. a big step day) genuinely burns more than the average assumes,
    // so today's logged burn is added back on top before computing the deficit. This can overlap
    // with the averaged-in activity on a totally typical day, but undercounting a real high-effort
    // day is the worse failure mode.
    val deficit = totalBudget - target
    val pct = if (hasTarget) (cal.consumed / cal.target!!).coerceIn(0.0, 1.5).toFloat() else 0f

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Line, not ring — progress toward today's eating target, value at the end of the line.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("🔥", fontSize = 18.sp)
                Text("kcal left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("%,d".format(remaining), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NutritionColor)
            }
            KaizenProgressBar(progress = pct, color = NutritionColor, height = 10.dp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCell(Modifier.weight(1f), "🏋️", "Body Need", "%,d".format(bodyNeed), KaizenCoral)
                StatCell(Modifier.weight(1f), "🎯", "Target", if (hasTarget) "%,d".format(target) else "—", KaizenCoral)
                StatCell(
                    Modifier.weight(1f),
                    if (deficit > 0) "📉" else "📈",
                    if (deficit > 0) "Deficit" else "Surplus",
                    "%,d".format(kotlin.math.abs(deficit)),
                    KaizenCoral,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCell(Modifier.weight(1f), "🍽️", "Eaten", "%,d".format(consumed), KaizenBlue)
                StatCell(Modifier.weight(1f), "🔥", "Burned", if (burned > 0) "%,d".format(burned) else "—", KaizenBlue)
                StatCell(Modifier.weight(1f), "⏳", "Remaining", "%,d".format(remaining), KaizenBlue)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCell(
                    Modifier.weight(1f),
                    "💪",
                    "Protein",
                    dashboard.protein.target?.let { "${(dashboard.protein.consumed ?: 0.0).toInt()}/${it.toInt()}g" } ?: "${(dashboard.protein.consumed ?: 0.0).toInt()}g",
                    NutritionColor,
                )
                StatCell(
                    Modifier.weight(1f),
                    "🌾",
                    "Carbs",
                    dashboard.macros.carbTargetG?.let { "${dashboard.macros.carbG.toInt()}/${it.toInt()}g" } ?: "${dashboard.macros.carbG.toInt()}g",
                    NutritionColor,
                )
                StatCell(
                    Modifier.weight(1f),
                    "🥑",
                    "Fat",
                    dashboard.macros.fatTargetG?.let { "${dashboard.macros.fatG.toInt()}/${it.toInt()}g" } ?: "${dashboard.macros.fatG.toInt()}g",
                    NutritionColor,
                )
            }
        }
    }
}

@Composable
private fun StatCell(modifier: Modifier = Modifier, emoji: String, label: String, value: String, color: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(SharpRadius))
            .background(color.copy(alpha = 0.08f))
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhasePlanCard(phase: com.nutriai.data.remote.dto.PhasePlan) {
    val emoji = when (phase.phase) {
        "fat_loss" -> "🔥"
        "shape" -> "🎯"
        else -> "💪"
    }
    val color = when (phase.phase) {
        "fat_loss" -> KaizenCoral
        "shape" -> NutritionColor
        else -> KaizenLavender
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            EmojiBadge(emoji = emoji, bgColor = color.copy(alpha = 0.12f))
            Column(Modifier.weight(1f)) {
                Text(
                    "Phase ${phase.phaseIndex}/${phase.phaseCount} · ${phase.phaseLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Week ${phase.weekInPhase} of ${phase.weeksInPhase} in this phase · ${phase.weeksRemaining}w left overall",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Domain Cards — 2x2 grid, no horizontal scrolling
// ---------------------------------------------------------------------------

@Composable
private fun DomainCardsGrid(
    dashboard: Dashboard,
    steps: Long,
    stepsPermission: Boolean,
    stepsKcal: Int,
    sleepHours: Double?,
    heartRate: Int?,
    bodyFatPct: Double? = null,
    bloodPressure: Pair<Int, Int>? = null,
    oxygenSaturation: Int? = null,
    onAddWater: () -> Unit = {},
    onOpenDietLog: () -> Unit = {},
    onOpenMove: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenVitals: () -> Unit = {},
) {
    val cal = dashboard.calories
    val calPct = if (cal.target != null && cal.target > 0) (cal.consumed / cal.target).coerceIn(0.0, 1.0).toFloat() else 0f
    val proteinPct = if (dashboard.protein.target != null && dashboard.protein.target > 0) {
        (((dashboard.protein.consumed ?: 0.0) / dashboard.protein.target) * 100).toInt()
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Row 1: Nutrition + Movement
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "🍎", title = "Nutrition",
                mainValue = "${cal.consumed.toInt()}", mainUnit = "kcal",
                progress = calPct, accentColor = GridGreen, bgColor = GridGreenLight,
                detail = proteinPct?.let { "$it% protein" } ?: "Log meals",
                borderColor = GridGreen,
                onQuickAction = onOpenDietLog,
            )
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "💪", title = "Movement",
                mainValue = if (stepsPermission) "%,d".format(steps) else "-",
                mainUnit = if (stepsPermission) "steps" else "",
                progress = if (stepsPermission) (steps / 10000f).coerceIn(0f, 1f) else 0f,
                accentColor = GridOrange, bgColor = GridOrangeLight,
                detail = when {
                    stepsPermission && steps > 0 -> "≈ $stepsKcal kcal"
                    stepsPermission -> "No steps yet today"
                    else -> "Connect Health"
                },
                borderColor = GridOrange,
                onQuickAction = onOpenMove,
            )
        }
        // Row 2: Body + Hydration
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "📐", title = "Body",
                mainValue = dashboard.bmi?.let { "%.1f".format(it) } ?: "-",
                mainUnit = if (dashboard.bmi != null) "BMI" else "",
                progress = dashboard.bmi?.let { ((it - 18.5) / (30.0 - 18.5)).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
                accentColor = GridPurple, bgColor = GridPurpleLight,
                detail = bodyFatPct?.let { "%.1f%% body fat".format(it) } ?: "Add measurements",
                borderColor = GridPurple,
                onQuickAction = onOpenProgress,
            )
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "💧", title = "Hydration",
                mainValue = "${((dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0) / 250.0).toInt()}",
                mainUnit = "glasses",
                progress = ((dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0) / (dashboard.water.targetMl ?: dashboard.water.target ?: 2500.0)).coerceIn(0.0, 1.0).toFloat(),
                accentColor = GridBlue, bgColor = GridBlueLight,
                detail = "${"%.1f".format((dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0) / 1000.0)}L",
                borderColor = GridBlue,
                onQuickAction = onAddWater,
            )
        }
        // Row 3: Blood Pressure (or Blood Oxygen, if that's what the watch actually supports) + Heart Rate
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (bloodPressure != null || oxygenSaturation == null) {
                DomainCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🩺", title = "Blood Pressure",
                    mainValue = bloodPressure?.let { "${it.first}/${it.second}" } ?: "-",
                    mainUnit = "",
                    progress = bloodPressure?.let { ((it.first - 90) / (140.0 - 90)).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
                    accentColor = GridPink, bgColor = GridPinkLight,
                    detail = if (bloodPressure != null) "mmHg" else "Needs a synced watch reading",
                    borderColor = GridPink,
                    onQuickAction = onOpenVitals,
                )
            } else {
                // No BP data, but the watch does report SpO2 - show the real signal instead of
                // a permanently-dead "needs a synced watch" card.
                DomainCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🫁", title = "Blood Oxygen",
                    mainValue = "$oxygenSaturation",
                    mainUnit = "%",
                    progress = ((oxygenSaturation - 90) / 10.0).coerceIn(0.0, 1.0).toFloat(),
                    accentColor = GridPink, bgColor = GridPinkLight,
                    detail = "SpO2",
                    borderColor = GridPink,
                    onQuickAction = onOpenVitals,
                )
            }
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "❤️", title = "Heart rate",
                mainValue = heartRate?.let { "$it" } ?: "-",
                mainUnit = if (heartRate != null) "bpm" else "",
                progress = heartRate?.let { ((it - 50) / (120.0 - 50)).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
                accentColor = GridRed, bgColor = GridRedLight,
                detail = if (heartRate != null) "Resting" else "Needs a synced watch reading",
                borderColor = GridRed,
                onQuickAction = onOpenVitals,
            )
        }
        // Row 4: Sleep — only shown once there's real data, not as a permanent dead tile.
        if (sleepHours != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                DomainCard(
                    modifier = Modifier.weight(1f),
                    emoji = "😴", title = "Sleep",
                    mainValue = "$sleepHours",
                    mainUnit = "hrs",
                    progress = (sleepHours / 8.0).coerceIn(0.0, 1.0).toFloat(),
                    accentColor = KaizenLavender, bgColor = CardLavenderLight,
                    detail = "Last night",
                    borderColor = KaizenLavender,
                    onQuickAction = onOpenVitals,
                )
            }
        }
    }
}

@Composable
private fun DomainCard(
    modifier: Modifier = Modifier,
    emoji: String, title: String, mainValue: String, mainUnit: String,
    progress: Float, accentColor: Color, bgColor: Color, detail: String,
    borderColor: Color,
    onQuickAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(emoji, fontSize = 14.sp)
                    Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
                }
                if (onQuickAction != null) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accentColor.copy(alpha = 0.15f))
                            .clickable(onClick = onQuickAction)
                            .semantics { contentDescription = "$title quick action" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mainValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = accentColor)
                if (mainUnit.isNotEmpty()) Text(mainUnit, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 1.dp))
            }
            KaizenProgressBar(progress = progress, color = accentColor, height = 3.dp)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Priorities
// ---------------------------------------------------------------------------

@Composable
private fun PrioritiesContent(dashboard: Dashboard, todayWorkout: com.nutriai.data.remote.dto.WorkoutDay?, onOpenMove: () -> Unit) {
    val mealsLogged = dashboard.calories.consumed > 0

    PriorityRow("🏋️", MovementColor, "Complete today's workout", when { todayWorkout == null -> "Generate your plan in Move"; todayWorkout.rest -> "Rest day — recovery"; else -> todayWorkout.focus }, if (todayWorkout?.rest == true) "Rest" else "Open", if (todayWorkout?.rest == true) Status.Positive else Status.Information, onOpenMove)
    HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
    PriorityRow("🍽️", NutritionColor, "Log today's meals", if (mealsLogged) "${dashboard.calories.consumed.toInt()} kcal logged" else "Nothing logged yet", if (mealsLogged) "Started" else "Pending", if (mealsLogged) Status.Positive else Status.Caution)
}

@Composable
private fun PriorityRow(emoji: String, emojiColor: Color, title: String, subtitle: String, status: String? = null, statusType: Status? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji = emoji, bgColor = emojiColor.copy(alpha = 0.12f))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else if (status != null && statusType != null) StatusIndicator(text = status, status = statusType)
    }
}

// ---------------------------------------------------------------------------
// Insight
// ---------------------------------------------------------------------------

@Composable
private fun InsightSection(rating: com.nutriai.data.remote.dto.RatingResult?, coach: com.nutriai.data.remote.dto.CoachBrief?, expanded: Boolean, onToggle: () -> Unit) {
    val headline = rating?.biggestLever?.message?.takeIf { it.isNotBlank() } ?: coach?.greeting?.takeIf { it.isNotBlank() } ?: "Keep logging to unlock your insight."
    // A confident weight-trend prediction reads as contradictory right under "no data yet" - only
    // show it once there's actually a rating pillar established.
    val noPillarData = headline.startsWith("No pillar data yet")
    val supporting = (coach?.prediction?.takeIf { it.isNotBlank() && !noPillarData } ?: coach?.streak?.takeIf { it.isNotBlank() })

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = CardLavenderLight),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("💡", fontSize = 16.sp)
                Text("Your Insight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = KaizenLavender)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            TextAction(text = if (expanded) "Hide full analysis" else "See full analysis →", onClick = onToggle)
            if (expanded) com.nutriai.ui.analysis.AnalysisCard(rating = rating, coach = coach, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

// ---------------------------------------------------------------------------
// Safety & Risk
// ---------------------------------------------------------------------------

@Composable
private fun SafetyRows(flags: List<com.nutriai.data.remote.dto.Flag>) {
    var expanded by remember { mutableStateOf(true) }
    val order = mapOf("critical" to 0, "warning" to 1, "info" to 2)
    val sorted = flags.sortedBy { order[it.severity] ?: 3 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Health & Safety", emoji = "🛡️")
        // Header card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharpRadius),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = CardCoralLight),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("⚠️", fontSize = 20.sp)
                Text("Safety Alerts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = KaizenCoral)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(KaizenCoral).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text("${sorted.size} alert${if (sorted.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = KaizenCoral)
            }
        }
        // Each flag as its own card
        if (expanded) sorted.forEach { f ->
            val (flagColor, flagBg) = when (f.severity) {
                "critical" -> KaizenCoral to CardCoralLight
                "warning" -> KaizenRose to CardRoseLight
                else -> KaizenBlue to CardBlueLight
            }
            val status = when (f.severity) { "critical" -> Status.Critical; "warning" -> Status.Caution; else -> Status.Information }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharpRadius),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = flagBg),
            ) {
                Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(SharpRadius)).background(flagColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(when (f.severity) { "critical" -> "🚨"; "warning" -> "⚠️"; else -> "ℹ️" }, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        StatusIndicator(text = f.severity.uppercase(), status = status)
                        Text(f.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskRows(findings: List<com.nutriai.data.remote.dto.RiskFinding>) {
    var expanded by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Health Signals", emoji = "📡")
        // Header card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharpRadius),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = CardRoseLight),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("🔍", fontSize = 20.sp)
                Text("Risk Findings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = KaizenRose)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(KaizenRose).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text("${findings.size} finding${if (findings.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = KaizenRose)
            }
        }
        // Each finding as its own colorful card
        if (expanded) findings.forEach { f ->
            val (findColor, findBg) = when (f.level) {
                "high" -> KaizenCoral to CardCoralLight
                "moderate" -> KaizenRose to CardRoseLight
                else -> KaizenBlue to CardBlueLight
            }
            val status = when (f.level) { "high" -> Status.Critical; "moderate" -> Status.Caution; else -> Status.Information }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharpRadius),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = findBg),
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(SharpRadius)).background(findColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(when (f.level) { "high" -> "🚨"; "moderate" -> "⚠️"; else -> "💡" }, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            StatusIndicator(text = f.label, status = status)
                        }
                    }
                    Text(f.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(SharpRadius),
                        elevation = CardDefaults.cardElevation(0.dp),
                        colors = CardDefaults.cardColors(containerColor = findColor.copy(alpha = 0.08f)),
                    ) {
                        Text(
                            "→ ${f.nextAction}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = findColor,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vitamins, Vitals, Journey, Goal
// ---------------------------------------------------------------------------

@Composable
private fun VitaminsRow(mn: com.nutriai.data.remote.dto.Micronutrients, expanded: Boolean, onToggle: () -> Unit) {
    val lowCount = mn.targets.count { it.low }
    val summary = if (lowCount == 0) "Most tracked nutrients are within target" else "$lowCount nutrient${if (lowCount > 1) "s" else ""} running low"
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = CardGreenLight),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            ListRow(
                title = "💊 Vitamins & minerals",
                subtitle = summary,
                leading = { EmojiBadge(emoji = "💊", bgColor = NutritionColor.copy(alpha = 0.12f)) },
                trailing = { TextAction(text = if (expanded) "Hide" else "Details →", onClick = onToggle) },
                onClick = onToggle,
            )
            if (expanded) com.nutriai.ui.analysis.MicronutrientsCard(mn, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun VitalsRow(sleepHours: Double?, bloodPressure: Pair<Int, Int>? = null, onEdit: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = CardCoralLight),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            ListRow(
                title = "🛌 Sleep & BP",
                subtitle = buildString {
                    val parts = mutableListOf<String>()
                    if (sleepHours != null) parts.add("${sleepHours}h sleep")
                    if (bloodPressure != null) parts.add("BP ${bloodPressure.first}/${bloodPressure.second}")
                    append(if (parts.isEmpty()) "No data yet" else parts.joinToString(" · "))
                },
                leading = { EmojiBadge(emoji = "🛌", bgColor = CoralAccent.copy(alpha = 0.15f)) },
                trailing = { TextAction(text = "Edit →", onClick = onEdit) },
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun JourneySummaryRow(dashboard: Dashboard) {
    val next = dashboard.projection.getOrNull(1) ?: return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlueLight),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            ListRow(title = "🚀 Your journey", subtitle = "Projected ${next.weightKg} kg by ${next.label.lowercase()} at your current pace", leading = { EmojiBadge(emoji = "📈", bgColor = BrandGreen.copy(alpha = 0.12f)) })
        }
    }
}

@Composable
private fun GoalMonitorSection(days: List<com.nutriai.data.remote.dto.ReportDay>, target: Double) {
    val today = remember { java.time.LocalDate.now() }
    val byDate = remember(days) { days.associateBy { it.date } }
    val week = remember(days) {
        (6 downTo 0).map { off ->
            val iso = today.minusDays(off.toLong()).toString()
            byDate[iso] ?: com.nutriai.data.remote.dto.ReportDay(date = iso, kcal = 0.0, proteinG = 0.0)
        }
    }
    val maxKcal = (week.maxOfOrNull { it.kcal } ?: target).coerceAtLeast(target).coerceAtLeast(1.0)
    val hit = week.count { it.kcal > 0 && it.kcal <= target * 1.1 }

    SectionHeader("7-Day Goal", emoji = "📅")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("📊 Weekly Progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(NutritionColor.copy(alpha = 0.12f)).padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                    Text("$hit/${week.size} on target", style = MaterialTheme.typography.labelSmall, color = NutritionColor, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
                week.forEach { day ->
                    val frac = (day.kcal / maxKcal).coerceIn(0.0, 1.0).toFloat()
                    val over = day.kcal > target * 1.15
                    val under = day.kcal in 0.1..(target * 0.7)
                    val logged = day.kcal > 0
                    val barColor = when { !logged -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f); over -> CoralAccent; under -> BrandAmber; else -> NutritionColor }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.55f).fillMaxHeight(frac.coerceAtLeast(0.06f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(barColor))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(dayShort(day.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun dayShort(date: String): String = runCatching {
    java.time.LocalDate.parse(date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
}.getOrElse { date.substringAfterLast('-') }

// ---------------------------------------------------------------------------
// Vitals Entry Dialog
// ---------------------------------------------------------------------------

@Composable
private fun VitalsEntryDialog(initialHr: Int?, initialStress: Int?, initialSoreness: Int? = null, onDismiss: () -> Unit, onSave: (Int?, Int?, Int?) -> Unit) {
    var hrText by remember { mutableStateOf(initialHr?.toString() ?: "") }
    var stress by remember { mutableStateOf(initialStress) }
    var soreness by remember { mutableStateOf(initialSoreness) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("❤️ Log your vitals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                OutlinedTextField(value = hrText, onValueChange = { hrText = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Resting heart rate (bpm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Text("😰 How stressed do you feel today?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Got a 0-100 stress score from your watch? 1 = <25 (calm), 2 = 25-45, 3 = 45-65 (normal), 4 = 65-85, 5 = 85+ (high)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { lvl ->
                        val selected = stress == lvl
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(SharpRadius)).background(if (selected) BrandGreen else MaterialTheme.colorScheme.surfaceVariant).clickable { stress = if (selected) null else lvl }, contentAlignment = Alignment.Center) {
                            Text("$lvl", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Text("💪 How sore are your muscles?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("High soreness softens tomorrow's plan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { lvl ->
                        val selected = soreness == lvl
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(SharpRadius)).background(if (selected) BrandAmber else MaterialTheme.colorScheme.surfaceVariant).clickable { soreness = if (selected) null else lvl }, contentAlignment = Alignment.Center) {
                            Text("$lvl", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hrText.toIntOrNull(), stress, soreness) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
