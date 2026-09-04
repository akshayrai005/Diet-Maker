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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.remote.dto.FoodDto
import com.nutriai.data.remote.dto.FoodLogEntry
import com.nutriai.data.remote.dto.RecentFood
import com.nutriai.data.remote.dto.SavedFood
import com.nutriai.data.remote.dto.SavedFoodRequest
import com.nutriai.data.remote.dto.VisionFoodItem
import com.nutriai.ui.home.LogFoodViewModel
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep

import com.nutriai.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

// ---------------------------------------------------------------------------
// Premium food-logging screen for NutriAI.
// One self-contained file. Reuses LogFoodViewModel. Foundation / Material3 only.
// ---------------------------------------------------------------------------

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

    // Quantity bottom sheet - set exact grams for THIS food before logging (accurate calories).
    pendingFood?.let { food ->
        com.nutriai.ui.components.QuantityBottomSheet(
            food = food,
            onConfirm = { g -> viewModel.log(food, g); pendingFood = null },
            onDismiss = { pendingFood = null },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
    ) {
        // 1. Header
        item { LogHeader(todayCount = state.today.size) }

        // 2. Meal-slot chips
        item {
            SlotChipRow(
                slots = viewModel.slots,
                selected = state.slot,
                onSelect = { viewModel.onSlot(it) },
            )
        }

        // 3. Search field + button
        item {
            SearchCard(
                query = state.query,
                onQuery = { viewModel.onQuery(it) },
                onSearch = { viewModel.search(state.query) },
            )
        }

        // 3b. Snap a meal (AI photo logging)
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { snapMeal() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.analyzing,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) { if (state.analyzing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("📷 Snap a meal") }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Text("🖼️ Photo")
                }
            }
        }

        // 3c. AI-detected items from the photo
        if (state.photoItems.isNotEmpty()) {
            item { SectionLabel("Detected in photo", "AI estimates - tap Add (adjust grams anytime)") }
            items(state.photoItems, key = { it.name + it.grams }) { it2 ->
                DetectedItemCard(it2, onAdd = { viewModel.logVisionItem(it2) })
            }
        }

        // 3d. Recent + saved quick-log
        if (state.recents.isNotEmpty()) {
            item { QuickRow("🕘 Recent", state.recents.map { it.name }) { idx -> val r = state.recents[idx]; pendingQty = PendingQty(r.name, r.per100g.kcal) { g -> viewModel.logRecent(r, g) } } }
        }
        item {
            QuickRow("⭐ Saved", state.saved.map { it.name }, trailingLabel = "＋ Custom", onTrailing = { showCustom = true }) { idx ->
                val s = state.saved[idx]; pendingQty = PendingQty(s.name, s.kcal) { g -> viewModel.logSaved(s, g) }
            }
        }

        // 4. Status message
        state.message?.let { msg ->
            item { MessageBanner(msg) }
        }

        // 5. Loading
        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }
        }

        // 6. Results
        if (state.results.isNotEmpty()) {
            item { SectionLabel("Search results", "Tap Add to log a portion") }
            items(state.results, key = { it.id }) { food ->
                ResultCard(
                    food = food,
                    onAdd = { pendingFood = food },
                    onFavorite = { viewModel.favorite(food) },
                )
            }
        }

        // 7. Today's log
        if (state.today.isNotEmpty()) {
            val totalKcal = state.today.sumOf { it.kcal }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Today's log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${state.today.size} item${if (state.today.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TotalChip(kcal = totalKcal)
                }
            }
            items(state.today, key = { it.id }) { entry ->
                LogEntryCard(entry, onDelete = { viewModel.delete(entry.id) })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun LogHeader(todayCount: Int) {
    com.nutriai.ui.components.ScreenHeader(
        title = "log food",
        subtitle = if (todayCount > 0) "✓ $todayCount logged today" else null,
    )
}

// ---------------------------------------------------------------------------
// Meal-slot chips
// ---------------------------------------------------------------------------

@Composable
private fun SlotChipRow(
    slots: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Meal",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            slots.forEach { slot ->
                SlotChip(
                    label = slot.replaceFirstChar { it.uppercase() },
                    selected = slot == selected,
                    onClick = { onSelect(slot) },
                )
            }
        }
    }
}

@Composable
private fun SlotChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) BrandGreen else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
        )
    }
}

