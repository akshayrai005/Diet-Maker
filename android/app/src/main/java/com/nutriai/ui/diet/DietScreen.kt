package com.nutriai.ui.diet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
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
import com.nutriai.ui.theme.CardLavenderLight
import com.nutriai.ui.theme.CardMintLight
import com.nutriai.ui.theme.HeroGradientBottom
import com.nutriai.ui.theme.HeroGradientTop
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        // ── Purple gradient hero with calorie ring ──
        dashboard?.let { d ->
            NutritionHero(dashboard = d, onAddWater = { summaryViewModel.addWater() })
        }

        // ── Macro + Hydration 2x2 grid (matches dashboard "Your Day") ──
        dashboard?.let { d ->
            Column(Modifier.padding(horizontal = Spacing.screenHorizontal).padding(top = Spacing.lg)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    MacroTile(Modifier.weight(1f), "💪", "Protein", (d.protein.consumed ?: 0.0).toInt(), d.protein.target?.toInt(), "g", NutritionColor, CardGreenLight)
                    MacroTile(Modifier.weight(1f), "🌾", "Carbs", d.macros.carbG.toInt(), null, "g", BrandAmber, CardAmberLight)
                }
                Spacer(Modifier.height(Spacing.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    MacroTile(Modifier.weight(1f), "🥑", "Fat", d.macros.fatG.toInt(), null, "g", KaizenCoral, CardCoralLight)
                    HydrationTile(Modifier.weight(1f), d, onAddWater = { summaryViewModel.addWater() })
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Action buttons ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Button(
                onClick = { section = 1 },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(Sharp),
                colors = ButtonDefaults.buttonColors(containerColor = NutritionColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Log food", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { section = 1 },
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(Sharp),
                border = BorderStroke(2.dp, NutritionColor.copy(alpha = 0.5f)),
            ) {
                Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp), tint = NutritionColor)
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Tab bar matching dashboard style ──
        val tabs = listOf("📅" to "Today", "📝" to "Log", "🛒" to "Grocery", "🏢" to "Office")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            shape = RoundedCornerShape(Sharp),
            elevation = CardDefaults.cardElevation(3.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                tabs.forEachIndexed { idx, (emoji, label) ->
                    val selected = section == idx
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(HeroGradientTop, HeroGradientBottom))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { section = idx }
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 14.sp)
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        when (section) {
            0 -> com.nutriai.ui.calendar.CalendarScreen(Modifier.fillMaxSize())
            1 -> com.nutriai.ui.log.LogScreen(Modifier.fillMaxSize())
            2 -> com.nutriai.ui.grocery.GroceryScreen(Modifier.fillMaxSize())
            else -> com.nutriai.ui.lifestyle.LifestyleScreen(Modifier.fillMaxSize())
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Purple gradient hero — matches dashboard hero
// ─────────────────────────────────────────────────────────────

@Composable
private fun NutritionHero(dashboard: Dashboard, onAddWater: () -> Unit) {
    val cal = dashboard.calories
    val hasTarget = cal.target != null && cal.target > 0
    val pct = if (hasTarget) (cal.consumed / cal.target!!).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining = if (hasTarget) (cal.target!! - cal.consumed).toInt() else 0

    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HeroGradientTop, HeroGradientBottom)))
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = Spacing.lg, bottom = Spacing.xl),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: text info
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("🍎 Nutrition", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(Spacing.sm))
                // Remaining badge
                Card(
                    shape = RoundedCornerShape(Sharp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔥", fontSize = 14.sp)
                        Text("$remaining", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("left", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // Right: calorie ring
            CalorieRing(consumed = cal.consumed.toInt(), target = cal.target?.toInt(), progress = pct)
        }
    }
}

@Composable
private fun CalorieRing(consumed: Int, target: Int?, progress: Float) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }
    val anim by animateFloatAsState(if (animate) progress else 0f, tween(1200), label = "ring")

    Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(80.dp)) {
            val stroke = 8.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(Color.White.copy(alpha = 0.2f), -90f, 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White, -90f, anim * 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(consumed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            target?.let { Text("/ %,d".format(it), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Macro tile — matches dashboard domain cards exactly
// ─────────────────────────────────────────────────────────────

@Composable
private fun MacroTile(modifier: Modifier, emoji: String, label: String, value: Int, target: Int?, unit: String, color: Color, bgColor: Color) {
    val pct = if (target != null && target > 0) (value.toFloat() / target).coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Sharp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(emoji, fontSize = 16.sp)
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
            }
            if (target != null) {
                KaizenProgressBar(progress = pct, color = color, height = 5.dp)
                Text("/ $target$unit", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Hydration tile — matches domain card style
// ─────────────────────────────────────────────────────────────

@Composable
private fun HydrationTile(modifier: Modifier, dashboard: Dashboard, onAddWater: () -> Unit) {
    val waterConsumed = dashboard.water.consumedMl ?: dashboard.water.consumed ?: 0.0
    val waterTarget = dashboard.water.targetMl ?: dashboard.water.target ?: 2500.0
    val glasses = (waterConsumed / 250.0).toInt()
    val glassTarget = (waterTarget / 250.0).toInt()
    val pct = (waterConsumed / waterTarget).coerceIn(0.0, 1.0).toFloat()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Sharp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = CardMintLight),
        border = BorderStroke(1.5.dp, HydrationColor.copy(alpha = 0.3f)),
        onClick = onAddWater,
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("💧", fontSize = 16.sp)
                    Text("Hydration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = HydrationColor)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$glasses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = HydrationColor)
                Text("glasses", style = MaterialTheme.typography.labelSmall, color = HydrationColor.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
            }
            KaizenProgressBar(progress = pct, color = HydrationColor, height = 5.dp)
            Text("${"%.1f".format(waterConsumed / 1000.0)}L / ${"%.1f".format(waterTarget / 1000.0)}L", style = MaterialTheme.typography.labelSmall, color = HydrationColor.copy(alpha = 0.5f))
        }
    }
}
