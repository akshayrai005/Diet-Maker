package com.nutriai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide feedback for saving something (an exercise set, a meal...): a "Saving..." card while the request runs - which also
 * blocks the screen so a second tap cannot save a duplicate - and a Google Pay-style animated tick when it is done.
 * Any view model can call [start] / [done]; [LogFeedbackHost] (placed once at the app root) draws it.
 */
object LogFeedback {
    enum class Phase { IDLE, SAVING, SUCCESS }

    data class State(val phase: Phase = Phase.IDLE, val label: String = "", val ticket: Int = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while a save is running: callers must ignore further taps (prevents the double / triple logging). */
    val busy: Boolean get() = _state.value.phase == Phase.SAVING

    fun start(label: String = "Saving...") {
        _state.value = State(Phase.SAVING, label, _state.value.ticket + 1)
    }

    /** [success] shows the tick with [label]; a failure just clears the overlay so the caller can show its own error. */
    fun done(success: Boolean, label: String = "Logged") {
        val t = _state.value.ticket + 1
        _state.value = if (success) State(Phase.SUCCESS, label, t) else State(Phase.IDLE, "", t)
    }

    fun clear() {
        _state.value = State(Phase.IDLE, "", _state.value.ticket + 1)
    }
}

/** The confirmation tick follows the app theme (it used to be a fixed green). */
private val TickGreen: Color get() = com.nutriai.ui.theme.AppPalette.primary

@Composable
fun LogFeedbackHost() {
    val s by LogFeedback.state.collectAsState()
    if (s.phase == LogFeedback.Phase.IDLE) return
    val haptic = LocalHapticFeedback.current

    // The success tick dismisses itself; "saving" also gives up after 60 s so a dead request can never block the app.
    LaunchedEffect(s.ticket, s.phase) {
        when (s.phase) {
            LogFeedback.Phase.SUCCESS -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(1500)
                LogFeedback.clear()
            }
            LogFeedback.Phase.SAVING -> {
                delay(60_000)
                LogFeedback.clear()
            }
            else -> Unit
        }
    }

    // A full-screen scrim swallows taps while saving/celebrating.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (s.phase == LogFeedback.Phase.SAVING) 0.35f else 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier.semantics { contentDescription = if (s.phase == LogFeedback.Phase.SUCCESS) "${s.label}, saved" else "Saving" },
        ) {
            Column(Modifier.padding(horizontal = 40.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.phase == LogFeedback.Phase.SUCCESS) AnimatedTick(key = s.ticket) else CircularProgressIndicator(Modifier.size(64.dp), color = TickGreen, strokeWidth = 5.dp)
                Text(
                    if (s.phase == LogFeedback.Phase.SUCCESS) s.label else s.label.ifBlank { "Saving..." },
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1B1F23),
                )
            }
        }
    }
}

/** Green circle that pops in, then the check mark draws itself - the same feel as a payment confirmation. */
@Composable
private fun AnimatedTick(key: Int) {
    val pop = remember(key) { Animatable(0f) }
    val draw = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        draw.animateTo(1f, tween(380))
    }
    Box(
        Modifier.size(96.dp).scale(pop.value.coerceAtLeast(0.01f)).clip(CircleShape).background(TickGreen),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(52.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.12f, h * 0.55f)
                lineTo(w * 0.40f, h * 0.82f)
                lineTo(w * 0.90f, h * 0.20f)
            }
            val measure = PathMeasure().also { it.setPath(path, false) }
            val shown = Path()
            measure.getSegment(0f, measure.length * draw.value, shown, true)
            drawPath(shown, Color.White, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
