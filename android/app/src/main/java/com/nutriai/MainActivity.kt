package com.nutriai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nutriai.data.local.ThemePrefs
import com.nutriai.data.local.ThemeStore
import com.nutriai.notifications.ReminderPrefs
import com.nutriai.notifications.ReminderScheduler
import com.nutriai.notifications.ReminderWorker
import com.nutriai.ui.navigation.AppRoot
import com.nutriai.ui.theme.NutriAiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var reminderPrefs: ReminderPrefs
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var themeStore: ThemeStore

    private val openTab = mutableIntStateOf(0)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* worker re-checks at fire time */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        openTab.intValue = intent?.getIntExtra(ReminderWorker.EXTRA_TAB, 0) ?: 0

        // Ensure the user's enabled reminders are scheduled (survives reboot via WorkManager).
        lifecycleScope.launch { reminderScheduler.apply(reminderPrefs.snapshot()) }
        maybeRequestNotificationPermission()

        setContent {
            val theme by themeStore.prefs.collectAsState(initial = ThemePrefs())
            val dark = when (theme.mode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            NutriAiTheme(darkTheme = dark, accent = theme.accent) {
                // Opaque, full-screen background so the launcher never shows through.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val startTab by openTab
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        AppRoot(startTab = startTab)
                    }
                }
            }
        }
    }

    // A notification tapped while the app is already running switches to its tab.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTab.intValue = intent.getIntExtra(ReminderWorker.EXTRA_TAB, 0)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
