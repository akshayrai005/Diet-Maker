package com.nutriai.ui.barcode

import android.Manifest
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

data class BarcodeState(
    val code: String = "",
    val food: BarcodeFood? = null,
    /** Blank, not pre-filled - forces a deliberate "how much did I actually eat" entry instead
     * of silently defaulting to 100g of a product that might be a 1kg pack. */
    val grams: String = "",
    val slot: String = "breakfast",
    val loading: Boolean = false,
    val message: String? = null,
    /** True once a lookup 404s - offers "add it manually" instead of a dead end. */
    val notFound: Boolean = false,
    val manualName: String = "",
    val manualKcal: String = "",
    val manualProtein: String = "",
    val manualCarb: String = "",
    val manualFat: String = "",
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
        _state.value = _state.value.copy(loading = true, message = null, food = null, notFound = false)
        viewModelScope.launch {
            val r = repository.barcode(code)
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, food = r.getOrNull(), notFound = false)
            } else {
                _state.value.copy(
                    loading = false,
                    food = null,
                    notFound = true,
                    message = "Couldn't find that barcode. Add it below so it's remembered next time, or search by name in the Log tab.",
                )
            }
        }
    }

    fun onManualField(name: String? = null, kcal: String? = null, protein: String? = null, carb: String? = null, fat: String? = null) {
        val st = _state.value
        _state.value = st.copy(
            manualName = name ?: st.manualName,
            manualKcal = kcal ?: st.manualKcal,
            manualProtein = protein ?: st.manualProtein,
            manualCarb = carb ?: st.manualCarb,
            manualFat = fat ?: st.manualFat,
        )
    }

    /** Saves a user-entered product against this barcode - found instantly on the next scan. */
    fun saveManualProduct() {
        val st = _state.value
        val name = st.manualName.trim()
        val kcal = st.manualKcal.toDoubleOrNull()
        if (name.isBlank() || kcal == null) return
        _state.value = st.copy(loading = true, message = null)
        viewModelScope.launch {
            val body = com.nutriai.data.remote.dto.SavedFoodRequest(
                name = name,
                kcal = kcal,
                proteinG = st.manualProtein.toDoubleOrNull() ?: 0.0,
                carbG = st.manualCarb.toDoubleOrNull() ?: 0.0,
                fatG = st.manualFat.toDoubleOrNull() ?: 0.0,
                barcode = st.code,
            )
            val r = repository.saveFood(body)
            if (r.isSuccess) {
                lookup() // re-fetch: the barcode endpoint now finds this saved entry first
            } else {
                _state.value = _state.value.copy(loading = false, message = r.exceptionOrNull()?.message ?: "Could not save")
            }
        }
    }

    fun logIt() {
        val food = _state.value.food ?: return
        val grams = _state.value.grams.toDoubleOrNull() ?: 100.0
        val slot = com.nutriai.util.MealSlot.now()
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

@Composable
private fun BarcodeMacroStat(emoji: String, label: String, grams: Double, color: Color) {
    Column {
        Text("$emoji ${grams.let { if (it == it.toInt().toDouble()) "${it.toInt()}" else "%.1f".format(it) }}g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xs))

        // ---- Camera preview ----
        Text("🎥 Camera", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        if (cameraPermission.status.isGranted) {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(Sharp),
                    ) {
                        CameraScanner(
                            onBarcode = viewModel::onScanned,
                            modifier = Modifier.fillMaxSize().clip(Sharp),
                        )
                    }
                    Text(
                        "📸 Hold still • good light • 15-20 cm from barcode",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandGreen,
                    )
                    Text(
                        "💡 Camera not scanning? Enter barcode number manually below",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Camera Permission", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    PrimaryButton(
                        text = "Enable camera to scan",
                        onClick = { cameraPermission.launchPermissionRequest() },
                        containerColor = KaizenBlue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ---- Manual entry ----
        Text("⌨️ Manual Entry", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = Sharp,
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCode,
                    label = { Text("Barcode number") },
                    singleLine = true,
                    shape = Sharp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaizenLavender,
                        focusedLabelColor = KaizenLavender,
                        cursorColor = KaizenLavender,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "Look up",
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
            Text("✅ Found Food", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(food.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column {
                            Text(
                                "${food.per100g.kcal.toInt()} kcal",
                                style = MaterialTheme.typography.titleMedium,
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

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BarcodeMacroStat("💪", "Protein", food.per100g.proteinG, NutritionColor)
                        BarcodeMacroStat("🌾", "Carbs", food.per100g.carbG, BrandAmber)
                        BarcodeMacroStat("🥑", "Fat", food.per100g.fatG, KaizenCoral)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    Text(
                        "⚠️ How much did you actually eat? The barcode is for the whole pack, not one serving.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandAmber,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = state.grams,
                        onValueChange = viewModel::onGrams,
                        label = { Text("Grams you ate (not the pack size)") },
                        placeholder = { Text("e.g. 50") },
                        singleLine = true,
                        shape = Sharp,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NutritionColor,
                            focusedLabelColor = NutritionColor,
                            cursorColor = NutritionColor,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )


                    PrimaryButton(
                        text = "Log it",
                        onClick = { viewModel.logIt() },
                        enabled = !state.loading && (state.grams.toIntOrNull() ?: 0) > 0,
                        containerColor = BrandGreenDeep,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ---- Friendly message ----
        state.message?.let {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }

        // ---- Add product manually (fills the gap for next time) ----
        if (state.notFound) {
            Text("✍️ Add This Product", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Enter the nutrition facts per 100 g from the pack label - saved against this barcode so it's found instantly next time you scan it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.manualName,
                        onValueChange = { viewModel.onManualField(name = it) },
                        label = { Text("Product name") },
                        singleLine = true,
                        shape = Sharp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = state.manualKcal,
                            onValueChange = { viewModel.onManualField(kcal = it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("kcal/100g") },
                            singleLine = true,
                            shape = Sharp,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.manualProtein,
                            onValueChange = { viewModel.onManualField(protein = it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("Protein (g)") },
                            singleLine = true,
                            shape = Sharp,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = state.manualCarb,
                            onValueChange = { viewModel.onManualField(carb = it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("Carbs (g)") },
                            singleLine = true,
                            shape = Sharp,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.manualFat,
                            onValueChange = { viewModel.onManualField(fat = it.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("Fat (g)") },
                            singleLine = true,
                            shape = Sharp,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PrimaryButton(
                        text = "Save product",
                        onClick = { viewModel.saveManualProduct() },
                        enabled = !state.loading && state.manualName.isNotBlank() && state.manualKcal.toDoubleOrNull() != null,
                        containerColor = KaizenLavender,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
    )
}
