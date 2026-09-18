package com.nutriai.ui.bodytype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Body-type model (spec Section 4). Silhouettes are drawn on a Canvas (no image
// assets), parameterised by shoulder / waist / hip width + a muscle flag.
// ---------------------------------------------------------------------------

private val Sharp = RoundedCornerShape(8.dp)

/** Proportions as fractions of the drawing width; `muscular` adds a subtle V-taper shading. */
data class Physique(
    val id: String,
    val emoji: String,
    val label: String,
    val desc: String,
    val shoulder: Float,
    val waist: Float,
    val hip: Float,
    val muscular: Boolean = false,
)

val CURRENT_TYPES = listOf(
    Physique("skinny", "🦴", "Skinny", "Very thin, little muscle", shoulder = 0.34f, waist = 0.24f, hip = 0.28f),
    Physique("skinnyfat", "😐", "Skinny fat", "Normal weight, soft belly", shoulder = 0.38f, waist = 0.40f, hip = 0.38f),
    Physique("average", "📊", "Average", "Moderate build", shoulder = 0.42f, waist = 0.36f, hip = 0.38f),
    Physique("overweight", "⚖️", "Overweight", "Visible excess fat", shoulder = 0.46f, waist = 0.52f, hip = 0.48f),
    Physique("athletic", "💪", "Athletic", "Muscular, low fat", shoulder = 0.52f, waist = 0.32f, hip = 0.36f, muscular = true),
    Physique("heavy", "🐻", "Heavy build", "Big frame, carrying extra weight", shoulder = 0.50f, waist = 0.50f, hip = 0.46f),
)

val GOAL_TYPES = listOf(
    Physique("lean", "🔥", "Lean & toned", "Low fat, light definition", shoulder = 0.44f, waist = 0.30f, hip = 0.34f, muscular = true),
    Physique("vshape", "⚡", "Athletic V-shape", "Broad shoulders, defined abs", shoulder = 0.54f, waist = 0.30f, hip = 0.35f, muscular = true),
    Physique("bodybuilder", "🏆", "Muscular", "Maximum muscle mass", shoulder = 0.60f, waist = 0.36f, hip = 0.40f, muscular = true),
    Physique("endurance", "🏃", "Lean / endurance", "Lean, high stamina", shoulder = 0.40f, waist = 0.28f, hip = 0.32f),
    Physique("strong", "🏋️", "Strong & solid", "Strength with size", shoulder = 0.56f, waist = 0.38f, hip = 0.42f, muscular = true),
    Physique("slim", "🌿", "Slim & fit", "Light, slim, healthy", shoulder = 0.38f, waist = 0.26f, hip = 0.30f),
)

/** Auto strategy for a current->goal combination (spec Section 4 table). */
data class Strategy(val calories: String, val protein: String, val approach: String, val time: String)

fun strategyFor(current: String?, goal: String?): Strategy = when {
    current == "skinny" && goal == "bodybuilder" -> Strategy("~2,500 kcal", "2.2 g/kg", "Lean bulk", "12–18 months")
    current == "athletic" && goal == "bodybuilder" -> Strategy("~2,800 kcal", "2.5 g/kg", "Bulk", "12+ months")
    current == "overweight" && (goal == "lean" || goal == "endurance") -> Strategy("~1,500 kcal", "1.8 g/kg", "Cut", "6–10 months")
    current == "overweight" -> Strategy("~1,700 kcal", "2 g/kg", "Slow cut + muscle", "10–16 months")
    goal == "bodybuilder" -> Strategy("~2,600 kcal", "2.3 g/kg", "Lean bulk", "12+ months")
    goal == "endurance" -> Strategy("~1,800 kcal", "1.8 g/kg", "Lean + conditioning", "6–12 months")
    else -> Strategy("~1,900 kcal", "2 g/kg", "Body recomposition", "8–14 months")
}

/** Recomp / lean-bulk / cut / maintain - the field the calorie engine actually reads. */
val PHYSIQUE_GOALS: List<Triple<String, String, String>> = listOf(
    Triple("recomp", "🔄 Recomp", "Build muscle and lose fat at the same time (maintenance calories, high protein)."),
    Triple("lean_bulk", "📈 Lean bulk", "Gain muscle slowly with a slight calorie surplus."),
    Triple("cut", "📉 Cut", "Lose fat while keeping muscle (a safe calorie deficit)."),
    Triple("maintain", "⚖️ Maintain", "Keep your current physique."),
)

data class BodyTypeState(
    val current: String? = null,
    val goal: String? = null,
    val physiqueGoal: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val savedJustNow: Boolean = false,
)

/**
 * Real bug fixed here: this screen used to save current/goal body type ONLY to on-device
 * DataStore prefs - never reached the server, so it was purely decorative. A user changing their
 * goal here (reasonably expecting it to affect their calorie target, since that's what "goal"
 * means everywhere else in the app) saw the exact same numbers as before, because nothing was
 * ever sent. Now backed by the real profile: bodyTypeCurrent/bodyTypeGoal round-trip for
 * consistency, and the new Physique Goal picker writes `physiqueGoal` - the field
 * computeAndSaveForUser() actually reads - so changing it here genuinely recalculates the target,
 * same as every other profile-editing surface in the app.
 */
@HiltViewModel
class BodyTypeViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(BodyTypeState())
    val state: StateFlow<BodyTypeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = repository.getProfile().getOrNull()
            val s = p?.sensitive
            _state.value = BodyTypeState(
                current = s?.bodyTypeCurrent, goal = s?.bodyTypeGoal, physiqueGoal = s?.physiqueGoal, loading = false,
            )
        }
    }

    private fun persist() {
        val st = _state.value
        _state.value = st.copy(saving = true, savedJustNow = false)
        viewModelScope.launch {
            val ok = repository.updateBodyType(st.current, st.goal, st.physiqueGoal).isSuccess
            _state.value = _state.value.copy(saving = false, savedJustNow = ok)
        }
    }

    fun selectCurrent(id: String) { _state.value = _state.value.copy(current = id); persist() }
    fun selectGoal(id: String) { _state.value = _state.value.copy(goal = id); persist() }
    fun selectPhysiqueGoal(id: String) { _state.value = _state.value.copy(physiqueGoal = id); persist() }
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