// ---------------------------------------------------------------------------
// Search card
// ---------------------------------------------------------------------------

@Composable
private fun SearchCard(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    // Voice search: system speech recognizer fills the search box with what you say
    // (e.g. "four eggs and dal"), then runs the search. No parsing - just speech→text.
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search foods (local + USDA)…") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                trailingIcon = {
                    IconButton(onClick = { startVoice() }) {
                        Icon(Icons.Filled.Mic, contentDescription = "Search by voice", tint = BrandGreen)
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = BrandGreen,
                    cursorColor = BrandGreen,
                ),
            )
            Button(
                onClick = onSearch,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) { Text("Search") }
        }
    }
}

// ---------------------------------------------------------------------------
// Message banner
// ---------------------------------------------------------------------------

@Composable
private fun MessageBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.12f)),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = BrandGreenDeep,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Result card
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(food: FoodDto, onAdd: () -> Unit, onFavorite: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${food.kcal.toInt()} kcal · P ${food.proteinG.toInt()} / C ${food.carbG.toInt()} / F ${food.fatG.toInt()} per 100g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("☆", style = MaterialTheme.typography.titleLarge, color = BrandGreen, modifier = Modifier.clickable { onFavorite() }.padding(4.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) { Text("Add") }
        }
    }
}

private data class PendingQty(val name: String, val kcalPer100: Double, val onLog: (Double) -> Unit)

@Composable
private fun DetectedItemCard(item: VisionFoodItem, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.08f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("≈ ${item.grams.toInt()} g · ${(item.per100g.kcal * item.grams / 100).toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("Add") }
        }
    }
}

@Composable
private fun QuickRow(
    title: String,
    names: List<String>,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n -> QuickChip(n) { onPick(i) } }
            if (trailingLabel != null && onTrailing != null) QuickChip(trailingLabel, accent = true) { onTrailing() }
        }
    }
}

@Composable
private fun QuickChip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (accent) BrandGreen else BrandGreen.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = if (accent) Color.White else BrandGreenDeep)
    }
}

@Composable
private fun GenericQtyDialog(pq: PendingQty, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var g by remember(pq) { mutableStateOf("150") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("How much ${pq.name}?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = g, onValueChange = { g = it.filter { c -> c.isDigit() } },
                    label = { Text("Grams") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val grams = g.toDoubleOrNull() ?: 0.0
                Text("= ${(pq.kcalPer100 * grams / 100).toInt()} kcal", color = BrandGreenDeep, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { Button(onClick = { g.toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm) }, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("Add") } },
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
        shape = RoundedCornerShape(24.dp),
        title = { Text("Create a food", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Per 100 g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(kcal, { kcal = it.filter { c -> c.isDigit() } }, label = { Text("Calories") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(protein, { protein = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Protein") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(carb, { carb = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Carbs") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(fat, { fat = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Fat") }, singleLine = true, modifier = Modifier.weight(1f))
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
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Quantity dialog
// ---------------------------------------------------------------------------


// ---------------------------------------------------------------------------
// Today's log entry card
// ---------------------------------------------------------------------------

private fun slotEmoji(slot: String): String = when (slot.lowercase()) {
    "breakfast" -> "🍳"
    "midmorning" -> "🍎"
    "lunch" -> "🥗"
    "eveningsnack" -> "☕"
    "dinner" -> "🍽️"
    "bedtime" -> "🥛"
    else -> "🍴"
}

@Composable
private fun LogEntryCard(entry: FoodLogEntry, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading meal badge.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(slotEmoji(entry.mealSlot), style = MaterialTheme.typography.titleLarge) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    entry.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaPill("${entry.grams.toInt()} g")
                    MetaPill(entry.mealSlot.replaceFirstChar { it.uppercase() })
                }
                Text(
                    "P ${entry.proteinG.toInt()} · C ${entry.carbG.toInt()} · F ${entry.fatG.toInt()} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${entry.kcal.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreenDeep,
                )
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "Remove",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BrandGreen.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = BrandGreenDeep,
        )
    }
}

// ---------------------------------------------------------------------------
// Daily total chip
// ---------------------------------------------------------------------------

@Composable
private fun TotalChip(kcal: Double) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(colors = listOf(BrandGreen, BrandGreenDeep)),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${kcal.toInt()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                " kcal",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}
