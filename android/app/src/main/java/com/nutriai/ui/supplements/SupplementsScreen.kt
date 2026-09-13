package com.nutriai.ui.supplements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.SuggestedSupplement
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

data class SupplementsState(
    val loading: Boolean = true,
    val items: List<SuggestedSupplement> = emptyList(),
)

@HiltViewModel
class SupplementsViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(SupplementsState())
    val state: StateFlow<SupplementsState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            val items = repository.supplements().getOrDefault(emptyList())
            _state.value = SupplementsState(loading = false, items = items)
        }
    }
}

private val CATEGORY_EMOJI = mapOf(
    "protein" to "🥤",
    "creatine" to "💪",
    "vitamin" to "☀️",
    "omega3" to "🐟",
    "electrolyte" to "⚡",
)

@Composable
fun SupplementsScreen(modifier: Modifier = Modifier, viewModel: SupplementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Text(
                "💊 Trusted, evidence-based options - not a store, not brand endorsements. Food comes first; these fill real gaps.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MovementColor) } }
        }

        if (!state.loading && state.items.isEmpty()) {
            item { EmptyState(title = "No supplements to show right now", emoji = "💊") }
        }

        // Recommended-for-you first, rest of the catalog after.
        val (recommended, rest) = state.items.partition { it.reason != null }

        if (recommended.isNotEmpty()) {
            item { Text("🎯 Recommended for you", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            items(recommended) { SupplementCard(it, highlighted = true) }
        }

        if (rest.isNotEmpty()) {
            item { Text("📦 Full catalog", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            items(rest) { SupplementCard(it, highlighted = false) }
        }
    }
}

@Composable
private fun SupplementCard(s: SuggestedSupplement, highlighted: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Sharp,
        elevation = CardDefaults.cardElevation(if (highlighted) 3.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${CATEGORY_EMOJI[s.category] ?: "💊"} ${s.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            s.reason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = KaizenLavender, fontWeight = FontWeight.Medium)
            }
            Text(s.whyTrusted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Typical dosage: ${s.typicalDosage}",
                style = MaterialTheme.typography.labelSmall,
                color = KaizenBlue,
            )
        }
    }
}
