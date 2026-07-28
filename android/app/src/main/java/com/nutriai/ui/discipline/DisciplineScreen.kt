package com.nutriai.ui.discipline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nutriai.data.remote.dto.DisciplineToday
import com.nutriai.data.remote.dto.HabitView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisciplineState(val loading: Boolean = true, val data: DisciplineToday? = null)

@HiltViewModel
class DisciplineViewModel @Inject constructor(
    private val repository: AppRepository,
    private val healthConnect: com.nutriai.data.health.HealthConnectManager,
) : ViewModel() {
    private val _state = MutableStateFlow(DisciplineState())
    val state: StateFlow<DisciplineState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            // Feed device metrics (Health Connect) so steps/sleep habits auto-tick when the goal's hit.
            val steps = runCatching { healthConnect.readTodaySteps().toInt() }.getOrNull()?.takeIf { it > 0 }
            val sleep = runCatching { healthConnect.readLastSleepHours() }.getOrNull()
            _state.value = DisciplineState(loading = false, data = repository.disciplineToday(steps, sleep).getOrNull())
        }
    }

    fun toggle(habit: HabitView) {
        viewModelScope.launch {
            repository.toggleHabit(habit.id, !habit.doneToday).getOrNull()?.let {
                _state.value = _state.value.copy(data = it)
            }
        }
    }

    fun addHabit(title: String) {
        viewModelScope.launch {
            if (repository.createHabit(title).isSuccess) refresh()
        }
    }

    fun removeHabit(id: String) {
        viewModelScope.launch {
            if (repository.deleteHabit(id).isSuccess) refresh()
        }
    }
}

@Composable
fun DisciplineScreen(modifier: Modifier = Modifier, viewModel: DisciplineViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a habit") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it.take(60) },
                    label = { Text("e.g. 10-minute walk after lunch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.addHabit(newTitle); newTitle = ""; showAdd = false },
                    enabled = newTitle.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Text("Discipline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        state.data?.let { d ->
            item { AdherenceCard(score = d.adherence.score, review = d.review) }

            d.nudges.forEach { nudge ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Text(nudge, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { Text("Today’s habits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(d.habits.size) { i ->
                HabitRow(
                    d.habits[i],
                    onToggle = { viewModel.toggle(d.habits[i]) },
                    onDelete = { viewModel.removeHabit(d.habits[i].id) },
                )
            }
            item {
                TextButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add a habit" },
                ) { Text("＋ Add habit") }
            }

            if (d.adherence.topActions.isNotEmpty()) {
                item { Text("Quick wins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                d.adherence.topActions.forEach { a ->
                    item { Text("• $a", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun AdherenceCard(score: Int, review: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AdherenceRing(score)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today’s adherence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(review, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun AdherenceRing(score: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier.size(84.dp).semantics { contentDescription = "Adherence $score out of 100" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = accent, startAngle = -90f, sweepAngle = 360f * (score.coerceIn(0, 100) / 100f), useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun HabitRow(habit: HabitView, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = habit.doneToday,
                // Auto-tracked habits reflect the metric (steps/water/sleep) and can't be toggled by hand.
                enabled = !habit.autoTracked,
                onCheckedChange = { if (!habit.autoTracked) onToggle() },
                modifier = Modifier.semantics { contentDescription = "${habit.title}, ${if (habit.doneToday) "done" else "not done"}${if (habit.autoTracked) ", auto-tracked" else ""}" },
            )
            Column(Modifier.weight(1f)) {
                Text("${habit.icon ?: ""} ${habit.title}".trim(), style = MaterialTheme.typography.bodyLarge)
                if (habit.autoTracked && habit.current != null && habit.target != null) {
                    val unit = habit.unit ?: ""
                    val cur = habit.current.toInt()
                    val tgt = habit.target.toInt()
                    Text(
                        if (habit.doneToday) "✓ auto: $cur/$tgt $unit" else "$cur/$tgt $unit — auto-tracks when you hit the goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (habit.streakDays > 0) {
                Text(
                    "🔥 ${habit.streakDays}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onDelete() }
                    .semantics { contentDescription = "Delete ${habit.title}" }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
