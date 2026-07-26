package com.nutriai.ui.move

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.StrengthTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Strength trend - estimated 1-rep-max over time, per exercise. A selector picks
// the exercise; a Canvas line chart (same pattern as the Vitals trend chart)
// plots its est-1RM points, with best + change stats.
// ---------------------------------------------------------------------------

data class StrengthState(
    val loading: Boolean = true,
    val trends: List<StrengthTrend> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class StrengthTrendViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(StrengthState())
    val state: StateFlow<StrengthState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.strengthTrend()
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, trends = r.getOrNull().orEmpty(), error = null)
            } else {
                _state.value.copy(loading = false, error = "Couldn't load your strength trend.")
            }
        }
    }
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((Math.round(v * 10.0)) / 10.0).toString()

@Composable
fun StrengthTrendSection(
    modifier: Modifier = Modifier,
    viewModel: StrengthTrendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Strength trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        when {
            state.loading -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.trends.isEmpty() -> {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "Log a few weighted sets to see your strength trend.",
                        Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> StrengthTrendContent(state.trends)
        }
    }
}

@Composable
private fun StrengthTrendContent(trends: List<StrengthTrend>) {
    var selectedName by remember(trends) { mutableStateOf(trends.first().exerciseName) }
    val selected = trends.firstOrNull { it.exerciseName == selectedName } ?: trends.first()
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Exercise selector.
        Box {
            Card(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Select exercise. Current: ${selected.exerciseName}" },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selected.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Change ▾") }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                trends.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.exerciseName) },
                        onClick = { selectedName = t.exerciseName; menuOpen = false },
                    )
                }
            }
        }

        // Stats row: best · latest · change.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Best", "${trimNum(selected.best)} kg", Modifier.weight(1f))
            StatChip("Latest", "${trimNum(selected.latest)} kg", Modifier.weight(1f))
            val sign = if (selected.change > 0) "+" else ""
            StatChip("Change", "$sign${trimNum(selected.change)} kg", Modifier.weight(1f))
        }

        // Chart.
        val points = selected.points.map { it.est1RM }
        if (points.size >= 2) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    StrengthLineChart(
                        values = points,
                        exerciseName = selected.exerciseName,
                        lineColor = MaterialTheme.colorScheme.primary,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${trimNum(points.min())} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${trimNum(points.max())} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text(
                "Log a few more weighted sets of ${selected.exerciseName} to chart its trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier.semantics { contentDescription = "$label $value" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Canvas line chart mirroring the Vitals trend chart. */
@Composable
private fun StrengthLineChart(values: List<Double>, exerciseName: String, lineColor: Color) {
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).let { if (it == 0.0) 1.0 else it }
    val fill = lineColor.copy(alpha = 0.15f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription =
                    "Estimated one-rep-max trend for $exerciseName, ${values.size} points, " +
                        "from ${trimNum(minV)} to ${trimNum(maxV)} kilograms"
            },
    ) {
        val n = values.size
        if (n < 2) return@Canvas
        val padX = 6.dp.toPx()
        val padY = 10.dp.toPx()
        val plotW = size.width - padX * 2
        val plotH = size.height - padY * 2
        val dx = plotW / (n - 1)
        fun px(i: Int) = padX + dx * i
        fun py(v: Double) = padY + (plotH * (1f - ((v - minV) / range).toFloat()))

        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = px(i)
            val y = py(v)
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, size.height - padY)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(px(n - 1), size.height - padY)
        area.close()

        drawPath(area, color = fill)
        drawPath(line, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(px(i), py(v)))
        }
    }
}
