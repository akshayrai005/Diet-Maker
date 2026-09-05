package com.nutriai.ui.barcode

import android.Manifest
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.BarcodeFood
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BarcodeState(
    val code: String = "",
    val food: BarcodeFood? = null,
    val grams: String = "100",
    val slot: String = "breakfast",
    val loading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BarcodeState())
    val state: StateFlow<BarcodeState> = _state.asStateFlow()

    val slots = listOf("breakfast", "midmorning", "lunch", "eveningsnack", "dinner", "bedtime")

    fun onCode(c: String) {
        _state.value = _state.value.copy(code = c.filter { it.isDigit() })
    }

    fun onGrams(g: String) {
        _state.value = _state.value.copy(grams = g.filter { it.isDigit() })
    }

    fun onSlot(s: String) {
        _state.value = _state.value.copy(slot = s)
    }

    /** Called by the camera when a barcode is detected - guarded against repeats. */
    fun onScanned(code: String) {
        val digits = code.filter { it.isDigit() }
        val st = _state.value
        if (digits.isBlank() || st.loading || st.food != null || digits == st.code) return
        _state.value = st.copy(code = digits)
        lookup()
    }

    fun lookup() {
        val code = _state.value.code
        if (code.isBlank()) return
        _state.value = _state.value.copy(loading = true, message = null, food = null)
        viewModelScope.launch {
            val r = repository.barcode(code)
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, food = r.getOrNull())
            } else {
                _state.value.copy(
                    loading = false,
                    food = null,
                    message = "Couldn't find that barcode. Try again, or search the food by name in the Log tab.",
                )
            }
        }
    }

    fun logIt() {
        val food = _state.value.food ?: return
        val grams = _state.value.grams.toDoubleOrNull() ?: 100.0
        val slot = _state.value.slot
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = repository.logBarcodeFood(slot, food, grams)
            _state.value = _state.value.copy(
                loading = false,
                message = if (r.isSuccess) {
                    "Logged ${grams.toInt()} g of ${food.name} to $slot"
                } else {
                    r.exceptionOrNull()?.message ?: "Could not log"
                },
            )
        }
    }
}

private val slotEmojis = mapOf(
    "breakfast" to "🍳",
    "midmorning" to "☕",
    "lunch" to "🍛",
    "eveningsnack" to "🍪",
    "dinner" to "🍝",
    "bedtime" to "🌙",
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScreen(
    modifier: Modifier = Modifier,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val tokens = MaterialTheme.kaizenColors

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xs))

        // ---- Hero header ----
        com.nutriai.ui.components.ScreenHeader(
            title = "📷 Barcode Scanner",
            subtitle = "Scan packaged food and log it instantly",
        )

        FeatureCard(
            emoji = "📦",
            title = "Scan & Log",
            accentColor = BrandGreen,
        ) {
            Text(
                "Point, scan, and log packaged food in seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Camera preview ----
        SectionHeader(title = "Camera", emoji = "🎥")

        if (cameraPermission.status.isGranted) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(Radius.lg))
                            .border(
                                width = 3.dp,
                                brush = Brush.verticalGradient(listOf(NutritionColor, KaizenBlue)),
                                shape = RoundedCornerShape(Radius.lg),
                            ),
                    ) {
                        CameraScanner(
                            onBarcode = viewModel::onScanned,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.lg)),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        EmojiBadge(emoji = "🎯", bgColor = BrandGreen.copy(alpha = 0.15f))
                        Text(
                            "Point at a barcode",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandGreen,
                        )
                    }
                }
            }
        } else {
            FeatureCard(
                emoji = "📸",
                title = "Camera Permission",
                accentColor = KaizenBlue,
            ) {
                PrimaryButton(
                    text = "Enable camera to scan",
                    onClick = { cameraPermission.launchPermissionRequest() },
                    containerColor = KaizenBlue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Manual entry ----
        SectionHeader(title = "Manual Entry", emoji = "⌨️")

        FeatureCard(
            emoji = "🔢",
            title = "Enter Barcode",
            accentColor = KaizenLavender,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCode,
                    label = { Text("Barcode number") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.md),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaizenLavender,
                        focusedLabelColor = KaizenLavender,
                        cursorColor = KaizenLavender,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "🔍 Look up",
                    onClick = { viewModel.lookup() },
                    enabled = state.code.isNotBlank() && !state.loading,
                    containerColor = KaizenLavender,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
        }

        // ---- Found food + grams + meal + log ----
        state.food?.let { food ->
            SectionHeader(title = "Found Food", emoji = "✅")

            FeatureCard(
                emoji = "🍽️",
                title = food.name,
                accentColor = NutritionColor,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        EmojiBadge(
                            emoji = "🔥",
                            bgColor = KaizenCoral.copy(alpha = 0.18f),
                            size = 48.dp,
                        )
                        Column {
                            Text(
                                "${food.per100g.kcal.toInt()} kcal",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = KaizenCoral,
                            )
                            Text(
                                "per 100 g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.grams,
                        onValueChange = viewModel::onGrams,
                        label = { Text("⚖️ Grams") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NutritionColor,
                            focusedLabelColor = NutritionColor,
                            cursorColor = NutritionColor,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionHeader(title = "Meal Slot", emoji = "🍽️")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        viewModel.slots.forEach { s ->
                            val emoji = slotEmojis[s] ?: "🍽️"
                            FilterChip(
                                selected = s == state.slot,
                                onClick = { viewModel.onSlot(s) },
                                label = { Text("$emoji $s") },
                                shape = RoundedCornerShape(Radius.md),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NutritionColor,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    PrimaryButton(
                        text = "✅ Log it",
                        onClick = { viewModel.logIt() },
                        enabled = !state.loading,
                        containerColor = BrandGreenDeep,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ---- Friendly message ----
        state.message?.let {
            FeatureCard(
                emoji = "💬",
                title = "Status",
                accentColor = BrandAmber,
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

/**
 * CameraX preview + ML Kit barcode analysis via [LifecycleCameraController] (the high-level
 * camera-view API - avoids the ProcessCameraProvider ListenableFuture entirely).
 * Calls [onBarcode] with the first detected value.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }

    LaunchedEffect(controller) {
        val scanner = BarcodeScanning.getClient()
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            ImageAnalysis.Analyzer { proxy ->
                val media = proxy.image
                if (media != null) {
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let(onBarcode)
                        }
                        .addOnCompleteListener { proxy.close() }
                } else {
                    proxy.close()
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
    )
}
