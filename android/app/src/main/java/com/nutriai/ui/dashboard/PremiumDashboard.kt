package com.nutriai.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutriai.data.remote.dto.Dashboard
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.ListRow
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.Status
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Today — Phase 3 rebuild. Structure: HEADER -> TODAY STATUS -> PRIORITIES ->
// HEALTH SNAPSHOT -> ONE INSIGHT -> SECONDARY INFORMATION.
// One dominant surface (Today status) + at most one other meaningful surface
// (the 7-day goal chart). Everything else is rows, dividers and whitespace.
// ---------------------------------------------------------------------------

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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        // HEADER
        item { TodayHeader(greetingName = greetingName, streakDays = d.streakDays) }

        // TODAY STATUS — the one dominant surface.
        item {
            TodayStatusCard(
                dashboard = d,
                steps = steps,
                stepsPermission = stepsPermission,
                sleepHours = sleepHours,
                heartRate = heartRate ?: manualHeartRate,
            )
        }

        // PRIORITIES — row-based action list, real completion signals only.
        item {
            PrioritiesSection(
                dashboard = d,
                todayWorkout = todayWorkout,
                onOpenMove = onOpenMove,
                onAddWater = onAddWater,
            )
        }

        // HEALTH SNAPSHOT — one grouped section, Nutrition | Movement.
        item { HealthSnapshotSection(dashboard = d, steps = steps, stepsPermission = stepsPermission, stepsKcal = stepsKcal) }

        // ONE INSIGHT — compact preview, full analysis reachable on demand (not deleted).
        if (rating != null || coach != null) {
            item {
                InsightSection(
                    rating = rating,
                    coach = coach,
                    expanded = showFullAnalysis,
                    onToggle = { showFullAnalysis = !showFullAnalysis },
                )
            }
        }

        // SECONDARY INFORMATION — always below the primary experience, rows not cards.
        d.micronutrients?.let { mn ->
            if (mn.targets.isNotEmpty()) {
                item {
                    VitaminsRow(mn = mn, expanded = showVitamins, onToggle = { showVitamins = !showVitamins })
                }
            }
        }
        item {
            VitalsRow(
                heartRate = heartRate,
                manualHeartRate = manualHeartRate,
                sleepHours = sleepHours,
                onEdit = { editingVitals = true },
            )
        }
        if (weekDays.isNotEmpty() && weekKcalTarget != null && weekKcalTarget > 0) {
            item { GoalMonitorCard(days = weekDays, target = weekKcalTarget) }
        }
        if (d.projection.size > 1) {
            item { JourneySummaryRow(dashboard = d) }
        }
        if (safetyFlags.isNotEmpty()) {
            item { SafetyRows(flags = safetyFlags) }
        }
        if (riskFindings.isNotEmpty()) {
            item { RiskRows(findings = riskFindings) }
        }

        item {
            Text(
                "Educational guidance, not medical advice - consult a professional.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// HEADER
// ---------------------------------------------------------------------------

@Composable
private fun TodayHeader(greetingName: String?, streakDays: Int) {
    val now = remember { LocalTime.now() }
    val today = remember { java.time.LocalDate.now() }
    val greeting = when {
        now.hour < 12 -> "Good morning"
        now.hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    ScreenHeader(
        title = "$greeting, ${greetingName ?: "there"}",
        subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        modifier = Modifier.padding(top = Spacing.sm),
        action = {
            if (streakDays > 0) {
                StatusIndicator(text = "${streakDays}d streak", status = Status.Caution)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// TODAY STATUS — the dominant surface. Primary metric = calories (the strongest
// existing daily score); supporting row = Nutrition | Movement | Recovery.
// ---------------------------------------------------------------------------

@Composable
private fun TodayStatusCard(
    dashboard: Dashboard,
    steps: Long,
    stepsPermission: Boolean,
    sleepHours: Double?,
    heartRate: Int?,
) {
    val cal = dashboard.calories
    val hasTarget = cal.target != null && cal.target > 0
    val pct = if (hasTarget) (cal.consumed / cal.target!!).coerceIn(0.0, 1.5).toFloat() else 0f
    val remaining = if (hasTarget) (cal.target!! - cal.consumed) else 0.0

    val statusText = when {
        !hasTarget -> "Log meals to see your status"
        pct < 0.7f -> "${remaining.toInt()} kcal to go today"
        pct <= 1.1f -> "On track - ${remaining.toInt().coerceAtLeast(0)} kcal left"
        else -> "Over target by ${(-remaining).toInt()} kcal"
    }
    val proteinPct = if (dashboard.protein.target != null && dashboard.protein.target > 0) {
        (((dashboard.protein.consumed ?: 0.0) / dashboard.protein.target) * 100).toInt()
    } else null

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("TODAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(statusText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

            Text(
                "${cal.consumed.toInt()}${cal.target?.let { " / ${it.toInt()}" } ?: ""} kcal",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasTarget) {
                KaizenProgressBar(progress = pct.coerceIn(0f, 1f), color = BrandAmber, height = 8.dp)
            }

            Spacer(Modifier.height(Spacing.xs))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(Spacing.xs))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricBlock(label = "Nutrition", value = proteinPct?.let { "$it%" } ?: "-", color = BrandGreen)
                MetricBlock(label = "Movement", value = if (stepsPermission && steps > 0) "%,d".format(steps) else "-", color = MaterialTheme.colorScheme.onSurface)
                MetricBlock(
                    label = "Recovery",
                    value = sleepHours?.let { "${it}h" } ?: heartRate?.let { "$it bpm" } ?: "-",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PRIORITIES — row-based, real completion signals only (no invented data).
// ---------------------------------------------------------------------------

@Composable
private fun PrioritiesSection(
    dashboard: Dashboard,
    todayWorkout: com.nutriai.data.remote.dto.WorkoutDay?,
    onOpenMove: () -> Unit,
    onAddWater: () -> Unit,
) {
    val mealsLogged = dashboard.calories.consumed > 0
    val waterConsumed = dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0
    val waterTarget = dashboard.water.targetMl ?: dashboard.water.target
    val waterMet = waterTarget != null && waterConsumed >= waterTarget

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Today's priorities")

        ListRow(
            title = "Complete today's workout",
            subtitle = when {
                todayWorkout == null -> "Generate your plan in Move"
                todayWorkout.rest -> "Rest day - recovery"
                else -> todayWorkout.focus
            },
            leading = { Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            trailing = {
                StatusIndicator(
                    text = if (todayWorkout?.rest == true) "Rest" else "Open",
                    status = if (todayWorkout?.rest == true) Status.Positive else Status.Information,
                )
            },
            onClick = onOpenMove,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        ListRow(
            title = "Log today's meals",
            subtitle = if (mealsLogged) "${dashboard.calories.consumed.toInt()} kcal logged" else "Nothing logged yet",
            leading = { Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            trailing = { StatusIndicator(text = if (mealsLogged) "Started" else "Pending", status = if (mealsLogged) Status.Positive else Status.Caution) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        ListRow(
            title = "Drink more water",
            subtitle = "${(waterConsumed / 250.0).toInt()}${waterTarget?.let { "/${(it / 250.0).toInt()}" } ?: ""} glasses",
            leading = { Icon(Icons.Filled.LocalDrink, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            trailing = { TextAction(text = "+ Add", onClick = onAddWater) },
        )
    }
}

// ---------------------------------------------------------------------------
// HEALTH SNAPSHOT — Nutrition | Movement, grouped, no card-per-metric.
// ---------------------------------------------------------------------------

@Composable
private fun HealthSnapshotSection(dashboard: Dashboard, steps: Long, stepsPermission: Boolean, stepsKcal: Int) {
    val cal = dashboard.calories
    val calPct = if (cal.target != null && cal.target > 0) (cal.consumed / cal.target).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Today's health")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Nutrition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${cal.consumed.toInt()}${cal.target?.let { " / ${it.toInt()}" } ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                KaizenProgressBar(progress = calPct, color = BrandAmber)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Movement", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (stepsPermission && steps > 0) "%,d steps".format(steps) else "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (stepsPermission && steps > 0) "≈ $stepsKcal kcal" else "Connect Health Connect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ONE INSIGHT — compact preview; full analysis kept, revealed on demand.
// ---------------------------------------------------------------------------

@Composable
private fun InsightSection(
    rating: com.nutriai.data.remote.dto.RatingResult?,
    coach: com.nutriai.data.remote.dto.CoachBrief?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val headline = rating?.biggestLever?.message?.takeIf { it.isNotBlank() }
        ?: coach?.greeting?.takeIf { it.isNotBlank() }
        ?: "Keep logging to unlock your insight."
    val supporting = coach?.prediction?.takeIf { it.isNotBlank() } ?: coach?.streak?.takeIf { it.isNotBlank() }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Your insight")
        Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        supporting?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        TextAction(text = if (expanded) "Hide full analysis" else "See full analysis →", onClick = onToggle, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs))
        if (expanded) {
            com.nutriai.ui.analysis.AnalysisCard(rating = rating, coach = coach, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

// ---------------------------------------------------------------------------
// SECONDARY INFORMATION — concise rows, not one large card per item.
// ---------------------------------------------------------------------------

@Composable
private fun SafetyRows(flags: List<com.nutriai.data.remote.dto.Flag>) {
    val order = mapOf("critical" to 0, "warning" to 1, "info" to 2)
    val sorted = flags.sortedBy { order[it.severity] ?: 3 }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Health & safety notes")
        sorted.forEachIndexed { index, f ->
            val status = when (f.severity) {
                "critical" -> Status.Critical
                "warning" -> Status.Caution
                else -> Status.Information
            }
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                StatusIndicator(text = f.severity.uppercase(), status = status)
                Text(f.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
            }
            if (index != sorted.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun RiskRows(findings: List<com.nutriai.data.remote.dto.RiskFinding>) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Your health signals")
        findings.forEachIndexed { index, f ->
            val status = when (f.level) {
                "high" -> Status.Critical
                "moderate" -> Status.Caution
                else -> Status.Information
            }
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                StatusIndicator(text = f.label, status = status)
                Text(f.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                Text("→ ${f.nextAction}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index != findings.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun VitaminsRow(mn: com.nutriai.data.remote.dto.Micronutrients, expanded: Boolean, onToggle: () -> Unit) {
    val lowCount = mn.targets.count { it.low }
    val summary = if (lowCount == 0) "Most tracked nutrients are within target" else "$lowCount nutrient${if (lowCount > 1) "s" else ""} running low"
    Column(Modifier.fillMaxWidth()) {
        ListRow(
            title = "Vitamins & minerals",
            subtitle = summary,
            trailing = { TextAction(text = if (expanded) "Hide" else "Details →", onClick = onToggle) },
            onClick = onToggle,
        )
        if (expanded) {
            com.nutriai.ui.analysis.MicronutrientsCard(mn, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun VitalsRow(heartRate: Int?, manualHeartRate: Int?, sleepHours: Double?, onEdit: () -> Unit) {
    val hr = heartRate ?: manualHeartRate
    ListRow(
        title = "Heart rate",
        subtitle = when {
            hr != null && sleepHours != null -> "$hr bpm · ${sleepHours}h sleep"
            hr != null -> "$hr bpm"
            sleepHours != null -> "${sleepHours}h sleep"
            else -> "No data yet"
        },
        leading = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.kaizenColors.critical, modifier = Modifier.size(20.dp)) },
        trailing = { TextAction(text = "Edit →", onClick = onEdit) },
        onClick = onEdit,
    )
}

@Composable
private fun JourneySummaryRow(dashboard: Dashboard) {
    val next = dashboard.projection.getOrNull(1) ?: return
    ListRow(
        title = "Your journey",
        subtitle = "Projected ${next.weightKg} kg by ${next.label.lowercase()} at your current pace",
    )
}

// ---------------------------------------------------------------------------
// 7-day goal monitor — the one secondary chart surface worth a card.
// ---------------------------------------------------------------------------

@Composable
private fun GoalMonitorCard(days: List<com.nutriai.data.remote.dto.ReportDay>, target: Double) {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("7-day goal monitor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$hit/${week.size} on target", style = MaterialTheme.typography.labelMedium, color = BrandGreenDeep, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                week.forEach { day ->
                    val frac = (day.kcal / maxKcal).coerceIn(0.0, 1.0).toFloat()
                    val over = day.kcal > target * 1.15
                    val under = day.kcal in 0.1..(target * 0.7)
                    val logged = day.kcal > 0
                    val barColor = when {
                        !logged -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        over -> Color(0xFFB45309)
                        under -> BrandAmber
                        else -> BrandGreen
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (logged) "${(day.kcal / 100).toInt() * 100}" else "-", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .fillMaxHeight(frac.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(barColor),
                            )
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
// Vitals entry dialog — unchanged behavior, invoked from the compact Vitals row.
// ---------------------------------------------------------------------------

@Composable
private fun VitalsEntryDialog(
    initialHr: Int?,
    initialStress: Int?,
    initialSoreness: Int? = null,
    onDismiss: () -> Unit,
    onSave: (Int?, Int?, Int?) -> Unit,
) {
    var hrText by remember { mutableStateOf(initialHr?.toString() ?: "") }
    var stress by remember { mutableStateOf(initialStress) }
    var soreness by remember { mutableStateOf(initialSoreness) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log your vitals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = hrText,
                    onValueChange = { hrText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Resting heart rate (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Text("How stressed do you feel today?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { lvl ->
                        val selected = stress == lvl
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) BrandGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { stress = if (selected) null else lvl },
                            contentAlignment = Alignment.Center,
                        ) { Text("$lvl", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    }
                }
                Text("How sore are your muscles?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("High soreness softens tomorrow's plan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { lvl ->
                        val selected = soreness == lvl
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) BrandAmber else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { soreness = if (selected) null else lvl },
                            contentAlignment = Alignment.Center,
                        ) { Text("$lvl", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hrText.toIntOrNull(), stress, soreness) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
