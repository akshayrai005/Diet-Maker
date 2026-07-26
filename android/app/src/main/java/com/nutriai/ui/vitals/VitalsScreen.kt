package com.nutriai.ui.vitals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.VitalClassification
import com.nutriai.data.remote.dto.VitalPoint
import com.nutriai.data.remote.dto.VitalSeries
import com.nutriai.data.remote.dto.VitalSummaryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Vitals & Labs - track blood pressure, resting HR, weight, waist and 9 blood
// labs; each reading gets an educational band + guideline citation from the
// server. Not medical advice. Mirrors the cycle/discipline/wellness features.
// ---------------------------------------------------------------------------

private const val DEFAULT_DISCLAIMER =
    "Educational, not medical advice. Confirm any result with your doctor."

/** Static catalog so the overview can list all 13 metrics even before any reading exists. */
data class VitalMeta(val type: String, val label: String, val unit: String, val isBp: Boolean = false)

private val BODY_VITALS = listOf(
    VitalMeta("bloodPressure", "Blood pressure", "mmHg", isBp = true),
    VitalMeta("restingHr", "Resting heart rate", "bpm"),
    VitalMeta("weight", "Weight", "kg"),
    VitalMeta("waist", "Waist", "cm"),
)

private val LAB_VITALS = listOf(
    VitalMeta("fastingGlucose", "Fasting glucose", "mg/dL"),
    VitalMeta("hba1c", "HbA1c", "%"),
    VitalMeta("ldl", "LDL cholesterol", "mg/dL"),
    VitalMeta("hdl", "HDL cholesterol", "mg/dL"),
    VitalMeta("triglycerides", "Triglycerides", "mg/dL"),
    VitalMeta("tsh", "TSH", "mIU/L"),
    VitalMeta("vitaminD", "Vitamin D", "ng/mL"),
    VitalMeta("ferritin", "Ferritin", "ng/mL"),
    VitalMeta("hemoglobin", "Hemoglobin", "g/dL"),
)

private val ALL_VITALS = BODY_VITALS + LAB_VITALS

private fun metaFor(type: String): VitalMeta =
    ALL_VITALS.firstOrNull { it.type == type } ?: VitalMeta(type, type, "")

/** Trims a trailing .0 so 120.0 reads as "120" but 5.4 stays "5.4". */
private fun formatNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((Math.round(v * 10.0)) / 10.0).toString()

private fun displayValue(type: String, point: VitalPoint): String =
    if (type == "bloodPressure") {
        "${point.systolic ?: "-"}/${point.diastolic ?: "-"}"
    } else {
        point.value?.let { formatNum(it) } ?: "-"
    }

/** Numeric series for the trend chart. BP charts systolic; every other metric charts value. */
private fun numericPoints(series: VitalSeries): List<Double> =
    series.points.mapNotNull { p ->
        if (series.type == "bloodPressure") p.systolic?.toDouble() else p.value
    }

/**
 * Severity → colour. Routed through MaterialTheme.colorScheme where a slot exists; amber/orange
 * have no theme slot so they are named constants. The chip never relies on colour alone - the
 * band text is always shown alongside.
 */
@Composable
private fun severityColor(severity: String): Color = when (severity) {
    "normal" -> MaterialTheme.colorScheme.primary
    "watch" -> Color(0xFFF2B705) // amber
    "elevated" -> Color(0xFFEF8A17) // orange
    "high" -> MaterialTheme.colorScheme.error
    "urgent" -> Color(0xFFB3261E) // strong red
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ---------------------------------------------------------------------------
// State + ViewModel
// ---------------------------------------------------------------------------

data class VitalsUiState(
    val loading: Boolean = true,
    val items: List<VitalSummaryItem> = emptyList(),
    val disclaimer: String = DEFAULT_DISCLAIMER,
    val error: String? = null,
    val selectedType: String? = null,
    val seriesLoading: Boolean = false,
    val series: VitalSeries? = null,
    val submitting: Boolean = false,
    val savedClassification: VitalClassification? = null,
    val toast: String? = null,
)

@HiltViewModel
class VitalsViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VitalsUiState())
    val state: StateFlow<VitalsUiState> = _state.asStateFlow()

    init { loadSummary() }

    fun loadSummary() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.vitalsSummary()
            _state.value = if (r.isSuccess) {
                val s = r.getOrThrow()
                _state.value.copy(
                    loading = false,
                    items = s.items,
                    disclaimer = s.disclaimer.ifBlank { DEFAULT_DISCLAIMER },
                    error = null,
                )
            } else {
                _state.value.copy(loading = false, error = "Couldn't load your vitals.")
            }
        }
    }

    fun openMetric(type: String) {
        _state.value = _state.value.copy(selectedType = type, series = null, savedClassification = null)
        loadSeries(type)
    }

    fun closeMetric() {
        _state.value = _state.value.copy(selectedType = null, series = null, savedClassification = null)
    }

    private fun loadSeries(type: String) {
        _state.value = _state.value.copy(seriesLoading = true)
        viewModelScope.launch {
            val r = repository.vitalSeries(type)
            _state.value = _state.value.copy(seriesLoading = false, series = r.getOrNull())
        }
    }

    fun submitReading(
        type: String,
        value: Double?,
        systolic: Int?,
        diastolic: Int?,
        note: String?,
        measuredAt: String?,
    ) {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            val r = repository.logVital(
                type = type,
                value = value,
                systolic = systolic,
                diastolic = diastolic,
                note = note?.ifBlank { null },
                measuredAt = measuredAt?.ifBlank { null },
            )
            if (r.isSuccess) {
                _state.value = _state.value.copy(
                    submitting = false,
                    savedClassification = r.getOrNull()?.classification,
                    toast = "Reading saved",
                )
                loadSeries(type)
                loadSummary()
            } else {
                _state.value = _state.value.copy(submitting = false, toast = "Couldn't save - try again")
            }
        }
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun VitalsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: VitalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // While a metric detail is open, system back returns to the overview list.
    BackHandler(enabled = state.selectedType != null) { viewModel.closeMetric() }

    val selected = state.selectedType
    if (selected != null) {
        VitalDetail(
            modifier = modifier,
            meta = metaFor(selected),
            state = state,
            onBack = { viewModel.closeMetric() },
            onSubmit = { v, s, d, note, at -> viewModel.submitReading(selected, v, s, d, note, at) },
            onClearToast = { viewModel.clearToast() },
        )
        return
    }

    VitalsOverview(
        modifier = modifier,
        state = state,
        onOpen = { viewModel.openMetric(it) },
        onBack = onBack,
    )
}

