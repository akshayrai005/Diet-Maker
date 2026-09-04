package com.nutriai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutriai.data.remote.dto.FoodDto
import com.nutriai.ui.theme.BrandGreen

/**
 * Replaces AlertDialog for every "how much of this food?" prompt. Shows smart portion-unit chips
 * first (eggs by count, dal by katori, milk by glass, whey by scoop…) so most foods need one tap, with
 * a grams field underneath for anything else or fine adjustment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantityBottomSheet(food: FoodDto, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var qtyText by remember(food) { mutableStateOf(food.typicalServingG.toInt().toString()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("How much ${food.name}?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${food.kcal.toInt()} kcal per 100 g", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            PortionUnitPicker(food) { grams -> qtyText = grams.toInt().toString() }

            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                label = { Text("Grams") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            val g = qtyText.toDoubleOrNull() ?: 0.0
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(vertical = 2.dp),
            ) {
                Text(
                    "= ${(food.kcal * g / 100).toInt()} kcal · ${(food.proteinG * g / 100).toInt()} g protein",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen,
                )
            }

            Button(
                onClick = { g.takeIf { it > 0 }?.let(onConfirm) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) { Text("Add") }
        }
    }
}

/**
 * Smart portion-unit chips (spec Section 8): for non-gram foods, offer the natural unit (eggs by
 * count, dal/curd by katori, milk by glass, whey by scoop) instead of forcing grams. Each chip
 * resolves to grams via the food's unitGrams so logging stays accurate.
 */
@Composable
fun PortionUnitPicker(food: FoodDto, onPick: (Double) -> Unit) {
    val unit = food.portionUnit.lowercase()
    if (unit == "grams" || unit.isBlank()) return
    val base = if (food.unitGrams > 0) food.unitGrams else food.typicalServingG
    val options: List<Pair<String, Double>> = when (unit) {
        "count" -> listOf("1", "2", "3", "4", "6").map { it to (it.toDouble() * base) }
        "slice" -> listOf("1", "2", "3", "4").map { "$it slice" to (it.toDouble() * base) }
        "scoop" -> listOf("1 scoop" to base, "2 scoops" to 2 * base)
        "cup" -> listOf("1 cup" to base, "2 cups" to 2 * base)
        "glass" -> listOf("½ glass" to 0.5 * base, "1 glass" to base, "2 glasses" to 2 * base)
        "katori", "bowl" -> listOf("Small" to base, "Medium" to base * (250.0 / 150.0), "Large" to base * (350.0 / 150.0))
        else -> emptyList()
    }
    if (options.isEmpty()) return
    val noun = if (unit == "count") "how many" else "portion"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tap $noun", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, grams) ->
                AssistChip(onClick = { onPick(grams) }, label = { Text(label) })
            }
        }
    }
}
