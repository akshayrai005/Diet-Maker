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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.AppRepository
import com.nutriai.data.health.HealthConnectManager
import com.nutriai.data.remote.dto.DailyHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

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
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Your history",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { d ->
                    FilterChip(
                        selected = state.days == d,
                        onClick = { viewModel.load(d) },
                        label = { Text("Last $d days") },
                    )
                }
            }
        }

        if (state.loading) {
            item { Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        } else {
            val h = state.history
            if (h == null || h.days.isEmpty()) {
                item { Text("No history yet — log a few days and it'll fill in here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val dates = h.days.map { it.date }
                val stepVals = dates.map { (state.stepsByDate[it] ?: 0L).toFloat() }
                val stepsTotal = stepVals.sum().toLong()

                item {
                    MetricChart(
                        title = "🚶 Steps",
                        subtitle = if (stepsTotal > 0) "avg ${(stepsTotal / max(1, dates.size)).toInt()}/day" else "Connect Health Connect to see steps",
                        values = stepVals,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    MetricChart(
                        title = "🔥 Calories eaten",
                        subtitle = "avg ${(h.days.map { it.kcal }.average().takeIf { it.isFinite() } ?: 0.0).toInt()} kcal/day",
                        values = h.days.map { it.kcal.toFloat() },
                        color = Color(0xFFEF6C56),
                    )
                }
                item {
                    MetricChart(
                        title = "💧 Water",
                        subtitle = "avg ${(h.days.map { it.waterMl }.average().takeIf { it.isFinite() } ?: 0.0).toInt()} ml/day",
                        values = h.days.map { it.waterMl.toFloat() },
                        color = Color(0xFF3FA7F0),
                    )
                }
                item {
                    MetricChart(
                        title = "💪 Protein",
                        subtitle = "avg ${(h.days.map { it.proteinG }.average().takeIf { it.isFinite() } ?: 0.0).toInt()} g/day",
                        values = h.days.map { it.proteinG.toFloat() },
                        color = Color(0xFF7BC96F),
                    )
                }
                if (h.weight.size >= 2) {
                    item {
                        val first = h.weight.first().weightKg
                        val last = h.weight.last().weightKg
                        val delta = Math.round((last - first) * 10) / 10.0
                        MetricChart(
                            title = "⚖️ Weight",
                            subtitle = "${last} kg · ${if (delta <= 0) "" else "+"}$delta kg over ${state.days} days",
                            values = h.weight.map { it.weightKg.toFloat() },
                            color = Color(0xFFB07BE0),
                            line = true,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A compact bar (or line) chart drawn on a Canvas. Bars scale to the max value in the window. */
@Composable
private fun MetricChart(title: String, subtitle: String, values: List<Float>, color: Color, line: Boolean = false) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val maxV = max(1f, values.maxOrNull() ?: 1f)
            val minV = if (line) (values.minOrNull() ?: 0f) else 0f
            val span = max(1f, maxV - minV)
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
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
                        drawCircle(color, radius = 4f, center = p)
                        prev = p
                    }
                } else {
                    val gap = size.width / n * 0.25f
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
