package com.nutriai.ui.mind

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.MoodInsightDto
import com.nutriai.data.remote.dto.MoodPoint
import com.nutriai.ui.safety.RedFlagDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Mood / stress / sleep check-in (Mind pillar). A gentle 1–5 self-report with an
// optional note; the server returns a supportive insight + trend. Free text is run
// through the red-flag safety net before saving.
// ---------------------------------------------------------------------------

private const val DEFAULT_DISCLAIMER =
    "This is a gentle self-check, not a diagnosis. If you're struggling, reach out to someone you trust or a professional."

data class MoodUiState(
    val loading: Boolean = true,
    val series: List<MoodPoint> = emptyList(),
    val insight: MoodInsightDto = MoodInsightDto(),
    val submitting: Boolean = false,
    val toast: String? = null,
    val redFlagMessage: String? = null,
)

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MoodUiState())
    val state: StateFlow<MoodUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val r = repository.moodCheckins()
            _state.value = if (r.isSuccess) {
                val res = r.getOrThrow()
                _state.value.copy(loading = false, series = res.series, insight = res.insight)
            } else {
                _state.value.copy(loading = false)
            }
        }
    }

    fun submit(mood: Int?, stress: Int?, sleepQuality: Int?, notes: String?) {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            // Red-flag safety net on any free-text note — surfaced alongside, never blocking the save.
            val note = notes?.trim()?.ifBlank { null }
            if (note != null) {
                repository.checkRedFlags(note).getOrNull()?.let { rf ->
                    if (rf.urgent) _state.value = _state.value.copy(redFlagMessage = rf.message)
                }
            }
            val r = repository.saveMoodCheckin(mood = mood, stress = stress, sleepQuality = sleepQuality, notes = note)
            _state.value = if (r.isSuccess) {
                _state.value.copy(submitting = false, toast = "Saved — thanks for checking in")
            } else {
                _state.value.copy(submitting = false, toast = "Couldn't save — try again")
            }
            if (r.isSuccess) load()
        }
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
    fun clearRedFlag() { _state.value = _state.value.copy(redFlagMessage = null) }
}

private val MOOD_FACES = listOf("😞", "🙁", "😐", "🙂", "😄")
private val STRESS_FACES = listOf("😌", "🙂", "😐", "😟", "😣") // 1 calm → 5 tense
private val SLEEP_FACES = listOf("😫", "😪", "😐", "🙂", "😴") // 1 poor → 5 great

@Composable
fun MoodCheckinCard(modifier: Modifier = Modifier, viewModel: MoodViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var mood by remember { mutableStateOf<Int?>(null) }
    var stress by remember { mutableStateOf<Int?>(null) }
    var sleep by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    state.redFlagMessage?.let { msg ->
        RedFlagDialog(message = msg, onDismiss = { viewModel.clearRedFlag() })
    }

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("How are you today? 🧠", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            FaceSelector("Mood", MOOD_FACES, mood) { mood = it }
            FaceSelector("Stress", STRESS_FACES, stress) { stress = it }
            FaceSelector("Sleep", SLEEP_FACES, sleep) { sleep = it }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("Anything on your mind? (optional)") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Optional note" },
            )

            TextButton(
                onClick = {
                    viewModel.submit(mood, stress, sleep, note)
                    note = ""
                },
                enabled = (mood != null || stress != null || sleep != null) && !state.submitting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Save today's check-in" },
            ) { Text(if (state.submitting) "Saving…" else "Save check-in") }

            state.toast?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                androidx.compose.runtime.LaunchedEffect(it) { kotlinx.coroutines.delay(2500); viewModel.clearToast() }
            }

            // Supportive insight message.
            if (state.insight.message.isNotBlank()) {
                Text(state.insight.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Calm, supportive escalation when the server suggests professional support.
            if (state.insight.professionalNudge) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💛", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.insight.message.ifBlank { "It might help to talk with someone. Reaching out is a sign of strength." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // A small emoji trend of recent moods (oldest → newest).
            val recentMoods = state.series.mapNotNull { it.mood }.takeLast(10)
            if (recentMoods.size >= 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    recentMoods.forEach { m ->
                        Text(MOOD_FACES.getOrElse(m - 1) { "•" }, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Text(
                state.insight.disclaimer.ifBlank { DEFAULT_DISCLAIMER },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FaceSelector(label: String, faces: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            faces.forEachIndexed { i, face ->
                val value = i + 1
                val isSel = selected == value
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSelect(value) }
                        .semantics { contentDescription = "$label level $value of 5" },
                    contentAlignment = Alignment.Center,
                ) { Text(face, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
