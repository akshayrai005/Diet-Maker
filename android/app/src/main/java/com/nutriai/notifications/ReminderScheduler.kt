package com.nutriai.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules local reminder notifications with EXACT alarms via [AlarmManager] - 100% on-device, no
 * push service.
 *
 * Each reminder is a one-shot exact alarm ([AlarmManager.setExactAndAllowWhileIdle]) set for the next
 * occurrence of its target local time; when it fires, [AlarmReceiver] posts the notification and
 * re-arms the next occurrence. Exact alarms are the right tool for clock-anchored reminders: unlike
 * WorkManager delayed jobs (the previous approach), they are NOT batched by Doze into a single
 * maintenance window - which is what made every reminder arrive at once, or hours late. When the OS
 * won't grant exact-alarm scheduling (Android 12/13 with the permission denied), we fall back to an
 * inexact allow-while-idle alarm so reminders still fire, just not to-the-minute.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Applies the given on/off map: schedules enabled groups, cancels disabled ones. */
    suspend fun apply(settings: Map<ReminderGroup, Boolean>) {
        settings.forEach { (group, enabled) ->
            if (enabled) scheduleGroup(group) else cancelGroup(group)
        }
    }

    suspend fun scheduleGroup(group: ReminderGroup) {
        ReminderNotifier.ensureChannel(context)
        val jobs = if (group == ReminderGroup.WORKOUT) {
            val (h, m) = ReminderPrefs(context).workoutTime()
            listOf(ReminderCatalog.workoutJob(h, m))
        } else {
            ReminderCatalog.jobs(group)
        }
        jobs.forEach { schedule(context, it) }
    }

    fun cancelGroup(group: ReminderGroup) {
        ReminderCatalog.jobs(group).forEach { cancel(context, it.key) }
    }

    /**
     * Step-aware walk nudge. Unlike the clock-anchored reminders this is genuinely periodic
     * (~90 min) and stays on WorkManager, because it only reacts to Health Connect step deltas -
     * exact timing doesn't matter, and the worker itself gates on waking hours + movement.
     */
    fun scheduleWalkNudge() {
        ReminderNotifier.ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<WalkNudgeWorker>(90, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WalkNudgeWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelWalkNudge() {
        workManager.cancelUniqueWork(WalkNudgeWorker.UNIQUE_NAME)
    }

    companion object {
        const val TAG = "nutriai_reminder"

        /** Stable request code per reminder key so re-scheduling replaces the same alarm. */
        private fun requestCode(key: String): Int = key.hashCode()

        private fun pendingIntent(context: Context, job: ReminderJob, mutableForCancel: Boolean = false): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.nutriai.REMINDER_$" + job.key
                putExtra(ReminderNotifier.KEY_JOB_KEY, job.key)
                putExtra(ReminderNotifier.KEY_TITLE, job.title)
                putExtra(ReminderNotifier.KEY_TEXT, job.text)
                putExtra(ReminderNotifier.KEY_NOTIF_ID, requestCode(job.key))
                putExtra(ReminderNotifier.KEY_TAB, job.tab)
                putExtra(ReminderNotifier.KEY_HOUR, job.hour)
                putExtra(ReminderNotifier.KEY_MINUTE, job.minute)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode(job.key),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** (Re)schedules an exact alarm for the job's next occurrence. Replaces any existing one. */
        fun schedule(context: Context, job: ReminderJob) {
            ReminderNotifier.ensureChannel(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = nextOccurrenceMs(job)
            val pi = pendingIntent(context, job)
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            runCatching {
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    // Permission not granted: still fire (allow-while-idle), just not to-the-minute.
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        }

        /** Cancels a scheduled reminder by key. */
        fun cancel(context: Context, key: String) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.nutriai.REMINDER_$" + key
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(key),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }

        /** Absolute wall-clock millis of the next occurrence of the job's local time (and weekday). */
        private fun nextOccurrenceMs(job: ReminderJob): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, job.hour)
                set(Calendar.MINUTE, job.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (job.weeklyDayIso != null) {
                // Calendar.DAY_OF_WEEK: Sunday=1..Saturday=7; ISO: Monday=1..Sunday=7.
                val targetCal = if (job.weeklyDayIso == 7) Calendar.SUNDAY else job.weeklyDayIso + 1
                while (next.get(Calendar.DAY_OF_WEEK) != targetCal || !next.after(now)) {
                    next.add(Calendar.DAY_OF_MONTH, 1)
                }
            } else if (!next.after(now)) {
                next.add(Calendar.DAY_OF_MONTH, 1)
            }
            return next.timeInMillis
        }
    }
}
