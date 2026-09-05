package com.nutriai.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors

private data class TabItem(val route: String, val label: String, val iconFilled: ImageVector, val iconOutlined: ImageVector)

private const val LOG_TAB_ROUTE = "log"

private val TABS = listOf(
    TabItem("today", "Today", Icons.Filled.Home, Icons.Outlined.Home),
    TabItem("diet", "Nutrition", Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
    TabItem(LOG_TAB_ROUTE, "Log", Icons.Filled.Add, Icons.Filled.Add),
    TabItem("move", "Move", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    TabItem("me", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)

private fun tabRoute(index: Int): String = when (index) {
    1 -> "move"
    2 -> "diet"
    else -> "today"
}

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onCompleteProfile: () -> Unit,
    initialTab: Int = 0,
) {
    val navController = rememberNavController()
    val backEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route ?: "today"
    var showQuickLog by remember { mutableStateOf(false) }

    if (showQuickLog) {
        QuickLogSheet(onDismiss = { showQuickLog = false })
    }

    LaunchedEffect(initialTab) {
        val route = tabRoute(initialTab)
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.kaizenColors.pageBackground,
        bottomBar = {
            KaizenBottomNav(
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    if (route == LOG_TAB_ROUTE) {
                        showQuickLog = true
                    } else if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("today") {
                DashboardTab(
                    onLogout = onLogout,
                    onCompleteProfile = onCompleteProfile,
                    onOpenVitals = { navController.navigate("vitals") },
                    onOpenMove = {
                        navController.navigate("move") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("vitals") {
                com.nutriai.ui.vitals.VitalsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("diet") { com.nutriai.ui.diet.DietScreen(Modifier.fillMaxSize()) }
            composable("move") { com.nutriai.ui.move.MoveScreen(Modifier.fillMaxSize()) }
            composable("me") {
                MoreScreen(
                    Modifier.fillMaxSize(),
                    onEditProfile = onCompleteProfile,
                    onLoggedOut = onLogout,
                )
            }
        }
    }
}

@Composable
private fun KaizenBottomNav(currentRoute: String, onTabSelected: (String) -> Unit) {
    val tokens = MaterialTheme.kaizenColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.pageBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(tokens.cardGradientStart, tokens.cardGradientEnd),
                    ),
                )
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { item ->
                if (item.route == LOG_TAB_ROUTE) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    ),
                                ),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(LOG_TAB_ROUTE) },
                            )
                            .semantics { contentDescription = "Quick log" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Quick log",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    val selected = currentRoute == item.route
                    val bgColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        animationSpec = tween(250),
                        label = "tabBg",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        animationSpec = tween(250),
                        label = "navColor",
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(item.route) },
                            )
                            .padding(horizontal = if (selected) 14.dp else 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (selected) item.iconFilled else item.iconOutlined,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp),
                        )
                        if (selected) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTab(
    onLogout: () -> Unit,
    onCompleteProfile: () -> Unit,
    onOpenVitals: () -> Unit = {},
    onOpenMove: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val stepPerms = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )
    }
    val stepLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.loadSteps() }

    LaunchedEffect(Unit) { viewModel.refresh(); viewModel.loadSteps() }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This deletes your account and logs you out. You have 7 days to change your mind - just log back in within a week to restore it. After that it's permanent.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteAccount(onLogout)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }

    when {
        state.dashboard != null -> com.nutriai.ui.dashboard.PremiumDashboard(
            dashboard = state.dashboard!!,
            greetingName = state.firstName,
            onAddWater = { viewModel.logWater(250) },
            onCompleteProfile = onCompleteProfile,
            onLogout = { viewModel.logout(onLogout) },
            onDeleteAccount = { showDelete = true },
            steps = state.steps,
            stepsKcal = state.stepsKcal,
            stepsPermission = state.stepsPermission,
            stepsAvailable = state.stepsAvailable,
            heartRate = state.heartRate,
            sleepHours = state.sleepHours,
            manualHeartRate = state.manualHeartRate,
            stress = state.stress,
            onSaveVitals = { hr, s, sore -> viewModel.saveManualVitals(hr, s, sore) },
            soreness = state.soreness,
            safetyFlags = state.safetyFlags,
            riskFindings = state.riskFindings,
            weekDays = state.weekDays,
            weekKcalTarget = state.weekKcalTarget,
            maintenanceKcal = state.maintenanceKcal,
            coach = state.coach,
            rating = state.rating,
            todayWorkout = state.todayWorkout,
            onOpenVitals = onOpenVitals,
            onOpenMove = onOpenMove,
            onConnectSteps = {
                if (state.stepsAvailable) {
                    runCatching { stepLauncher.launch(stepPerms) }
                } else {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                            ),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        state.loading -> Column(
            Modifier.fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Text("Waking up your coach…", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "The free server can take up to ~30s to start on first open. Hang tight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            com.nutriai.ui.components.SkeletonList(count = 4, lines = 3, modifier = Modifier.padding(top = Spacing.xs))
        }
        else -> Column(
            Modifier.fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(state.error ?: "Couldn't load your dashboard.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onCompleteProfile, modifier = Modifier.fillMaxWidth()) { Text("Complete profile") }
            OutlinedButton(onClick = { viewModel.logout(onLogout) }, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        }
    }
}
