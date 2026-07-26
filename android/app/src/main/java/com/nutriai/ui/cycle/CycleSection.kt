package com.nutriai.ui.cycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Cycle
import com.nutriai.data.remote.dto.CycleHealth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class CycleUiState(val loading: Boolean = true, val cycle: Cycle? = null, val submitting: Boolean = false)

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CycleUiState())
    val state: StateFlow<CycleUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val c = repository.cycle().getOrNull()
            _state.value = _state.value.copy(loading = false, cycle = c)
        }
    }

    /** Marks the ongoing period as ended today, then refreshes (enables duration analysis). */
    fun endPeriod() {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            repository.endPeriod()
            _state.value = _state.value.copy(submitting = false, cycle = repository.cycle().getOrNull())
        }
    }

    /** Logs a period that started `daysAgo` days ago (0 = today), then refreshes. */
    fun logPeriod(daysAgo: Int) {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            val iso = LocalDate.now().minusDays(daysAgo.toLong())
                .atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant().toString()
            repository.logPeriod(iso)
            val c = repository.cycle().getOrNull()
            _state.value = _state.value.copy(submitting = false, cycle = c)
        }
    }

    /** Logs a period start on a calendar-picked date (UTC millis), then refreshes. */
    fun logPeriodOn(millis: Long) {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            val iso = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
                .toLocalDate().atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant().toString()
            repository.logPeriod(iso)
            _state.value = _state.value.copy(submitting = false, cycle = repository.cycle().getOrNull())
        }
    }
}

@Composable
fun CycleSection(modifier: Modifier = Modifier, viewModel: CycleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cycle = state.cycle ?: return
    if (!cycle.applicable) return

    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        PeriodDatePicker(
            onPick = { viewModel.logPeriodOn(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cycle.needsSetup) {
                Text("🌸 Track your cycle", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Log when your last period started and I'll tailor your diet, workouts and yoga to each phase.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("When did it start?", style = MaterialTheme.typography.labelMedium)
                DayAgoChips(enabled = !state.submitting, onPickDate = { showDatePicker = true }) { viewModel.logPeriod(it) }
            } else {
                Text(
                    "🌸 ${cycle.phaseLabel ?: "Your cycle"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        cycle.cycleDay?.let { append("Day $it") }
                        cycle.nextPeriodInDays?.let { append("  ·  next period ~$it day${if (it == 1) "" else "s"}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )

                if (cycle.shouldPromptPeriod) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Your period is due around now - has it started?", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { viewModel.logPeriod(0) }, enabled = !state.submitting) {
                                Text("Yes - log today")
                            }
                        }
                    }
                }

                if (cycle.periodOngoing) {
                    Button(onClick = { viewModel.endPeriod() }, enabled = !state.submitting) {
                        Text("🩸 My period ended today")
                    }
                }

                cycle.guidance?.let { g ->
                    CyclePhaseTips(g.summary, g.dietTips, g.exerciseTips, g.yogaTips, g.cravingTips, g.pmsTips)
                }

                cycle.health?.takeIf { it.status != "insufficient_data" || it.findings.isNotEmpty() }?.let { h ->
                    CycleHealthCard(h)
                }

                var showLog by remember { mutableStateOf(false) }
                Text(
                    if (showLog) "Cancel" else "＋ Log a new period start",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showLog = !showLog },
                )
                if (showLog) DayAgoChips(enabled = !state.submitting, onPickDate = { showDatePicker = true; showLog = false }) { viewModel.logPeriod(it); showLog = false }
            }
        }
    }
}

@Composable
private fun DayAgoChips(enabled: Boolean, onPickDate: (() -> Unit)? = null, onPick: (Int) -> Unit) {
    val options = listOf("Today" to 0, "3 days ago" to 3, "1 week ago" to 7, "2 weeks ago" to 14)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, days) ->
            AssistChip(onClick = { if (enabled) onPick(days) }, label = { Text(label) })
        }
        if (onPickDate != null) {
            AssistChip(onClick = { if (enabled) onPickDate() }, label = { Text("📅 Pick date") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDatePicker(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onPick) ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
}

@Composable
private fun CyclePhaseTips(
    summary: String,
    diet: List<String>,
    exercise: List<String>,
    yoga: List<String>,
    cravings: List<String>,
    pms: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary.ifBlank { "This phase's diet, exercise & yoga" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodyMedium)
    }
    if (expanded) {
        TipGroup("🥗 Diet", diet)
        TipGroup("🌡️ PMS management", pms)
        TipGroup("🍫 Cravings", cravings)
        TipGroup("🏃 Exercise", exercise)
        TipGroup("🧘 Yoga", yoga)
    }
}

@Composable
private fun CycleHealthCard(h: CycleHealth) {
    var expanded by remember(h) { mutableStateOf(h.status == "concern") }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (h.status == "concern") MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("🩺 Cycle health check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(h.headline, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (expanded) "▲" else "▼")
            }
            if (expanded) {
                if (h.seeDoctor) {
                    Text(
                        "⚠️ Worth getting checked by a gynaecologist.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                h.findings.forEach { f ->
                    Text("• ${f.issue}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("This can be due to: ${f.possibleCauses}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (h.redFlags.isNotEmpty()) {
                    Text("🚩 See a doctor if any of these", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    h.redFlags.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                TipGroup("🥗 Diet", h.advice.diet)
                TipGroup("😴 Sleep", h.advice.sleep)
                TipGroup("🌿 Lifestyle", h.advice.lifestyle)
                TipGroup("🏃 Exercise", h.advice.exercise)
                if (h.disclaimer.isNotBlank()) {
                    Text(h.disclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TipGroup(title: String, tips: List<String>) {
    if (tips.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    tips.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
}
