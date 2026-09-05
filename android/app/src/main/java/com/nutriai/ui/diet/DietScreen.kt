package com.nutriai.ui.diet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.nutriai.ui.theme.Spacing
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
        // ── Slim gradient header with calorie info ──
        dashboard?.let { d ->
            val cal = d.calories
            val hasTarget = cal.target != null && cal.target > 0
            val pct = if (hasTarget) (cal.consumed / cal.target!!).coerceIn(0.0, 1.0).toFloat() else 0f
            val remaining = if (hasTarget) (cal.target!! - cal.consumed).toInt() else 0

            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))))
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("🍎 Nutrition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("🔥", fontSize = 14.sp)
                            Text("${cal.consumed.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            cal.target?.let { Text("/ ${it.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)) }
                            Text("·", color = Color.White.copy(alpha = 0.5f))
                            Text("💧 ${((d.water.consumedMl ?: d.water.consumed ?: 0.0) / 250.0).toInt()} glasses", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    // Mini calorie ring
                    MiniRing(consumed = cal.consumed.toInt(), target = cal.target?.toInt(), progress = pct)
                }
            }
        }

        // ── Tab bar ──
        val tabs = listOf("📅" to "Today", "📝" to "Log", "🛒" to "Grocery", "🏢" to "Office")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            shape = RoundedCornerShape(Sharp),
            elevation = CardDefaults.cardElevation(2.dp),
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
                                if (selected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { section = idx }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(emoji, fontSize = 12.sp)
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

        // ── Tab content gets the rest of the screen ──
        when (section) {
            0 -> com.nutriai.ui.calendar.CalendarScreen(Modifier.fillMaxSize())
            1 -> com.nutriai.ui.log.LogScreen(Modifier.fillMaxSize())
            2 -> com.nutriai.ui.grocery.GroceryScreen(Modifier.fillMaxSize())
            else -> com.nutriai.ui.lifestyle.LifestyleScreen(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MiniRing(consumed: Int, target: Int?, progress: Float) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }
    val anim by animateFloatAsState(if (animate) progress else 0f, tween(1000), label = "ring")

    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(46.dp)) {
            val stroke = 5.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(Color.White.copy(alpha = 0.25f), -90f, 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White, -90f, anim * 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("🔥", fontSize = 16.sp)
    }
}
