package com.nutriai.ui.diet

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.nutriai.ui.components.TextAction
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.CardAmberLight
import com.nutriai.ui.theme.CardBlueLight
import com.nutriai.ui.theme.CardCoralLight
import com.nutriai.ui.theme.CardGreenLight
import com.nutriai.ui.theme.CardMintLight
import com.nutriai.ui.theme.HeroGradientBottom
import com.nutriai.ui.theme.HeroGradientTop
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenBlue
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

private val Sharp = 8.dp

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
    val dashboard by summaryViewModel.dashboard.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { summaryViewModel.load() }

    Column(modifier.fillMaxSize()) {
        // Bold gradient header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(NutritionColor, BrandGreen)))
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("🍎", fontSize = 24.sp)
                    Text("Nutrition", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                // Calorie mini ring in header
                dashboard?.let { d ->
                    val pct = if (d.calories.target != null && d.calories.target > 0) (d.calories.consumed / d.calories.target).coerceIn(0.0, 1.0).toFloat() else 0f
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MiniCalorieRing(consumed = d.calories.consumed.toInt(), target = d.calories.target?.toInt(), progress = pct)
                    }
                }
            }
        }

        // Macro strip + actions
        dashboard?.let { d ->
            NutritionStrip(
                dashboard = d,
                onLogFood = { section = 1 },
                onAddWater = { summaryViewModel.addWater() },
            )
        }

        // Tab bar
        val tabs = listOf("📅 Today" to 0, "📝 Log" to 1, "🛒 Grocery" to 2, "🏢 Office" to 3)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            tabs.forEach { (label, idx) ->
                val selected = section == idx
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Sharp))
                        .background(if (selected) NutritionColor else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { section = idx }
                        .padding(vertical = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun MiniCalorieRing(consumed: Int, target: Int?, progress: Float) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }
    val anim by animateFloatAsState(if (animate) progress else 0f, tween(1000), label = "ring")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(horizontalAlignment = Alignment.End) {
            Text("%,d".format(consumed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            target?.let { Text("/ %,d kcal".format(it), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)) }
        }
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(40.dp)) {
                val stroke = 5.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(stroke / 2, stroke / 2)
                drawArc(Color.White.copy(alpha = 0.25f), -90f, 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Color.White, -90f, anim * 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("🔥", fontSize = 14.sp)
        }
    }
}

@Composable
private fun NutritionStrip(dashboard: Dashboard, onLogFood: () -> Unit, onAddWater: () -> Unit) {
    val cal = dashboard.calories
    val waterConsumed = dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0
    val waterTarget = dashboard.water.targetMl ?: dashboard.water.target ?: 2500.0
    val waterFrac = (waterConsumed / waterTarget).coerceIn(0.0, 1.0).toFloat()
    val glasses = (waterConsumed / 250.0).toInt()
    val glassTarget = (waterTarget / 250.0).toInt()

    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal).padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 3 macro cards side by side
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MacroCard(Modifier.weight(1f), "💪", "Protein", "${(dashboard.protein.consumed ?: 0.0).toInt()}g", dashboard.protein.target?.let { "${it.toInt()}g" }, NutritionColor, CardGreenLight)
            MacroCard(Modifier.weight(1f), "🌾", "Carbs", "${dashboard.macros.carbG.toInt()}g", null, BrandAmber, CardAmberLight)
            MacroCard(Modifier.weight(1f), "🥑", "Fat", "${dashboard.macros.fatG.toInt()}g", null, KaizenCoral, CardCoralLight)
        }

        // Action row: Log food + Barcode + Water
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = onLogFood,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(Sharp),
                colors = ButtonDefaults.buttonColors(containerColor = NutritionColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            ) {
                Icon(Icons.Filled.Restaurant, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Log food", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onLogFood,
                modifier = Modifier.height(42.dp),
                shape = RoundedCornerShape(Sharp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NutritionColor.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(16.dp), tint = NutritionColor)
            }
            // Water pill
            Card(
                onClick = onAddWater,
                modifier = Modifier.height(42.dp),
                shape = RoundedCornerShape(Sharp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = CardMintLight),
                border = androidx.compose.foundation.BorderStroke(1.dp, HydrationColor.copy(alpha = 0.3f)),
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.md).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("💧", fontSize = 14.sp)
                    Text("$glasses/$glassTarget", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = HydrationColor)
                }
            }
        }
    }
}

@Composable
private fun MacroCard(modifier: Modifier, emoji: String, label: String, value: String, target: String?, color: Color, bgColor: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Sharp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(emoji, fontSize = 16.sp)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
            if (target != null) {
                Text("/ $target", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
