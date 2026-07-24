package com.nutriai.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("home", "Home", Icons.Filled.Home),
    TabItem("diet", "Diet", Icons.Filled.Restaurant),
    TabItem("move", "Move", Icons.Filled.FitnessCenter),
    TabItem("me", "Me", Icons.Filled.Person),
)

/** Maps a notification's requested tab index to the new tab routes. */
private fun tabRoute(index: Int): String = when (index) {
    1 -> "move" // 5 AM workout reminder
    2 -> "diet" // meal reminders → diet (plan/log/grocery)
    else -> "home"
}

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onCompleteProfile: () -> Unit,
    initialTab: Int = 0,
) {
    val navController = rememberNavController()
    val backEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route ?: "home"

    // A notification tap (or new intent) can request a specific tab while Home is showing.
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
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                ) {
                    TABS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("home") { DashboardTab(onLogout = onLogout, onCompleteProfile = onCompleteProfile) }
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
private fun DashboardTab(
    onLogout: () -> Unit,
    onCompleteProfile: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Health Connect step-permission request.
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

    // Refresh every time the Home tab becomes visible (e.g. after logging food).
    LaunchedEffect(Unit) { viewModel.refresh(); viewModel.loadSteps() }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and all your data from our servers. This cannot be undone.") },
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
            onSaveVitals = { hr, s -> viewModel.saveManualVitals(hr, s) },
            safetyFlags = state.safetyFlags,
            riskFindings = state.riskFindings,
            weekDays = state.weekDays,
            weekKcalTarget = state.weekKcalTarget,
            maintenanceKcal = state.maintenanceKcal,
            coach = state.coach,
            rating = state.rating,
            onConnectSteps = {
                if (state.stepsAvailable) {
                    runCatching { stepLauncher.launch(stepPerms) }
                } else {
                    // Health Connect not installed — send them to the Play Store.
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
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text("Waking up your coach…", style = MaterialTheme.typography.titleMedium)
            Text(
                "The free server can take up to ~30s to start on first open. Hang tight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.error ?: "Couldn't load your dashboard.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onCompleteProfile, modifier = Modifier.fillMaxWidth()) { Text("Complete profile") }
            OutlinedButton(onClick = { viewModel.logout(onLogout) }, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        }
    }
}