@Composable
fun BodyTypeScreen(modifier: Modifier = Modifier, viewModel: BodyTypeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = CURRENT_TYPES.firstOrNull { it.id == state.current }
    val goal = GOAL_TYPES.firstOrNull { it.id == state.goal }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        // Motivation banner: current -> goal.
        if (current != null && goal != null) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(KaizenBlue, BrandGreen, KaizenLavender),
                                ),
                            )
                            .padding(Spacing.lg),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiniFigure(current, Modifier.size(72.dp), textColor = Color.White)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("➡️", style = MaterialTheme.typography.headlineMedium)
                                val s = strategyFor(state.current, state.goal)
                                Text(
                                    s.approach,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            MiniFigure(goal, Modifier.size(72.dp), textColor = Color.White)
                        }
                    }
                }
            }
            item {
                val s = strategyFor(state.current, state.goal)
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text("📋 Your Plan", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Spacing.sm))
                        // Only the approach is shown: the old generic "~1,900 kcal / 8-14 months" figures
                        // contradicted the user's real, engine-computed target on the dashboard.
                        PlanMetric(label = "Approach", value = s.approach)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Your real calories, protein and timeline are on the Today dashboard - they come from your profile and the Physique Goal below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🎯 Physique Goal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                when {
                    state.saving -> Text("Saving…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.savedJustNow -> Text("✓ Saved - target updated", style = MaterialTheme.typography.labelSmall, color = BrandGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text(
                "This is what actually drives your calorie/protein target and plan - not the illustration above.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { PhysiqueGoalGrid(state.physiqueGoal) { viewModel.selectPhysiqueGoal(it) } }

        item { Text("🏋️ Current Body Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        item { PhysiqueGrid(CURRENT_TYPES, state.current) { viewModel.selectCurrent(it) } }

        item { Text("🎯 Goal Body Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        item { PhysiqueGrid(GOAL_TYPES, state.goal) { viewModel.selectGoal(it) } }
    }
}

@Composable
private fun PhysiqueGoalGrid(selectedId: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PHYSIQUE_GOALS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { (id, label, desc) ->
                    val selected = selectedId == id
                    Card(
                        Modifier.weight(1f).clip(Sharp).clickable { onSelect(id) },
                        shape = Sharp,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                    ) {
                        Box {
                            if (selected) {
                                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)))))
                            }
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlanMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * Compact current->goal body-type picker for reuse in onboarding (spec Section 4, Step 0). Draws the
 * same silhouettes as the standalone screen; the caller owns the selected ids and persistence.
 */
@Composable
fun BodyTypeInlinePicker(
    currentId: String?,
    goalId: String?,
    onCurrent: (String) -> Unit,
    onGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("📍 Where are you now?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        PhysiqueGrid(CURRENT_TYPES, currentId, onCurrent)
        Text("🎯 Where do you want to be?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        PhysiqueGrid(GOAL_TYPES, goalId, onGoal)
    }
}

@Composable
private fun PhysiqueGrid(types: List<Physique>, selectedId: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        types.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p -> PhysiqueTile(p, selectedId == p.id, Modifier.weight(1f)) { onSelect(p.id) } }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Small dashboard-style tile: emoji + label, tinted; filled brand colour when selected. */
@Composable
private fun PhysiqueTile(p: Physique, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.emoji, fontSize = 20.sp)
        Text(p.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun PhysiqueCard(p: Physique, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier
            .clip(Sharp)
            .clickable(onClick = onClick),
        shape = Sharp,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            if (selected) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))))
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg, horizontal = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(p.emoji, style = MaterialTheme.typography.headlineMedium)
                Text(
                    p.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    p.desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MiniFigure(p: Physique, modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(p.emoji, style = MaterialTheme.typography.headlineMedium)
        Text(p.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, color = textColor)
    }
}
