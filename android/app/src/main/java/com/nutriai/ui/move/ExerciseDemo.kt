package com.nutriai.ui.move

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

/**
 * Animated GIF demonstration for an exercise, so the user can see the movement without leaving the
 * app. The GIF comes from the free ExerciseGymGifsDB (via jsDelivr CDN), mapped in [ExerciseDemoMap].
 * Falls back to the bundled offline muscle diagram when there's no demo for the exercise, or if the
 * GIF fails to load (offline). Tapping opens a larger looping view.
 */
@Composable
fun ExerciseDemo(
    name: String,
    muscleGroup: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
) {
    val url = remember(name) { ExerciseDemoMap.gifUrl(name) }
    var failed by remember(name) { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    if (url == null || failed) {
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
        GifImage(url = url, onError = { failed = true })
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
                    ) { GifImage(url = url, onError = { failed = true }) }
                    Text(
                        "Looping demo of the movement. Free community GIF set (ExerciseGymGifsDB).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** Loads an animated GIF with a decoder-enabled Coil ImageLoader; reports load failures to fall back. */
@Composable
private fun GifImage(url: String, onError: () -> Unit) {
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
        imageLoader = loader,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(),
        onState = { state -> if (state is AsyncImagePainter.State.Error) onError() },
    )
}
