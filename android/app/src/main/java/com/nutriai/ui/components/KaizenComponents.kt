package com.nutriai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriai.ui.theme.ComponentHeight
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors

// ---------------------------------------------------------------------------
// Glass Card — elevated white card with subtle shadow
// ---------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Box(Modifier.padding(Spacing.lg)) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Feature Card — bold colored background card
// ---------------------------------------------------------------------------

@Composable
fun FeatureCard(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 18.sp)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Section Header with emoji
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, emoji: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (emoji != null) {
                Text(emoji, fontSize = 20.sp)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        action?.invoke()
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ComponentHeight.buttonLarge),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ComponentHeight.touchTarget),
        shape = RoundedCornerShape(Radius.md),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KaizenIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    M3IconButton(onClick = onClick, modifier = modifier.size(ComponentHeight.touchTarget)) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

// ---------------------------------------------------------------------------
// ListRow
// ---------------------------------------------------------------------------

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.listRow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------
// MetricBlock
// ---------------------------------------------------------------------------

@Composable
fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            unit?.let { "$value $it" } ?: value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// ProgressBar — rounded pill
// ---------------------------------------------------------------------------

@Composable
fun KaizenProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackAlpha: Float = 0.12f,
    height: Dp = 8.dp,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated by animateFloatAsState(
        targetValue = if (shown) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "progress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        color = color,
        trackColor = color.copy(alpha = trackAlpha),
        strokeCap = StrokeCap.Round,
    )
}

// ---------------------------------------------------------------------------
// StatusIndicator — pill badge
// ---------------------------------------------------------------------------

enum class Status { Positive, Caution, Critical, Information }

@Composable
fun StatusIndicator(text: String, status: Status, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.kaizenColors
    val color = when (status) {
        Status.Positive -> tokens.positive
        Status.Caution -> tokens.caution
        Status.Critical -> tokens.critical
        Status.Information -> tokens.information
    }
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Icon Badge — rounded icon with colored bg
// ---------------------------------------------------------------------------

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

// ---------------------------------------------------------------------------
// Emoji Badge — circular emoji container
// ---------------------------------------------------------------------------

@Composable
fun EmojiBadge(
    emoji: String,
    modifier: Modifier = Modifier,
    bgColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    size: Dp = 44.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(14.dp)).background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.45f).sp)
    }
}

// ---------------------------------------------------------------------------
// Empty / error / loading states
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    emoji: String = "📭",
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        } else {
            Text(emoji, fontSize = 48.sp)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        action?.invoke()
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.kaizenColors
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("😵", fontSize = 48.sp)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = tokens.critical, textAlign = TextAlign.Center)
        onRetry?.let { retry -> TextAction("Retry", retry) }
    }
}

@Composable
fun LoadingRow(label: String = "Loading…", modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
