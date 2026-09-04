package com.nutriai.ui.move

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.ExerciseLogDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { com.nutriai.ui.components.ScreenHeader("training log") }

            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }

            if (!state.loading && state.todayExercise.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().heightIn(max = 80.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${state.todayExercise.size} sets logged today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("~$burnedToday kcal 🔥", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                item { com.nutriai.ui.components.SectionLabel("today's sets") }
                items(state.todayExercise.size) { i ->
                    val e = state.todayExercise[i]
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("💪 ${e.exerciseName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                buildString {
                                    if (e.sets != null) append("${e.sets} sets")
                                    if (e.reps != null) append(if (isEmpty()) "${e.reps} reps" else " × ${e.reps}")
                                    if (e.weightKg != null) append(" @ ${e.weightKg} kg")
                                    if (e.durationMin != null) append("${e.durationMin} min")
                                }.ifBlank { "session" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${e.kcal ?: 0} kcal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (i < state.todayExercise.lastIndex) HorizontalDivider()
                }
            }

            if (!state.loading && state.todayExercise.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🏋️", style = MaterialTheme.typography.displayMedium)
                        Text("No sets logged yet today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Go to Today tab to start your workout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            item {
                Text(
                    "Calories burned are educational estimates, not measurements.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
