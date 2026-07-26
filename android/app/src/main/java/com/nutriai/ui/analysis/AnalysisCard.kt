package com.nutriai.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutriai.data.remote.dto.CoachBrief
import com.nutriai.data.remote.dto.RatingResult

/**
 * "Your Analysis" - the server-computed health rating (ring + per-pillar bars) plus today's
 * deterministic coach brief. Read-only; no number is computed on the client.
 */
@Composable
fun AnalysisCard(rating: RatingResult?, coach: CoachBrief?, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            coach?.greeting?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            rating?.let { r ->
                if (!r.hasData) {
                    // Brand-new user: encourage, don't shame with a 0 / red F.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your health rating", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            r.biggestLever.message.ifBlank { "Start by logging your meals - your rating builds as you go." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RatingRing(overall = r.overall, grade = r.grade)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Health rating", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            r.biggestLever.message.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Per-pillar bars - full width so the value never crosses the card edge.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        r.pillars.forEach { p -> PillarBar(label = p.label, score = p.score.toInt()) }
                    }
                }
            }

            coach?.let { c ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BriefLine("🍽️", c.dietFocus)
                    BriefLine("🏋️", c.moveOrRest)
                    BriefLine("🧘", c.mindNudge)
                    c.disciplineActions.forEach { BriefLine("✅", it) }
                    BriefLine("🔥", c.streak)
                    BriefLine("📈", c.prediction)
                }
                c.disclaimer.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RatingRing(overall: Int, grade: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = when (grade.uppercase()) {
        "A", "B" -> MaterialTheme.colorScheme.primary
        "C" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        Modifier.size(96.dp).semantics { contentDescription = "Health rating $overall out of 100, grade $grade" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                color = accent, startAngle = -90f, sweepAngle = 360f * (overall.coerceIn(0, 100) / 100f),
                useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$overall", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(grade, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PillarBar(label: String, score: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.width(84.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Box(
            Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier.fillMaxWidth(score.coerceIn(0, 100) / 100f).height(10.dp)
                    .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            "$score",
            Modifier.width(30.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BriefLine(icon: String, text: String) {
    if (text.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
