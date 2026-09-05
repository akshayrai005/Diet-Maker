package com.nutriai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.FoodLogEntry
import com.nutriai.data.remote.dto.FoodLogPer100g
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * A one-tap pre-built Indian meal (Section 15 of the spec). Macros are the TOTAL for the whole combo;
 * we log it as grams = 100 with per-100g = these totals, so the server records exactly this.
 */
data class QuickCombo(
    val label: String,
    val emoji: String,
    val kcal: Double,
    val proteinG: Double,
    val carbG: Double,
    val fatG: Double,
) {
    val subtitle get() = "~${kcal.toInt()} kcal · ${proteinG.toInt()}g protein"
}

private val COMBOS = listOf(
    QuickCombo("4 boiled eggs", "🥚", 248.0, 24.0, 2.0, 16.0),
    QuickCombo("2 rotis + dal", "🫓", 380.0, 18.0, 58.0, 8.0),
    QuickCombo("150g chicken breast", "🍗", 248.0, 47.0, 0.0, 5.0),
    QuickCombo("Office chai + 2 biscuits", "☕", 150.0, 4.0, 22.0, 5.0),
    QuickCombo("1 scoop whey", "🥛", 120.0, 24.0, 3.0, 1.5),
    QuickCombo("30g peanuts", "🥜", 170.0, 8.0, 5.0, 14.0),
    QuickCombo("1 katori curd", "🍚", 90.0, 8.0, 7.0, 4.0),
    QuickCombo("100g paneer bhurji", "🧀", 265.0, 18.0, 6.0, 20.0),
)

data class QuickLogState(
    val recents: List<FoodLogEntry> = emptyList(),
    val toast: String? = null,
)

@HiltViewModel
class QuickLogViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(QuickLogState())
    val state: StateFlow<QuickLogState> = _state.asStateFlow()

    val combos: List<QuickCombo> = COMBOS

    /** The meal slot that fits the current time of day, matching the app's slot vocabulary. */
    fun currentSlot(): String {
        val minutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        return when {
            minutes < 10 * 60 + 30 -> "breakfast"
            minutes < 12 * 60 -> "midmorning"
            minutes < 16 * 60 -> "lunch"
            minutes < 18 * 60 + 30 -> "eveningsnack"
            minutes < 22 * 60 -> "dinner"
            else -> "bedtime"
        }
    }

    fun loadRecents() {
        viewModelScope.launch {
            val entries = repository.todayLogs().getOrNull().orEmpty()
            // Most recent first, de-duplicated by food name, capped at 5 for one-tap re-logging.
            val recent = entries.asReversed().distinctBy { it.foodName.lowercase() }.take(5)
            _state.value = _state.value.copy(recents = recent)
        }
    }

    fun logCombo(combo: QuickCombo) {
        viewModelScope.launch {
            val r = repository.logNamed(
                slot = currentSlot(),
                name = combo.label,
                per100g = FoodLogPer100g(kcal = combo.kcal, proteinG = combo.proteinG, carbG = combo.carbG, fatG = combo.fatG),
                grams = 100.0,
                method = "quicklog",
            )
            _state.value = _state.value.copy(
                toast = if (r.isSuccess) "Logged ${combo.label} 🍽️" else "Couldn't log - try again",
            )
            if (r.isSuccess) loadRecents()
        }
    }

    fun logRecent(entry: FoodLogEntry) {
        viewModelScope.launch {
            // Re-log the same quantity; reconstruct per-100g from the stored totals.
            val g = entry.grams.takeIf { it > 0 } ?: 100.0
            val factor = 100.0 / g
            val r = repository.logNamed(
                slot = currentSlot(),
                name = entry.foodName,
                per100g = FoodLogPer100g(
                    kcal = entry.kcal * factor,
                    proteinG = entry.proteinG * factor,
                    carbG = entry.carbG * factor,
                    fatG = entry.fatG * factor,
                    fiberG = entry.fiberG * factor,
                    sugarG = entry.sugarG * factor,
                    sodiumMg = entry.sodiumMg * factor,
                ),
                grams = g,
                method = "quicklog",
            )
            _state.value = _state.value.copy(toast = if (r.isSuccess) "Logged ${entry.foodName} again ✅" else "Couldn't log - try again")
            if (r.isSuccess) loadRecents()
        }
    }

    fun logWater() {
        viewModelScope.launch {
            val r = repository.logWater(250)
            _state.value = _state.value.copy(toast = if (r.isSuccess) "+1 glass of water 💧" else "Couldn't log water")
        }
    }

    fun markSkipped() {
        _state.value = _state.value.copy(toast = "Meal marked as skipped - try to eat something small so you don't lose muscle.")
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
}

/**
 * Quick-log bottom sheet reached from the dashboard's floating "+" button. Built for busy users who
 * won't open the app several times a day: one-tap Indian combos, re-log recent foods, add water, or
 * mark a meal skipped - all without leaving whatever screen they're on. Every action auto-picks the
 * meal slot from the time of day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(
    onDismiss: () -> Unit,
    viewModel: QuickLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadRecents() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = com.nutriai.ui.theme.Spacing.screenHorizontal).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("⚡ Quick log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            state.toast?.let { msg ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) { Text(msg, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium) }
                androidx.compose.runtime.LaunchedEffect(msg) { kotlinx.coroutines.delay(2200); viewModel.clearToast() }
            }

            // Water + skipped, side by side.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("💧", "Log water", "+1 glass (250ml)", Modifier.weight(1f)) { viewModel.logWater() }
                QuickActionCard("⏭️", "Skipped meal", "Mark this meal skipped", Modifier.weight(1f)) { viewModel.markSkipped() }
            }

            if (state.recents.isNotEmpty()) {
                Text("🔄 Recent - tap to re-log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                state.recents.forEach { entry ->
                    QuickRow("🔁", entry.foodName, "${entry.kcal.toInt()} kcal · ${entry.proteinG.toInt()}g protein") { viewModel.logRecent(entry) }
                }
            }

            Text("🍽️ One-tap meals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            viewModel.combos.forEach { combo ->
                QuickRow(combo.emoji, combo.label, combo.subtitle) { viewModel.logCombo(combo) }
            }
        }
    }
}

@Composable
private fun QuickActionCard(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$emoji $title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun QuickRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .semantics { contentDescription = "Log $title" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+ Log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
