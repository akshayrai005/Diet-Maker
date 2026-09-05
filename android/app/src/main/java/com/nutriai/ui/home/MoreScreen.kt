package com.nutriai.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriai.ui.badges.BadgesScreen
import com.nutriai.ui.barcode.BarcodeScreen
import com.nutriai.ui.checkin.CheckinScreen
import com.nutriai.ui.components.EmojiBadge
import com.nutriai.ui.components.GlassCard
import com.nutriai.ui.components.KaizenIconButton
import com.nutriai.ui.components.ScreenHeader
import com.nutriai.ui.components.SectionHeader
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.CardAmberLight
import com.nutriai.ui.theme.CardBlueLight
import com.nutriai.ui.theme.CardCoralLight
import com.nutriai.ui.theme.CardGreenLight
import com.nutriai.ui.theme.CardLavenderLight
import com.nutriai.ui.theme.CardMintLight
import com.nutriai.ui.theme.HeroGradientTop
import com.nutriai.ui.theme.HeroGradientBottom
import com.nutriai.ui.theme.HydrationColor
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.MovementColor
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.RecoveryColor
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors
import com.nutriai.ui.family.FamilyScreen
import com.nutriai.ui.reports.ReportsScreen
import com.nutriai.ui.body.BodyScreen
import com.nutriai.ui.settings.SettingsScreen

private data class ProfileItem(val key: String, val emoji: String, val label: String, val subtitle: String, val tint: Color = BrandGreen)
private data class ProfileGroup(val title: String, val emoji: String, val bgColor: Color, val items: List<ProfileItem>)

private val GROUPS = listOf(
    ProfileGroup(
        "Your Health", "❤️", CardCoralLight,
        listOf(
            ProfileItem("progress", "📈", "Progress", "Measurements & photos", NutritionColor),
            ProfileItem("body", "📸", "Body", "Body check photos", NutritionColor),
            ProfileItem("physique", "🏋️", "Body type", "Current vs. goal physique", MovementColor),
            ProfileItem("vitals", "❤️", "Vitals", "Heart rate, sleep, stress", KaizenCoral),
            ProfileItem("history", "📜", "History", "Past logs & trends", BrandGreen),
        ),
    ),
    ProfileGroup(
        "Your Plan", "📋", CardBlueLight,
        listOf(
            ProfileItem("plan", "📅", "Plan", "AI plan review", MovementColor),
            ProfileItem("coach", "🤖", "Coach", "Chat with your coach", KaizenLavender),
            ProfileItem("checkin", "⚖️", "Check-in", "Weekly weigh-in", BrandGreen),
        ),
    ),
    ProfileGroup(
        "Wellness", "🧘", CardLavenderLight,
        listOf(
            ProfileItem("mind", "🧠", "Mind", "Mindfulness & wellness", RecoveryColor),
            ProfileItem("discipline", "🎯", "Discipline", "Habit tracking", NutritionColor),
            ProfileItem("badges", "🏅", "Badges", "Achievements", Color(0xFFF4B740)),
        ),
    ),
    ProfileGroup(
        "Tools", "🔧", CardAmberLight,
        listOf(
            ProfileItem("medications", "💊", "Medications", "Reminders & log", KaizenCoral),
            ProfileItem("family", "👨‍👩‍👧", "Family", "Shared household", HydrationColor),
            ProfileItem("reports", "📊", "Reports", "Download PDF", BrandGreen),
            ProfileItem("barcode", "📷", "Barcode", "Scan a product", MovementColor),
        ),
    ),
    ProfileGroup(
        "Account", "⚙️", Color(0xFFF0F0F0),
        listOf(
            ProfileItem("settings", "⚙️", "Settings", "", Color(0xFF627069)),
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

    androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }

    when (selected) {
        null -> ProfileMenu(modifier) { selected = it }
        else -> Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                KaizenIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Profile",
                    onClick = { selected = null },
                )
                Text(
                    GROUPS.flatMap { it.items }.firstOrNull { it.key == selected }?.label ?: "Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
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
private fun ProfileMenu(modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        // Profile header with vibrant gradient
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HeroGradientTop, HeroGradientBottom)))
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = Spacing.xxl, bottom = Spacing.xl),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👤", fontSize = 36.sp)
                }
                Spacer(Modifier.height(Spacing.md))
                Text("Your Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("🌟 Health & wellness hub", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Groups as colored cards
        GROUPS.forEach { group ->
            Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                SectionHeader(group.title, emoji = group.emoji)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    elevation = CardDefaults.cardElevation(3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(vertical = Spacing.xs)) {
                        group.items.forEachIndexed { index, item ->
                            ProfileMenuRow(item = item, onClick = { onSelect(item.key) })
                            if (index != group.items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 64.dp),
                                    color = MaterialTheme.kaizenColors.divider,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ProfileMenuRow(item: ProfileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji = item.emoji, bgColor = item.tint.copy(alpha = 0.12f), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}
