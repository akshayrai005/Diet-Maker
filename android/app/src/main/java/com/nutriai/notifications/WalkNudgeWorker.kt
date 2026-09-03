package com.nutriai.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nutriai.MainActivity
import com.nutriai.R
import com.nutriai.data.health.HealthConnectManager
import java.util.Calendar

/**
 * Step-aware walk nudge. Runs periodically (~90 min via WorkManager); if the user hasn't moved
 * meaningfully since the last check during waking hours, it suggests a short walk. Skips silently
 * when they DID move, when it's outside waking hours, or when Health Connect / step permission is
 * unavailable - so it never nags.
 */
class WalkNudgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = ReminderPrefs(applicationContext)
        if (!prefs.isWalkEnabled()) return Result.success()

        // Waking hours only (8:00-20:59).
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 8 || hour >= 21) return Result.success()

        val hc = HealthConnectManager(applicationContext)
        if (!hc.isAvailable() || !hc.hasStepPermission()) return Result.success() // graceful fallback

        val current = hc.readTodaySteps()
        val last = prefs.walkLastSteps()
        prefs.setWalkLastSteps(current)

        // New day (counter reset) → just re-baseline, don't nudge.
        if (current < last) return Result.success()

        val moved = current - last
        if (moved >= MOVED_THRESHOLD_STEPS) return Result.success() // they moved - no nudge

        // Android 13+ runtime permission.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return Result.success()

        ReminderNotifier.ensureChannel(applicationContext)
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderNotifier.EXTRA_TAB, 1) // open Move
        }
        val pending = android.app.PendingIntent.getActivity(
            applicationContext,
            NOTIF_ID,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "You've been still for a while - please go for a walk for at least 5 minutes."
        val notification = NotificationCompat.Builder(applicationContext, ReminderNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚶 Time to move")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "kaizen_walk_nudge"
        private const val NOTIF_ID = 990001
        /** Fewer steps than this since the last ~90-min check counts as "stagnant". */
        private const val MOVED_THRESHOLD_STEPS = 300L
    }
}
