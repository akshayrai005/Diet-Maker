package com.nutriai.ui.lifestyle

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.FeatureCard
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.Status
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.Radius
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
// Data: 8 real-life eating patterns (spec Section 6) + universal office guides.
// ---------------------------------------------------------------------------

data class MealRow(val time: String, val meal: String, val food: String)

/** One tailored eating pattern with its meal timing and pattern-specific tips. */
enum class EatingPattern(
    val id: String,
    val emoji: String,
    val label: String,
    val fits: String,
    val meals: List<MealRow>,
    val tips: List<String>,
) {
    MORNING_NIGHT(
        "morning_night", "⏰", "Morning + Night only", "Most common — leaves early, skips lunch",
        listOf(
            MealRow("7:30 AM", "Meal 1 — biggest", "4 boiled eggs + 2 rotis + 1 banana (~550 kcal, 30g)"),
            MealRow("10:30 AM", "Pocket snack", "30g peanuts OR 2 boiled eggs in tiffin (~180 kcal, 12g)"),
            MealRow("1:00 PM", "Light lunch / smart skip", "Dal + sabzi + 1 roti, or a protein snack (~300 kcal, 15g)"),
            MealRow("4:30 PM", "Pre-gym", "1 banana + peanut butter (~200 kcal, 8g)"),
            MealRow("8:30 PM", "Meal 2 — high protein", "150g chicken/fish + sabzi + roti + dal (~600 kcal, 45g)"),
            MealRow("10:00 PM", "Optional", "1 cup curd or a glass of milk (~120 kcal, 10g)"),
        ),
        listOf(
            "Front-load protein: the 7:30 AM meal is the most important — never skip it.",
            "Carry the 10:30 snack in your bag the night before so you never miss it.",
            "A slight surplus on non-fast days covers the Tuesday fast deficit.",
        ),
    ),
    HOME_ALL_DAY(
        "home", "🏠", "Home all day", "WFH, freelancer, homemaker",
        listOf(
            MealRow("8:00 AM", "Breakfast", "3 eggs + 40g oats + milk"),
            MealRow("11:00 AM", "Mid-morning", "Fruit + handful of nuts"),
            MealRow("1:30 PM", "Lunch", "Dal + sabzi + 2 rotis + curd"),
            MealRow("5:00 PM", "Snack", "Sprouts chaat or roasted chana"),
            MealRow("8:00 PM", "Dinner", "150g chicken/paneer + sabzi + 1 roti"),
        ),
        listOf(
            "You control the kitchen — cook fresh, keep portions steady across 5 meals.",
            "Prep a protein source (eggs/paneer/chicken) at every main meal.",
        ),
    ),
    OFFICE_CANTEEN(
        "office_canteen", "🏢", "Office with canteen", "Has canteen / dabba access",
        listOf(
            MealRow("7:30 AM", "Breakfast", "4 eggs + 2 rotis + banana"),
            MealRow("11:00 AM", "Snack", "Boiled eggs from tiffin"),
            MealRow("1:00 PM", "Canteen lunch", "Dal + sabzi + 1 roti (skip rice & fried items)"),
            MealRow("5:00 PM", "Pre-gym", "Banana + peanut butter"),
            MealRow("8:30 PM", "Dinner", "150g chicken/fish + sabzi + roti"),
        ),
        listOf(
            "Canteen rule: always dal + sabzi + 1 roti. Skip rice, fried snacks and cold drinks.",
            "Carry 2 boiled eggs to top up the canteen meal's protein.",
        ),
    ),
    OFFICE_NO_CANTEEN(
        "office_no_canteen", "🏢", "Office, no canteen", "Brings tiffin or skips lunch",
        listOf(
            MealRow("7:30 AM", "Breakfast — biggest", "4 eggs + 2 rotis + banana"),
            MealRow("11:00 AM", "Snack", "Peanuts / roasted chana in pocket"),
            MealRow("1:00 PM", "Tiffin", "Paneer/chicken + sabzi + 1 roti (prepped Sunday)"),
            MealRow("5:00 PM", "Pre-gym", "Banana + PB"),
            MealRow("8:30 PM", "Dinner", "150g protein + sabzi + roti"),
        ),
        listOf(
            "Your tiffin is your lunch insurance — use the Sunday prep plan below.",
            "Keep dry snacks (peanuts, chana, protein bar) in your bag at all times.",
        ),
    ),
    FIELD_JOB(
        "field", "🚗", "Field job / travelling", "Sales, delivery, driver",
        listOf(
            MealRow("7:30 AM", "Breakfast", "4 eggs + 2 rotis (eat before leaving)"),
            MealRow("On the go", "Dhaba lunch", "Dal tadka + 1 roti + raita + salad, or egg bhurji + roti"),
            MealRow("Anytime", "Pocket fuel", "Peanuts, roasted chana, boiled eggs, protein bar"),
            MealRow("8:30 PM", "Dinner", "Grilled chicken / home dinner + sabzi"),
        ),
        listOf(
            "Order smart at a dhaba: dal + roti + salad. Avoid butter chicken, biryani, lassi.",
            "Always carry dry high-protein snacks — you can't rely on finding good food.",
        ),
    ),
    NIGHT_SHIFT(
        "night_shift", "🌙", "Night shift", "BPO, security, hospital",
        listOf(
            MealRow("8:00 PM", "\"Breakfast\" (before shift)", "4 eggs + 2 rotis + banana"),
            MealRow("1:00 AM", "\"Lunch\" (mid-shift)", "Dal + sabzi + 1 roti"),
            MealRow("5:00 PM", "Gym (before shift)", "Pre: banana + PB"),
            MealRow("7:00 AM", "\"Dinner\" (after shift)", "Light — curd + eggs, then sleep"),
        ),
        listOf(
            "Meal timing is reversed but the plate stays the same: protein + veg every meal.",
            "Don't eat heavy right before sleeping at 8 AM — keep the last meal light.",
        ),
    ),
    OMAD(
        "omad", "🕐", "One meal a day (OMAD)", "Extreme fasting type",
        listOf(
            MealRow("Fasting window", "Water / black coffee / green tea", "No calories until your eating window"),
            MealRow("Eating window (~1 hr)", "One large balanced meal", "200g protein + 3 rotis + big sabzi + curd + salad"),
        ),
        listOf(
            "Hard to hit 130g protein in one sitting — prioritise a large protein portion.",
            "Break the fast gently, chew slowly, and hydrate well through the fasting window.",
        ),
    ),
    RELIGIOUS_FASTING(
        "religious_fasting", "🕌", "Religious fasting", "Tuesday / Ekadashi / Navratri",
        listOf(
            MealRow("Fast day", "Water / black coffee / green tea", "Complete fast until evening"),
            MealRow("Evening", "Break gently", "Khichdi or curd rice — don't overeat"),
            MealRow("Non-fast days", "Slight surplus", "Add a little extra to cover the deficit"),
        ),
        listOf(
            "On a complete fast day, the app shows a fast protocol instead of a meal plan.",
            "Break your fast with something light and warm — not a heavy fried meal.",
        ),
    ),
    ;

    companion object {
        fun byId(id: String?): EatingPattern = entries.firstOrNull { it.id == id } ?: MORNING_NIGHT
    }
}

