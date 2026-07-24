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

            m.targets.forEach { t ->
                val pct = (t.pct ?: 0).coerceIn(0, 100)
                val barColor = if (t.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.label, style = MaterialTheme.typography.labelMedium)
                        Text("${t.pct ?: 0}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = barColor)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(barColor))
                    }
                }
            }

            m.tips.forEach { tip ->
                Text("• $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(m.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
