package com.nutriai.ui.discipline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.Status
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

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
            title = { Text("🎯 Add a habit") },
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
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandGreen) } }
        }

        state.data?.let { d ->
            // Adherence score card
            item {
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("🏆 Today's Adherence", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AdherenceRing(d.adherence.score)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    "${d.adherence.score}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = KaizenLavender,
                                )
                                KaizenProgressBar(
                                    progress = d.adherence.score / 100f,
                                    color = KaizenLavender,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    d.review,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Nudges
            if (d.nudges.isNotEmpty()) {
                item {
                    Text("💡 Nudges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                d.nudges.forEach { nudge ->
                    item {
                        Card(
                            shape = Sharp,
                            elevation = CardDefaults.cardElevation(2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.padding(Spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("💬", style = MaterialTheme.typography.titleMedium)
                                Text(nudge, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Today's habits
            item {
                Text("✅ Today's Habits", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            itemsIndexed(d.habits) { i, habit ->
                HabitRow(habit, onToggle = { viewModel.toggle(habit) }, onDelete = { viewModel.removeHabit(habit.id) })
            }
            item {
                PrimaryButton(
                    text = "➕ Add Habit",
                    onClick = { showAdd = true },
                    containerColor = KaizenBlue,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Add a habit" },
                )
            }

            // Quick wins
            if (d.adherence.topActions.isNotEmpty()) {
                item {
                    Text("🚀 Quick Wins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                d.adherence.topActions.forEach { a ->
                    item {
                        Card(
                            shape = Sharp,
                            elevation = CardDefaults.cardElevation(2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.padding(Spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⚡", style = MaterialTheme.typography.titleMedium)
                                Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdherenceRing(score: Int) {
    val accent = KaizenLavender
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
    val habitEmoji = when {
        habit.doneToday -> "✅"
        habit.autoTracked -> "🤖"
        else -> "⬜"
    }
    val accentColor = if (habit.doneToday) NutritionColor else KaizenBlue

    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Checkbox(
                checked = habit.doneToday,
                enabled = !habit.autoTracked,
                onCheckedChange = { if (!habit.autoTracked) onToggle() },
                modifier = Modifier.semantics { contentDescription = "${habit.title}, ${if (habit.doneToday) "done" else "not done"}${if (habit.autoTracked) ", auto-tracked" else ""}" },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "$habitEmoji ${habit.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (habit.autoTracked && habit.current != null && habit.target != null) {
                    val unit = habit.unit ?: ""
                    val cur = habit.current.toInt()
                    val tgt = habit.target.toInt()
                    KaizenProgressBar(
                        progress = (cur.toFloat() / tgt.coerceAtLeast(1)).coerceIn(0f, 1f),
                        color = accentColor,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        if (habit.doneToday) "🤖 Auto: $cur/$tgt $unit" else "$cur/$tgt $unit - auto-tracks when you hit the goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (habit.doneToday) NutritionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (habit.streakDays > 0) {
                StatusIndicator(text = "🔥 ${habit.streakDays}d", status = Status.Caution)
            }
            androidx.compose.material3.Icon(
                Icons.Filled.Close,
                contentDescription = "Delete ${habit.title}",
                tint = KaizenCoral,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onDelete() }
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}