/** A collapsible guide card shared across patterns (canteen, tiffin, damage control, travel). */
private data class GuideCard(val emoji: String, val title: String, val lines: List<String>)

private val UNIVERSAL_GUIDES = listOf(
    GuideCard(
        "🍽️", "Smart canteen ordering",
        listOf(
            "✅ Best: dal + sabzi + 1 roti + salad",
            "✅ Good: egg bhurji + roti, or grilled chicken + salad",
            "❌ Avoid: fried items, extra rice, cold drinks, sweets",
            "Protein hack: carry 2 boiled eggs to top up any canteen meal.",
        ),
    ),
    GuideCard(
        "🥡", "Sunday tiffin prep (5-day)",
        listOf(
            "10 boiled eggs (batch, 15 min) — ₹70, 6g each",
            "Sprouts (soak overnight) — ₹20, 8g/100g",
            "Roasted chana (weekly buy) — ₹30, 22g/100g",
            "Paneer cubes (cook once, 20 min) — ₹100, 18g/100g",
            "Fruit (banana/apple) for quick energy — ₹30",
        ),
    ),
    GuideCard(
        "⚖️", "Damage control (cheat meal)",
        listOf(
            "No guilt — one meal won't undo your progress.",
            "Balance today: skip the evening snack, keep dinner light.",
            "Add a 20-min walk and drink extra water tonight.",
            "Adjust tomorrow, not by starving yourself today.",
        ),
    ),
    GuideCard(
        "🧳", "Travel / outstation mode",
        listOf(
            "Order at hotels/dhabas: dal + roti + salad, grilled items, curd.",
            "Avoid buffets-as-a-challenge, biryani, and sugary drinks.",
            "Carry dry snacks: peanuts, roasted chana, protein bars.",
            "Aim for protein at every stop, even if calories vary.",
        ),
    ),
)

