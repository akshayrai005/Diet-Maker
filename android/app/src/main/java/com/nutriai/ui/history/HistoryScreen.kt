package com.nutriai.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.AppRepository
import com.nutriai.data.health.HealthConnectManager
import com.nutriai.data.remote.dto.DailyHistory
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

private val Sharp = RoundedCornerShape(8.dp)

data class HistoryState(
    val loading: Boolean = true,
    val days: Int = 30,
    val history: DailyHistory? = null,
    val stepsByDate: Map<String, Long> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: AppRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init { load(30) }

    fun load(days: Int) {
        _state.value = _state.value.copy(loading = true, days = days)
        viewModelScope.launch {
            val history = repository.history(days).getOrNull()
            val steps = runCatching { healthConnect.readDailySteps(days) }.getOrDefault(emptyMap())
            _state.value = _state.value.copy(loading = false, history = history, stepsByDate = steps)
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Spacer(Modifier.height(Spacing.md))
            Text("📜 Your History", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(7 to "📅", 30 to "🗓️").forEach { (d, emoji) ->
                    FilterChip(
                        selected = state.days == d,
                        onClick = { viewModel.load(d) },
                        label = { Text("$emoji Last $d days") },
                        shape = Sharp,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandGreen,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        if (state.loading) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.xl),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = BrandGreen) }
            }
        } else {
            val h = state.history
            if (h == null || h.days.isEmpty()) {
                item {
                    EmptyState(
                        title = "No history yet",
                        emoji = "📊",
                        message = "Log a few days and it'll fill in here.",
                    )
                }
            } else {
                val dates = h.days.map { it.date }
                val stepVals = dates.map { (state.stepsByDate[it] ?: 0L).toFloat() }
                val stepsTotal = stepVals.sum().toLong()

                item {
                    Text("🚶 Activity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    MetricChart(
                        title = "🚶 Steps",
                        subtitle = if (stepsTotal > 0) "avg ${(stepsTotal / max(1, dates.size)).toInt()}/day" else "Connect Health Connect to see steps",
                        values = stepVals,
                        color = MovementColor,
                    )
                }

                item {
                    Text("🍲 Nutrition", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    MetricChart(
                        title = "🔥 Calories eaten",
                        subtitle = "avg ${(h.days.map { it.kcal }.average().takeIf { it.isFinite() } ?: 0.0).toInt()} kcal/day",
                        values = h.days.map { it.kcal.toFloat() },
                        color = KaizenCoral,
                    )
                }
                item {
                    MetricChart(
                        title = "💧 Water",
                        subtitle = "avg ${(h.days.map { it.waterMl }.average().takeIf { it.isFinite() } ?: 0.0).toInt()} ml/day",
                        values = h.days.map { it.waterMl.toFloat() },
                        color = HydrationColor,
                    )
                }
                item {
                    MetricChart(
                        title = "💪 Protein",
                        subtitle = "avg ${(h.days.map { it.proteinG }.average().takeIf { it.isFinite() } ?: 0.0).toInt()} g/day",
                        values = h.days.map { it.proteinG.toFloat() },
                        color = NutritionColor,
                    )
                }
                if (h.weight.size >= 2) {
                    item {
                        Text("🏋️ Body", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        val first = h.weight.first().weightKg
                        val last = h.weight.last().weightKg
                        val delta = Math.round((last - first) * 10) / 10.0
                        MetricChart(
                            title = "⚖️ Weight",
                            subtitle = "${last} kg · ${if (delta <= 0) "" else "+"}$delta kg over ${state.days} days",
                            values = h.weight.map { it.weightKg.toFloat() },
                            color = KaizenLavender,
                            line = true,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

/** A compact bar (or line) chart drawn on a Canvas inside a plain Card. */
@Composable
private fun MetricChart(title: String, subtitle: String, values: List<Float>, color: Color, line: Boolean = false) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            val maxV = max(1f, values.maxOrNull() ?: 1f)
            val minV = if (line) (values.minOrNull() ?: 0f) else 0f
            val span = max(1f, maxV - minV)
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                val n = values.size
                if (n == 0) return@Canvas
                if (line) {
                    val step = if (n > 1) size.width / (n - 1) else size.width
                    var prev: Offset? = null
                    values.forEachIndexed { i, v ->
                        val x = i * step
                        val y = size.height - ((v - minV) / span) * size.height
                        val p = Offset(x, y)
                        prev?.let { drawLine(color, it, p, strokeWidth = 4f) }
                        drawCircle(color, radius = 5f, center = p)
                        prev = p
                    }
                } else {
                    val gap = size.width / n * 0.20f
                    val barW = size.width / n - gap
                    values.forEachIndexed { i, v ->
                        val bh = (v / maxV) * size.height
                        val x = i * (barW + gap)
                        drawRect(
                            color = if (v > 0f) color else color.copy(alpha = 0.15f),
                            topLeft = Offset(x, size.height - bh),
                            size = androidx.compose.ui.geometry.Size(barW, max(bh, 1f)),
                        )
                    }
                }
            }
        }
    }
}
