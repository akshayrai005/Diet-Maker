package com.nutriai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
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
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep

private data class MoreItem(val key: String, val icon: String, val label: String, val subtitle: String)

private val MORE_ITEMS = listOf(
    MoreItem("coach", "💬", "AI Coach", "Chat with your coach"),
    MoreItem("discipline", "✅", "Discipline", "Daily habits, streaks & adherence"),
    MoreItem("vitals", "🩺", "Vitals & labs", "BP, glucose, cholesterol & trends"),
    MoreItem("medications", "💊", "Medicines & supplements", "Track doses & get reminders"),
    MoreItem("checkin", "⚖️", "Weekly check-in", "Log weight & measurements"),
    MoreItem("physique", "🧍", "Body type & goal", "Pick your shape & target physique"),
    MoreItem("body", "📸", "Body Check", "AI body-fat from a photo"),
    MoreItem("progress", "📈", "Progress & body", "Measurements, trends & private photos"),
    MoreItem("history", "🗓️", "History", "Steps, calories, water & weight day-by-day"),
    MoreItem("barcode", "📷", "Scan barcode", "Camera food lookup"),
    MoreItem("reports", "📄", "Health reports", "Weekly & monthly · view or share"),
    MoreItem("badges", "🏅", "Achievements", "Streaks & milestones"),
    MoreItem("family", "👨‍👩‍👧", "Family", "Members with their own plan"),
    MoreItem("settings", "⚙️", "Settings", "Profile, account & more"),
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(BrandGreen, BrandGreenDeep)))
                    .padding(22.dp),
            ) {
                Column {
                    Text("Me", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Coach, progress, family & settings", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        MORE_ITEMS.forEach { item ->
            Card(
                Modifier.fillMaxWidth().clickable { onSelect(item.key) },
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Text(item.icon, fontSize = 22.sp) }
                        Column {
                            Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
