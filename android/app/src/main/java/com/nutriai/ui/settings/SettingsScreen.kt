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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { SettingsHero(name = state.user?.firstName, email = state.user?.email) }

        item {
            SettingTile(icon = "🧍", title = "Edit health profile", subtitle = "Height, weight, goal, diet & conditions", onClick = onEditProfile)
        }

        item { RemindersCard(reminders = reminders, onToggle = onToggleReminder) }

        remoteReminders?.let { rp ->
            item {
                WorkoutReminderCard(
                    prefs = rp,
                    onSave = { viewModel.saveReminderPrefs(it) },
                )
            }
        }

        item {
            WalkNudgeCard(
                enabled = walkNudge,
                onToggle = { enabled ->
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

        item {
            ThemeCard(
                accent = theme.accent,
                mode = theme.mode,
                onAccent = viewModel::setAccent,
                onMode = viewModel::setThemeMode,
            )
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
                    Text(
                        "Export a JSON backup of your profile, food & water logs and check-ins, or restore food entries from a backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
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
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export my data") }
                    OutlinedButton(
                        onClick = { runCatching { importLauncher.launch(arrayOf("application/json")) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Import data (restore food logs)") }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
                    OutlinedButton(onClick = { viewModel.logout(onLoggedOut) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Log out")
                    }
                    OutlinedButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete account", color = MaterialTheme.colorScheme.error)
                    }
                }
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
private fun SettingsHero(name: String?, email: String?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(BrandGreen, BrandGreenDeep))).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Text(name?.firstOrNull()?.uppercase() ?: "👤", style = MaterialTheme.typography.titleLarge, color = Color.White) }
                Column {
                    Text(name?.ifBlank { "Your account" } ?: "Your account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    if (!email.isNullOrBlank()) {
                        Text(email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    accent: String,
    mode: String,
    onAccent: (String) -> Unit,
    onMode: (String) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎨 Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
            Text("Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("green" to "Calm Green", "pink" to "Pastel Pink", "yellow" to "Warm Yellow").forEach { (key, label) ->
                    FilterChip(selected = accent == key, onClick = { onAccent(key) }, label = { Text(label) })
                }
            }
            Text("Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    FilterChip(selected = mode == key, onClick = { onMode(key) }, label = { Text(label) })
                }
            }
        }
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
private fun WorkoutReminderCard(
    prefs: com.nutriai.data.remote.dto.ReminderPrefsDto,
    onSave: (com.nutriai.data.remote.dto.ReminderPrefsDto) -> Unit,
) {
    val parts = (prefs.workoutTime ?: "18:00").split(":")
    var hour by remember(prefs) { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 18) }
    var minute by remember(prefs) { mutableStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }
    var workout by remember(prefs) { mutableStateOf(prefs.workoutEnabled) }
    var walk by remember(prefs) { mutableStateOf(prefs.walkEnabled) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val tp = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("When do you work out?") },
            text = { TimePicker(state = tp) },
            confirmButton = { TextButton(onClick = { hour = tp.hour; minute = tp.minute; showPicker = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⏰ Workout & walk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Workout pre-alert", style = MaterialTheme.typography.bodyLarge)
                    Text("A nudge 10 minutes before your set time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = workout, onCheckedChange = { workout = it })
            }
            if (workout) {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Workout time:  ${fmt12(hour, minute)}", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "You'll get a heads-up at ${fmt12(if (minute >= 10) hour else (hour + 23) % 24, (minute + 50) % 60)} (10 min before).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Walk nudge", style = MaterialTheme.typography.bodyLarge)
                    Text("A reminder to move when you've been still too long", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = walk, onCheckedChange = { walk = it })
            }
            Button(
                onClick = { onSave(prefs.copy(workoutEnabled = workout, walkEnabled = walk, workoutTime = "%02d:%02d".format(hour, minute))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save preferences") }
        }
    }
}

@Composable
private fun RemindersCard(
    reminders: Map<ReminderGroup, Boolean>,
    onToggle: (ReminderGroup, Boolean) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🔔 Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
            Text(
                "Gentle on-device nudges. No account or internet needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReminderGroup.entries.forEachIndexed { i, group ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.label, style = MaterialTheme.typography.bodyLarge)
                        Text(group.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = reminders[group] ?: false,
                        onCheckedChange = { onToggle(group, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WalkNudgeCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🚶 Walk nudge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandGreen)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Remind me to move", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Uses Health Connect steps - if you've been sitting still during the day, a gentle nudge suggests a 5-minute walk. Needs step access; stays quiet at night.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun SettingTile(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
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
                ) { Text(icon) }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
