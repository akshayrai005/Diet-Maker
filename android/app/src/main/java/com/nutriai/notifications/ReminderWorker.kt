package com.nutriai.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.nutriai.MainActivity
import com.nutriai.R

/** Posts a single local reminder notification. All content is passed via inputData. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val text = inputData.getString(KEY_TEXT).orEmpty()
        val notifId = inputData.getInt(KEY_NOTIF_ID, title.hashCode())

        ensureChannel(applicationContext)

        // Android 13+ requires runtime POST_NOTIFICATIONS; skip silently if not granted.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TAB, inputData.getInt(KEY_TAB, 0))
        }
        val pending = android.app.PendingIntent.getActivity(
            applicationContext,
            notifId,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        // Quiet hours (22:00–06:59): suppress gentle hydration nudges so they never wake anyone.
        // User-set workout pre-alerts, meals and weigh-in still fire as scheduled.
        val jobKey = inputData.getString(KEY_JOB_KEY).orEmpty()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val quietHours = hour >= 22 || hour < 7
        val suppress = quietHours && jobKey.startsWith("water")

        if (!suppress) {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            runCatching { NotificationManagerCompat.from(applicationContext).notify(notifId, notification) }
        }

        // Re-schedule this reminder for its next occurrence (tomorrow / next weekly day).
        // This is what keeps a one-time job repeating at the exact clock time without drift.
        inputData.getString(KEY_JOB_KEY)?.let { key ->
            // The workout pre-alert re-arms from the user's stored time; medication reminders
            // re-arm from the hour/minute carried in inputData (so they stay dynamic per med);
            // all others come from the fixed catalog.
            val job = when {
                key == ReminderCatalog.WORKOUT_KEY -> {
                    val (h, m) = ReminderPrefs(applicationContext).workoutTime()
                    ReminderCatalog.workoutJob(h, m)
                }
                key.startsWith(MED_KEY_PREFIX) -> {
                    val h = inputData.getInt(KEY_HOUR, -1)
                    val m = inputData.getInt(KEY_MINUTE, -1)
                    if (h in 0..23 && m in 0..59) {
                        ReminderJob(key, h, m, title, text, tab = inputData.getInt(KEY_TAB, 0))
                    } else {
                        null
                    }
                }
                else -> ReminderCatalog.jobByKey(key)
            }
            job?.let { runCatching { ReminderScheduler.enqueue(applicationContext, it, ExistingWorkPolicy.REPLACE) } }
        }
        return Result.success()
    }

    companion object {
        // v2: recreated at HIGH importance so nudges show as heads-up banners (channel importance
        // is immutable once created, so upgrading requires a new id).
        const val CHANNEL_ID = "kaizen_reminders_v2"
        const val KEY_JOB_KEY = "job_key"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_NOTIF_ID = "notif_id"
        const val KEY_TAB = "tab"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val EXTRA_TAB = "com.nutriai.OPEN_TAB"

        /** Unique-work key prefix for per-med, per-time dose reminders (keyed by med id + time). */
        const val MED_KEY_PREFIX = "med::"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(NotificationManager::class.java)
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Reminders",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Workout, meal, hydration and weigh-in nudges" }
                    mgr.createNotificationChannel(channel)
                }
            }
        }
    }
}
