package com.nutriai.ui.move

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
}

@Composable
fun MoveLogScreen(modifier: Modifier = Modifier, viewModel: MoveLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val burnedToday = state.todayExercise.sumOf { it.kcal ?: 0 }

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
                        state.todayExercise.forEachIndexed { i, e ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.exerciseName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        buildString {
                                            if (e.sets != null) append("${e.sets} sets")
                                            if (e.reps != null) append(if (isEmpty()) "${e.reps} reps" else " × ${e.reps}")
                                            if (e.weightKg != null) append(" @ ${e.weightKg} kg")
                                            if (e.durationMin != null) append("${e.durationMin} min")
                                        }.ifBlank { "session" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    )
                                }
                                Box(
                                    Modifier.clip(Sharp).background(KaizenCoral).padding(horizontal = Spacing.sm, vertical = 3.dp),
                                ) {
                                    Text("${e.kcal ?: 0} kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                                }
                            }
                            if (i != state.todayExercise.lastIndex) {
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
