package com.nutriai.ui.body

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.BodyMetricRequest
import com.nutriai.data.remote.dto.BodyPhotoDto
import com.nutriai.data.remote.dto.BodyPoint
import com.nutriai.data.remote.dto.BodySeries
import com.nutriai.data.remote.dto.Whr
import com.nutriai.util.ImageUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Progress & body — physique tracking. Log body measurements, watch per-metric
// trends, and keep a private progress-photo timeline whose images NEVER leave
// the phone (only a filename is recorded on the server). Body-neutral, encouraging
// copy — no "ideal" targets. Body-fat is hidden entirely for minors.
// ---------------------------------------------------------------------------

private const val DEFAULT_DISCLAIMER =
    "For your own tracking only — not a medical or fitness assessment, and never a measure of your worth."

private const val CONSENT_LINE =
    "Photos stay private on your phone and are never uploaded."

/** The measurements we let people log & chart. Body-fat is handled separately (minor-gated). */
data class BodyMetricMeta(val key: String, val label: String, val unit: String)

private val BODY_METRICS = listOf(
    BodyMetricMeta("weightKg", "Weight", "kg"),
    BodyMetricMeta("waistCm", "Waist", "cm"),
    BodyMetricMeta("hipCm", "Hip", "cm"),
    BodyMetricMeta("chestCm", "Chest", "cm"),
    BodyMetricMeta("armCm", "Arm", "cm"),
    BodyMetricMeta("thighCm", "Thigh", "cm"),
    BodyMetricMeta("neckCm", "Neck", "cm"),
)

/** Reads the value for a given metric key off a point (null if not measured that day). */
private fun BodyPoint.valueFor(key: String): Double? = when (key) {
    "weightKg" -> weightKg
    "waistCm" -> waistCm
    "hipCm" -> hipCm
    "chestCm" -> chestCm
    "armCm" -> armCm
    "thighCm" -> thighCm
    "neckCm" -> neckCm
    "bodyFatPct" -> bodyFatPct
    else -> null
}

private fun labelFor(key: String): String =
    BODY_METRICS.firstOrNull { it.key == key }?.label ?: if (key == "bodyFatPct") "Body fat" else key

private fun unitFor(key: String): String =
    BODY_METRICS.firstOrNull { it.key == key }?.unit ?: if (key == "bodyFatPct") "%" else ""

/** Trims a trailing .0 so 72.0 reads as "72" but 81.4 stays "81.4". */
private fun formatNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((Math.round(v * 10.0)) / 10.0).toString()

// ---------------------------------------------------------------------------
// State + ViewModel
// ---------------------------------------------------------------------------

