package com.nutriai.ui.recipe

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.FoodDto
import com.nutriai.data.remote.dto.RecipeIngredientInput
import com.nutriai.data.remote.dto.UserRecipeDto
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.CardGreenLight
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

private val fieldColors: @Composable () -> androidx.compose.material3.TextFieldColors = {
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BrandGreen,
        focusedLabelColor = BrandGreen,
        cursorColor = BrandGreen,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
    )
}

data class RecipeState(
    val loading: Boolean = true,
    val recipes: List<UserRecipeDto> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class RecipeViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecipeState())
    val state: StateFlow<RecipeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val recipes = repository.userRecipes().getOrDefault(emptyList())
            _state.value = _state.value.copy(loading = false, recipes = recipes)
        }
    }

    fun create(name: String, ingredients: List<RecipeIngredientInput>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = repository.createRecipe(name, ingredients)
            if (r.isSuccess) { load(); onDone(true) } else { _state.value = _state.value.copy(message = "Couldn't save recipe"); onDone(false) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteRecipe(id); load() }
    }

    /** Used when neither the local catalog nor USDA has this ingredient (e.g. raw "bajra flour" -
     * only finished dishes are seeded). Saves a real Food row so it's searchable from now on. */
    suspend fun aiEstimate(name: String): FoodDto? = repository.aiEstimateFood(name).getOrNull()

    fun logPortion(id: String, mealSlot: String, percent: Double?, grams: Double?, name: String) {
        viewModelScope.launch {
            val r = repository.logRecipe(id, mealSlot, percent, grams)
            _state.value = _state.value.copy(message = if (r.isSuccess) "✓ Logged $name" else "Couldn't log - try again")
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    suspend fun search(q: String): List<FoodDto> = repository.searchFoods(q).getOrDefault(emptyList())
}

@Composable
fun RecipeBuilderScreen(modifier: Modifier = Modifier, viewModel: RecipeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var logTarget by remember { mutableStateOf<UserRecipeDto?>(null) }

    if (showCreate) {
        CreateRecipeDialog(
            onDismiss = { showCreate = false },
            onSave = { name, ingredients -> viewModel.create(name, ingredients) { ok -> if (ok) showCreate = false } },
        )
    }
    logTarget?.let { recipe ->
        LogRecipeDialog(recipe = recipe, onDismiss = { logTarget = null }, onConfirm = { slot, pct, g ->
            viewModel.logPortion(recipe.id, slot, pct, g, recipe.name)
            logTarget = null
        })
    }

    Column(modifier.fillMaxSize()) {
        // Gradient header - matches every other sub-screen in the app (Discipline, Badges, Family…).
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))))
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        ) {
            Text("🍳 My Recipes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
        ) {
            item {
                Text(
                    "Build a homemade dish from real ingredients (incl. cooking oil/ghee), save it, then log any portion you actually ate.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = Sharp,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) { Text("+ New Recipe", fontWeight = FontWeight.Bold) }
            }

            state.message?.let { msg ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = CardGreenLight)) {
                        Text(msg, Modifier.padding(Spacing.md), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = BrandGreen)
                    }
                    LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.clearMessage() }
                }
            }

            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandGreen) } }
            } else if (state.recipes.isEmpty()) {
                item {
                    Text(
                        "No recipes yet - tap + New Recipe to build your first one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            } else {
                items(state.recipes, key = { it.id }) { r -> RecipeCard(r, onLog = { logTarget = r }, onDelete = { viewModel.delete(r.id) }) }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: UserRecipeDto, onLog: () -> Unit, onDelete: () -> Unit) {
    val factor = if (recipe.totalGrams > 0) 100.0 / recipe.totalGrams else 0.0
    Card(
        Modifier.fillMaxWidth(),
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete, modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete recipe", tint = KaizenCoral, modifier = Modifier.height(18.dp))
                }
            }
            Text(
                "${recipe.totalGrams.toInt()} g total · ${recipe.ingredients.joinToString(", ") { it.name }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RecipeStat("🔥", "${(recipe.kcal * factor).toInt()}", "kcal", KaizenCoral, Modifier.weight(1f))
                RecipeStat("💪", "${(recipe.proteinG * factor).toInt()}g", "protein", NutritionColor, Modifier.weight(1f))
                RecipeStat("🌾", "${(recipe.carbG * factor).toInt()}g", "carbs", BrandAmber, Modifier.weight(1f))
                RecipeStat("🥑", "${(recipe.fatG * factor).toInt()}g", "fat", KaizenLavender, Modifier.weight(1f))
            }
            Text("per 100 g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(36.dp), shape = Sharp) {
                Text("🍽️ Log a portion", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecipeStat(emoji: String, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.08f)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-screen sheet, styled like every other flow in the app - not a cramped default AlertDialog. */
@Composable
private fun RecipeDialogScaffold(title: String, emoji: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))))
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                    Text("$emoji $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                content()
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

@Composable
private fun LogRecipeDialog(recipe: UserRecipeDto, onDismiss: () -> Unit, onConfirm: (slot: String, percent: Double?, grams: Double?) -> Unit) {
    var mode by remember { mutableStateOf("percent") } // "percent" | "grams"
    var percent by remember { mutableStateOf("50") }
    var grams by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf("lunch") }
    val slots = listOf("breakfast", "midmorning", "lunch", "eveningsnack", "dinner", "bedtime")
    val slotEmojis = mapOf("breakfast" to "🍳", "midmorning" to "☕", "lunch" to "🍛", "eveningsnack" to "🍪", "dinner" to "🍝", "bedtime" to "🌙")
    val factor = if (recipe.totalGrams > 0) 100.0 / recipe.totalGrams else 0.0
    val previewGrams = if (mode == "percent") (recipe.totalGrams * (percent.toDoubleOrNull() ?: 0.0)) / 100 else grams.toDoubleOrNull() ?: 0.0
    val previewKcal = (recipe.kcal * factor * previewGrams / 100).toInt()

    RecipeDialogScaffold(title = "Log · ${recipe.name}", emoji = "🍽️", onDismiss = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = CardGreenLight)) {
            Column(Modifier.padding(Spacing.md)) {
                Text("How much did you eat?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = mode == "percent", onClick = { mode = "percent" }, label = { Text("% of batch") }, shape = Sharp,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandGreen, selectedLabelColor = Color.White),
                    )
                    FilterChip(
                        selected = mode == "grams", onClick = { mode = "grams" }, label = { Text("Exact grams") }, shape = Sharp,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandGreen, selectedLabelColor = Color.White),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                if (mode == "percent") {
                    OutlinedTextField(
                        value = percent,
                        onValueChange = { v -> percent = v.filter { it.isDigit() } },
                        label = { Text("% eaten (e.g. 40)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = Sharp,
                        colors = fieldColors(),
                    )
                } else {
                    OutlinedTextField(
                        value = grams,
                        onValueChange = { v -> grams = v.filter { it.isDigit() } },
                        label = { Text("Grams eaten") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = Sharp,
                        colors = fieldColors(),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text("≈ ${previewGrams.toInt()} g · ~$previewKcal kcal", style = MaterialTheme.typography.bodyMedium, color = BrandGreen, fontWeight = FontWeight.Bold)
            }
        }

        Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(Spacing.md)) {
                Text("Meal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    slots.forEach { s ->
                        FilterChip(
                            selected = slot == s,
                            onClick = { slot = s },
                            label = { Text("${slotEmojis[s]} ${s.take(4)}", style = MaterialTheme.typography.labelSmall) },
                            shape = Sharp,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NutritionColor, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (mode == "percent") onConfirm(slot, percent.toDoubleOrNull(), null)
                else onConfirm(slot, null, grams.toDoubleOrNull())
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            shape = Sharp,
        ) { Text("✅ Log it", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

private data class DraftIngredient(val food: FoodDto, var grams: String, var parts: String = "1")

@Composable
private fun CreateRecipeDialog(onDismiss: () -> Unit, onSave: (String, List<RecipeIngredientInput>) -> Unit, viewModel: RecipeViewModel = hiltViewModel()) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodDto>>(emptyList()) }
    var aiEstimating by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val ingredients = remember { androidx.compose.runtime.mutableStateListOf<DraftIngredient>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Ratio mode: enter parts (e.g. 2:1:1:1) + a total batch weight instead of typing each
    // ingredient's grams by hand - grams are computed proportionally from the parts.
    var ratioMode by remember { mutableStateOf(false) }
    var batchWeight by remember { mutableStateOf("500") }
    val totalParts = ingredients.sumOf { it.parts.toDoubleOrNull() ?: 0.0 }
    fun gramsFor(ing: DraftIngredient): Double =
        if (ratioMode) {
            val bw = batchWeight.toDoubleOrNull() ?: 0.0
            val p = ing.parts.toDoubleOrNull() ?: 0.0
            if (totalParts > 0) bw * p / totalParts else 0.0
        } else {
            ing.grams.toDoubleOrNull() ?: 0.0
        }

    val totalKcal = ingredients.sumOf { it.food.kcal * gramsFor(it) / 100 }
    val totalGrams = ingredients.sumOf { gramsFor(it) }

    RecipeDialogScaffold(title = "New Recipe", emoji = "🍳", onDismiss = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Recipe name (e.g. Paneer Bhurji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Sharp,
                    colors = fieldColors(),
                )
            }
        }

        Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = CardGreenLight), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("⚖️ How do you want to enter quantities?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = !ratioMode, onClick = { ratioMode = false }, label = { Text("Grams per item") }, shape = Sharp,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandGreen, selectedLabelColor = Color.White),
                    )
                    FilterChip(
                        selected = ratioMode, onClick = { ratioMode = true }, label = { Text("Ratio (2:1:1:1)") }, shape = Sharp,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandGreen, selectedLabelColor = Color.White),
                    )
                }
                if (ratioMode) {
                    OutlinedTextField(
                        value = batchWeight,
                        onValueChange = { v -> batchWeight = v.filter { it.isDigit() } },
                        label = { Text("Total batch weight (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = Sharp,
                        colors = fieldColors(),
                    )
                    Text(
                        "Enter each ingredient's ratio part below (e.g. wheat 2, bajra 1, ragi 1, jowar 1) - grams are split automatically from the batch weight.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("🧂 Ingredients (incl. oil/ghee/butter)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { q ->
                        query = q
                        aiError = null
                        // Only real catalog foods (source == "local") - a recipe ingredient must
                        // resolve to an actual Food row server-side so its nutrition can be summed;
                        // USDA search results have no local row and would fail to save.
                        scope.launch { results = if (q.length >= 2) viewModel.search(q).filter { it.source != "usda" } else emptyList() }
                    },
                    label = { Text("Search a food (try \"oil\", \"paneer\"...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Sharp,
                    colors = fieldColors(),
                )
                results.take(5).forEach { f ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                ingredients.add(DraftIngredient(f, "100"))
                                query = ""; results = emptyList()
                            }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("+ add", style = MaterialTheme.typography.labelSmall, color = BrandGreen, fontWeight = FontWeight.Bold)
                    }
                }
                // Not in the catalog (e.g. raw "bajra flour" - only finished dishes are seeded) -
                // ask the AI to estimate it and save it as a real, searchable Food from now on.
                if (query.length >= 2 && results.isEmpty() && !aiEstimating) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandAmber.copy(alpha = 0.1f))
                            .clickable {
                                val q = query
                                scope.launch {
                                    aiEstimating = true
                                    val f = viewModel.aiEstimate(q)
                                    aiEstimating = false
                                    if (f != null) {
                                        ingredients.add(DraftIngredient(f, "100"))
                                        query = ""; results = emptyList()
                                    } else {
                                        aiError = "Couldn't estimate \"$q\" - try a more specific name."
                                    }
                                }
                            }
                            .padding(Spacing.sm),
                    ) {
                        Text("🤖 Can't find it? Ask AI for \"$query\"", style = MaterialTheme.typography.labelSmall, color = BrandAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (aiEstimating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp, color = BrandAmber)
                        Text("Estimating nutrition…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                aiError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = KaizenCoral)
                }
            }
        }

        if (ingredients.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("📋 Your mix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ingredients.forEachIndexed { i, ing ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(ing.food.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                            if (ratioMode) {
                                OutlinedTextField(
                                    value = ing.parts,
                                    onValueChange = { v -> ingredients[i] = ing.copy(parts = v.filter { it.isDigit() }) },
                                    label = { Text("parts", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(78.dp),
                                    shape = Sharp,
                                    colors = fieldColors(),
                                )
                                Text("≈${gramsFor(ing).toInt()}g", style = MaterialTheme.typography.labelSmall, color = NutritionColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                            } else {
                                OutlinedTextField(
                                    value = ing.grams,
                                    onValueChange = { v -> ingredients[i] = ing.copy(grams = v.filter { it.isDigit() }) },
                                    label = { Text("g", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(84.dp),
                                    shape = Sharp,
                                    colors = fieldColors(),
                                )
                            }
                            IconButton(onClick = { ingredients.removeAt(i) }, modifier = Modifier.height(32.dp).width(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = KaizenCoral, modifier = Modifier.height(16.dp))
                            }
                        }
                        if (i < ingredients.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                    HorizontalDivider()
                    Text("Total: ${totalGrams.toInt()} g · ~${totalKcal.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
                }
            }
        }

        Button(
            onClick = {
                val input = ingredients.mapNotNull { ing -> gramsFor(ing).takeIf { it > 0 }?.let { RecipeIngredientInput(ing.food.id, it) } }
                if (name.isNotBlank() && input.isNotEmpty()) onSave(name.trim(), input)
            },
            enabled = name.isNotBlank() && ingredients.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            shape = Sharp,
        ) { Text("💾 Save Recipe", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
