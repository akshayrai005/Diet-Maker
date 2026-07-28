package com.nutriai.ui.move

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Animated movement demo for an exercise. When we have a demo in [ExerciseDemoMap], the two stills
 * (start → end position) are cross-faded on a loop into a simple animation showing the movement, so
 * the user never has to leave the app to look it up. Falls back to the bundled muscle-group diagram
 * when there's no demo, or if the images fail to load (offline). Tapping opens a larger view.
 */
@Composable
fun ExerciseDemo(
    name: String,
    muscleGroup: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
) {
    val frames = remember(name) { ExerciseDemoMap.framesFor(name) }
    var failed by remember(name) { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    if (frames == null || failed) {
        // No demo (or failed to load) → the offline diagram, exactly as before.
        ExerciseIllustration(muscleGroup = muscleGroup, modifier = modifier, sizeDp = sizeDp)
        return
    }

    Box(
        modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable { showDialog = true }
            .semantics { contentDescription = "$name demonstration, tap to enlarge" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedFrames(frames = frames, onError = { failed = true })
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Close") } },
            title = { Text(name, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) { AnimatedFrames(frames = frames, onError = { failed = true }) }
                    Text(
                        "Start → end of the movement, looped. Public-domain demo (free-exercise-db).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** Cross-fades between the start and end stills on a ~900ms loop → a lightweight 2-frame animation. */
@Composable
private fun AnimatedFrames(frames: Pair<String, String>, onError: () -> Unit) {
    var showEnd by remember { mutableStateOf(false) }
    LaunchedEffect(frames) {
        while (true) {
            delay(900)
            showEnd = !showEnd
        }
    }
    val url = if (showEnd) frames.second else frames.first
    AsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(),
        onState = { state -> if (state is AsyncImagePainter.State.Error) onError() },
    )
}
