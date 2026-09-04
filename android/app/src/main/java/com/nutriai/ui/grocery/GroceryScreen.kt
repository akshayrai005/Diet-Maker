package com.nutriai.ui.grocery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Grocery
import com.nutriai.data.remote.dto.GroceryCategory
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.ListRow
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroceryState(
    val loading: Boolean = true,
    val grocery: Grocery? = null,
    val error: String? = null,
)

@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GroceryState())
    val state: StateFlow<GroceryState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.grocery()
            _state.value = if (r.isSuccess) {
                GroceryState(loading = false, grocery = r.getOrNull())
            } else {
                GroceryState(loading = false, error = "Generate a plan first (Plan tab)")
            }
        }
    }
}

/** Fast shopping checklist (Phase 4, Change 09) - compact rows grouped by category, not a bordered
 * spreadsheet or a card gallery. */
@Composable
fun GroceryScreen(
    modifier: Modifier = Modifier,
    viewModel: GroceryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val grocery = state.grocery
    // Session-scoped "ticked off while shopping" set, keyed by ingredient name.
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item { GroceryHeader(totalItems = grocery?.totalItems ?: 0, weeklyKcal = grocery?.weeklyKcal ?: 0, targetWeeklyKcal = grocery?.targetWeeklyKcal) }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }
        }
        state.error?.let { err ->
            item {
                EmptyState(
                    title = "Nothing to shop yet",
                    message = err,
                    icon = Icons.Filled.ShoppingCart,
                    action = { Button(onClick = { viewModel.load() }, shape = RoundedCornerShape(Radius.md), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("Refresh") } },
                )
            }
        }

        grocery?.categories?.forEach { cat ->
            item { CategoryChecklist(cat, checked) }
        }
    }
}

@Composable
private fun GroceryHeader(totalItems: Int, weeklyKcal: Int, targetWeeklyKcal: Int?) {
    Column {
        ScreenHeader("Grocery")
        Text(
            "$totalItems ingredients for your Sun-Sat plan" + (if (weeklyKcal > 0) " · ${"%,d".format(weeklyKcal)} kcal this week" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryChecklist(
    cat: GroceryCategory,
    checked: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(cat.category)
        cat.items.forEachIndexed { index, line ->
            val u = if (line.unit == "pcs") "pcs" else "g"
            val isChecked = checked[line.name] == true
            ListRow(
                title = line.name,
                subtitle = "${line.qty} $u · ${line.perServing} $u × ${line.meals}" + if (line.kcal > 0) " · ${line.kcal} kcal" else "",
                leading = {
                    Icon(
                        if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isChecked) "Checked off" else "Not checked",
                        tint = if (isChecked) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = { checked[line.name] = !isChecked },
            )
            if (index != cat.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
    }
}
