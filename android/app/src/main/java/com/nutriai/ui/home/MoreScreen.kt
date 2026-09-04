package com.nutriai.ui.home


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriai.ui.badges.BadgesScreen
import com.nutriai.ui.barcode.BarcodeScreen
import com.nutriai.ui.checkin.CheckinScreen
import com.nutriai.ui.family.FamilyScreen
import com.nutriai.ui.grocery.GroceryScreen
import com.nutriai.ui.reports.ReportsScreen
import com.nutriai.ui.body.BodyScreen
import com.nutriai.ui.settings.SettingsScreen


private data class MoreItem(val key: String, val icon: String, val label: String, val subtitle: String)

/** The 4 most-used destinations — a 2×2 grid right under the profile strip. */
private val QUICK_ACCESS = listOf(
    MoreItem("progress", "📊", "Progress", "Measurements & photos"),
    MoreItem("plan", "📝", "Plan Tomorrow", "AI plan review"),
    MoreItem("checkin", "⚖️", "Weekly Check-in", "Log weight"),
    MoreItem("reports", "📄", "Reports", "Download PDF"),
)

/** Everything else — a horizontal-scroll chip row instead of a long flat list. */
private val TOOLS = listOf(
    MoreItem("mind", "🧘", "Mind", ""),
    MoreItem("coach", "💬", "Coach", ""),
    MoreItem("vitals", "🩺", "Vitals", ""),
    MoreItem("medications", "💊", "Medicines", ""),
    MoreItem("barcode", "📷", "Barcode", ""),
    MoreItem("badges", "🏅", "Badges", ""),
    MoreItem("family", "👨‍👩‍👧", "Family", ""),
    MoreItem("discipline", "✅", "Discipline", ""),
    MoreItem("history", "🗓️", "History", ""),
    MoreItem("physique", "🧍", "Body type", ""),
    MoreItem("body", "📸", "Body Check", ""),
)

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onEditProfile: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
) {
    var selected by remember { mutableStateOf<String?>(null) }

    // System back returns to the More menu from a sub-screen, not out to the dashboard.
    androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }

    when (selected) {
        null -> MoreMenu(modifier) { selected = it }
        else -> Column(modifier.fillMaxSize()) {
            TextButton(onClick = { selected = null }, modifier = Modifier.padding(4.dp)) {
                Text("← Me")
            }
            when (selected) {
                "plan" -> com.nutriai.ui.plan.PlanScreen(Modifier.fillMaxSize())
                "mind" -> com.nutriai.ui.wellness.WellnessScreen(Modifier.fillMaxSize())
                "coach" -> com.nutriai.ui.coach.CoachScreen(Modifier.fillMaxSize())
                "discipline" -> com.nutriai.ui.discipline.DisciplineScreen(Modifier.fillMaxSize())
                "vitals" -> com.nutriai.ui.vitals.VitalsScreen(Modifier.fillMaxSize())
                "medications" -> com.nutriai.ui.medications.MedicationsScreen(Modifier.fillMaxSize())
                "checkin" -> CheckinScreen(Modifier.fillMaxSize())
                "physique" -> com.nutriai.ui.bodytype.BodyTypeScreen(Modifier.fillMaxSize())
                "body" -> BodyScreen(Modifier.fillMaxSize())
                "progress" -> com.nutriai.ui.body.ProgressScreen(Modifier.fillMaxSize())
                "history" -> com.nutriai.ui.history.HistoryScreen(Modifier.fillMaxSize())
                "barcode" -> BarcodeScreen(Modifier.fillMaxSize())
                "reports" -> ReportsScreen(Modifier.fillMaxSize())
                "badges" -> BadgesScreen(Modifier.fillMaxSize())
                "family" -> FamilyScreen(Modifier.fillMaxSize())
                "settings" -> SettingsScreen(
                    onEditProfile = onEditProfile,
                    onLoggedOut = onLoggedOut,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        com.nutriai.ui.components.ScreenHeader("me")

        // Quick access — 2×2 grid, each card compact (not a full-width stack).
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("quick access", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            QUICK_ACCESS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item -> QuickAccessCard(item, Modifier.weight(1f)) { onSelect(item.key) } }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }

        // Tools — horizontal-scroll chip row instead of a long flat list.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("tools", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TOOLS.forEach { item -> ToolChip(item) { onSelect(item.key) } }
            }
        }

        // Account.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onSelect("settings") },
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⚙️", fontSize = 18.sp)
                        Text("Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickAccessCard(item: MoreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.heightIn(min = 100.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.icon, fontSize = 22.sp)
            Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolChip(item: MoreItem, onClick: () -> Unit) {
    Card(
        Modifier.heightIn(min = 72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.icon, fontSize = 20.sp)
            Text(item.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
