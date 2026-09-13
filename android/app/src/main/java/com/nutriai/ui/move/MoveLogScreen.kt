package com.nutriai.ui.move

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.ExerciseLogDto
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

data class MoveLogState(
    val loading: Boolean = true,
    val todayExercise: List<ExerciseLogDto> = emptyList(),
)

/** One row in the log list - either a single entry or several sets of the same exercise grouped together. */
data class LoggedGroup(
    val key: String,
    val exerciseName: String,
    val ids: List<String>,
    val setCount: Int,
    val reps: Int?,
    val weightKg: Double?,
    val durationMin: Int?,
    val totalKcal: Int,
)

/**
 * Groups same-session sets of the same exercise into one row (e.g. 3 sets of Incline barbell
 * press logged together used to render as 3 near-identical rows - confusing, looked like the
 * save happened 3 times when it was one save of a 3-set exercise). Falls back to one row per
 * entry when there's no sessionId to group by (older logs, or genuinely separate sessions).
 */
private fun groupLogs(entries: List<ExerciseLogDto>): List<LoggedGroup> {
    val groups = LinkedHashMap<String, MutableList<ExerciseLogDto>>()
    for (e in entries) {
        val key = if (e.sessionId != null) "${e.exerciseName}::${e.sessionId}" else "solo::${e.id}"
        groups.getOrPut(key) { mutableListOf() }.add(e)
    }
    return groups.map { (key, rows) ->
        val first = rows.first()
        LoggedGroup(
            key = key,
            exerciseName = first.exerciseName,
            ids = rows.map { it.id },
            setCount = rows.sumOf { it.sets ?: 1 },
            reps = first.reps,
            weightKg = first.weightKg,
            durationMin = rows.sumOf { it.durationMin ?: 0 }.takeIf { it > 0 },
            totalKcal = rows.sumOf { it.kcal ?: 0 },
        )
    }
}

@HiltViewModel
class MoveLogViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(MoveLogState())
    val state: StateFlow<MoveLogState> = _state.asStateFlow()
    init { refresh() }
    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val logs = repository.exerciseLogs(null).getOrDefault(emptyList())
            _state.value = MoveLogState(loading = false, todayExercise = logs)
        }
    }

    /** Deletes every row in a grouped entry (e.g. all 3 sets of a mis-logged exercise at once). */
    fun deleteGroup(group: LoggedGroup) {
        viewModelScope.launch {
            group.ids.forEach { id -> repository.deleteExerciseLog(id) }
            refresh()
        }
    }
}

@Composable
fun MoveLogScreen(modifier: Modifier = Modifier, viewModel: MoveLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val burnedToday = state.todayExercise.sumOf { it.kcal ?: 0 }
    val groups = remember(state.todayExercise) { groupLogs(state.todayExercise) }
    var pendingDelete by remember { mutableStateOf<LoggedGroup?>(null) }

    pendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this entry?") },
            text = { Text("Removes ${group.exerciseName} (${group.setCount} set${if (group.setCount == 1) "" else "s"}) from today's log. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group)
                    pendingDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item { Text("📋 Training Log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MovementColor) } }
        }

        if (!state.loading && state.todayExercise.isNotEmpty()) {
            // Summary card
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💪", fontSize = 20.sp)
                            Text("${state.todayExercise.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MovementColor)
                            Text("sets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 20.sp)
                            Text("~$burnedToday", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = KaizenCoral)
                            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Log entries in one card
            item { Text("🏋️ Today's Sets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        groups.forEachIndexed { i, g ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(g.exerciseName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        buildString {
                                            append("${g.setCount} set${if (g.setCount == 1) "" else "s"}")
                                            if (g.reps != null) append(" × ${g.reps}")
                                            if (g.weightKg != null) append(" @ ${g.weightKg} kg")
                                            if (g.durationMin != null) append(" · ${g.durationMin} min")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Box(
                                        Modifier.clip(Sharp).background(KaizenCoral).padding(horizontal = Spacing.sm, vertical = 3.dp),
                                    ) {
                                        Text("${g.totalKcal} kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                                    }
                                    Text(
                                        "✕",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { pendingDelete = g }
                                            .padding(6.dp)
                                            .semantics { contentDescription = "Remove ${g.exerciseName} from today's log" },
                                    )
                                }
                            }
                            if (i != groups.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }

        if (!state.loading && state.todayExercise.isEmpty()) {
            item { EmptyState(title = "No sets logged yet today", emoji = "🏃") }
            item {
                Text(
                    "Go to Today tab to start your workout! 💪",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Text(
                "⚠️ Calories burned are educational estimates, not measurements.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}
