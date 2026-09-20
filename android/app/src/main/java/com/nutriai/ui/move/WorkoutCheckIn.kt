package com.nutriai.ui.move

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.ui.theme.SpectrumBrush
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Loads the last 30 days of training once and turns it into the balance report. */
@HiltViewModel
class FocusViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _report = MutableStateFlow<FocusBalance.Report?>(null)
    val report: StateFlow<FocusBalance.Report?> = _report

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            repository.recentExerciseLogs(30).getOrNull()?.let { _report.value = FocusBalance.compute(it) }
        }
    }
}

/** A rounded, bordered popup card with a gradient title - the same look as the other Kaizen popups. */
@Composable
private fun KaizenPopup(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
        ) {
            Text(
                title,
                Modifier.fillMaxWidth().background(SpectrumBrush).padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center,
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) SpectrumBrush else SolidColor(Color(0xFFCFD3DA)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Color(0xFF4A5060)) }
}

/**
 * Shown once per eating day when the app opens: "Ready to train?" then "What are you training?".
 * Choosing groups sends the user straight to the exercise picker on the Move tab.
 */
@Composable
fun WorkoutCheckInHost(onChose: () -> Unit, vm: FocusViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf(if (SessionStore.needsCheckIn(ctx)) 1 else 0) }
    val report by vm.report.collectAsState()
    when (stage) {
        1 -> KaizenPopup("🏋️ Ready to train today?", onDismiss = { stage = 0 }) {
            Text("Are you at the gym, or ready to exercise now?", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { SessionStore.answer(ctx, false); stage = 0 },
                    modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                ) { Text("Not today", fontWeight = FontWeight.Bold) }
                GradientButton("Yes, let's go", Modifier.weight(1f)) { stage = 2 }
            }
        }
        2 -> GroupPickerDialog(
            report = report,
            initial = emptySet(),
            title = "🎯 What are you training?",
            onDismiss = { stage = 0 },
            onConfirm = { groups ->
                SessionStore.saveGroups(ctx, groups)
                SessionStore.builderRequest.value = true
                stage = 0
                onChose()
            },
        )
    }
}

/** Multi-select of muscle groups, with the app's suggestion (from the last 2 weeks) starred at the top. */
@Composable
fun GroupPickerDialog(
    report: FocusBalance.Report?,
    initial: Set<TrainGroup>,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (List<TrainGroup>) -> Unit,
) {
    val suggestion = remember(report) { FocusBalance.suggest(report) }
    var picked by remember { mutableStateOf(initial) }
    KaizenPopup(title, onDismiss) {
        if (suggestion.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("⭐ Suggested from your last 2 weeks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                suggestion.forEach { g ->
                    Text("${g.emoji} ${g.label} - ${FocusBalance.reason(report, g)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TrainGroup.values().toList().chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { g ->
                    val on = g in picked
                    Box(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp))
                            .then(if (on) Modifier.background(SpectrumBrush) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)))
                            .clickable { picked = if (on) picked - g else picked + g },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g.emoji)
                            Text(
                                g.label + if (g in suggestion && !on) " ⭐" else "",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel", fontWeight = FontWeight.Bold) }
            GradientButton("Choose exercises", Modifier.weight(1f), enabled = picked.isNotEmpty()) { onConfirm(TrainGroup.values().filter { it in picked }) }
        }
    }
}

/**
 * Full-screen exercise picker for the chosen groups: every body part is split into regions (Upper / Middle / Lower...),
 * the region you have trained least is flagged "Behind", and you tick 2-3 exercises per region. Picks become today's session.
 */
@Composable
fun SessionBuilderDialog(groups: List<TrainGroup>, onDone: () -> Unit, vm: FocusViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val report by vm.report.collectAsState()
    val selected = remember { mutableStateListOf<String>() }
    val primary = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(SpectrumBrush).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🎯 Build today's session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(groups.joinToString(" · ") { it.label } + " - pick 2-3 per part", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f))
                    }
                    Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(6.dp).size(26.dp))
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { g ->
                        g.cats.forEach { cat ->
                            val behind = report?.behindRegion(cat)
                            val regions = report?.regionsBehindFirst(cat) ?: SubParts.forCategory(cat)
                            item(key = "h-$cat") {
                                Text("${g.emoji} ${cat.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                            }
                            items(regions, key = { "$cat-$it" }) { region ->
                                val exercises = remember(cat, region) {
                                    ExerciseCatalog.entries.filter {
                                        it.category == cat && SubParts.classify(cat, it.item.name) == region && ExerciseDemoMap.gifUrl(it.item.name) != null
                                    }.take(10)
                                }
                                if (exercises.isNotEmpty()) {
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(region, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${report?.regionCount(cat, region) ?: 0} sets in the last 30 days",
                                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (region == behind) {
                                                Text(
                                                    "⚠️ Behind - start here",
                                                    Modifier.clip(RoundedCornerShape(50)).background(SpectrumBrush).padding(horizontal = 10.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                                                )
                                            }
                                        }
                                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(exercises, key = { it.item.name }) { e ->
                                                val name = e.item.name
                                                val on = name in selected
                                                Box(
                                                    Modifier.width(112.dp).clip(RoundedCornerShape(12.dp))
                                                        .border(if (on) 2.dp else 1.dp, if (on) primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                                        .clickable { if (on) selected.remove(name) else selected.add(name) }.padding(6.dp),
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        ExerciseDemo(name = name, muscleGroup = e.item.muscleGroup, sizeDp = 90)
                                                        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, textAlign = TextAlign.Center)
                                                    }
                                                    // sits above the GIF so a tap anywhere on the card selects it (the GIF itself would open the info popup)
                                                    Box(Modifier.matchParentSize().clickable { if (on) selected.remove(name) else selected.add(name) })
                                                    if (on) {
                                                        Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(primary), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                GradientButton(
                    if (selected.isEmpty()) "Pick exercises above" else "Add ${selected.size} to today's session",
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).padding(bottom = 60.dp), enabled = selected.isNotEmpty(),
                ) { SessionStore.addPicks(ctx, selected.toList()); onDone() }
            }
        }
    }
}
