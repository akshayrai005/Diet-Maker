package com.nutriai.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Badge
import com.nutriai.data.remote.dto.Gamification
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BadgesState(
    val loading: Boolean = true,
    val gamification: Gamification? = null,
    val error: String? = null,
)

@HiltViewModel
class BadgesViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BadgesState())
    val state: StateFlow<BadgesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.gamification()
            _state.value = if (r.isSuccess) {
                BadgesState(loading = false, gamification = r.getOrNull())
            } else {
                BadgesState(loading = false, error = r.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }
}

@Composable
fun BadgesScreen(
    modifier: Modifier = Modifier,
    viewModel: BadgesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = BrandGreen) }
        }

        state.error != null -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        state.gamification != null -> {
            val g = state.gamification!!
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BadgesHero(earned = g.earnedCount, total = g.total)
                }

                if (g.badges.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Your badges",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                }

                items(g.badges) { badge ->
                    BadgeCard(badge)
                }
            }
        }

        else -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No achievements yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BadgesHero(earned: Int, total: Int) {
    val fraction = if (total > 0) earned.toFloat() / total.toFloat() else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandGreenLight, BrandGreen, BrandGreenDeep),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$earned",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        " / $total badges earned 🏆",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f),
                )
            }
        }
    }
}

private data class BadgeVisual(val emoji: String, val accent: Color)

/** Distinct emoji + accent per badge so they look varied (not identical gray locks). */
private fun badgeVisual(badge: Badge): BadgeVisual {
    val t = (badge.title + " " + badge.code).lowercase()
    return when {
        "streak" in t || "day" in t -> BadgeVisual("🔥", Color(0xFFF97316))
        "week" in t -> BadgeVisual("🗓️", Color(0xFF6366F1))
        "consist" in t || "king" in t -> BadgeVisual("👑", Color(0xFFEAB308))
        "hydrat" in t || "water" in t -> BadgeVisual("💧", Color(0xFF0EA5E9))
        "protein" in t -> BadgeVisual("💪", Color(0xFFEF4444))
        "track" in t || "progress" in t -> BadgeVisual("📈", Color(0xFF10B981))
        "kilo" in t || "weight" in t -> BadgeVisual("⚖️", Color(0xFF8B5CF6))
        "bite" in t || "first" in t -> BadgeVisual("🍽️", BrandGreen)
        else -> BadgeVisual("🏆", BrandGreen)
    }
}

@Composable
private fun BadgeCard(badge: Badge) {
    val earned = badge.earned
    val v = badgeVisual(badge)
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (earned) 3.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (earned) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        border = if (earned) androidx.compose.foundation.BorderStroke(1.dp, v.accent.copy(alpha = 0.35f)) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Medallion: soft accent ring + gradient disc (earned) or muted disc + lock (locked).
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(v.accent.copy(alpha = if (earned) 0.16f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).then(
                            if (earned) Modifier.background(Brush.linearGradient(listOf(v.accent, v.accent.copy(alpha = 0.72f))))
                            else Modifier.background(v.accent.copy(alpha = 0.18f)),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(v.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alpha(if (earned) 1f else 0.5f))
                    }
                }
                if (earned) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface).padding(2.dp)
                            .clip(CircleShape).background(v.accent),
                        contentAlignment = Alignment.Center,
                    ) { Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) { Text("🔒", style = MaterialTheme.typography.labelSmall) }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    badge.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    badge.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Text(
                if (earned) "EARNED" else "LOCKED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (earned) v.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (earned) v.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
