package com.nutriai.ui.checkin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.CheckinDto
import com.nutriai.data.remote.dto.CheckinRequest
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.round

private val Sharp = RoundedCornerShape(8.dp)

data class CheckinState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val checkins: List<CheckinDto> = emptyList(),
    val message: String? = null,
    val redFlagMessage: String? = null,
)

@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckinState())
    val state: StateFlow<CheckinState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val r = repository.checkins()
            _state.value = _state.value.copy(
                loading = false,
                checkins = r.getOrDefault(emptyList()),
            )
        }
    }

    fun submit(
        weightKg: Double,
        waistCm: Double?,
        energy: Int?,
        sleepHours: Double?,
        mood: Int?,
        notes: String?,
    ) {
        _state.value = _state.value.copy(submitting = true, message = null)
        viewModelScope.launch {
            notes?.trim()?.ifBlank { null }?.let { note ->
                repository.checkRedFlags(note).getOrNull()?.let { rf ->
                    if (rf.urgent) _state.value = _state.value.copy(redFlagMessage = rf.message)
                }
            }
            val r = repository.createCheckin(
                CheckinRequest(
                    weightKg = weightKg,
                    waistCm = waistCm,
                    energy = energy,
                    sleepHours = sleepHours,
                    mood = mood,
                    notes = notes,
                ),
            )
            if (r.isSuccess) {
                _state.value = _state.value.copy(submitting = false, message = "Check-in saved")
                load()
            } else {
                _state.value = _state.value.copy(
                    submitting = false,
                    message = r.exceptionOrNull()?.message ?: "Could not save check-in",
                )
            }
        }
    }

    fun clearRedFlag() { _state.value = _state.value.copy(redFlagMessage = null) }
}

