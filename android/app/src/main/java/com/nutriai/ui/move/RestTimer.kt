package com.nutriai.ui.move

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Reusable rest timer. Preset 60 / 90 / 120s + a custom entry, a live countdown,
// and haptic feedback on finish. Used both as a standalone card on the Exercise
// tab and inside the set-log dialog. Self-contained state so any caller just
// drops it in.
// ---------------------------------------------------------------------------

private val PRESETS = listOf(60, 90, 120)

/** Fires device haptics: a Compose long-press tick plus a stronger two-pulse vibration. */
private fun vibrateFinish(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() != true) return
    // minSdk 26, so VibrationEffect is always available.
    runCatching {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 130, 220), -1))
    }
}

private fun mmss(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

/**
 * Compact rest-timer control.
 *
 * @param compact when true, renders without the surrounding card (for embedding in a dialog).
 */
@Composable
fun RestTimer(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var selected by remember { mutableIntStateOf(90) }
    var customText by remember { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var runToken by remember { mutableIntStateOf(0) }

    fun startFor(seconds: Int) {
        if (seconds <= 0) return
        selected = seconds
        remaining = seconds
        running = true
        runToken++
    }

    // One countdown pass per (re)start / resume. Exits early if paused (running=false).
    LaunchedEffect(runToken) {
        if (!running) return@LaunchedEffect
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (running && remaining <= 0) {
            running = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vibrateFinish(context)
        }
    }

    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Rest timer",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            // Preset durations.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { secs ->
                    FilterChip(
                        selected = selected == secs && (running || remaining == 0),
                        onClick = { startFor(secs) },
                        label = { Text("${secs}s") },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Start ${secs} second rest timer" },
                    )
                }
            }

            // Custom duration.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Custom (s)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Custom rest duration in seconds" },
                )
                TextButton(
                    onClick = { customText.toIntOrNull()?.let { startFor(it) } },
                    enabled = customText.toIntOrNull()?.let { it > 0 } == true,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Start custom rest timer" },
                ) { Text("Start") }
            }

            // Countdown + controls. liveRegion announces each tick / the finish to TalkBack.
            val stateLabel = when {
                running -> "Resting, ${mmss(remaining)} remaining"
                remaining > 0 -> "Paused, ${mmss(remaining)} remaining"
                else -> "Rest timer ready"
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (remaining > 0 || running) mmss(remaining) else "Ready",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = stateLabel
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (running) {
                        TextButton(
                            onClick = { running = false },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Pause rest timer" },
                        ) { Text("Pause") }
                    } else if (remaining > 0) {
                        TextButton(
                            onClick = { running = true; runToken++ },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Resume rest timer" },
                        ) { Text("Resume") }
                    }
                    if (remaining > 0) {
                        TextButton(
                            onClick = { running = false; remaining = 0 },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Reset rest timer" },
                        ) { Text("Reset") }
                    }
                }
            }
        }
    }

    if (compact) {
        body()
    } else {
        Card(
            modifier.fillMaxWidth().semantics { contentDescription = "Rest timer between sets" },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) { body() }
    }
}
