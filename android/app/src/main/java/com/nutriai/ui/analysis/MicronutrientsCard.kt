package com.nutriai.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutriai.data.remote.dto.Micronutrients

/**
 * Vitamins & minerals vs RDA, estimated server-side from today's logged foods. Read-only; shows
 * targets-only text until enough is logged, then per-nutrient bars + food-source tips for gaps.
 */
@Composable
fun MicronutrientsCard(m: Micronutrients, modifier: Modifier = Modifier) {
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.label, style = MaterialTheme.typography.labelMedium)
                        Text("${t.pct}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = barColor)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
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