// ---------------------------------------------------------------------------
// Prefs + ViewModel (client-side; the selected pattern is stored on-device).
// ---------------------------------------------------------------------------

private val Context.lifestyleStore by preferencesDataStore(name = "lifestyle")

@Singleton
class LifestylePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("eating_pattern")
    suspend fun pattern(): EatingPattern = EatingPattern.byId(context.lifestyleStore.data.first()[key])
    suspend fun setPattern(p: EatingPattern) { context.lifestyleStore.edit { it[key] = p.id } }
}

@HiltViewModel
class LifestyleViewModel @Inject constructor(
    private val prefs: LifestylePrefs,
    private val repository: com.nutriai.data.AppRepository,
) : ViewModel() {
    private val _pattern = MutableStateFlow(EatingPattern.MORNING_NIGHT)
    val pattern: StateFlow<EatingPattern> = _pattern.asStateFlow()

    init {
        viewModelScope.launch {
            _pattern.value = prefs.pattern()
            val serverId = repository.getProfile().getOrNull()?.sensitive?.eatingPattern
            if (serverId != null) {
                val p = EatingPattern.byId(serverId)
                _pattern.value = p
                prefs.setPattern(p)
            }
        }
    }

    fun select(p: EatingPattern) {
        _pattern.value = p
        viewModelScope.launch {
            prefs.setPattern(p)
            repository.updateEatingPattern(p.id)
        }
    }
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

private val GUIDE_COLORS = listOf(KaizenBlue, NutritionColor, BrandAmber, KaizenLavender)

@Composable
fun LifestyleScreen(modifier: Modifier = Modifier, viewModel: LifestyleViewModel = hiltViewModel()) {
    val selected by viewModel.pattern.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.md),
    ) {
        item {
            com.nutriai.ui.components.ScreenHeader(
                title = "🌿 Lifestyle",
                subtitle = "Pick the pattern that matches how you actually eat — the guide adapts to it.",
            )
        }

        // Pattern picker
        item {
            SectionHeader(title = "Your Eating Pattern", emoji = "🍽️")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EatingPattern.entries.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { p ->
                            FilterChip(
                                selected = selected == p,
                                onClick = { viewModel.select(p) },
                                label = { Text("${p.emoji} ${p.label}", maxLines = 2) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radius.md),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandGreen,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Selected pattern summary
        item {
            FeatureCard(
                emoji = selected.emoji,
                title = selected.label,
                accentColor = BrandGreen,
            ) {
                StatusIndicator(text = selected.fits, status = Status.Information)
            }
        }

        // Meal timing
        item { SectionHeader(title = "Meal Timing", emoji = "🕐") }
        itemsIndexed(selected.meals) { i, row ->
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    EmojiBadge(
                        emoji = "🍽️",
                        bgColor = KaizenBlue.copy(alpha = 0.2f),
                        size = 36.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                row.meal,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            StatusIndicator(text = row.time, status = Status.Positive)
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            row.food,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Pattern-specific tips
        if (selected.tips.isNotEmpty()) {
            item { SectionHeader(title = "Tips for You", emoji = "💡") }
            item {
                FeatureCard(
                    emoji = "🎯",
                    title = "Pattern Tips",
                    accentColor = BrandAmber,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        selected.tips.forEachIndexed { idx, tip ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.Top,
                            ) {
                                EmojiBadge(emoji = "💪", bgColor = BrandAmber.copy(alpha = 0.2f), size = 24.dp)
                                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        // Universal office guides (collapsible)
        item { SectionHeader(title = "Office & Travel Guides", emoji = "🧳") }
        itemsIndexed(UNIVERSAL_GUIDES.toList()) { idx, guide ->
            ExpandableGuide(guide, accentColor = GUIDE_COLORS[idx % GUIDE_COLORS.size])
        }
    }
}

@Composable
private fun ExpandableGuide(guide: GuideCard, accentColor: Color = KaizenBlue) {
    var expanded by remember { mutableStateOf(false) }
    FeatureCard(
        emoji = guide.emoji,
        title = guide.title,
        accentColor = accentColor,
        onClick = { expanded = !expanded },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                if (expanded) "▲ Collapse" else "▼ Expand",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                guide.lines.forEach { line ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("  ", style = MaterialTheme.typography.bodySmall)
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
