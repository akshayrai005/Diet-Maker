package com.nutriai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nutriai.ui.theme.SpectrumBrush

/**
 * A button filled with the app's blended orange - pink - violet - indigo gradient. Same call shape as Material's Button
 * ([colors] and [elevation] are accepted so call sites can be switched by renaming, and ignored), but it is exactly the size
 * you give it: no invisible 48 dp touch area, so a 28 dp "Add" button stays 28 dp.
 */
@Composable
fun SpectrumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: ButtonElevation? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = 1.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(SpectrumBrush)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) { content() }
    }
}
