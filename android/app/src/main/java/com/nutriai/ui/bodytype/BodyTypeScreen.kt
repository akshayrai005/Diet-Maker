package com.nutriai.ui.bodytype

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Body-type model (spec Section 4). Silhouettes are drawn on a Canvas (no image
// assets), parameterised by shoulder / waist / hip width + a muscle flag.
// ---------------------------------------------------------------------------

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
)

val GOAL_TYPES = listOf(
    Physique("lean", "🔥", "Lean & toned", "Low fat, light definition", shoulder = 0.44f, waist = 0.30f, hip = 0.34f, muscular = true),
    Physique("vshape", "⚡", "Athletic V-shape", "Broad shoulders, defined abs", shoulder = 0.54f, waist = 0.30f, hip = 0.35f, muscular = true),
    Physique("bodybuilder", "🏆", "Muscular", "Maximum muscle mass", shoulder = 0.60f, waist = 0.36f, hip = 0.40f, muscular = true),
    Physique("endurance", "🏃", "Lean / endurance", "Lean, high stamina", shoulder = 0.40f, waist = 0.28f, hip = 0.32f),
)

/** Auto strategy for a current→goal combination (spec Section 4 table). */
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

// ---------------------------------------------------------------------------
// Prefs + ViewModel (stored on-device).
// ---------------------------------------------------------------------------

private val Context.bodyTypeStore by preferencesDataStore(name = "bodytype")

@Singleton
class BodyTypePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val currentKey = stringPreferencesKey("current_type")
    private val goalKey = stringPreferencesKey("goal_type")
    suspend fun current(): String? = context.bodyTypeStore.data.first()[currentKey]
    suspend fun goal(): String? = context.bodyTypeStore.data.first()[goalKey]
    suspend fun setCurrent(id: String) { context.bodyTypeStore.edit { it[currentKey] = id } }
    suspend fun setGoal(id: String) { context.bodyTypeStore.edit { it[goalKey] = id } }
}

data class BodyTypeState(val current: String? = null, val goal: String? = null)

@HiltViewModel
class BodyTypeViewModel @Inject constructor(private val prefs: BodyTypePrefs) : ViewModel() {
    private val _state = MutableStateFlow(BodyTypeState())
    val state: StateFlow<BodyTypeState> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = BodyTypeState(prefs.current(), prefs.goal()) } }

    fun selectCurrent(id: String) { _state.value = _state.value.copy(current = id); viewModelScope.launch { prefs.setCurrent(id) } }
    fun selectGoal(id: String) { _state.value = _state.value.copy(goal = id); viewModelScope.launch { prefs.setGoal(id) } }
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

/**
 * Visual body-type selector (spec Section 4): pick where you are now and where you want to be, drawn
 * as silhouettes rather than words, and see the auto-calculated strategy for that combination. The
 * choice is saved on-device and shown as a current → goal motivation banner.
 */
