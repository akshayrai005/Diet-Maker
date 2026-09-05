package com.nutriai.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.nutriai.ui.theme.CoralAccent
import com.nutriai.ui.theme.HeroGradientTop
import com.nutriai.ui.theme.HeroGradientBottom
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenBlue
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
    stepsPermission: Boolean = true,
    stepsAvailable: Boolean = false,
    onConnectSteps: () -> Unit = {},
    heartRate: Int? = null,
    sleepHours: Double? = null,
    manualHeartRate: Int? = null,
    stress: Int? = null,
    onSaveVitals: (Int?, Int?, Int?) -> Unit = { _, _, _ -> },
    soreness: Int? = null,
    safetyFlags: List<com.nutriai.data.remote.dto.Flag> = emptyList(),
    riskFindings: List<com.nutriai.data.remote.dto.RiskFinding> = emptyList(),
    weekDays: List<com.nutriai.data.remote.dto.ReportDay> = emptyList(),
    weekKcalTarget: Double? = null,
    maintenanceKcal: Double? = null,
    coach: com.nutriai.data.remote.dto.CoachBrief? = null,
    rating: com.nutriai.data.remote.dto.RatingResult? = null,
    todayWorkout: com.nutriai.data.remote.dto.WorkoutDay? = null,
    onOpenVitals: () -> Unit = {},
    onOpenMove: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val d = dashboard
    var showFullAnalysis by remember { mutableStateOf(false) }
    var showVitamins by remember { mutableStateOf(false) }
    var editingVitals by remember { mutableStateOf(false) }

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
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xxl),
    ) {
        // Hero greeting
        item { HeroSection(greetingName = greetingName, streakDays = d.streakDays, dashboard = d) }

        // Calorie ring card
        item {
            Column(sectionPadding) {
                CalorieSummaryCard(dashboard = d, steps = steps, stepsPermission = stepsPermission)
            }
        }

        // Macro breakdown — Protein / Carbs / Fat
        item {
            Row(
                sectionPadding.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                MacroTile(Modifier.weight(1f), "💪", "Protein", (d.protein.consumed ?: 0.0).toInt(), d.protein.target?.toInt(), "g", NutritionColor, CardGreenLight)
                MacroTile(Modifier.weight(1f), "🌾", "Carbs", d.macros.carbG.toInt(), null, "g", BrandAmber, CardAmberLight)
                MacroTile(Modifier.weight(1f), "🥑", "Fat", d.macros.fatG.toInt(), null, "g", KaizenCoral, CardCoralLight)
            }
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
                        PrioritiesContent(dashboard = d, todayWorkout = todayWorkout, onOpenMove = onOpenMove, onAddWater = onAddWater)
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
                VitalsRow(heartRate = heartRate, manualHeartRate = manualHeartRate, sleepHours = sleepHours, onEdit = { editingVitals = true })
            }
        }

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
            .background(Brush.verticalGradient(listOf(HeroGradientTop, HeroGradientBottom)))
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = Spacing.xxl, bottom = Spacing.xl),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("$greetEmoji $greeting,", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                    Text(greetingName ?: "there", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
                if (streakDays > 0) {
                    Card(
                        shape = RoundedCornerShape(SharpRadius),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔥", fontSize = 20.sp)
                            Text("${streakDays}d", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
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
private fun CalorieSummaryCard(dashboard: Dashboard, steps: Long, stepsPermission: Boolean) {
    val cal = dashboard.calories
    val hasTarget = cal.target != null && cal.target > 0
    val pct = if (hasTarget) (cal.consumed / cal.target!!).coerceIn(0.0, 1.5).toFloat() else 0f
    val remaining = if (hasTarget) (cal.target!! - cal.consumed) else 0.0

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CalorieRing(consumed = cal.consumed.toInt(), target = cal.target?.toInt(), progress = pct)
            Column(Modifier.weight(1f).padding(start = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                QuickStat("🔥", "Remaining", if (hasTarget) "${remaining.toInt()}" else "—", "kcal", NutritionColor)
                QuickStat("💪", "Protein", dashboard.protein.let { p ->
                    val c = (p.consumed ?: 0.0).toInt()
                    p.target?.let { "${c}/${it.toInt()}" } ?: "$c"
                }, "g", KaizenBlue)
                QuickStat("🚶", "Steps", if (stepsPermission && steps > 0) "%,d".format(steps) else "—", "", MovementColor)
            }
        }
    }
}

@Composable
private fun QuickStat(emoji: String, label: String, value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(emoji, fontSize = 16.sp)
        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalorieRing(consumed: Int, target: Int?, progress: Float) {
    val ringProgress = progress.coerceIn(0f, 1f)
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }
    val animatedProgress by animateFloatAsState(targetValue = if (animate) ringProgress else 0f, animationSpec = tween(1200), label = "ring")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
        Canvas(modifier = Modifier.size(110.dp)) {
            val stroke = 12.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(color = NutritionColor.copy(alpha = 0.12f), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = NutritionColor, startAngle = -90f, sweepAngle = animatedProgress * 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔥", fontSize = 18.sp)
            Text("%,d".format(consumed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NutritionColor)
            Text(target?.let { "/ %,d".format(it) } ?: "kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Domain Cards — 2x2 grid, no horizontal scrolling
// ---------------------------------------------------------------------------

@Composable
private fun DomainCardsGrid(dashboard: Dashboard, steps: Long, stepsPermission: Boolean, stepsKcal: Int, sleepHours: Double?, heartRate: Int?) {
    val cal = dashboard.calories
    val calPct = if (cal.target != null && cal.target > 0) (cal.consumed / cal.target).coerceIn(0.0, 1.0).toFloat() else 0f
    val proteinPct = if (dashboard.protein.target != null && dashboard.protein.target > 0) {
        (((dashboard.protein.consumed ?: 0.0) / dashboard.protein.target) * 100).toInt()
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // Row 1: Nutrition + Movement
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "🍎", title = "Nutrition",
                mainValue = "${cal.consumed.toInt()}", mainUnit = "kcal",
                progress = calPct, accentColor = NutritionColor, bgColor = CardGreenLight,
                detail = proteinPct?.let { "$it% protein" } ?: "Log meals",
                borderColor = NutritionColor,
            )
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "💪", title = "Movement",
                mainValue = if (stepsPermission && steps > 0) "%,d".format(steps) else "-",
                mainUnit = if (stepsPermission && steps > 0) "steps" else "",
                progress = if (stepsPermission && steps > 0) (steps / 10000f).coerceIn(0f, 1f) else 0f,
                accentColor = MovementColor, bgColor = CardBlueLight,
                detail = if (stepsPermission && steps > 0) "≈ $stepsKcal kcal" else "Connect Health",
                borderColor = MovementColor,
            )
        }
        // Row 2: Recovery + Hydration
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "😴", title = "Recovery",
                mainValue = sleepHours?.let { "$it" } ?: heartRate?.let { "$it" } ?: "-",
                mainUnit = if (sleepHours != null) "hrs" else if (heartRate != null) "bpm" else "",
                progress = sleepHours?.let { (it / 8.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
                accentColor = RecoveryColor, bgColor = CardLavenderLight,
                detail = if (sleepHours != null && heartRate != null) "$heartRate bpm" else "Track sleep",
                borderColor = RecoveryColor,
            )
            DomainCard(
                modifier = Modifier.weight(1f),
                emoji = "💧", title = "Hydration",
                mainValue = "${((dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0) / 250.0).toInt()}",
                mainUnit = "glasses",
                progress = ((dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0) / (dashboard.water.targetMl ?: dashboard.water.target ?: 2500.0)).coerceIn(0.0, 1.0).toFloat(),
                accentColor = HydrationColor, bgColor = CardMintLight,
                detail = "${"%.1f".format((dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0) / 1000.0)}L",
                borderColor = HydrationColor,
            )
        }
    }
}

@Composable
private fun MacroTile(modifier: Modifier, emoji: String, label: String, value: Int, target: Int?, unit: String, color: Color, bgColor: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
            }
            if (target != null) {
                Text("/ $target$unit", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.5f))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DomainCard(
    modifier: Modifier = Modifier,
    emoji: String, title: String, mainValue: String, mainUnit: String,
    progress: Float, accentColor: Color, bgColor: Color, detail: String,
    borderColor: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(emoji, fontSize = 18.sp)
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentColor)
            }
            Text(mainValue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = accentColor)
            if (mainUnit.isNotEmpty()) Text(mainUnit, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f))
            KaizenProgressBar(progress = progress, color = accentColor, height = 6.dp)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Priorities
// ---------------------------------------------------------------------------

@Composable
private fun PrioritiesContent(dashboard: Dashboard, todayWorkout: com.nutriai.data.remote.dto.WorkoutDay?, onOpenMove: () -> Unit, onAddWater: () -> Unit) {
    val mealsLogged = dashboard.calories.consumed > 0
    val waterConsumed = dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0
    val waterTarget = dashboard.water.targetMl ?: dashboard.water.target

    PriorityRow("🏋️", MovementColor, "Complete today's workout", when { todayWorkout == null -> "Generate your plan in Move"; todayWorkout.rest -> "Rest day — recovery"; else -> todayWorkout.focus }, if (todayWorkout?.rest == true) "Rest" else "Open", if (todayWorkout?.rest == true) Status.Positive else Status.Information, onOpenMove)
    HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
    PriorityRow("🍽️", NutritionColor, "Log today's meals", if (mealsLogged) "${dashboard.calories.consumed.toInt()} kcal logged" else "Nothing logged yet", if (mealsLogged) "Started" else "Pending", if (mealsLogged) Status.Positive else Status.Caution)
    HorizontalDivider(color = MaterialTheme.kaizenColors.divider)
    PriorityRow(emoji = "💧", emojiColor = HydrationColor, title = "Drink more water", subtitle = "${(waterConsumed / 250.0).toInt()}${waterTarget?.let { "/${(it / 250.0).toInt()}" } ?: ""} glasses", trailing = { TextAction(text = "+ Add", onClick = onAddWater) })
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
    val supporting = coach?.prediction?.takeIf { it.isNotBlank() } ?: coach?.streak?.takeIf { it.isNotBlank() }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = KaizenLavender.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, KaizenLavender.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("💡", fontSize = 18.sp)
                Text("Your Insight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = KaizenLavender)
            }
            Spacer(Modifier.height(Spacing.md))
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
    val order = mapOf("critical" to 0, "warning" to 1, "info" to 2)
    val sorted = flags.sortedBy { order[it.severity] ?: 3 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Health & Safety", emoji = "🛡️")
        // Header card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharpRadius),
            elevation = CardDefaults.cardElevation(5.dp),
            colors = CardDefaults.cardColors(containerColor = CardCoralLight),
            border = androidx.compose.foundation.BorderStroke(2.dp, KaizenCoral.copy(alpha = 0.4f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("⚠️", fontSize = 22.sp)
                Text("Safety Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = KaizenCoral)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(KaizenCoral).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text("${sorted.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        // Each flag as its own card
        sorted.forEach { f ->
            val (flagColor, flagBg) = when (f.severity) {
                "critical" -> KaizenCoral to CardCoralLight
                "warning" -> BrandAmber to CardAmberLight
                else -> KaizenBlue to CardBlueLight
            }
            val status = when (f.severity) { "critical" -> Status.Critical; "warning" -> Status.Caution; else -> Status.Information }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharpRadius),
                elevation = CardDefaults.cardElevation(3.dp),
                colors = CardDefaults.cardColors(containerColor = flagBg),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, flagColor.copy(alpha = 0.35f)),
            ) {
                Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Health Signals", emoji = "📡")
        // Header card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharpRadius),
            elevation = CardDefaults.cardElevation(5.dp),
            colors = CardDefaults.cardColors(containerColor = CardAmberLight),
            border = androidx.compose.foundation.BorderStroke(2.dp, BrandAmber.copy(alpha = 0.4f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("🔍", fontSize = 22.sp)
                Text("Risk Findings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(BrandAmber).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text("${findings.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        // Each finding as its own colorful card
        findings.forEach { f ->
            val (findColor, findBg) = when (f.level) {
                "high" -> KaizenCoral to CardCoralLight
                "moderate" -> BrandAmber to CardAmberLight
                else -> KaizenBlue to CardBlueLight
            }
            val status = when (f.level) { "high" -> Status.Critical; "moderate" -> Status.Caution; else -> Status.Information }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharpRadius),
                elevation = CardDefaults.cardElevation(3.dp),
                colors = CardDefaults.cardColors(containerColor = findBg),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, findColor.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = CardGreenLight),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NutritionColor.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
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
private fun VitalsRow(heartRate: Int?, manualHeartRate: Int?, sleepHours: Double?, onEdit: () -> Unit) {
    val hr = heartRate ?: manualHeartRate
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharpRadius),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = CardCoralLight),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CoralAccent.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            ListRow(
                title = "❤️ Heart rate",
                subtitle = when { hr != null && sleepHours != null -> "$hr bpm · ${sleepHours}h sleep"; hr != null -> "$hr bpm"; sleepHours != null -> "${sleepHours}h sleep"; else -> "No data yet" },
                leading = { EmojiBadge(emoji = "❤️", bgColor = CoralAccent.copy(alpha = 0.15f)) },
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
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlueLight),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MovementColor.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
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
