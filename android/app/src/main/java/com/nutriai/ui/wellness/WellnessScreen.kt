package com.nutriai.ui.wellness

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Meditation
import com.nutriai.data.remote.dto.Wellness
import com.nutriai.data.remote.dto.YogaFlow
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class WellnessUiState(
    val loading: Boolean = true,
    val wellness: Wellness? = null,
    val suggestion: com.nutriai.data.remote.dto.NowSuggestion? = null,
    val toast: String? = null,
)

@HiltViewModel
class WellnessViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WellnessUiState())
    val state: StateFlow<WellnessUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val w = repository.wellness().getOrNull()
            _state.value = _state.value.copy(loading = false, wellness = w)
        }
        viewModelScope.launch {
            repository.wellnessSuggest().getOrNull()?.let { s ->
                _state.value = _state.value.copy(suggestion = s)
            }
        }
    }

    /** Records a completed yoga/meditation/breathing session (calories computed server-side). */
    fun logSession(refId: String) {
        viewModelScope.launch {
            val r = repository.logWellnessSession(refId)
            _state.value = if (r.isSuccess) {
                val s = r.getOrNull()
                _state.value.copy(toast = "Logged ${s?.refName ?: "session"} · ~${s?.kcal ?: 0} kcal 🔥")
            } else {
                _state.value.copy(toast = "Couldn't log - try again")
            }
        }
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
}

@Composable
fun WellnessScreen(modifier: Modifier = Modifier, viewModel: WellnessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf<Meditation?>(null) }

    active?.let { med ->
        // Finishing the session (the only exit is "Done") logs it, connecting it to calories.
        MeditationSession(med, onClose = { viewModel.logSession(med.id); active = null })
        return
    }

    val w = state.wellness
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Hero() }

        // Mind pillar - daily mood / stress / sleep check-in with a supportive insight.
        item { com.nutriai.ui.mind.MoodCheckinCard() }

        state.toast?.let { msg ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(msg, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                }
                LaunchedEffect(msg) { delay(2500); viewModel.clearToast() }
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandGreen) } }
        }

        state.suggestion?.let { s ->
            if (s.meditation != null || s.yoga != null) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(s.reason.ifBlank { "For right now" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            val picks = listOfNotNull(s.yoga?.name, s.meditation?.name).joinToString("  ·  ")
                            if (picks.isNotBlank()) {
                                Text(picks, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f))
                            }
                            s.meditation?.let { med ->
                                TextButton(
                                    onClick = { active = med },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Start ${med.name}" },
                                ) { Text("▶ Start ${med.name}") }
                            }
                        }
                    }
                }
            }
        }

        if (w != null && w.yoga.isNotEmpty()) {
            item { SectionLabel("🧘 Yoga flows") }
            items(w.yoga, key = { it.id }) { YogaFlowCard(it, onDone = { viewModel.logSession(it.id) }) }
        }
        if (w != null && w.meditation.isNotEmpty()) {
            item { SectionLabel("🌬️ Meditation & breathing") }
            items(w.meditation, key = { it.id }) { MeditationCard(it, onOpen = { active = it }) }
        }
    }
}

@Composable
private fun Hero() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(BrandGreenLight, BrandGreen, BrandGreenDeep))).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mind & Body", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Yoga flows and guided breathing to calm, focus and recover.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

// One shared item style for every content row on the Mind page (yoga flows and
// meditation/breathing). Same 16dp card, padding, title/meta typography and a single
// trailing action slot, so a yoga flow and a meditation read as siblings.
@Composable
private fun MindItemCard(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    expandedContent: (@Composable () -> Unit)? = null,
) {
    val shell = Modifier.fillMaxWidth().then(modifier)
    Card(
        modifier = if (onClick != null) shell.clickable { onClick() } else shell,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing()
            }
            expandedContent?.invoke()
        }
    }
}

