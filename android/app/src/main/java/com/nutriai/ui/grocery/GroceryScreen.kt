package com.nutriai.ui.grocery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Grocery
import com.nutriai.data.remote.dto.GroceryCategory
import com.nutriai.data.remote.dto.GroceryLine
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.CardLavenderLight
import com.nutriai.ui.theme.CardGreenLight
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

private val CAT_COLORS = listOf(
    BrandGreen, KaizenBlue, BrandAmber, KaizenCoral, KaizenLavender, NutritionColor,
)
private val CAT_EMOJIS = mapOf(
    "grains" to "🌾", "flours" to "🌾", "pulses" to "🫘", "lentils" to "🫘",
    "vegetables" to "🥬", "fruits" to "🍎", "dairy" to "🥛", "milk" to "🥛",
    "eggs" to "🥚", "meat" to "🍗", "chicken" to "🍗", "fish" to "🐟",
    "oils" to "🫒", "fats" to "🫒", "nuts" to "🥜", "seeds" to "🥜",
    "spices" to "🌶️", "condiments" to "🌶️", "sugar" to "🍬", "sweeteners" to "🍬",
    "beverages" to "☕", "protein" to "💪", "supplements" to "💊",
)

private fun emojiForCategory(cat: String): String {
    val lower = cat.lowercase()
    return CAT_EMOJIS.entries.firstOrNull { lower.contains(it.key) }?.value ?: "🛒"
}

private val COMMON_SUBSTITUTES = mapOf(
    "brown rice" to listOf("Quinoa", "Broken wheat", "Millets"),
    "rice" to listOf("Quinoa", "Broken wheat", "Millets"),
    "poha" to listOf("Oats", "Muesli", "Quinoa flakes"),
    "quinoa" to listOf("Brown rice", "Millets", "Amaranth"),
    "wheat flour" to listOf("Ragi flour", "Bajra flour", "Jowar flour"),
    "bajra flour" to listOf("Ragi flour", "Jowar flour", "Wheat flour"),
    "semolina" to listOf("Oats", "Broken wheat", "Poha"),
    "chicken" to listOf("Paneer", "Tofu", "Soy chunks"),
    "paneer" to listOf("Tofu", "Cottage cheese", "Soy chunks"),
    "eggs" to listOf("Paneer", "Tofu", "Greek yogurt"),
    "milk" to listOf("Soy milk", "Almond milk", "Oat milk"),
    "curd" to listOf("Greek yogurt", "Buttermilk", "Coconut yogurt"),
    "butter" to listOf("Ghee", "Olive oil", "Coconut oil"),
    "ghee" to listOf("Olive oil", "Coconut oil", "Butter"),
    "sugar" to listOf("Jaggery", "Honey", "Stevia"),
    "peanuts" to listOf("Almonds", "Walnuts", "Sunflower seeds"),
    "oil" to listOf("Olive oil", "Coconut oil", "Mustard oil"),
)

private fun substitutesFor(name: String): List<String> {
    val lower = name.lowercase()
    return COMMON_SUBSTITUTES.entries.firstOrNull { lower.contains(it.key) }?.value ?: emptyList()
}

data class GroceryState(
    val loading: Boolean = true,
    val grocery: Grocery? = null,
    val error: String? = null,
)

@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GroceryState())
    val state: StateFlow<GroceryState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.grocery()
            _state.value = if (r.isSuccess) {
                GroceryState(loading = false, grocery = r.getOrNull())
            } else {
                GroceryState(loading = false, error = "Generate a plan first (Plan tab)")
            }
        }
    }
}

@Composable
fun GroceryScreen(
    modifier: Modifier = Modifier,
    viewModel: GroceryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val grocery = state.grocery
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var detailItem by remember { mutableStateOf<GroceryLine?>(null) }

    detailItem?.let { item ->
        GroceryDetailDialog(item = item, onDismiss = { detailItem = null })
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        // Header
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🛒 Grocery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (grocery != null) {
                    val checkedCount = checked.count { it.value }
                    Text(
                        "$checkedCount/${grocery.totalItems}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (checkedCount == grocery.totalItems) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (grocery != null) {
                Text(
                    "${grocery.totalItems} items · ${"%,d".format(grocery.weeklyKcal)} kcal/week",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(24.dp))
                }
            }
        }
        state.error?.let { err ->
            item {
                EmptyState(
                    title = "Nothing to shop yet",
                    message = err,
                    icon = Icons.Filled.ShoppingCart,
                    action = { Button(onClick = { viewModel.load() }, shape = Sharp, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("Refresh") } },
                )
            }
        }

        grocery?.categories?.forEachIndexed { catIdx, cat ->
            val catColor = CAT_COLORS[catIdx % CAT_COLORS.size]
            val catEmoji = emojiForCategory(cat.category)

            // Category header card
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    colors = CardDefaults.cardColors(containerColor = catColor.copy(alpha = 0.1f)),
                    border = BorderStroke(1.5.dp, catColor.copy(alpha = 0.3f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(catEmoji, fontSize = 16.sp)
                            Text(cat.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = catColor)
                        }
                        Text("${cat.items.size} items", style = MaterialTheme.typography.labelSmall, color = catColor.copy(alpha = 0.7f))
                    }
                }
            }

            // Items as compact rows inside a card
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        cat.items.forEachIndexed { index, line ->
                            val isChecked = checked[line.name] == true
                            val subs = substitutesFor(line.name)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { detailItem = line }
                                    .padding(horizontal = Spacing.md, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Icon(
                                    if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isChecked) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp).clickable { checked[line.name] = !isChecked },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        line.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                    val u = if (line.unit == "pcs") "pcs" else "g"
                                    Text(
                                        "${line.qty} $u · ${line.perServing} $u × ${line.meals}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    )
                                }
                                if (line.kcal > 0) {
                                    Text("${line.kcal}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = catColor)
                                    Text("kcal", style = MaterialTheme.typography.labelSmall, color = catColor.copy(alpha = 0.6f), fontSize = 9.sp)
                                }
                                if (subs.isNotEmpty()) {
                                    Text("🔄", fontSize = 10.sp)
                                }
                            }
                            if (index != cat.items.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }

        // Bottom spacer for nav bar
        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun GroceryDetailDialog(item: GroceryLine, onDismiss: () -> Unit) {
    val subs = substitutesFor(item.name)
    val u = if (item.unit == "pcs") "pcs" else "g"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("🛒 ${item.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DetailRow("📦", "Total quantity", "${item.qty} $u")
                DetailRow("🍽️", "Per serving", "${item.perServing} $u")
                DetailRow("📅", "Meals/week", "${item.meals}")
                if (item.kcal > 0) DetailRow("🔥", "Calories", "${item.kcal} kcal")

                if (subs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("🔄 Substitutes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KaizenBlue)
                    subs.forEach { sub ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Sharp)
                                .background(KaizenBlue.copy(alpha = 0.08f))
                                .padding(horizontal = Spacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("→", color = KaizenBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = BrandGreen) }
        },
    )
}

@Composable
private fun DetailRow(emoji: String, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 12.sp)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
