package com.nutriai.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Dashboard
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class DietSummaryViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _dashboard = MutableStateFlow<Dashboard?>(null)
    val dashboard: StateFlow<Dashboard?> = _dashboard.asStateFlow()
    fun load() { viewModelScope.launch { _dashboard.value = repository.dashboard().getOrNull() } }
    fun addWater() { viewModelScope.launch { repository.logWater(250); load() } }
}

/**
 * The Diet pillar (60%) - today's plan, food log and grocery under one tab via a segmented switcher,
 * so nothing is buried and everything diet-related is one tap from the tab root.
 */
@Composable
fun DietScreen(
    modifier: Modifier = Modifier,
    initialSection: Int = 0,
    summaryViewModel: DietSummaryViewModel = hiltViewModel(),
) {
    var section by remember { mutableIntStateOf(initialSection.coerceIn(0, 3)) }
    val labels = listOf("Today", "Log", "Grocery", "Office")
    val dashboard by summaryViewModel.dashboard.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { summaryViewModel.load() }

    Column(modifier.fillMaxSize()) {
        com.nutriai.ui.components.ScreenHeader("Nutrition", modifier = Modifier.padding(horizontal = Spacing.screenHorizontal))

        // Landing hierarchy (Phase 4, Change 01): today's intake -> meal activity (below, via
        // sub-tabs) -> primary Log action -> hydration -> secondary tools (sub-tab chips).
        dashboard?.let { d ->
            NutritionLanding(
                dashboard = d,
                onLogFood = { section = 1 },
                onAddWater = { summaryViewModel.addWater() },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                FilterChip(
                    selected = section == i,
                    onClick = { section = i },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = "$label section" },
                )
            }
        }
        when (section) {
            0 -> com.nutriai.ui.calendar.CalendarScreen(Modifier.fillMaxSize())
            1 -> com.nutriai.ui.log.LogScreen(Modifier.fillMaxSize())
            2 -> com.nutriai.ui.grocery.GroceryScreen(Modifier.fillMaxSize())
            else -> com.nutriai.ui.lifestyle.LifestyleScreen(Modifier.fillMaxSize())
        }
    }
}

/**
 * Nutrition landing block, in the Today visual language (Phase 3): one dominant calorie summary,
 * macros as a grouped row (not three colored cards), an obvious primary Log action, and a compact
 * hydration line. No card wrapper - typography + spacing carry it (Change 01-03, 07).
 */
@Composable
private fun NutritionLanding(dashboard: Dashboard, onLogFood: () -> Unit, onAddWater: () -> Unit) {
    val cal = dashboard.calories
    val calFraction = if (cal.target != null && cal.target > 0) (cal.consumed / cal.target).coerceIn(0.0, 1.0).toFloat() else 0f
    val waterConsumed = dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0
    val waterTarget = dashboard.water.targetMl ?: dashboard.water.target
    val litersConsumed = waterConsumed / 1000.0
    val litersTarget = (waterTarget ?: 2500.0) / 1000.0
    val waterFraction = (waterConsumed / (waterTarget ?: 2500.0)).coerceIn(0.0, 1.0).toFloat()

    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)) {
        // Today's intake — the dominant number (Change 02).
        Text("Today's intake", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${cal.consumed.toInt()}${cal.target?.let { " / ${it.toInt()}" } ?: ""} kcal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        KaizenProgressBar(progress = calFraction, modifier = Modifier.padding(top = Spacing.xs), color = BrandAmber, height = 6.dp)

        Spacer(Modifier.height(Spacing.lg))

        // Macros — one grouped row, not three colored cards (Change 03).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricBlock(label = "Protein", value = "${(dashboard.protein.consumed ?: 0.0).toInt()}", unit = "g", color = BrandGreen)
            MetricBlock(label = "Carbs", value = "${dashboard.macros.carbG.toInt()}", unit = "g")
            MetricBlock(label = "Fat", value = "${dashboard.macros.fatG.toInt()}", unit = "g")
        }

        Spacer(Modifier.height(Spacing.lg))

        // Primary action - the obvious way to log food, no competing buttons (Change 05).
        Button(
            onClick = onLogFood,
            modifier = Modifier.fillMaxWidth().heightIn(min = com.nutriai.ui.theme.ComponentHeight.touchTarget),
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
        ) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Log food", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(Spacing.lg))

        // Hydration — one compact line, not a giant dashboard (Change 07).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Hydration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${"%.1f".format(litersConsumed)} / ${"%.1f".format(litersTarget)} L",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextAction(text = "+ Add water", onClick = onAddWater)
        }
        KaizenProgressBar(progress = waterFraction, modifier = Modifier.padding(top = Spacing.xs), color = KaizenBlue, height = 6.dp)
    }
}
