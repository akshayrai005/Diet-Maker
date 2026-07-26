package com.nutriai.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutriai.data.remote.dto.Micronutrients
import com.nutriai.data.remote.dto.MicronutrientTarget

/** Educational "why it matters + good sources" per nutrient key. Qualitative copy, no numbers. */
private val NUTRIENT_INFO: Map<String, Pair<String, String>> = mapOf(
    "ironMg" to ("Carries oxygen in your blood; low iron causes tiredness and low focus." to "Spinach, rajma, dates, roasted chana, pumpkin seeds; pair with vitamin-C foods to absorb more."),
    "calciumMg" to ("Builds and maintains strong bones and teeth." to "Milk, curd, paneer, ragi, sesame (til), tofu."),
    "vitaminB12Mcg" to ("Keeps nerves and red blood cells healthy; common gap on plant-based diets." to "Milk, curd, paneer, eggs, fish; fortified cereals / nutritional yeast for vegans."),
    "vitaminDMcg" to ("Helps absorb calcium and supports mood and immunity." to "15–20 min sunlight, fortified milk, egg yolk, mushrooms, fatty fish."),
    "folateMcg" to ("Supports cell growth — especially important before/during pregnancy." to "Spinach & greens, chana, beetroot, oranges."),
    "potassiumMg" to ("Balances fluids and helps keep blood pressure healthy." to "Banana, coconut water, sweet potato, spinach."),
    "magnesiumMg" to ("Supports muscles, nerves, sleep and energy." to "Almonds, whole grains (bajra, oats), banana, dark chocolate."),
    "zincMg" to ("Supports immunity, healing and taste." to "Chana & legumes, pumpkin seeds, cashews, eggs."),
    "vitaminCMg" to ("Boosts immunity and helps your body absorb iron." to "Amla, guava, oranges/lemon, bell peppers."),
    "vitaminAMcg" to ("Supports vision, skin and immunity." to "Carrots, spinach & greens, mango/papaya, egg yolk."),
    "vitaminEMg" to ("An antioxidant that protects your cells." to "Almonds, peanuts, sunflower seeds, vegetable oils."),
    "vitaminKMcg" to ("Needed for healthy blood clotting and bones." to "Leafy greens like spinach, and other green vegetables."),
    "vitaminB1Mg" to ("Helps turn food into energy (thiamin)." to "Whole grains, legumes, peanuts, fortified cereals."),
    "vitaminB2Mg" to ("Supports energy and healthy skin (riboflavin)." to "Milk, curd, eggs, almonds, greens."),
    "vitaminB3Mg" to ("Supports energy metabolism (niacin)." to "Chicken, fish, peanuts, whole grains."),
    "vitaminB6Mg" to ("Supports brain and metabolism." to "Chickpeas, banana, potato, chicken."),
    "seleniumMcg" to ("An antioxidant that supports thyroid and immunity." to "Eggs, fish, whole grains, a couple of Brazil nuts."),
    "iodineMcg" to ("Essential for a healthy thyroid." to "Iodised salt, curd/milk, fish."),
    "phosphorusMg" to ("Works with calcium for bones and energy." to "Dairy, eggs, legumes, whole grains, nuts."),
    "copperMcg" to ("Helps form red blood cells and connective tissue." to "Chana, nuts, seeds, whole grains."),
    "manganeseMg" to ("Supports bone health and metabolism." to "Whole grains, nuts, legumes, leafy greens."),
)

/**
 * Vitamins & minerals vs RDA, estimated server-side from today's logged foods. Read-only; shows
 * targets-only text until enough is logged, then per-nutrient bars + food-source tips for gaps.
 */
@Composable
fun MicronutrientsCard(m: Micronutrients, modifier: Modifier = Modifier) {
    var info by remember { mutableStateOf<MicronutrientTarget?>(null) }
    info?.let { t ->
        val (why, sources) = NUTRIENT_INFO[t.key] ?: ("An essential vitamin or mineral your body needs." to "A varied, whole-food diet.")
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Got it") } },
            title = { Text(t.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.pct?.let { Text("You're at about $it% of your daily target from what you logged.", style = MaterialTheme.typography.bodyMedium) }
                    Text("Why it matters", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text(why, style = MaterialTheme.typography.bodySmall)
                    Text("Good sources", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text(sources, style = MaterialTheme.typography.bodySmall)
                    Text("Educational guidance — reference ranges vary; confirm concerns with a doctor.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vitamins & minerals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                m.coveragePct?.takeIf { m.available }?.let {
                    Text("$it% of RDA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            if (!m.available) {
                Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            // Only nutrients we can actually estimate from today's foods get a bar; the rest are
            // shown honestly as "no data yet" (never a fake 0% / deficiency).
            val tracked = m.targets.filter { it.pct != null }
            val noData = m.targets.filter { it.pct == null }

            tracked.forEach { t ->
                val pct = (t.pct ?: 0).coerceIn(0, 100)
                val barColor = if (t.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { info = t }
                        .semantics { contentDescription = "${t.label}, ${t.pct} percent of target. Tap for why it matters and good sources." }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.label}  ⓘ", style = MaterialTheme.typography.labelMedium)
                        Text("${t.pct}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = barColor)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics {}) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(barColor))
                    }
                }
            }

            m.tips.forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Deficiency → food fixes: for each low nutrient, concrete foods to eat more of.
            m.foodFixes.filter { it.foods.isNotEmpty() }.forEach { fix ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Low on ${fix.label} → try: ${fix.foods.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (fix.note.isNotBlank()) {
                        Text(fix.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (noData.isNotEmpty()) {
                Text(
                    "No data yet: ${noData.joinToString(", ") { it.label }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(m.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
