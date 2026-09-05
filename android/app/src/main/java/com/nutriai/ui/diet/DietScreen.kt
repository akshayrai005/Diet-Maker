package com.nutriai.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.NutritionColor
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
        ScreenHeader("🍎 Nutrition", modifier = Modifier.padding(horizontal = Spacing.screenHorizontal))

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
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.semantics { contentDescription = "$label section" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandGreen.copy(alpha = 0.12f),
                        selectedLabelColor = BrandGreen,
                    ),
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

@Composable
private fun NutritionLanding(dashboard: Dashboard, onLogFood: () -> Unit, onAddWater: () -> Unit) {
    val cal = dashboard.calories
    val calFraction = if (cal.target != null && cal.target > 0) (cal.consumed / cal.target).coerceIn(0.0, 1.0).toFloat() else 0f
    val waterConsumed = dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0
    val waterTarget = dashboard.water.targetMl ?: dashboard.water.target
    val litersConsumed = waterConsumed / 1000.0
    val litersTarget = (waterTarget ?: 2500.0) / 1000.0
    val waterFraction = (waterConsumed / (waterTarget ?: 2500.0)).coerceIn(0.0, 1.0).toFloat()

    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md)) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("🔥 Today's intake", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${cal.consumed.toInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = NutritionColor)
                    cal.target?.let { Text(" / ${it.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                KaizenProgressBar(progress = calFraction, color = NutritionColor, height = 6.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MacroItem(label = "Protein", value = "${(dashboard.protein.consumed ?: 0.0).toInt()}", unit = "g", color = NutritionColor)
                    MacroItem(label = "Carbs", value = "${dashboard.macros.carbG.toInt()}", unit = "g", color = BrandAmber)
                    MacroItem(label = "Fat", value = "${dashboard.macros.fatG.toInt()}", unit = "g", color = KaizenCoral)
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(
                onClick = onLogFood,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(containerColor = NutritionColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Log food", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onLogFood,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(Radius.md),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        GlassCard {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(HydrationColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text("💧", style = MaterialTheme.typography.labelMedium)
                        }
                        Column {
                            Text("💧 Hydration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text("${"%.1f".format(litersConsumed)} / ${"%.1f".format(litersTarget)} L", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextAction(text = "+ Add", onClick = onAddWater)
                }
                KaizenProgressBar(progress = waterFraction, modifier = Modifier.padding(top = Spacing.sm), color = HydrationColor, height = 4.dp)
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
