package com.nutriai.ui.move

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
        // Card = tiny still (~19 KB, instant) with the looping GIF fading in on top once downloaded.
        // Only cards on screen are composed (lazy grid), so nothing is fetched for off-screen rows.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(thumbUrl(url)).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
            onState = { st -> if (st is AsyncImagePainter.State.Error) failed = true },
        )
        GifImage(url = url, onError = { }, showSpinner = false)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Close") } },
            title = { Text(name, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) { GifImage(url = url, onError = { failed = true }) }
                    val guide = remember(name) { ExerciseGuide.forName(name, muscleGroup) }
                    val secondary = remember(name) { ExerciseMetaDb.forName(name)?.secondary.orEmpty() }
                    GuideTable(
                        headers = listOf("Main muscle", "Also works"),
                        rows = listOf(listOf(muscleGroup?.replaceFirstChar { it.uppercase() } ?: "-", secondary.takeIf { it.isNotEmpty() }?.joinToString(", ") { m -> m.replace('-', ' ') } ?: "-")),
                        weights = listOf(0.4f, 0.6f),
                    )
                    GuideTable(
                        headers = listOf("Step", "How to perform"),
                        rows = guide.steps.mapIndexed { i, step -> listOf("${i + 1}", step) },
                        weights = listOf(0.16f, 0.84f),
                    )
                    GuideTable(
                        headers = listOf("#", "Common mistakes"),
                        rows = guide.mistakes.mapIndexed { i, m -> listOf("${i + 1}", m) },
                        weights = listOf(0.16f, 0.84f),
                    )
                    GuideTable(
                        headers = listOf("Safety"),
                        rows = listOf(listOf(guide.safety)),
                        weights = listOf(1f),
                    )
                    Text(
                        "General guidance, not medical advice. Demo GIFs: free community set (ExerciseGymGifsDB).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** The dataset's `main` branch ships a small WebP still next to every GIF (same path, `.thumb.webp`). */
private fun thumbUrl(gifUrl: String): String =
    gifUrl.replace("@v1.1.0/", "@main/").removeSuffix(".gif") + ".thumb.webp"

/** Loads an animated GIF with a decoder-enabled Coil ImageLoader; reports load failures to fall back. */
@Composable
private fun GifImage(url: String, onError: () -> Unit, showSpinner: Boolean = true) {
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }
    var loading by remember(url) { mutableStateOf(true) }
    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            imageLoader = loader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
                if (state is AsyncImagePainter.State.Error) onError()
            },
        )
        if (loading && showSpinner) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** A bordered table: shaded header row, every cell boxed, columns sized by [weights]. */
@Composable
private fun GuideTable(headers: List<String>, rows: List<List<String>>, weights: List<Float>) {
    val line = Color(0xFF5C6B7A)
    @Composable
    fun tableRow(cells: List<String>, header: Boolean) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { i, text ->
                Box(
                    Modifier
                        .weight(weights[i])
                        .fillMaxHeight()
                        .background(if (header) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .border(0.8.dp, line)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Text(
                        text,
                        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        tableRow(headers, header = true)
        rows.forEach { tableRow(it, header = false) }
    }
}
