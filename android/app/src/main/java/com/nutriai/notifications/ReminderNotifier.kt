package com.nutriai.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nutriai.MainActivity
import com.nutriai.R
import java.util.Calendar

/**
 * Central place that OWNS the reminders notification channel and posts a single local notification.
 * Shared by the exact-alarm reminder pipeline ([AlarmReceiver]) and the step-aware walk nudge
 * ([WalkNudgeWorker]) so channel id, importance and tap-to-open behaviour stay identical everywhere.
 */
object ReminderNotifier {
    // v2: HIGH importance so nudges show as heads-up banners (channel importance is immutable once
    // created, so upgrading requires a new id).
    const val CHANNEL_ID = "kaizen_reminders_v2"
    const val EXTRA_TAB = "com.nutriai.OPEN_TAB"

    // Intent-extra keys carried on the alarm PendingIntent through to [AlarmReceiver].
    const val KEY_JOB_KEY = "job_key"
    const val KEY_TITLE = "title"
    const val KEY_TEXT = "text"
    const val KEY_NOTIF_ID = "notif_id"
    const val KEY_TAB = "tab"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"

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

    /**
     * Posts one reminder notification, applying quiet-hours suppression for gentle hydration nudges
     * (22:00-06:59). No-ops silently when Android 13+ notification permission isn't granted.
     */
    fun post(context: Context, jobKey: String, title: String, text: String, notifId: Int, tab: Int) {
        ensureChannel(context)

        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        // Quiet hours (22:00-06:59): suppress gentle hydration nudges so they never wake anyone.
        // User-set workout pre-alerts, meals, meds and weigh-in still fire as scheduled.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val quietHours = hour >= 22 || hour < 7
        if (quietHours && jobKey.startsWith("water")) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TAB, tab)
        }
        val pending = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
    }
}
