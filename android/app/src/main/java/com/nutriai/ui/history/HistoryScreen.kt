package com.nutriai.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.data.AppRepository
import com.nutriai.data.health.HealthConnectManager
import com.nutriai.data.remote.dto.ExerciseLogDto
import com.nutriai.data.remote.dto.FoodLogEntry
import com.nutriai.data.remote.dto.HistoryDay
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

data class DayDetail(
    val date: String,
    val food: List<FoodLogEntry> = emptyList(),
    val exercise: List<ExerciseLogDto> = emptyList(),
    val kcal: Int = 0,
    val proteinG: Int = 0,
    val waterMl: Int = 0,
    val steps: Long = 0,
    val loading: Boolean = false,
)

data class HistoryState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val daysByDate: Map<String, HistoryDay> = emptyMap(),
    val stepsByDate: Map<String, Long> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val detail: DayDetail? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: AppRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        loadMonth(YearMonth.now())
        selectDate(LocalDate.now())
    }

    /** Loads aggregate data (kcal/protein/water + steps) for the last ~35 days so the calendar can dot active days. */
    fun loadMonth(month: YearMonth) {
        _state.value = _state.value.copy(loading = true, month = month)
        viewModelScope.launch {
            val history = repository.history(35).getOrNull()
            val byDate = history?.days?.associateBy { it.date } ?: emptyMap()
            val steps = runCatching { healthConnect.readDailySteps(35) }.getOrDefault(emptyMap())
            _state.value = _state.value.copy(loading = false, daysByDate = byDate, stepsByDate = steps)
        }
    }

    fun selectDate(date: LocalDate) {
        _state.value = _state.value.copy(selectedDate = date, detail = DayDetail(date = date.toString(), loading = true))
        viewModelScope.launch {
            val dateStr = date.toString()
            val food = repository.foodLogs(dateStr).getOrDefault(emptyList())
            val exercise = repository.exerciseLogs(dateStr).getOrDefault(emptyList())
            val agg = _state.value.daysByDate[dateStr]
            val steps = _state.value.stepsByDate[dateStr] ?: 0L
            _state.value = _state.value.copy(
                detail = DayDetail(
                    date = dateStr,
                    food = food,
                    exercise = exercise,
                    kcal = agg?.kcal ?: food.sumOf { it.kcal }.toInt(),
                    proteinG = agg?.proteinG ?: food.sumOf { it.proteinG }.toInt(),
                    waterMl = agg?.waterMl ?: 0,
                    steps = steps,
                    loading = false,
                ),
            )
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadMonth(YearMonth.now()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Spacer(Modifier.height(Spacing.md))
            Text("📜 Your History", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }

        item {
            CalendarCard(
                month = state.month,
                selectedDate = state.selectedDate,
                daysByDate = state.daysByDate,
                onPrevMonth = { viewModel.loadMonth(state.month.minusMonths(1)) },
                onNextMonth = { viewModel.loadMonth(state.month.plusMonths(1)) },
                onSelectDate = { viewModel.selectDate(it) },
            )
        }

        item {
            val detail = state.detail
            if (detail == null || detail.loading) {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.xl),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = BrandGreen) }
            } else {
                DayDetailPanel(detail)
            }
        }

        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    daysByDate: Map<String, HistoryDay>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevMonth) { Text("‹", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNextMonth) { Text("›", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.values().forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))

            val firstOfMonth = month.atDay(1)
            val leadingBlanks = (firstOfMonth.dayOfWeek.value % 7) // Sunday=0..Saturday=6 (value: Mon=1..Sun=7)
            val totalDays = month.lengthOfMonth()
            val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..totalDays).map { month.atDay(it) }
            val today = LocalDate.now()

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height(((cells.size / 7 + 1) * 44).dp),
            ) {
                items(cells) { date ->
                    if (date == null) {
                        Box(Modifier.aspectRatio(1f))
                    } else {
                        val hasData = daysByDate[date.toString()]?.let { it.kcal > 0 || it.workout || it.waterMl > 0 } ?: false
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val isFuture = date.isAfter(today)
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> BrandGreen
                                        isToday -> BrandGreen.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable(enabled = !isFuture) { onSelectDate(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (hasData) {
                                    Box(
                                        Modifier
                                            .padding(top = 1.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else BrandGreen)
                                            .height(4.dp)
                                            .aspectRatio(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailPanel(detail: DayDetail) {
    val date = LocalDate.parse(detail.date)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )

        // Summary row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryStat(Modifier.weight(1f), "🔥", "${detail.kcal}", "kcal", KaizenCoral)
            SummaryStat(Modifier.weight(1f), "💪", "${detail.proteinG}", "g protein", NutritionColor)
            SummaryStat(Modifier.weight(1f), "💧", "${detail.waterMl}", "ml", HydrationColor)
            SummaryStat(Modifier.weight(1f), "🚶", if (detail.steps > 0) "%,d".format(detail.steps) else "-", "steps", MovementColor)
        }

        // Food log
        Card(
            shape = Sharp,
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Text("🍲 Food", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (detail.food.isEmpty()) {
                    Text(
                        "No meals logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                } else {
                    detail.food.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(f.foodName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "${f.mealSlot} · ${f.grams.toInt()}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("${f.kcal.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Exercise log
        Card(
            shape = Sharp,
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Text("🏋️ Exercise", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (detail.exercise.isEmpty()) {
                    Text(
                        "No workouts logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                } else {
                    val totalKcal = detail.exercise.sumOf { it.kcal ?: 0 }
                    Text(
                        "$totalKcal kcal burned · ${detail.exercise.size} sets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    detail.exercise.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(e.exerciseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    listOfNotNull(
                                        e.sets?.let { "$it sets" },
                                        e.reps?.let { "$it reps" },
                                        e.weightKg?.let { "${it}kg" },
                                        e.durationMin?.let { "${it}min" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("${e.kcal ?: 0} kcal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(modifier: Modifier, emoji: String, value: String, label: String, color: Color) {
    Card(
        modifier = modifier,
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
