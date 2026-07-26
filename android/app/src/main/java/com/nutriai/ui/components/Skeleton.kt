package com.nutriai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight shimmer skeletons — pure Compose, no external library. Shown while a screen's first
 * load is in flight so the layout doesn't pop in from blank. Announced to TalkBack as "Loading".
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.6f), highlight.copy(alpha = 0.9f), base.copy(alpha = 0.6f)),
        start = androidx.compose.ui.geometry.Offset(x - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(x, 0f),
    )
}

/** A single shimmering block. Use for a title, line or thumbnail placeholder. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp,
    widthFraction: Float = 1f,
) {
    Column(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
            .semantics { contentDescription = "Loading" },
    ) {}
}

/** A card-shaped skeleton with a few lines — a generic stand-in for a loading content card. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Loading" },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(height = 20.dp, widthFraction = 0.5f)
            repeat(lines.coerceAtLeast(1)) { i ->
                SkeletonBlock(height = 14.dp, widthFraction = if (i == lines - 1) 0.7f else 1f)
            }
        }
    }
}

/** A vertical stack of [count] skeleton cards, for list-style loading states. */
@Composable
fun SkeletonList(count: Int = 3, lines: Int = 2, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { SkeletonCard(lines = lines) }
    }
}