@Composable
fun BodyTypeScreen(modifier: Modifier = Modifier, viewModel: BodyTypeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = CURRENT_TYPES.firstOrNull { it.id == state.current }
    val goal = GOAL_TYPES.firstOrNull { it.id == state.goal }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Shape your body", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Where are you now, and where do you want to be?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Motivation banner: current → goal.
        if (current != null && goal != null) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniFigure(current, Modifier.size(64.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("→", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            val s = strategyFor(state.current, state.goal)
                            Text(s.approach, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
                        }
                        MiniFigure(goal, Modifier.size(64.dp))
                    }
                }
            }
            item {
                val s = strategyFor(state.current, state.goal)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("🎯 ${s.calories} · ${s.protein} protein", style = MaterialTheme.typography.bodyMedium)
                        Text("📋 ${s.approach}  ·  ⏳ ${s.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Estimates — your actual targets come from your full profile & goal.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Text("Current body type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { PhysiqueGrid(CURRENT_TYPES, state.current) { viewModel.selectCurrent(it) } }

        item { Text("Goal body type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { PhysiqueGrid(GOAL_TYPES, state.goal) { viewModel.selectGoal(it) } }
    }
}

/**
 * Compact current→goal body-type picker for reuse in onboarding (spec Section 4, Step 0). Draws the
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Where are you now?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        PhysiqueGrid(CURRENT_TYPES, currentId, onCurrent)
        Text("Where do you want to be?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        PhysiqueGrid(GOAL_TYPES, goalId, onGoal)
    }
}

@Composable
private fun PhysiqueGrid(types: List<Physique>, selectedId: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        types.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p -> PhysiqueCard(p, selectedId == p.id, Modifier.weight(1f)) { onSelect(p.id) } }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhysiqueCard(p: Physique, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BodySilhouette(p, Modifier.fillMaxWidth().aspectRatio(0.7f))
            Text("${p.emoji} ${p.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(p.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MiniFigure(p: Physique, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BodySilhouette(p, modifier)
        Text(p.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Draws a symmetric front-facing body silhouette from the [Physique] proportions. Shoulder/waist/hip
 * widths taper the torso; a muscular type gets a subtly darker fill to read as more defined.
 */
@Composable
private fun BodySilhouette(p: Physique, modifier: Modifier = Modifier) {
    val fill = if (p.muscular) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headR = w * 0.11f
        val topPad = h * 0.02f

        // Head.
        drawCircle(color = fill, radius = headR, center = Offset(cx, topPad + headR))

        val shoulderY = topPad + headR * 2f + h * 0.04f
        val waistY = shoulderY + h * 0.30f
        val hipY = waistY + h * 0.10f
        val legBottom = h * 0.98f

        val shoulderHalf = w * p.shoulder / 2f
        val waistHalf = w * p.waist / 2f
        val hipHalf = w * p.hip / 2f

        // Torso outline (shoulders → belly/waist → hips) as one smooth closed path. A wider waist
        // bows OUTWARD (belly) so skinny-fat / overweight read as soft, while a narrow waist under
        // broad shoulders gives the athletic V-taper. Shoulders are rounded, not square.
        val midY = (shoulderY + waistY) / 2f
        val bellyHalf = maxOf(waistHalf, shoulderHalf * 0.7f) * 1.08f
        val torso = Path().apply {
            // Left side: rounded shoulder → belly bulge → waist → hip.
            moveTo(cx - shoulderHalf + w * 0.02f, shoulderY)
            quadraticBezierTo(cx - shoulderHalf - w * 0.02f, shoulderY + h * 0.02f, cx - shoulderHalf, shoulderY + h * 0.05f)
            quadraticBezierTo(cx - bellyHalf, midY, cx - waistHalf, waistY)
            quadraticBezierTo(cx - hipHalf, waistY + h * 0.04f, cx - hipHalf, hipY)
            lineTo(cx + hipHalf, hipY)
            // Right side back up (mirror).
            quadraticBezierTo(cx + hipHalf, waistY + h * 0.04f, cx + waistHalf, waistY)
            quadraticBezierTo(cx + bellyHalf, midY, cx + shoulderHalf, shoulderY + h * 0.05f)
            quadraticBezierTo(cx + shoulderHalf + w * 0.02f, shoulderY + h * 0.02f, cx + shoulderHalf - w * 0.02f, shoulderY)
            close()
        }
        drawPath(torso, color = fill)

        // Neck.
        drawRect(color = fill, topLeft = Offset(cx - w * 0.045f, topPad + headR * 2f - h * 0.005f), size = androidx.compose.ui.geometry.Size(w * 0.09f, shoulderY - (topPad + headR * 2f) + h * 0.01f))

        // Arms (taper from shoulders down past the waist).
        val armW = w * (if (p.muscular) 0.10f else 0.075f)
        drawRoundRectArm(cx - shoulderHalf, shoulderY, armW, hipY - shoulderY + h * 0.02f, fill)
        drawRoundRectArm(cx + shoulderHalf - armW, shoulderY, armW, hipY - shoulderY + h * 0.02f, fill)

        // Legs (two, from hips to bottom).
        val legW = hipHalf * 0.85f
        drawRoundRectLeg(cx - hipHalf, hipY, legW, legBottom - hipY, fill)
        drawRoundRectLeg(cx + hipHalf - legW, hipY, legW, legBottom - hipY, fill)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectArm(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRoundRect(color = color, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectLeg(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRoundRect(color = color, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 3f, w / 3f))
}
