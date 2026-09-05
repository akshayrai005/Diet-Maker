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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Badge
import com.nutriai.data.remote.dto.Gamification
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.KaizenProgressBar
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

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
                modifier = modifier.fillMaxSize().padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = BrandGreen) }
        }

        state.error != null -> {
            Box(
                modifier = modifier.fillMaxSize().padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        state.error!!,
                        color = KaizenCoral,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }

        state.gamification != null -> {
            val g = state.gamification!!
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(vertical = Spacing.lg),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BadgesHero(earned = g.earnedCount, total = g.total)
                }

                if (g.badges.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("🏆 Your Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                items(g.badges) { badge ->
                    BadgeCard(badge)
                }
            }
        }

        else -> {
            Box(
                modifier = modifier.fillMaxSize().padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "No achievements yet",
                    emoji = "🏅",
                    message = "Keep logging meals and staying active to earn badges!",
                )
            }
        }
    }
}

@Composable
private fun BadgesHero(earned: Int, total: Int) {
    val fraction = if (total > 0) earned.toFloat() / total.toFloat() else 0f
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("🌟 Progress", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "$earned",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandAmber,
                )
                Text(
                    "/ $total badges earned",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            KaizenProgressBar(
                progress = fraction.coerceIn(0f, 1f),
                color = BrandAmber,
                height = 8.dp,
            )
        }
    }
}

private data class BadgeVisual(val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color)

/** Distinct vector icon + accent per badge so they look varied - not emoji as the visual language. */
private fun badgeVisual(badge: Badge): BadgeVisual {
    val t = (badge.title + " " + badge.code).lowercase()
    return when {
        "streak" in t || "day" in t -> BadgeVisual(Icons.Filled.Whatshot, Color(0xFFF97316))
        "week" in t -> BadgeVisual(Icons.Filled.CalendarMonth, KaizenLavender)
        "consist" in t || "king" in t -> BadgeVisual(Icons.Filled.MilitaryTech, BrandAmber)
        "hydrat" in t || "water" in t -> BadgeVisual(Icons.Filled.LocalDrink, KaizenBlue)
        "protein" in t -> BadgeVisual(Icons.Filled.FitnessCenter, KaizenCoral)
        "track" in t || "progress" in t -> BadgeVisual(Icons.Filled.TrendingUp, BrandGreen)
        "kilo" in t || "weight" in t -> BadgeVisual(Icons.Filled.Scale, KaizenLavender)
        "bite" in t || "first" in t -> BadgeVisual(Icons.Filled.Restaurant, BrandGreen)
        else -> BadgeVisual(Icons.Filled.EmojiEvents, BrandGreen)
    }
}

@Composable
private fun BadgeCard(badge: Badge) {
    val earned = badge.earned
    val v = badgeVisual(badge)
    Card(
        modifier = Modifier.fillMaxWidth().height(190.dp),
        shape = Sharp,
        elevation = CardDefaults.cardElevation(defaultElevation = if (earned) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = Spacing.lg, horizontal = Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Medallion
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(v.accent.copy(alpha = if (earned) 0.20f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).then(
                            if (earned) Modifier.background(Brush.linearGradient(listOf(v.accent, v.accent.copy(alpha = 0.72f))))
                            else Modifier.background(v.accent.copy(alpha = 0.18f)),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            v.icon,
                            contentDescription = null,
                            tint = if (earned) Color.White else v.accent,
                            modifier = Modifier.size(24.dp).alpha(if (earned) 1f else 0.5f),
                        )
                    }
                }
                if (earned) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface).padding(2.dp)
                            .clip(CircleShape).background(v.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, contentDescription = "Earned", tint = Color.White, modifier = Modifier.size(14.dp)) }
                } else {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    badge.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (earned) v.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    badge.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                if (earned) "EARNED" else "LOCKED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (earned) v.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (earned) v.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
