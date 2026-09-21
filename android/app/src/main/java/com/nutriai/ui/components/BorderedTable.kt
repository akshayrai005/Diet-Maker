package com.nutriai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF1B1F23)

/**
 * A colourful table with real columns and rows: a rounded card with a solid [accent] title bar, a tinted column-header
 * row, alternating tinted body rows and soft cell dividers. A first column made only of a short number (1, 2, 3...) is
 * drawn as a round badge. Columns are sized by [weights].
 */
@Composable
fun BorderedTable(
    headers: List<String>,
    rows: List<List<String>>,
    weights: List<Float>,
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = Color(0xFF5C6BC0),
    startExpanded: Boolean = false,
    /** Column indexes whose text (and header) is centred, both ways. */
    centeredColumns: Set<Int> = emptySet(),
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val divider = accent.copy(alpha = 0.28f)

    @Composable
    fun tableRow(cells: List<String>, index: Int, header: Boolean) {
        val bg = when {
            header -> accent.copy(alpha = 0.20f)
            index % 2 == 0 -> Color.White
            else -> accent.copy(alpha = 0.07f)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { i, text ->
                Box(
                    Modifier
                        .weight(weights[i])
                        .fillMaxHeight()
                        .background(bg)
                        .border(0.5.dp, divider)
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    contentAlignment = if (header || i in centeredColumns) Alignment.Center else Alignment.TopStart,
                ) {
                    val isBadge = !header && i == 0 && text.length <= 2 && text.all { it.isDigit() }
                    when {
                        isBadge -> Box(Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp).clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                        }
                        header -> Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = accent.copy(red = accent.red * 0.7f, green = accent.green * 0.7f, blue = accent.blue * 0.7f))
                        else -> Text(text, style = MaterialTheme.typography.bodySmall, textAlign = if (i in centeredColumns) TextAlign.Center else TextAlign.Start, color = Ink)
                    }
                }
            }
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().shadow(3.dp, shape).clip(shape).border(1.5.dp, accent, shape).background(Color.White)) {
        if (title != null) {
            // Tap the coloured bar to fold the section away or open it again.
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(red = (accent.red + 0.25f).coerceAtMost(1f), green = (accent.green + 0.25f).coerceAtMost(1f), blue = (accent.blue + 0.25f).coerceAtMost(1f)))))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = "$title, ${if (expanded) "expanded" else "collapsed"}. Tap to ${if (expanded) "collapse" else "expand"}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 44.dp))
                Box(Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.28f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(if (expanded) "▲" else "▼", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        AnimatedVisibility(visible = expanded || title == null) {
            Column {
                tableRow(headers, 0, header = true)
                rows.forEachIndexed { i, r -> tableRow(r, i, header = false) }
            }
        }
    }
}
