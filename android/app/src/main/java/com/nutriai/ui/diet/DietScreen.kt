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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Dashboard
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.CardGreenLight
import com.nutriai.ui.theme.CardMintLight
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

private val SharpRadius = 8.dp

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

        // Tab chips
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

    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Compact calorie card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharpRadius),
            elevation = CardDefaults.cardElevation(3.dp),
            colors = CardDefaults.cardColors(containerColor = CardGreenLight),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NutritionColor.copy(alpha = 0.25f)),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥 Today's intake", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NutritionColor)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${cal.consumed.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = NutritionColor)
                        cal.target?.let { Text(" / ${it.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                KaizenProgressBar(progress = calFraction, color = NutritionColor, height = 5.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CompactMacro("Protein", "${(dashboard.protein.consumed ?: 0.0).toInt()}g", NutritionColor)
                    CompactMacro("Carbs", "${dashboard.macros.carbG.toInt()}g", BrandAmber)
                    CompactMacro("Fat", "${dashboard.macros.fatG.toInt()}g", KaizenCoral)
                }
            }
        }

        // Log food + barcode row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(
                onClick = onLogFood,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(SharpRadius),
                colors = ButtonDefaults.buttonColors(containerColor = NutritionColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Log food", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onLogFood,
                modifier = Modifier.heightIn(min = 44.dp),
                shape = RoundedCornerShape(SharpRadius),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        // Compact hydration card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SharpRadius),
            elevation = CardDefaults.cardElevation(3.dp),
            colors = CardDefaults.cardColors(containerColor = CardMintLight),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, HydrationColor.copy(alpha = 0.25f)),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("💧", fontSize = 16.sp)
                        Text("Hydration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = HydrationColor)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("${"%.1f".format(litersConsumed)} / ${"%.1f".format(litersTarget)} L", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        TextAction(text = "+ Add", onClick = onAddWater)
                    }
                }
                KaizenProgressBar(progress = waterFraction, modifier = Modifier.padding(top = Spacing.xs), color = HydrationColor, height = 4.dp)
            }
        }
    }
}

@Composable
private fun CompactMacro(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(2.dp)))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
