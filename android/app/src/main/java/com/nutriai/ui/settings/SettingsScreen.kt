package com.nutriai.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.BuildConfig
import com.nutriai.notifications.ReminderGroup
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep

@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val remoteReminders by viewModel.serverReminders.collectAsStateWithLifecycle()
    val walkNudge by viewModel.walkNudge.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Import: pick a previously-exported JSON and re-log its food entries.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            else viewModel.importData(bytes) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        }
    }
    val onToggleReminder: (ReminderGroup, Boolean) -> Unit = { group, enabled ->
        if (enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setReminder(group, enabled)
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This deletes your account and logs you out. You have 7 days to change your mind - just log back in within a week to restore it. After that it's permanent.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteAccount(onLoggedOut)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = com.nutriai.ui.theme.Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(com.nutriai.ui.theme.Spacing.section),
        contentPadding = PaddingValues(vertical = com.nutriai.ui.theme.Spacing.md),
    ) {
        item { com.nutriai.ui.components.ScreenHeader("Settings") }

        // Profile.
        item {
            Column {
                com.nutriai.ui.components.SectionHeader("Profile")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.user?.firstName?.ifBlank { null } ?: "Your account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.user?.email?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                com.nutriai.ui.components.ListRow(
                    title = "Edit health profile",
                    subtitle = "Height, weight, goal, diet & conditions",
                    onClick = onEditProfile,
                )
            }
        }

        // Notifications.
        item {
            Column {
                com.nutriai.ui.components.SectionHeader("Notifications")
                ReminderGroup.entries.forEachIndexed { i, group ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    SwitchRow(
                        title = group.label,
                        subtitle = group.subtitle,
                        checked = reminders[group] ?: false,
                        onCheckedChange = { onToggleReminder(group, it) },
                    )
                }
                remoteReminders?.let { rp ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    WorkoutReminderRows(prefs = rp, onSave = { viewModel.saveReminderPrefs(it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                SwitchRow(
                    title = "Walk nudge",
                    subtitle = "A reminder to move when you've been still too long (uses Health Connect steps)",
                    checked = walkNudge,
                    onCheckedChange = { enabled ->
                        if (enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.setWalkNudge(enabled)
                    },
                )
            }
        }

        // Appearance.
        item {
            Column {
                com.nutriai.ui.components.SectionHeader("Appearance")
                Text("Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                    listOf("green" to "Calm Green", "pink" to "Pastel Pink", "yellow" to "Warm Yellow").forEach { (key, label) ->
                        FilterChip(selected = theme.accent == key, onClick = { viewModel.setAccent(key) }, label = { Text(label) })
                    }
                }
                Text("Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                        FilterChip(selected = theme.mode == key, onClick = { viewModel.setThemeMode(key) }, label = { Text(label) })
                    }
                }
            }
        }

        // Privacy & data.
        item {
            Column {
                com.nutriai.ui.components.SectionHeader("Privacy & data")
                Text(
                    "Export a JSON backup of your profile, food & water logs and check-ins, or restore food entries from a backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                com.nutriai.ui.components.ListRow(
                    title = "Export my data",
                    onClick = {
                        viewModel.exportData(
                            onBytes = { bytes ->
                                runCatching {
                                    val file = File(context.cacheDir, "kaizen-my-data.json")
                                    file.writeBytes(bytes)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "Export my data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure { Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_SHORT).show() }
                            },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                com.nutriai.ui.components.ListRow(
                    title = "Import data",
                    subtitle = "Restore food logs from a backup",
                    onClick = { runCatching { importLauncher.launch(arrayOf("application/json")) } },
                )
            }
        }

        // Account - destructive actions isolated at the bottom, clearly marked.
        item {
            Column {
                com.nutriai.ui.components.SectionHeader("Account")
                com.nutriai.ui.components.ListRow(title = "Log out", onClick = { viewModel.logout(onLoggedOut) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                com.nutriai.ui.components.ListRow(
                    title = "Delete account",
                    onClick = { showDelete = true },
                    trailing = { com.nutriai.ui.components.StatusIndicator(text = "Irreversible", status = com.nutriai.ui.components.Status.Critical) },
                )
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Kaizen v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Educational guidance, not medical advice - consult a professional.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** "11:00 AM" from 24h hour+minute. */
private fun fmt12(hour: Int, minute: Int): String {
    val ampm = if (hour < 12) "AM" else "PM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(h12, minute, ampm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutReminderRows(
    prefs: com.nutriai.data.remote.dto.ReminderPrefsDto,
    onSave: (com.nutriai.data.remote.dto.ReminderPrefsDto) -> Unit,
) {
    val parts = (prefs.workoutTime ?: "18:00").split(":")
    var hour by remember(prefs) { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 18) }
    var minute by remember(prefs) { mutableStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }
    var workout by remember(prefs) { mutableStateOf(prefs.workoutEnabled) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val tp = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("When do you work out?") },
            text = { TimePicker(state = tp) },
            confirmButton = {
                TextButton(onClick = {
                    hour = tp.hour; minute = tp.minute; showPicker = false
                    onSave(prefs.copy(workoutEnabled = workout, workoutTime = "%02d:%02d".format(hour, minute)))
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }

    SwitchRow(
        title = "Workout pre-alert",
        subtitle = "A nudge 10 minutes before your set time",
        checked = workout,
        onCheckedChange = { workout = it; onSave(prefs.copy(workoutEnabled = it, workoutTime = "%02d:%02d".format(hour, minute))) },
    )
    if (workout) {
        com.nutriai.ui.components.ListRow(
            title = "Workout time",
            subtitle = "${fmt12(hour, minute)} - heads-up at ${fmt12(if (minute >= 10) hour else (hour + 23) % 24, (minute + 50) % 60)}",
            onClick = { showPicker = true },
        )
    }
}
