package com.nutriai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val TableLine = Color(0xFF5C6B7A)
private val TableHeader = Color(0xFF37474F)

/**
 * A plain spreadsheet-style table: dark header row with white text, white cells, a dark border around every cell,
 * columns sized by [weights]. Used wherever the app lists steps, ingredients or facts.
 */
@Composable
fun BorderedTable(headers: List<String>, rows: List<List<String>>, weights: List<Float>, modifier: Modifier = Modifier) {
    @Composable
    fun tableRow(cells: List<String>, header: Boolean) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { i, text ->
                Box(
                    Modifier
                        .weight(weights[i])
                        .fillMaxHeight()
                        .background(if (header) TableHeader else Color.White)
                        .border(0.8.dp, TableLine)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Text(
                        text,
                        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                        color = if (header) Color.White else Color(0xFF1B1F23),
                    )
                }
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        tableRow(headers, header = true)
        rows.forEach { tableRow(it, header = false) }
    }
}
