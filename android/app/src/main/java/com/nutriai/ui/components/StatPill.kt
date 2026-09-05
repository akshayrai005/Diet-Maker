package com.nutriai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutriai.ui.theme.Radius

@Composable
fun StatPill(
    label: String,
    value: String,
    unit: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.15f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
    }
}