@Composable
private fun VitalsOverview(
    modifier: Modifier,
    state: VitalsUiState,
    onOpen: (String) -> Unit,
    onBack: (() -> Unit)?,
) {
    val byType = state.items.associateBy { it.type }
    val hasAny = state.items.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        if (onBack != null) {
            item {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Back" },
                ) { Text("← Back") }
            }
        }

        item {
            Text(
                "Vitals & labs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        state.error?.let { err ->
            item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        if (!state.loading && state.error == null && !hasAny) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Start tracking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Add your first reading to start tracking trends.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }

        item { SectionLabel("Body & vitals") }
        items(BODY_VITALS.size) { i ->
            val meta = BODY_VITALS[i]
            MetricRow(meta = meta, item = byType[meta.type], onClick = { onOpen(meta.type) })
        }

        item { SectionLabel("Blood labs") }
        items(LAB_VITALS.size) { i ->
            val meta = LAB_VITALS[i]
            MetricRow(meta = meta, item = byType[meta.type], onClick = { onOpen(meta.type) })
        }

        item {
            Text(
                state.disclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun MetricRow(meta: VitalMeta, item: VitalSummaryItem?, onClick: () -> Unit) {
    val hasData = item != null
    val valueText = item?.let { "${displayValue(meta.type, it.latest)} ${it.unit.ifBlank { meta.unit }}".trim() }
    val severity = item?.classification?.severity
    val band = item?.classification?.band

    val rowDesc = if (hasData) {
        "${meta.label}, $valueText${band?.let { ", $it" } ?: ""}"
    } else {
        "${meta.label}, no readings yet. Add first reading."
    }

    Card(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = rowDesc },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(meta.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (hasData) {
                    Text(
                        valueText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Add first reading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (band != null && severity != null) {
                SeverityChip(band = band, severity = severity)
            } else {
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Coloured band chip that always shows the band TEXT, so it never relies on colour alone. */
@Composable
private fun SeverityChip(band: String, severity: String) {
    val color = severityColor(severity)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "$band ($severity)" },
    ) {
        Text(band, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

@Composable
private fun VitalDetail(
    modifier: Modifier,
    meta: VitalMeta,
    state: VitalsUiState,
    onBack: () -> Unit,
    onSubmit: (value: Double?, systolic: Int?, diastolic: Int?, note: String?, measuredAt: String?) -> Unit,
    onClearToast: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    val series = state.series

    if (showAdd) {
        AddReadingDialog(
            meta = meta,
            submitting = state.submitting,
            onDismiss = { showAdd = false },
            onSubmit = { v, s, d, note, at ->
                onSubmit(v, s, d, note, at)
                showAdd = false
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Back to vitals list" },
            ) { Text("← Vitals & labs") }
        }

        item {
            Column {
                Text(meta.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Unit: ${series?.unit?.ifBlank { meta.unit } ?: meta.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Inline confirmation of the classification returned by the last save.
        state.savedClassification?.let { c ->
            item {
                state.toast?.let { LaunchedEffect(it) { delay(3000); onClearToast() } }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Saved", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (c.band.isNotBlank()) SeverityChip(band = c.band, severity = c.severity)
                    }
                }
            }
        }

        if (state.seriesLoading && series == null) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        if (series != null) {
            val chartPoints = numericPoints(series)

            // Educational latest reading: band + guideline citation + message.
            series.latest?.let { latest ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "${displayValue(series.type, latest.point)} ${series.unit}".trim(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                if (latest.band.isNotBlank()) SeverityChip(band = latest.band, severity = latest.severity)
                            }
                            if (latest.message.isNotBlank()) {
                                Text(latest.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f))
                            }
                            if (latest.guideline.isNotBlank()) {
                                Text(
                                    "Source: ${latest.guideline}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }
            }

            // Trend summary + chart.
            if (series.trend.summary.isNotBlank()) {
                item { Text(series.trend.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (chartPoints.size >= 2) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            val lineColor = MaterialTheme.colorScheme.primary
                            VitalLineChart(values = chartPoints, lineColor = lineColor)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${formatNum(chartPoints.min())} ${series.unit}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatNum(chartPoints.max())} ${series.unit}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Add reading action.
            item {
                Card(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showAdd = true }
                        .semantics { contentDescription = "Add ${meta.label} reading" },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        "＋ Add reading",
                        Modifier.fillMaxWidth().padding(14.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Past readings (newest first - server returns oldest→newest for the chart).
            if (series.points.isNotEmpty()) {
                item { SectionLabel("Past readings") }
                val past = series.points.reversed()
                items(past.size) { i ->
                    val p = past[i]
                    PastReadingRow(type = series.type, unit = series.unit.ifBlank { meta.unit }, point = p)
                }
            } else {
                item {
                    Text(
                        "Add your first reading to start tracking trends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    series.disclaimer.ifBlank { state.disclaimer },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PastReadingRow(type: String, unit: String, point: VitalPoint) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${displayValue(type, point)} $unit".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                point.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                point.measuredAt.take(10),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Simple Compose Canvas line chart, mirroring the private weight-trend chart in CheckinScreen. */
@Composable
private fun VitalLineChart(values: List<Double>, lineColor: Color) {
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).let { if (it == 0.0) 1.0 else it }
    val fill = lineColor.copy(alpha = 0.15f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "Trend chart, ${values.size} readings" },
    ) {
        val n = values.size
        if (n < 2) return@Canvas
        val padX = 6.dp.toPx()
        val padY = 10.dp.toPx()
        val plotW = size.width - padX * 2
        val plotH = size.height - padY * 2
        val dx = plotW / (n - 1)
        fun px(i: Int) = padX + dx * i
        fun py(v: Double) = padY + (plotH * (1f - ((v - minV) / range).toFloat()))

        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = px(i)
            val y = py(v)
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
        values.forEachIndexed { i, v ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(px(i), py(v)))
        }
    }
}

// ---------------------------------------------------------------------------
// Add-reading dialog
// ---------------------------------------------------------------------------

@Composable
private fun AddReadingDialog(
    meta: VitalMeta,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (value: Double?, systolic: Int?, diastolic: Int?, note: String?, measuredAt: String?) -> Unit,
) {
    var valueText by remember { mutableStateOf("") }
    var systolicText by remember { mutableStateOf("") }
    var diastolicText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }

    val valid = if (meta.isBp) {
        systolicText.toIntOrNull() != null && diastolicText.toIntOrNull() != null
    } else {
        valueText.toDoubleOrNull() != null
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Add ${meta.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (meta.isBp) {
                    OutlinedTextField(
                        value = systolicText,
                        onValueChange = { systolicText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Systolic (mmHg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Systolic in mmHg" },
                    )
                    OutlinedTextField(
                        value = diastolicText,
                        onValueChange = { diastolicText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Diastolic (mmHg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Diastolic in mmHg" },
                    )
                } else {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { new -> valueText = new.filter { c -> c.isDigit() || c == '.' }.take(7) },
                        label = { Text("Value${if (meta.unit.isNotBlank()) " (${meta.unit})" else ""}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${meta.label} value in ${meta.unit}" },
                    )
                }
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Measurement date" },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Optional note" },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !submitting,
                onClick = {
                    if (meta.isBp) {
                        onSubmit(null, systolicText.toIntOrNull(), diastolicText.toIntOrNull(), note, dateText)
                    } else {
                        onSubmit(valueText.toDoubleOrNull(), null, null, note, dateText)
                    }
                },
            ) { Text(if (submitting) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Compact Home card - surfaces the 2-3 most important logged metrics.
// ---------------------------------------------------------------------------

private val HOME_PRIORITY = listOf("bloodPressure", "fastingGlucose", "hba1c", "restingHr")

@Composable
fun HomeVitalsCard(
    onOpenVitals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VitalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val byType = state.items.associateBy { it.type }
    val picks = HOME_PRIORITY.mapNotNull { byType[it] }.take(3)

    Card(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpenVitals)
            .semantics { contentDescription = "Vitals and labs. Open to view or add readings." },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vitals & labs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (picks.isEmpty()) {
                Text(
                    "Track your vitals - blood pressure, glucose, cholesterol and more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                picks.forEach { item ->
                    val meta = metaFor(item.type)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(meta.label, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${displayValue(item.type, item.latest)} ${item.unit.ifBlank { meta.unit }}".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            item.classification?.let { c ->
                                if (c.band.isNotBlank()) SeverityChip(band = c.band, severity = c.severity)
                            }
                        }
                    }
                }
            }
        }
    }
}
