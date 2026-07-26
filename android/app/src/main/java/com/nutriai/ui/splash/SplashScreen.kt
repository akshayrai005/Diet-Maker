package com.nutriai.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.R
import com.nutriai.data.AppRepository
import com.nutriai.ui.theme.BrandGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Warms the free (cold-starting) backend the instant the app launches, so it's already up by the
 * time the splash ends. `ready` flips true as soon as the server answers (or the ping fails fast).
 */
@HiltViewModel
class SplashViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            repository.warmup()
            // If already signed in, prefetch the dashboard while the splash is up so Home opens
            // straight away instead of showing the "waking up" state.
            runCatching {
                if (repository.isLoggedIn.first()) repository.dashboard()
            }
            _ready.value = true
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit, viewModel: SplashViewModel = hiltViewModel()) {
    var visible by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    val fade by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(600), label = "fade")

    // Show the brand for a minimum beat, then hold (with the loader) until the backend is awake,
    // capped so we never hang. The warmup already kicked off the cold start in the ViewModel.
    LaunchedEffect(Unit) {
        visible = true
        val minMs = 1300L
        val maxMs = 20_000L
        val step = 200L
        var waited = 0L
        while (waited < maxMs && (waited < minMs || !viewModel.ready.value)) {
            if (waited >= 1500L) slow = true
            kotlinx.coroutines.delay(step)
            waited += step
        }
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Image(
            painter = painterResource(R.drawable.splash_kaizen),
            contentDescription = "Kaizen - Small Habits. Big Results.",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(fade),
        )

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                Modifier.size(34.dp).alpha(fade),
                color = BrandGreen,
                strokeWidth = 3.dp,
            )
            AnimatedVisibility(visible = slow) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x14000000)).padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Waking things up…",
                        color = Color(0xFF4A574F),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