@Composable
private fun YogaFlowCard(flow: YogaFlow, onDone: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    MindItemCard(
        title = flow.name,
        meta = "${flow.focus}  ·  ${flow.durationMin} min  ·  ${flow.level}",
        onClick = { expanded = !expanded },
        trailing = {
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        expandedContent = if (expanded) {
            {
                // Aligned table: #  |  Pose + cue  |  Hold
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text("#", Modifier.size(width = 22.dp, height = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Pose", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Hold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                flow.poses.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", Modifier.size(width = 22.dp, height = 20.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = BrandGreenDeep)
                            Text(p.cue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(p.hold, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Mark ${flow.name} done" },
                    ) { Text("✓ Mark done") }
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun MeditationCard(med: Meditation, onOpen: () -> Unit) {
    MindItemCard(
        title = med.name,
        meta = "${med.goal}  ·  ${med.durationMin} min",
        onClick = onOpen,
        trailing = {
            Text("▶", color = BrandGreen, style = MaterialTheme.typography.titleLarge)
        },
    )
}

@Composable
fun MeditationSession(med: Meditation, onClose: () -> Unit) {
    val pattern = med.pattern
    var phaseLabel by remember { mutableStateOf("Get ready") }
    var count by remember { mutableIntStateOf(0) }
    var scaleTarget by remember { mutableFloatStateOf(0.5f) }
    var phaseSec by remember { mutableIntStateOf(1) }

    // Spoken breathing cues so you can follow with your eyes closed.
    val context = LocalContext.current
    var soundOn by remember { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { TextToSpeech(context) { s -> ttsReady = s == TextToSpeech.SUCCESS } }
    DisposableEffect(Unit) { onDispose { runCatching { tts.stop(); tts.shutdown() } } }
    LaunchedEffect(ttsReady) {
        if (ttsReady) runCatching { tts.language = Locale.ENGLISH; tts.setSpeechRate(0.85f) }
    }
    LaunchedEffect(phaseLabel, soundOn, ttsReady) {
        if (soundOn && ttsReady && pattern != null && phaseLabel != "Get ready") {
            runCatching { tts.speak(phaseLabel, TextToSpeech.QUEUE_FLUSH, null, phaseLabel) }
        } else if (!soundOn) {
            runCatching { tts.stop() }
        }
    }

    // Soft breathing chime - a gentle tone that rises on inhale, holds steady, falls on exhale.
    // Generated on the fly (offline, no audio files). Independent of the voice cues.
    var tonesOn by remember { mutableStateOf(true) }
    val tonePlayer = remember { com.nutriai.util.BreathTonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }
    LaunchedEffect(phaseLabel, tonesOn) {
        if (tonesOn && pattern != null && phaseLabel != "Get ready") {
            val ms = (phaseSec * 1000).coerceIn(600, 2200)
            when (phaseLabel) {
                "Breathe in" -> tonePlayer.playGlide(196.0, 392.0, ms) // rising
                "Breathe out" -> tonePlayer.playGlide(392.0, 196.0, ms) // falling
                "Hold" -> tonePlayer.playGlide(294.0, 294.0, 600, volume = 0.22f) // soft steady
            }
        } else if (!tonesOn) {
            tonePlayer.stop()
        }
    }

    if (pattern != null) {
        LaunchedEffect(med.id) {
            val steps = buildList {
                if (pattern.inhaleSec > 0) add(Triple("Breathe in", pattern.inhaleSec, 1f))
                if (pattern.hold1Sec > 0) add(Triple("Hold", pattern.hold1Sec, 1f))
                if (pattern.exhaleSec > 0) add(Triple("Breathe out", pattern.exhaleSec, 0.5f))
                if (pattern.hold2Sec > 0) add(Triple("Hold", pattern.hold2Sec, 0.5f))
            }
            if (steps.isNotEmpty()) {
                while (true) {
                    for ((label, secs, target) in steps) {
                        phaseLabel = label
                        phaseSec = secs
                        scaleTarget = target
                        for (s in secs downTo 1) { count = s; delay(1000) }
                    }
                }
            }
        }
    }

    val animScale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = tween(durationMillis = phaseSec * 1000, easing = LinearEasing),
        label = "breath",
    )

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(med.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = BrandGreenDeep)
        Text(med.goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (pattern != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (tonesOn) "🔔 Chime on" else "🔕 Chime off",
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandGreen,
                    modifier = Modifier.clickable { tonesOn = !tonesOn },
                )
                Text(
                    if (soundOn) "🔊 Voice on" else "🔇 Voice off",
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandGreen,
                    modifier = Modifier.clickable { soundOn = !soundOn },
                )
            }
        }

        if (pattern != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(200.dp).scale(animScale).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(BrandGreen, BrandGreenDeep)))
                        .semantics { contentDescription = "Breathing guide: $phaseLabel" },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(phaseLabel, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        if (count > 0) Text("$count", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                med.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text(
            "Find a comfortable position. There's no wrong way to do this.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