@Composable
fun CheckinScreen(
    modifier: Modifier = Modifier,
    viewModel: CheckinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    state.redFlagMessage?.let { msg ->
        com.nutriai.ui.safety.RedFlagDialog(message = msg, onDismiss = { viewModel.clearRedFlag() })
    }

    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var sleep by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var energy by remember { mutableStateOf<Int?>(null) }
    var mood by remember { mutableStateOf<Int?>(null) }
    var step by remember { mutableStateOf(0) }
    val lastStep = 5

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xl),
    ) {
        // 1. Header
        item { CheckinHeader() }

        // 2. Guided check-in
        item {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Text("📝 Log Your Week", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Step ${step + 1} of ${lastStep + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    KaizenProgressBar(
                        progress = (step + 1f) / (lastStep + 1),
                        color = BrandGreen,
                    )

                    when (step) {
                        0 -> BrandField(value = weight, onValueChange = { weight = it }, label = "⚖️ Weight (kg)", keyboardType = KeyboardType.Decimal)
                        1 -> BrandField(value = waist, onValueChange = { waist = it }, label = "📏 Waist (cm, optional)", keyboardType = KeyboardType.Decimal)
                        2 -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("⚡ Energy", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            RatingChips(selected = energy) { energy = it }
                        }
                        3 -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("😊 Mood", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            RatingChips(selected = mood) { mood = it }
                        }
                        4 -> BrandField(value = sleep, onValueChange = { sleep = it }, label = "😴 Sleep hours (optional)", keyboardType = KeyboardType.Decimal)
                        else -> BrandField(value = notes, onValueChange = { notes = it }, label = "📝 Notes (optional)", keyboardType = KeyboardType.Text)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        if (step > 0) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = Sharp,
                            ) { Text("← Back") }
                        }
                        if (step < lastStep) {
                            Button(
                                onClick = { step++ },
                                enabled = step != 0 || weight.isNotBlank(),
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = Sharp,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                            ) { Text("Next →") }
                        } else {
                            Button(
                                onClick = {
                                    val w = weight.toDoubleOrNull()
                                    if (w != null) {
                                        viewModel.submit(
                                            weightKg = w,
                                            waistCm = waist.toDoubleOrNull(),
                                            energy = energy,
                                            sleepHours = sleep.toDoubleOrNull(),
                                            mood = mood,
                                            notes = notes.trim().ifBlank { null },
                                        )
                                    }
                                },
                                enabled = weight.isNotBlank() && !state.submitting,
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = Sharp,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                            ) {
                                if (state.submitting) {
                                    CircularProgressIndicator(modifier = Modifier.height(22.dp), color = Color.White)
                                } else {
                                    Text("✅ Save Check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    state.message?.let {
                        Text(
                            it,
                            color = BrandGreenDeep,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // 3. Weight trend + history
        if (state.loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = BrandGreen) }
            }
        } else {
            item { WeightTrend(state.checkins) }

            if (state.checkins.isNotEmpty()) {
                item {
                    Text(
                        "🗓️ Past Check-ins",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            items(state.checkins) { c ->
                PastCheckinCard(c)
            }
        }
    }
}

@Composable
private fun CheckinHeader() {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("⚖️ Weekly Check-in", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                "Track your progress — small steps, steady wins. 🏆",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrandField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = keyboardType != KeyboardType.Text,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = Sharp,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandGreen,
            focusedLabelColor = BrandGreen,
            cursorColor = BrandGreen,
        ),
    )
}

@Composable
private fun RatingChips(selected: Int?, onSelect: (Int) -> Unit) {
    val emojis = listOf("😫", "😕", "😐", "😊", "🤩")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        (1..5).forEach { n ->
            val isSelected = n == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(Sharp)
                    .background(
                        if (isSelected) BrandGreen else BrandGreen.copy(alpha = 0.10f),
                    )
                    .clickableChip { onSelect(n) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emojis[n - 1], style = MaterialTheme.typography.titleMedium)
                    Text(
                        n.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else BrandGreenDeep,
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableChip(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun WeightTrend(checkins: List<CheckinDto>) {
    if (checkins.isEmpty()) return
    val points = checkins
        .mapNotNull { c -> c.measurements?.weightKg?.let { c.date to it } }
        .sortedBy { it.first }
    val weights = points.map { it.second }
    val latest = weights.lastOrNull()
    val first = weights.firstOrNull()
    val delta = if (weights.size >= 2 && latest != null && first != null) {
        round((latest - first) * 10.0) / 10.0
    } else {
        null
    }

    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("📈 Weight Trend", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            latest?.let {
                Text(
                    "Latest: $it kg",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (weights.size >= 2) {
                Spacer(Modifier.height(Spacing.sm))
                WeightChart(weights = weights, lineColor = KaizenLavender)
                Spacer(Modifier.height(Spacing.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${weights.min()} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${weights.max()} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (delta != null) {
                Spacer(Modifier.height(Spacing.sm))
                val down = delta < 0
                val flat = delta == 0.0
                val arrow = when {
                    flat -> "▬"
                    down -> "📉"
                    else -> "📈"
                }
                val accent = when {
                    flat -> MaterialTheme.colorScheme.onSurface
                    down -> BrandGreen
                    else -> BrandAmber
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        arrow,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Text(
                        " ${if (delta > 0) "+" else ""}$delta kg",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                Text(
                    "since your first check-in",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (checkins.size < 2) {
                Text(
                    "Log again next week to see your trend. 🔄",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeightChart(weights: List<Double>, lineColor: Color) {
    val minW = weights.min()
    val maxW = weights.max()
    val range = (maxW - minW).let { if (it == 0.0) 1.0 else it }
    val fill = lineColor.copy(alpha = 0.15f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 4.dp),
    ) {
        val n = weights.size
        if (n < 2) return@Canvas
        val padX = 6.dp.toPx()
        val padY = 10.dp.toPx()
        val plotW = size.width - padX * 2
        val plotH = size.height - padY * 2
        val dx = plotW / (n - 1)
        fun px(i: Int) = padX + dx * i
        fun py(w: Double) = padY + (plotH * (1f - ((w - minW) / range).toFloat()))

        val line = Path()
        val area = Path()
        weights.forEachIndexed { i, w ->
            val x = px(i)
            val y = py(w)
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
        weights.forEachIndexed { i, w ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(px(i), py(w)))
        }
    }
}

@Composable
private fun PastCheckinCard(c: CheckinDto) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🗓️ ${c.date}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    c.measurements?.weightKg?.let { "⚖️ $it kg" } ?: "-",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MetricPill("⚡ Energy", c.energy?.toString() ?: "-", KaizenCoral)
                MetricPill("😊 Mood", c.mood?.toString() ?: "-", KaizenBlue)
                c.measurements?.waistCm?.let { MetricPill("📏 Waist", "$it cm", KaizenLavender) }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, accentColor: Color = BrandGreen) {
    Box(
        modifier = Modifier
            .clip(Sharp)
            .background(accentColor.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