data class ProgressUiState(
    val loading: Boolean = true,
    val series: BodySeries? = null,
    val error: String? = null,
    val submitting: Boolean = false,
    val savedPoint: BodyPoint? = null,
    val savedWhr: Whr? = null,
    val photos: List<BodyPhotoDto> = emptyList(),
    val photosLoading: Boolean = false,
    val consentGiven: Boolean = false,
    val savingPhoto: Boolean = false,
    val pendingPhotoRef: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("progress_prefs", Context.MODE_PRIVATE)

    init {
        _state.value = _state.value.copy(consentGiven = prefs.getBoolean("photo_consent", false))
        loadSeries()
        loadPhotos()
    }

    fun loadSeries() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.bodyMetrics(range = 365)
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, series = r.getOrThrow(), error = null)
            } else {
                _state.value.copy(loading = false, error = "Couldn't load your progress.")
            }
        }
    }

    private fun loadPhotos() {
        _state.value = _state.value.copy(photosLoading = true)
        viewModelScope.launch {
            val r = repository.bodyPhotos()
            _state.value = _state.value.copy(photosLoading = false, photos = r.getOrNull() ?: emptyList())
        }
    }

    fun saveMetric(req: BodyMetricRequest) {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            val r = repository.logBodyMetric(req)
            if (r.isSuccess) {
                val resp = r.getOrThrow()
                _state.value = _state.value.copy(
                    submitting = false,
                    savedPoint = resp.point,
                    savedWhr = resp.whr,
                    toast = "Measurements saved",
                )
                loadSeries()
            } else {
                _state.value = _state.value.copy(submitting = false, toast = "Couldn't save — try again")
            }
        }
    }

    fun giveConsent() {
        prefs.edit().putBoolean("photo_consent", true).apply()
        _state.value = _state.value.copy(consentGiven = true)
    }

    /** Copies a picked/captured image into private storage, then asks for an optional caption. */
    fun onImagePicked(uri: Uri) {
        _state.value = _state.value.copy(savingPhoto = true)
        viewModelScope.launch {
            val ref = withContext(Dispatchers.IO) { ImageUtil.saveProgressPhoto(context, uri) }
            _state.value = if (ref != null) {
                _state.value.copy(savingPhoto = false, pendingPhotoRef = ref)
            } else {
                _state.value.copy(savingPhoto = false, toast = "Couldn't save that photo")
            }
        }
    }

    fun confirmPhoto(caption: String?) {
        val ref = _state.value.pendingPhotoRef ?: return
        _state.value = _state.value.copy(pendingPhotoRef = null)
        viewModelScope.launch {
            val r = repository.addBodyPhoto(ref, caption?.ifBlank { null })
            if (r.isSuccess) {
                _state.value = _state.value.copy(toast = "Photo added")
                loadPhotos()
            } else {
                // Metadata failed — don't leave an orphan file on disk.
                withContext(Dispatchers.IO) { ImageUtil.deleteProgressPhoto(context, ref) }
                _state.value = _state.value.copy(toast = "Couldn't add photo — try again")
            }
        }
    }

    fun cancelPendingPhoto() {
        val ref = _state.value.pendingPhotoRef ?: return
        _state.value = _state.value.copy(pendingPhotoRef = null)
        viewModelScope.launch { withContext(Dispatchers.IO) { ImageUtil.deleteProgressPhoto(context, ref) } }
    }

    fun deletePhoto(photo: BodyPhotoDto) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ImageUtil.deleteProgressPhoto(context, photo.localRef) }
            val r = repository.deleteBodyPhoto(photo.id)
            _state.value = _state.value.copy(toast = if (r.isSuccess) "Photo removed" else "Removed on this device")
            loadPhotos()
        }
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun ProgressScreen(modifier: Modifier = Modifier, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val series = state.series
    val isMinor = series?.isMinor == true

    var showLog by remember { mutableStateOf(false) }
    var selectedMetric by remember { mutableStateOf("weightKg") }
    var compareMode by remember { mutableStateOf(false) }
    var compareSelection by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<BodyPhotoDto?>(null) }

    // Image pickers — gallery + camera. Captured/picked bytes are copied to private storage.
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) viewModel.onImagePicked(uri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, _) = ImageUtil.newCameraOutput(context, System.currentTimeMillis())
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }
    fun takePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val (uri, _) = ImageUtil.newCameraOutput(context, System.currentTimeMillis())
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showLog) {
        LogMeasurementsDialog(
            isMinor = isMinor,
            submitting = state.submitting,
            onDismiss = { showLog = false },
            onSave = { req ->
                viewModel.saveMetric(req)
                showLog = false
            },
        )
    }

    state.pendingPhotoRef?.let {
        CaptionDialog(
            onDismiss = { viewModel.cancelPendingPhoto() },
            onConfirm = { caption -> viewModel.confirmPhoto(caption) },
        )
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this photo?") },
            text = { Text("It's deleted from this phone and from your timeline. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhoto(photo)
                    compareSelection = compareSelection - photo.localRef
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    // Metrics that actually have data, for the chart selector (hide body-fat for minors).
    val chartableKeys = remember(series, isMinor) {
        val keys = BODY_METRICS.map { it.key } + if (isMinor) emptyList() else listOf("bodyFatPct")
        keys.filter { key -> series?.points?.any { it.valueFor(key) != null } == true }
    }
    if (chartableKeys.isNotEmpty() && selectedMetric !in chartableKeys) {
        selectedMetric = chartableKeys.first()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text(
                "Progress & body",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // Disclaimer — shown prominently near the top.
        item { DisclaimerCard(series?.disclaimer?.ifBlank { DEFAULT_DISCLAIMER } ?: DEFAULT_DISCLAIMER) }

        state.toast?.let { msg ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(msg, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
                }
                LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.clearToast() }
            }
        }

        // Log measurements action.
        item {
            Card(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { showLog = true }
                    .semantics { contentDescription = "Log body measurements" },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    "＋ Log measurements",
                    Modifier.fillMaxWidth().padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        state.error?.let { err ->
            item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        // Just-saved confirmation.
        state.savedPoint?.let { pt ->
            item { SavedPointCard(point = pt, whr = state.savedWhr, isMinor = isMinor) }
        }

        if (series != null) {
            // Body-fat card — hidden entirely for minors.
            if (!isMinor) {
                series.latestBodyFat?.let { bf ->
                    item { BodyFatCard(pct = bf.pct, band = bf.band) }
                }
            }

            // Waist-to-hip ratio (a ratio, not a body-fat number) with an educational band + note.
            series.whr?.let { whr ->
                item { WhrCard(whr) }
            }

            // Per-metric trend chart with a selector.
            if (chartableKeys.isNotEmpty()) {
                item {
                    MetricChartCard(
                        series = series,
                        chartableKeys = chartableKeys,
                        selectedKey = selectedMetric,
                        onSelect = { selectedMetric = it },
                    )
                }
            }

            // Trend summaries for every measurement with a first→last change.
            val trends = series.trends.filter { !(isMinor && it.key == "bodyFatPct") }
            if (trends.isNotEmpty()) {
                item { SectionLabel("Changes over time") }
                items(trends.size) { i -> TrendRow(trends[i]) }
            }
        }

        // ---- Progress-photo timeline ----
        item { SectionLabel("Progress photos") }

        if (!state.consentGiven) {
            item {
                ConsentCard(
                    savingPhoto = state.savingPhoto,
                    onAgree = { viewModel.giveConsent() },
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { takePhoto() },
                        enabled = !state.savingPhoto,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Take a progress photo with the camera" },
                    ) { Text("📷 Take photo") }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !state.savingPhoto,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Choose a progress photo from your gallery" },
                    ) { Text("🖼️ Choose") }
                }
            }
            item {
                Text(
                    CONSENT_LINE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.photos.size >= 2) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (compareMode) "Pick two photos to compare" else "Compare progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                compareMode = !compareMode
                                compareSelection = emptyList()
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = if (compareMode) "Exit compare mode" else "Compare two photos side by side"
                            },
                        ) { Text(if (compareMode) "Done" else "Compare") }
                    }
                }
            }

            // Side-by-side compare card once two are picked.
            if (compareMode && compareSelection.size == 2) {
                item {
                    CompareCard(
                        context = context,
                        photos = state.photos,
                        refs = compareSelection,
                    )
                }
            }

            if (state.photosLoading && state.photos.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }

            if (!state.photosLoading && state.photos.isEmpty()) {
                item {
                    Text(
                        "No photos yet. Add one to start a private, on-device timeline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.photos.size) { i ->
                val photo = state.photos[i]
                PhotoRow(
                    context = context,
                    photo = photo,
                    compareMode = compareMode,
                    selected = photo.localRef in compareSelection,
                    onToggleSelect = {
                        compareSelection = when {
                            photo.localRef in compareSelection -> compareSelection - photo.localRef
                            compareSelection.size >= 2 -> listOf(compareSelection[1], photo.localRef)
                            else -> compareSelection + photo.localRef
                        }
                    },
                    onDelete = { pendingDelete = photo },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun DisclaimerCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💚", style = MaterialTheme.typography.titleMedium)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SavedPointCard(point: BodyPoint, whr: Whr?, isMinor: Boolean) {
    val parts = buildList {
        point.weightKg?.let { add("Weight ${formatNum(it)} kg") }
        point.waistCm?.let { add("Waist ${formatNum(it)} cm") }
        point.hipCm?.let { add("Hip ${formatNum(it)} cm") }
        point.chestCm?.let { add("Chest ${formatNum(it)} cm") }
        point.armCm?.let { add("Arm ${formatNum(it)} cm") }
        point.thighCm?.let { add("Thigh ${formatNum(it)} cm") }
        point.neckCm?.let { add("Neck ${formatNum(it)} cm") }
        if (!isMinor) point.bodyFatPct?.let { add("Body fat ${formatNum(it)}%") }
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Saved ✓", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (whr != null && whr.ratio > 0.0) {
                Text(
                    "Waist-to-hip ratio ${formatNum(whr.ratio)}${whr.band.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun BodyFatCard(pct: Double, band: String) {
    Card(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Latest body fat ${formatNum(pct)} percent${band.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}"
        },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Body fat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${formatNum(pct)}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (band.isNotBlank()) {
                    Text(band, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun WhrCard(whr: Whr) {
    Card(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Waist to hip ratio ${formatNum(whr.ratio)}${whr.band.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}"
        },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Waist-to-hip ratio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatNum(whr.ratio), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (whr.band.isNotBlank()) {
                    Text(whr.band, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (whr.note.isNotBlank()) {
                Text(whr.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricChartCard(
    series: BodySeries,
    chartableKeys: List<String>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val values = series.points.mapNotNull { it.valueFor(selectedKey) }
    val unit = unitFor(selectedKey)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Metric selector — horizontally scrollable chips.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chartableKeys.forEach { key ->
                    FilterChip(
                        selected = key == selectedKey,
                        onClick = { onSelect(key) },
                        label = { Text(labelFor(key)) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Chart ${labelFor(key)}" },
                    )
                }
            }

            if (values.size >= 2) {
                val lineColor = MaterialTheme.colorScheme.primary
                BodyLineChart(values = values, lineColor = lineColor, metricLabel = labelFor(selectedKey))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${formatNum(values.min())} $unit".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatNum(values.max())} $unit".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    "Log ${labelFor(selectedKey).lowercase()} at least twice to see a trend line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Simple Compose Canvas line chart — same math as VitalsScreen's VitalLineChart. */
@Composable
private fun BodyLineChart(values: List<Double>, lineColor: Color, metricLabel: String) {
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).let { if (it == 0.0) 1.0 else it }
    val fill = lineColor.copy(alpha = 0.15f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$metricLabel trend chart, ${values.size} readings" },
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

@Composable
private fun TrendRow(trend: com.nutriai.data.remote.dto.BodyTrend) {
    val unit = unitFor(trend.key)
    val delta = trend.delta
    val arrow = when {
        delta > 0 -> "▲"
        delta < 0 -> "▼"
        else -> "→"
    }
    val deltaText = "$arrow ${formatNum(kotlin.math.abs(delta))} $unit".trim()
    Card(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "${labelFor(trend.key)} changed from ${formatNum(trend.first)} to ${formatNum(trend.last)} $unit"
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(labelFor(trend.key), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatNum(trend.first)} → ${formatNum(trend.last)} $unit".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(deltaText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------------------
// Progress-photo timeline pieces
// ---------------------------------------------------------------------------

@Composable
private fun ConsentCard(savingPhoto: Boolean, onAgree: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Private progress photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                CONSENT_LINE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(
                onClick = onAgree,
                enabled = !savingPhoto,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "I understand — enable private progress photos" },
            ) { Text("Got it") }
        }
    }
}

@Composable
private fun CompareCard(context: Context, photos: List<BodyPhotoDto>, refs: List<String>) {
    val picked = refs.mapNotNull { r -> photos.firstOrNull { it.localRef == r } }
    if (picked.size < 2) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Before & after", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                picked.forEach { photo ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LocalPhoto(
                            context = context,
                            localRef = photo.localRef,
                            desc = "Progress photo from ${photo.takenAt.take(10)}",
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)),
                        )
                        Text(photo.takenAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    context: Context,
    photo: BodyPhotoDto,
    compareMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderMod = if (compareMode && selected) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else Modifier
    Card(
        Modifier.fillMaxWidth().then(borderMod),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(photo.takenAt.take(10), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!compareMode) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove photo from ${photo.takenAt.take(10)}" },
                    ) { Text("Remove") }
                }
            }
            LocalPhoto(
                context = context,
                localRef = photo.localRef,
                desc = "Progress photo from ${photo.takenAt.take(10)}${photo.caption?.let { ", $it" } ?: ""}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (compareMode) Modifier.clickable(onClick = onToggleSelect) else Modifier),
            )
            photo.caption?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (compareMode) {
                Text(
                    if (selected) "Selected to compare" else "Tap the photo to select",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Loads a progress image from private storage by localRef; graceful placeholder if it isn't here. */
@Composable
private fun LocalPhoto(context: Context, localRef: String, desc: String, modifier: Modifier) {
    val file = remember(localRef) { ImageUtil.progressPhotoFile(context, localRef) }
    val exists = remember(localRef) { file.exists() }
    if (exists) {
        AsyncImage(
            model = file,
            contentDescription = desc,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = "Photo not on this device" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "📵 Photo not on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun LogMeasurementsDialog(
    isMinor: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (BodyMetricRequest) -> Unit,
) {
    val values = remember { mutableStateMapOf<String, String>() }
    var bodyFatText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }

    fun parse(key: String): Double? = values[key]?.toDoubleOrNull()
    val anyValue = BODY_METRICS.any { parse(it.key) != null } || (!isMinor && bodyFatText.toDoubleOrNull() != null)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Log measurements") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BODY_METRICS.forEach { meta ->
                    OutlinedTextField(
                        value = values[meta.key] ?: "",
                        onValueChange = { new -> values[meta.key] = new.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("${meta.label} (${meta.unit})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${meta.label} in ${meta.unit}" },
                    )
                }
                if (!isMinor) {
                    OutlinedTextField(
                        value = bodyFatText,
                        onValueChange = { new -> bodyFatText = new.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text("Body fat % (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Body fat percentage, optional" },
                    )
                }
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Measurement date" },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = anyValue && !submitting,
                onClick = {
                    onSave(
                        BodyMetricRequest(
                            measuredAt = dateText.ifBlank { null },
                            weightKg = parse("weightKg"),
                            waistCm = parse("waistCm"),
                            hipCm = parse("hipCm"),
                            chestCm = parse("chestCm"),
                            armCm = parse("armCm"),
                            thighCm = parse("thighCm"),
                            neckCm = parse("neckCm"),
                            bodyFatPct = if (isMinor) null else bodyFatText.toDoubleOrNull(),
                        ),
                    )
                },
            ) { Text(if (submitting) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } },
    )
}

@Composable
private fun CaptionDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var caption by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a note?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(CONSENT_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(80) },
                    label = { Text("Caption (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Optional caption for this photo" },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(caption) }) { Text("Save photo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
