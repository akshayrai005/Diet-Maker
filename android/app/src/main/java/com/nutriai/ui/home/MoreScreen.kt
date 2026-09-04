package com.nutriai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nutriai.ui.badges.BadgesScreen
import com.nutriai.ui.barcode.BarcodeScreen
import com.nutriai.ui.checkin.CheckinScreen
import com.nutriai.ui.components.KaizenIconButton
import com.nutriai.ui.components.ListRow
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.family.FamilyScreen
import com.nutriai.ui.reports.ReportsScreen
import com.nutriai.ui.body.BodyScreen
import com.nutriai.ui.settings.SettingsScreen

private data class ProfileItem(val key: String, val icon: ImageVector, val label: String, val subtitle: String)
private data class ProfileGroup(val title: String, val items: List<ProfileItem>)

/** Only destinations that actually exist — grouped by purpose, per Phase 2 Profile IA. */
private val GROUPS = listOf(
    ProfileGroup(
        "Health & Progress",
        listOf(
            ProfileItem("progress", Icons.Filled.TrendingUp, "Progress", "Measurements & photos"),
            ProfileItem("body", Icons.Filled.PhotoCamera, "Body", "Body check photos"),
            ProfileItem("physique", Icons.Filled.Accessibility, "Body type", "Current vs. goal physique"),
            ProfileItem("vitals", Icons.Filled.MonitorHeart, "Vitals", "Heart rate, sleep, stress"),
            ProfileItem("history", Icons.Filled.History, "History", "Past logs & trends"),
        ),
    ),
    ProfileGroup(
        "Planning & Coaching",
        listOf(
            ProfileItem("plan", Icons.Filled.EventNote, "Plan", "AI plan review"),
            ProfileItem("coach", Icons.Filled.Chat, "Coach", "Chat with your coach"),
            ProfileItem("checkin", Icons.Filled.MonitorWeight, "Check-in", "Weekly weigh-in"),
        ),
    ),
    ProfileGroup(
        "Wellness",
        listOf(
            ProfileItem("mind", Icons.Filled.SelfImprovement, "Mind", "Mindfulness & wellness"),
            ProfileItem("discipline", Icons.Filled.CheckCircle, "Discipline", "Habit tracking"),
            ProfileItem("badges", Icons.Filled.MilitaryTech, "Badges", "Achievements"),
        ),
    ),
    ProfileGroup(
        "Tools",
        listOf(
            ProfileItem("medications", Icons.Filled.Medication, "Medications", "Reminders & log"),
            ProfileItem("family", Icons.Filled.Groups, "Family", "Shared household"),
            ProfileItem("reports", Icons.Filled.Description, "Reports", "Download PDF"),
            ProfileItem("barcode", Icons.Filled.QrCodeScanner, "Barcode", "Scan a product"),
        ),
    ),
    ProfileGroup(
        "Account",
        listOf(
            ProfileItem("settings", Icons.Filled.Settings, "Settings", ""),
        ),
    ),
)

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onEditProfile: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
) {
    var selected by remember { mutableStateOf<String?>(null) }

    // System back returns to the Profile menu from a sub-screen, not out to the dashboard.
    androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }

    when (selected) {
        null -> ProfileMenu(modifier) { selected = it }
        else -> Column(modifier.fillMaxSize()) {
            ScreenHeader(
                title = GROUPS.flatMap { it.items }.firstOrNull { it.key == selected }?.label ?: "Profile",
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                action = {
                    KaizenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Profile",
                        onClick = { selected = null },
                    )
                },
            )
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
private fun ProfileMenu(modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        ScreenHeader("Profile")

        GROUPS.forEach { group ->
            Column {
                SectionHeader(group.title)
                group.items.forEachIndexed { index, item ->
                    ListRow(
                        title = item.label,
                        subtitle = item.subtitle.ifBlank { null },
                        leading = { Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) },
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { onSelect(item.key) },
                    )
                    if (index != group.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        }
    }
}
