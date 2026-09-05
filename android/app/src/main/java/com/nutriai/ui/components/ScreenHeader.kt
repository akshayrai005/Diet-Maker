package com.nutriai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutriai.ui.theme.Spacing

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gradient line indicator
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )

        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        action?.invoke()
    }
}
