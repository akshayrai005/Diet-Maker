package com.nutriai.ui.body

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.BodyAssessment
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BodyUiState(
    val analyzing: Boolean = false,
    val currentPhoto: Uri? = null,
    val assessment: BodyAssessment? = null,
    val error: String? = null,
)

@HiltViewModel
class BodyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BodyUiState())
    val state: StateFlow<BodyUiState> = _state.asStateFlow()

    init {
        // Clean up any photos saved by older versions - nothing is stored anymore.
        viewModelScope.launch { withContext(Dispatchers.IO) { ImageUtil.clearProgress(context) } }
    }

    /** Selects a photo for analysis. The image is NOT saved anywhere on the device. */
    fun onPhoto(uri: Uri) {
        _state.value = _state.value.copy(currentPhoto = uri, assessment = null, error = null)
    }

    fun analyze() {
        val uri = _state.value.currentPhoto ?: return
        _state.value = _state.value.copy(analyzing = true, error = null)
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.IO) {
                ImageUtil.downscaledJpegBytes(context, uri)?.let(ImageUtil::toBase64)
            }
            if (base64 == null) {
                _state.value = _state.value.copy(analyzing = false, error = "Couldn't process that photo.")
                return@launch
            }
            val r = repository.assessBodyPhoto(base64)
            _state.value = _state.value.copy(
                analyzing = false,
                assessment = r.getOrNull(),
                error = if (r.isFailure) "Couldn't analyse the photo. Make sure your profile is complete and the server is awake, then try again." else null,
            )
        }
    }
}

@Composable
fun BodyScreen(modifier: Modifier = Modifier, viewModel: BodyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onPhoto(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) viewModel.onPhoto(uri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera(context, System.currentTimeMillis()) { uri -> cameraUri = uri; cameraLauncher.launch(uri) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Hero() }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera(context, System.currentTimeMillis()) { uri -> cameraUri = uri; cameraLauncher.launch(uri) }
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("📷 Take photo") }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text("🖼️ Choose")
                }
            }
        }

        state.currentPhoto?.let { photo ->
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "Your photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(14.dp)),
                        )
                        Button(onClick = { viewModel.analyze() }, enabled = !state.analyzing, modifier = Modifier.fillMaxWidth()) {
                            if (state.analyzing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                            else Text("✨ Analyze with AI")
                        }
                        Text(
                            "Not saved anywhere. The photo is used only for this analysis and then discarded - it only leaves your phone when you tap Analyze.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        state.assessment?.let { item { AssessmentCard(it) } }
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
                Text("Body Check 📸", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("A rough AI body-fat read from a photo. Nothing is saved.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun AssessmentCard(a: BodyAssessment) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !a.available -> Text("AI analysis isn't enabled on the server. Your formula estimate is ${fmtPct(a.formulaEstimatePct)}.", style = MaterialTheme.typography.bodyMedium)
                a.refused -> Text(a.reason ?: "Couldn't analyse this photo.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    val fromPhoto = a.source == "ai"
                    Text(
                        if (fromPhoto) "📷 Estimated body fat (from your photo)" else "📐 Estimated body fat (from your stats)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "${trim(a.bodyFatLow)}-${trim(a.bodyFatHigh)}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandGreenDeep,
                    )
                    a.category?.takeIf { it.isNotBlank() }?.let { Text("Category: $it  ·  confidence ${a.confidence ?: "low"}", style = MaterialTheme.typography.bodySmall) }
                    a.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (fromPhoto) {
                        a.formulaEstimatePct?.let { Text("Formula (BMI-based) estimate: ${fmtPct(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) }
                    }
                }
            }
            Text(a.disclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        }
    }
}

private fun launchCamera(context: Context, ts: Long, onReady: (Uri) -> Unit) {
    val (uri, _) = ImageUtil.newCameraOutput(context, ts)
    onReady(uri)
}

private fun trim(v: Double?): String = v?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "?"
private fun fmtPct(v: Double?): String = v?.let { "${trim(it)}%" } ?: "unavailable"
