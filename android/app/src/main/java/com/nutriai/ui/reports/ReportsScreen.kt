package com.nutriai.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import java.io.File
import com.nutriai.data.remote.dto.ReportDay
import com.nutriai.data.remote.dto.WeeklyReport
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.kaizenColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportsState(
    val loading: Boolean = true,
    val report: WeeklyReport? = null,
    val error: String? = null,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.weeklyReport()
            _state.value = if (r.isSuccess) {
                ReportsState(loading = false, report = r.getOrNull())
            } else {
                ReportsState(loading = false, error = r.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }

    /** Downloads the server-generated PDF and opens the Android share sheet. */
    fun sharePdf(context: Context, onError: (String) -> Unit) {
        viewModelScope.launch {
            val r = repository.reportPdfBytes()
            val bytes = r.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                onError(r.exceptionOrNull()?.message ?: "Could not download the PDF")
                return@launch
            }
            try {
                val file = File(context.cacheDir, "nutriai-weekly.pdf")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(intent, "Share weekly report")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                onError(e.message ?: "Could not open the PDF")
            }
        }
    }
}

@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showViewer by remember { mutableStateOf(false) }
    var downloadMode by remember { mutableStateOf(false) }

    if (showViewer) {
        androidx.activity.compose.BackHandler(enabled = true) { showViewer = false }
        ReportViewerScreen(onClose = { showViewer = false }, autoPrint = downloadMode)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.kaizenColors.pageBackground)
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            com.nutriai.ui.components.ScreenHeader(
                title = "📊 Weekly Report",
                subtitle = "Your health journey at a glance",
            )
        }

        state.report?.let { report ->
            item { HeroSummaryCard(report) }
            // Two actions in one row: open the beautiful report, or save it as a PDF.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    PrimaryButton(
                        text = "📥 Download",
                        onClick = { downloadMode = true; showViewer = true },
                        modifier = Modifier.weight(1f),
                        containerColor = BrandGreen,
                    )
                    PrimaryButton(
                        text = "👁️ View Report",
                        onClick = { downloadMode = false; showViewer = true },
                        modifier = Modifier.weight(1f),
                        containerColor = KaizenBlue,
                    )
                }
            }
        }

        if (state.loading) {
            item { ReportsLoadingCard() }
        }

        state.error?.let { err ->
            item { ReportsErrorCard(err, onRetry = viewModel::load) }
        }

        state.report?.let { report ->
            item { SectionHeader(title = "Daily Breakdown", emoji = "📊") }
            item { DaysCard(report.days) }
        }

        state.report?.let { report ->
            item {
                Text(
                    report.disclaimer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Gradient hero summary
// ---------------------------------------------------------------------------

@Composable
private fun HeroSummaryCard(report: WeeklyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xl),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("📋", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Health Report",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    HeroStat(
                        emoji = "🔥",
                        value = report.targets?.dailyKcal?.let { "${it.toInt()}" } ?: "-",
                        unit = "kcal target",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStat(
                        emoji = "💪",
                        value = report.targets?.proteinG?.let { "${it.toInt()}" } ?: "-",
                        unit = "g protein",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStat(
                        emoji = "✅",
                        value = report.adherencePct?.let { "${it.toInt()}%" } ?: "-",
                        unit = "adherence",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    HeroStat(
                        emoji = "📏",
                        value = report.bmi?.let { String.format("%.1f", it) } ?: "-",
                        unit = "BMI",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStat(
                        emoji = "⚖️",
                        value = report.latestWeightKg?.let { "$it" } ?: "-",
                        unit = "kg now",
                        modifier = Modifier.weight(1f),
                    )
                    HeroStat(
                        emoji = "📈",
                        value = report.weightDeltaKg?.let { d ->
                            val sign = if (d > 0) "+" else ""
                            "$sign$d"
                        } ?: "-",
                        unit = "kg change",
                        modifier = Modifier.weight(1f),
                    )
                }

                report.avgKcal?.let { avg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(Color.White.copy(alpha = 0.20f))
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text("⚡", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Averaging ${avg.toInt()} kcal / day this week",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, unit: String, modifier: Modifier = Modifier, emoji: String = "") {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (emoji.isNotEmpty()) {
            Text(emoji, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

// ---------------------------------------------------------------------------
// Days card
// ---------------------------------------------------------------------------

@Composable
private fun DaysCard(days: List<ReportDay>) {
    FeatureCard(
        emoji = "📅",
        title = "This Week",
        accentColor = KaizenBlue,
    ) {
        if (days.isEmpty()) {
            EmptyState(
                title = "No daily entries logged yet",
                emoji = "📝",
                message = "Start logging meals to see your daily breakdown.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                days.forEachIndexed { index, day ->
                    DayRow(day)
                    if (index < days.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.kaizenColors.divider),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: ReportDay) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            EmojiBadge(emoji = "📆", bgColor = KaizenBlue.copy(alpha = 0.12f), size = 36.dp)
            Text(
                day.date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "${day.kcal.toInt()} kcal",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = BrandGreen,
            )
            Text(
                "·  ${day.proteinG.toInt()}g protein",
                style = MaterialTheme.typography.bodyMedium,
                color = KaizenLavender,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Loading + error states
// ---------------------------------------------------------------------------

@Composable
private fun ReportsLoadingCard() {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            CircularProgressIndicator(color = BrandGreen)
            Text(
                "📊 Compiling your week...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportsErrorCard(message: String, onRetry: () -> Unit) {
    FeatureCard(
        emoji = "⚠️",
        title = "Report Unavailable",
        accentColor = KaizenCoral,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = "🔄 Try Again",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            containerColor = KaizenCoral,
        )
    }
}
