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
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
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

    Column(modifier.fillMaxSize().background(MaterialTheme.kaizenColors.pageBackground)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(vertical = Spacing.md),
        ) {
            item { SectionHeader(title = "Training Log", emoji = "📋") }

            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MovementColor) } }
            }

            if (!state.loading && state.todayExercise.isNotEmpty()) {
                item {
                    FeatureCard(emoji = "🔥", title = "Today's Summary", accentColor = KaizenCoral) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💪", fontSize = 24.sp)
                                Text("${state.todayExercise.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MovementColor)
                                Text("sets logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔥", fontSize = 24.sp)
                                Text("~$burnedToday", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = KaizenCoral)
                                Text("kcal burned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { SectionHeader(title = "Today's Sets", emoji = "🏋️") }
                items(state.todayExercise.size) { i ->
                    val e = state.todayExercise[i]
                    GlassCard {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(KaizenCoral)
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            ) {
                                Text("${e.kcal ?: 0} kcal 🔥", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            if (!state.loading && state.todayExercise.isEmpty()) {
                item {
                    EmptyState(title = "No sets logged yet today", emoji = "🏃")
                }
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
}
