package com.nutriai.ui.wellness

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.RecoveryColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

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

private val sectionEmojis = listOf("🧘" to "Meditate", "🧘‍♀️" to "Yoga", "😌" to "Mood")
private val sectionColors = listOf(RecoveryColor, BrandGreen, BrandAmber)

@Composable
fun WellnessScreen(modifier: Modifier = Modifier, viewModel: WellnessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf<Meditation?>(null) }

    active?.let { med ->
        MeditationSession(med, onClose = { viewModel.logSession(med.id); active = null })
        return
    }

    val w = state.wellness
    var section by remember { mutableIntStateOf(0) }
    val labels = listOf("Meditate", "Yoga", "Mood")

    Column(modifier.fillMaxSize()) {
        // Section tabs
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            labels.forEachIndexed { i, label ->
                val (emoji, _) = sectionEmojis[i]
                FilterChip(
                    selected = section == i,
                    onClick = { section = i },
                    label = { Text("$emoji $label") },
                    shape = Sharp,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = sectionColors[i],
                        selectedLabelColor = Color.White,
                    ),
                    modifier = Modifier.semantics { contentDescription = "$label section" },
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(vertical = Spacing.md),
        ) {
            state.toast?.let { msg ->
                item {
                    com.nutriai.ui.components.StatusIndicator(text = msg, status = com.nutriai.ui.components.Status.Positive)
                    LaunchedEffect(msg) { delay(2500); viewModel.clearToast() }
                }
            }

            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandGreen) } }
            }

            // "For right now" suggestion
            state.suggestion?.let { s ->
                val show = (section == 0 && s.meditation != null) || (section == 1 && s.yoga != null)
                if (show) {
                    item {
                        Card(
                            shape = Sharp,
                            elevation = CardDefaults.cardElevation(2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text("✨ ${s.reason.ifBlank { "For right now" }}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                val picks = listOfNotNull(s.yoga?.name.takeIf { section == 1 }, s.meditation?.name.takeIf { section == 0 }).joinToString("  ·  ")
                                if (picks.isNotBlank()) {
                                    Text(
                                        picks,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sectionColors[section],
                                    )
                                }
                                if (section == 0) {
                                    s.meditation?.let { med ->
                                        Spacer(Modifier.height(Spacing.sm))
                                        TextButton(
                                            onClick = { active = med },
                                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Start ${med.name}" },
                                        ) { Text("▶ Start ${med.name}", color = sectionColors[0], fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when (section) {
                0 -> {
                    item {
                        Text("🧘 Meditation Sessions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (w != null && w.meditation.isNotEmpty()) {
                        items(w.meditation, key = { it.id }) { MeditationCard(it, onOpen = { active = it }) }
                    } else if (!state.loading) {
                        item {
                            EmptyState(
                                title = "No meditation sessions",
                                emoji = "🧘",
                                message = "Check back soon for guided sessions.",
                            )
                        }
                    }
                }
                1 -> {
                    item {
                        Text("🧘‍♀️ Yoga Flows", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (w != null && w.yoga.isNotEmpty()) {
                        items(w.yoga, key = { it.id }) { YogaFlowCard(it, onDone = { viewModel.logSession(it.id) }) }
                    } else if (!state.loading) {
                        item {
                            EmptyState(
                                title = "No yoga flows",
                                emoji = "🧘‍♀️",
                                message = "Yoga flows will appear here soon.",
                            )
                        }
                    }
                }
                else -> {
                    item {
                        Text("😌 Mood Check-in", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    item { com.nutriai.ui.mind.MoodCheckinCard() }
                }
            }
        }
    }
}

@Composable
private fun MindItemCard(
    title: String,
    meta: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    expandedContent: (@Composable () -> Unit)? = null,
) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("$emoji $title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
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
        emoji = "🧘‍♀️",
        onClick = { expanded = !expanded },
        trailing = {
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleMedium,
                color = BrandGreen,
            )
        },
        expandedContent = if (expanded) {
            {
                Spacer(Modifier.height(Spacing.sm))
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text("#", Modifier.size(width = 22.dp, height = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Pose", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Hold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                flow.poses.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", Modifier.size(width = 18.dp, height = 20.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
                        YogaPoseThumb(p.name, p.cue, Modifier.padding(end = 8.dp))
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
                    ) { Text("✓ Mark done", color = BrandGreen, fontWeight = FontWeight.Bold) }
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
        emoji = "🧘",
        onClick = onOpen,
        trailing = {
            Text("▶️", style = MaterialTheme.typography.titleMedium)
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

    var tonesOn by remember { mutableStateOf(true) }
    val tonePlayer = remember { com.nutriai.util.BreathTonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }
    LaunchedEffect(phaseLabel, tonesOn) {
        if (tonesOn && pattern != null && phaseLabel != "Get ready") {
            val ms = (phaseSec * 1000).coerceIn(600, 2200)
            when (phaseLabel) {
                "Breathe in" -> tonePlayer.playGlide(196.0, 392.0, ms)
                "Breathe out" -> tonePlayer.playGlide(392.0, 196.0, ms)
                "Hold" -> tonePlayer.playGlide(294.0, 294.0, 600, volume = 0.22f)
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
        Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text("🧘 ${med.name}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(med.goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (pattern != null) {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(
                        if (tonesOn) "🔔 Chime on" else "🔕 Chime off",
                        style = MaterialTheme.typography.labelLarge,
                        color = RecoveryColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { tonesOn = !tonesOn },
                    )
                    Text(
                        if (soundOn) "🔊 Voice on" else "🔇 Voice off",
                        style = MaterialTheme.typography.labelLarge,
                        color = RecoveryColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { soundOn = !soundOn },
                    )
                }
            }
        }

        if (pattern != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(200.dp).scale(animScale).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(RecoveryColor, KaizenLavender)))
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

        Card(
            shape = Sharp,
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("📋 Steps", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                med.steps.forEachIndexed { i, step ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RecoveryColor)
                        Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            "Find a comfortable position. There's no wrong way to do this. 😌",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("✅ Done") }
    }
}

@Composable
private fun YogaPoseThumb(name: String, cue: String, modifier: Modifier = Modifier) {
    val url = remember(name) { YogaPoseMap.imageUrl(name) }
    var failed by remember(name) { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }

    Box(
        modifier
            .size(44.dp)
            .clip(Sharp)
            .background(BrandGreen.copy(alpha = 0.08f))
            .clickable(enabled = url != null && !failed) { preview = true }
            .semantics { contentDescription = "$name pose, tap to preview" },
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Text("🧘", style = MaterialTheme.typography.titleMedium)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = "$name pose",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                onState = { state -> if (state is AsyncImagePainter.State.Error) failed = true },
            )
        }
    }

    if (preview && url != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { preview = false },
            confirmButton = { TextButton(onClick = { preview = false }) { Text("Close") } },
            title = { Text(name, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 240.dp).clip(Sharp).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                            contentDescription = "$name pose",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                    if (cue.isNotBlank()) {
                        Text(cue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
}
