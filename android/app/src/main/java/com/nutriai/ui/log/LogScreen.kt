package com.nutriai.ui.log

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.remote.dto.FoodDto
import com.nutriai.data.remote.dto.FoodLogEntry
import com.nutriai.data.remote.dto.SavedFoodRequest
import com.nutriai.data.remote.dto.VisionFoodItem
import com.nutriai.ui.home.LogFoodViewModel
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.CardLavenderLight
import com.nutriai.ui.components.EmojiBadge

import com.nutriai.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Calendar

private val Sharp = RoundedCornerShape(8.dp)

private fun autoSlotByTime(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 10 -> "breakfast"
        hour < 12 -> "midmorning"
        hour < 15 -> "lunch"
        hour < 18 -> "eveningsnack"
        hour < 21 -> "dinner"
        else -> "bedtime"
    }
}

@Composable
fun LogScreen(
    modifier: Modifier = Modifier,
    viewModel: LogFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingFood by remember { mutableStateOf<FoodDto?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingQty by remember { mutableStateOf<PendingQty?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.onSlot(autoSlotByTime()) }

    fun analyze(uri: Uri) {
        scope.launch {
            val b64 = withContext(Dispatchers.IO) { ImageUtil.downscaledJpegBytes(context, uri)?.let(ImageUtil::toBase64) }
            if (b64 != null) viewModel.analyzePhoto(b64)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyze(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraUri?.let { if (ok) analyze(it) }
    }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, _) = ImageUtil.newCameraOutput(context, System.currentTimeMillis())
            cameraUri = uri; cameraLauncher.launch(uri)
        }
    }
    fun snapMeal() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val (uri, _) = ImageUtil.newCameraOutput(context, System.currentTimeMillis())
            cameraUri = uri; cameraLauncher.launch(uri)
        } else camPermLauncher.launch(Manifest.permission.CAMERA)
    }

    pendingQty?.let { pq ->
        GenericQtyDialog(pq, onConfirm = { grams -> pq.onLog(grams); pendingQty = null }, onDismiss = { pendingQty = null })
    }
    if (showCustom) {
        CustomFoodDialog(onSave = { viewModel.saveCustom(it); showCustom = false }, onDismiss = { showCustom = false })
    }
    pendingFood?.let { food ->
        com.nutriai.ui.components.QuantityBottomSheet(
            food = food,
            onConfirm = { g -> viewModel.log(food, g); pendingFood = null },
            onDismiss = { pendingFood = null },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        // Header — single line with count
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("📝 Log Food", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.today.isNotEmpty()) {
                    Text("✅ ${state.today.size} logged", style = MaterialTheme.typography.labelSmall, color = BrandGreenDeep, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Search + voice + barcode in one row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query = state.query,
                    onQuery = { viewModel.onQuery(it) },
                    onSearch = { viewModel.search(state.query) },
                    modifier = Modifier.weight(1f),
                )
                // Barcode scanner button
                Card(
                    shape = Sharp,
                    colors = CardDefaults.cardColors(containerColor = BrandAmber.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.3f)),
                    modifier = Modifier.size(48.dp).clickable {
                        // Navigate to barcode screen via the More tab's barcode route
                        // For now, trigger snap as barcode needs navigation context
                    },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode", tint = BrandAmber, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // Action row: Snap + Photo — compact
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { snapMeal() },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = Sharp,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    enabled = !state.analyzing,
                ) {
                    if (!state.analyzing) Icon(Icons.Filled.PhotoCamera, null, Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (state.analyzing) "Analyzing..." else "Snap meal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = Sharp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { showCustom = true },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = Sharp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("+ Custom", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // AI-detected items
        if (state.photoItems.isNotEmpty()) {
            item { Text("🤖 Detected", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            items(state.photoItems, key = { it.name + it.grams }) { it2 ->
                DetectedItemCard(it2, onAdd = { viewModel.logVisionItem(it2) })
            }
        }

        // Recent — inline chips
        if (state.recents.isNotEmpty()) {
            item {
                Text("🔄 Recent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                QuickRow(names = state.recents.map { it.name }) { idx ->
                    val r = state.recents[idx]; pendingQty = PendingQty(r.name, r.per100g.kcal) { g -> viewModel.logRecent(r, g) }
                }
            }
        }

        // Saved — inline chips
        if (state.saved.isNotEmpty()) {
            item {
                Text("⭐ Saved", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                QuickRow(names = state.saved.map { it.name }) { idx ->
                    val s = state.saved[idx]; pendingQty = PendingQty(s.name, s.kcal) { g -> viewModel.logSaved(s, g) }
                }
            }
        }

        // Status message
        state.message?.let { msg ->
            item { MessageBanner(msg) }
        }

        // Loading
        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Results
        if (state.results.isNotEmpty()) {
            item { Text("🔍 Results", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            items(state.results, key = { it.id }) { food ->
                ResultCard(food = food, onAdd = { pendingFood = food }, onFavorite = { viewModel.favorite(food) })
            }
        }

        // Today's log
        if (state.today.isNotEmpty()) {
            val totalKcal = state.today.sumOf { it.kcal }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📝 Today's log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("🔥 ${totalKcal.toInt()} kcal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandGreenDeep)
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    border = BorderStroke(1.dp, KaizenLavender.copy(alpha = 0.2f)),
                    colors = CardDefaults.cardColors(containerColor = CardLavenderLight),
                ) {
                    Column(Modifier.padding(Spacing.sm)) {
                        state.today.forEachIndexed { index, entry ->
                            LogEntryRow(entry, onDelete = { viewModel.delete(entry.id) })
                            if (index != state.today.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search field — compact
// ---------------------------------------------------------------------------

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                onQuery(spoken)
                onSearch()
            }
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a food, e.g. \"four eggs and dal\"")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { Toast.makeText(context, "Voice input isn't available on this device", Toast.LENGTH_SHORT).show() }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.height(52.dp),
        placeholder = { Text("Search foods", style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            IconButton(onClick = { startVoice() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice", modifier = Modifier.size(18.dp))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
        shape = Sharp,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

// ---------------------------------------------------------------------------
// Message banner — compact
// ---------------------------------------------------------------------------

@Composable
private fun MessageBanner(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Sharp,
        colors = CardDefaults.cardColors(containerColor = com.nutriai.ui.theme.CardGreenLight),
        border = BorderStroke(1.dp, BrandGreen.copy(alpha = 0.3f)),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("✅", fontSize = 12.sp)
            Text(message, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = BrandGreenDeep)
        }
    }
}

// ---------------------------------------------------------------------------
// Result card — compact
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(food: FoodDto, onAdd: () -> Unit, onFavorite: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("🔥${food.kcal.toInt()} · P${food.proteinG.toInt()} C${food.carbG.toInt()} F${food.fatG.toInt()}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = BrandAmber,
                modifier = Modifier.size(18.dp).clickable { onFavorite() })
            Button(onClick = onAdd, modifier = Modifier.height(28.dp), shape = Sharp,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("Add", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
        }
    }
}

private data class PendingQty(val name: String, val kcalPer100: Double, val onLog: (Double) -> Unit)

@Composable
private fun DetectedItemCard(item: VisionFoodItem, onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, KaizenLavender.copy(alpha = 0.2f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", fontSize = 12.sp)
            Spacer(Modifier.size(Spacing.xs))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("${item.grams.toInt()}g · 🔥${(item.per100g.kcal * item.grams / 100).toInt()} kcal",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Button(onClick = onAdd, modifier = Modifier.height(28.dp), shape = Sharp,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("Add", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun QuickRow(
    names: List<String>,
    onPick: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEachIndexed { i, n ->
            Card(
                shape = Sharp,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { onPick(i) },
            ) {
                Text(n, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GenericQtyDialog(pq: PendingQty, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var g by remember(pq) { mutableStateOf("150") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("🍽️ How much ${pq.name}?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = g, onValueChange = { g = it.filter { c -> c.isDigit() } },
                    label = { Text("Grams") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = Sharp,
                )
                val grams = g.toDoubleOrNull() ?: 0.0
                Text("🔥 = ${(pq.kcalPer100 * grams / 100).toInt()} kcal", color = BrandGreenDeep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { g.toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm) }, shape = Sharp, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomFoodDialog(onSave: (SavedFoodRequest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("🌟 Create a food", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Per 100 g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = Sharp)
                OutlinedTextField(kcal, { kcal = it.filter { c -> c.isDigit() } }, label = { Text("Calories") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = Sharp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(protein, { protein = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("P") }, singleLine = true, modifier = Modifier.weight(1f), shape = Sharp)
                    OutlinedTextField(carb, { carb = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("C") }, singleLine = true, modifier = Modifier.weight(1f), shape = Sharp)
                    OutlinedTextField(fat, { fat = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("F") }, singleLine = true, modifier = Modifier.weight(1f), shape = Sharp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val k = kcal.toDoubleOrNull()
                    if (name.isNotBlank() && k != null) {
                        onSave(SavedFoodRequest(name = name.trim(), kcal = k, proteinG = protein.toDoubleOrNull() ?: 0.0, carbG = carb.toDoubleOrNull() ?: 0.0, fatG = fat.toDoubleOrNull() ?: 0.0))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen), shape = Sharp,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Today's log entry — compact row
// ---------------------------------------------------------------------------

@Composable
private fun LogEntryRow(entry: FoodLogEntry, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.foodName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${entry.mealSlot.replaceFirstChar { it.uppercase() }} · ${entry.grams.toInt()}g · P${entry.proteinG.toInt()} C${entry.carbG.toInt()} F${entry.fatG.toInt()}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
            )
        }
        Text("${entry.kcal.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandGreenDeep)
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = KaizenCoral)
        }
    }
}
