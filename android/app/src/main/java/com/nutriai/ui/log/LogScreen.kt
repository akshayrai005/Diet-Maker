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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.EmojiBadge

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
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
    ) {
        // 1. Header — compact
        item {
            Text("📝 log food", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.today.isNotEmpty()) Text("✅ ${state.today.size} logged today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 2. Meal-slot chips — inline, no card wrapper
        item {
            SlotChipRow(slots = viewModel.slots, selected = state.slot, onSelect = { viewModel.onSlot(it) })
        }

        // 3. Search field — compact
        item {
            SearchField(query = state.query, onQuery = { viewModel.onQuery(it) }, onSearch = { viewModel.search(state.query) })
        }

        // 3b. Snap a meal (AI photo logging) — compact row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { snapMeal() },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                    enabled = !state.analyzing,
                ) {
                    if (!state.analyzing) Icon(Icons.Filled.PhotoCamera, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (state.analyzing) "Analyzing…" else "📸 Snap a meal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("🖼️ Photo", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3c. AI-detected items from the photo
        if (state.photoItems.isNotEmpty()) {
            item { SectionHeader(title = "Detected in photo", emoji = "🤖") }
            items(state.photoItems, key = { it.name + it.grams }) { it2 ->
                DetectedItemCard(it2, onAdd = { viewModel.logVisionItem(it2) })
            }
        }

        // 3d. Recent + saved — compact inline chips
        if (state.recents.isNotEmpty()) {
            item {
                SectionHeader(title = "Recent", emoji = "🔄")
                QuickRow(names = state.recents.map { it.name }) { idx ->
                    val r = state.recents[idx]; pendingQty = PendingQty(r.name, r.per100g.kcal) { g -> viewModel.logRecent(r, g) }
                }
            }
        }
        item {
            SectionHeader(title = "Saved", emoji = "⭐")
            QuickRow(names = state.saved.map { it.name }, trailingLabel = "+ Custom", onTrailing = { showCustom = true }) { idx ->
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
            item { SectionHeader(title = "Search results", emoji = "🔍") }
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
                SectionHeader(title = "Today's log", emoji = "📝") {
                    TotalChip(kcal = totalKcal)
                }
            }
            item {
                GlassCard {
                    Column {
                        state.today.forEachIndexed { index, entry ->
                            LogEntryCard(entry, onDelete = { viewModel.delete(entry.id) })
                            if (index != state.today.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
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
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        slots.forEach { slot ->
            FilterChip(
                selected = slot == selected,
                onClick = { onSelect(slot) },
                label = { Text(slot.replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Search field
// ---------------------------------------------------------------------------

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("🔍 Search foods (local + USDA)") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { startVoice() }) {
                Icon(Icons.Filled.Mic, contentDescription = "Search by voice")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
    )
}

// ---------------------------------------------------------------------------
// Message banner
// ---------------------------------------------------------------------------

@Composable
private fun MessageBanner(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = com.nutriai.ui.theme.CardGreenLight),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreen.copy(alpha = 0.3f)),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("✅", style = MaterialTheme.typography.labelLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = BrandGreenDeep)
        }
    }
}

// ---------------------------------------------------------------------------
// Result card
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(food: FoodDto, onAdd: () -> Unit, onFavorite: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("🔥 ${food.kcal.toInt()} kcal · P ${food.proteinG.toInt()} / C ${food.carbG.toInt()} / F ${food.fatG.toInt()} per 100g",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = BrandAmber,
                modifier = Modifier.size(20.dp).clickable { onFavorite() })
            Button(onClick = onAdd, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) { Text("Add", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

private data class PendingQty(val name: String, val kcalPer100: Double, val onLog: (Double) -> Unit)

@Composable
private fun DetectedItemCard(item: VisionFoodItem, onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, KaizenLavender.copy(alpha = 0.2f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("≈ ${item.grams.toInt()} g · 🔥 ${(item.per100g.kcal * item.grams / 100).toInt()} kcal",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) { Text("Add", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun QuickRow(
    names: List<String>,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    onPick: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n -> QuickChip(n) { onPick(i) } }
        if (trailingLabel != null && onTrailing != null) QuickChip(trailingLabel, accent = true) { onTrailing() }
    }
}

@Composable
private fun QuickChip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (accent) {
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary,
                labelColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            androidx.compose.material3.AssistChipDefaults.assistChipColors()
        },
    )
}

@Composable
private fun GenericQtyDialog(pq: PendingQty, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var g by remember(pq) { mutableStateOf("150") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("🍽️ How much ${pq.name}?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = g, onValueChange = { g = it.filter { c -> c.isDigit() } },
                    label = { Text("Grams") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val grams = g.toDoubleOrNull() ?: 0.0
                Text("🔥 = ${(pq.kcalPer100 * grams / 100).toInt()} kcal", color = BrandGreenDeep, fontWeight = FontWeight.Bold)
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
        title = { Text("🌟 Create a food", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Per 100 g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(kcal, { kcal = it.filter { c -> c.isDigit() } }, label = { Text("🔥 Calories") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
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
// Today's log entry card
// ---------------------------------------------------------------------------

@Composable
private fun LogEntryCard(entry: FoodLogEntry, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji = "🍽️", bgColor = NutritionColor)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.foodName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${entry.mealSlot.replaceFirstChar { it.uppercase() }} · ${entry.grams.toInt()} g · P ${entry.proteinG.toInt()} C ${entry.carbG.toInt()} F ${entry.fatG.toInt()} g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "🔥 ${entry.kcal.toInt()} kcal",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = BrandGreenDeep,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove ${entry.foodName}", modifier = Modifier.size(18.dp), tint = KaizenCoral)
        }
    }
}

// ---------------------------------------------------------------------------
// Daily total chip
// ---------------------------------------------------------------------------

@Composable
private fun TotalChip(kcal: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("🔥", style = MaterialTheme.typography.titleMedium)
        Text(
            "${kcal.toInt()}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrandGreenDeep,
        )
        Text(
            "kcal",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
