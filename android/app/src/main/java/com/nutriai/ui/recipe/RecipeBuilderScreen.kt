package com.nutriai.ui.recipe

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🍳 My Recipes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
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
                Card(Modifier.fillMaxWidth(), shape = Sharp, colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.1f))) {
                    Text(msg, Modifier.padding(Spacing.sm), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = BrandGreen)
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
            Text(
                "per 100g · ${(recipe.kcal * factor).toInt()} kcal · P ${(recipe.proteinG * factor).toInt()}g · C ${(recipe.carbG * factor).toInt()}g · F ${(recipe.fatG * factor).toInt()}g",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = NutritionColor,
            )
            OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).height(36.dp), shape = Sharp) {
                Text("🍽️ Log a portion", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
    val factor = if (recipe.totalGrams > 0) 100.0 / recipe.totalGrams else 0.0
    val previewGrams = if (mode == "percent") (recipe.totalGrams * (percent.toDoubleOrNull() ?: 0.0)) / 100 else grams.toDoubleOrNull() ?: 0.0
    val previewKcal = (recipe.kcal * factor * previewGrams / 100).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🍽️ Log · ${recipe.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "percent", onClick = { mode = "percent" }, label = { Text("% of batch") })
                    FilterChip(selected = mode == "grams", onClick = { mode = "grams" }, label = { Text("Exact grams") })
                }
                if (mode == "percent") {
                    OutlinedTextField(
                        value = percent,
                        onValueChange = { v -> percent = v.filter { it.isDigit() } },
                        label = { Text("% eaten (e.g. 40)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = Sharp,
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
                    )
                }
                Text("≈ ${previewGrams.toInt()} g · ~$previewKcal kcal", style = MaterialTheme.typography.labelSmall, color = NutritionColor, fontWeight = FontWeight.Bold)
                Text("Meal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    slots.forEach { s ->
                        FilterChip(
                            selected = slot == s,
                            onClick = { slot = s },
                            label = { Text(s.take(4), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mode == "percent") onConfirm(slot, percent.toDoubleOrNull(), null)
                    else onConfirm(slot, null, grams.toDoubleOrNull())
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                shape = Sharp,
            ) { Text("✅ Log it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class DraftIngredient(val food: FoodDto, var grams: String)

@Composable
private fun CreateRecipeDialog(onDismiss: () -> Unit, onSave: (String, List<RecipeIngredientInput>) -> Unit, viewModel: RecipeViewModel = hiltViewModel()) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodDto>>(emptyList()) }
    val ingredients = remember { androidx.compose.runtime.mutableStateListOf<DraftIngredient>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val totalKcal = ingredients.sumOf { it.food.kcal * (it.grams.toDoubleOrNull() ?: 0.0) / 100 }
    val totalGrams = ingredients.sumOf { it.grams.toDoubleOrNull() ?: 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🍳 New Recipe", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Recipe name (e.g. Paneer Bhurji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Sharp,
                )
                HorizontalDivider()
                Text("Add ingredients (incl. oil/ghee/butter)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { q ->
                        query = q
                        // Only real catalog foods (source == "local") - a recipe ingredient must
                        // resolve to an actual Food row server-side so its nutrition can be summed;
                        // USDA search results have no local row and would fail to save.
                        scope.launch { results = if (q.length >= 2) viewModel.search(q).filter { it.source != "usda" } else emptyList() }
                    },
                    label = { Text("Search a food (try \"oil\", \"paneer\"...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Sharp,
                )
                results.take(5).forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            ingredients.add(DraftIngredient(f, "100"))
                            query = ""; results = emptyList()
                        }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text("+ add", style = MaterialTheme.typography.labelSmall, color = BrandGreen, fontWeight = FontWeight.Bold)
                    }
                }
                ingredients.forEachIndexed { i, ing ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ing.food.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        OutlinedTextField(
                            value = ing.grams,
                            onValueChange = { v -> ingredients[i] = ing.copy(grams = v.filter { it.isDigit() }) },
                            label = { Text("g", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            shape = Sharp,
                        )
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = KaizenCoral, modifier = Modifier.clickable { ingredients.removeAt(i) }.padding(4.dp).height(16.dp))
                    }
                }
                if (ingredients.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Total: ${totalGrams.toInt()} g · ~${totalKcal.toInt()} kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NutritionColor)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val input = ingredients.mapNotNull { ing -> ing.grams.toDoubleOrNull()?.takeIf { it > 0 }?.let { RecipeIngredientInput(ing.food.id, it) } }
                    if (name.isNotBlank() && input.isNotEmpty()) onSave(name.trim(), input)
                },
                enabled = name.isNotBlank() && ingredients.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                shape = Sharp,
            ) { Text("💾 Save Recipe") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
