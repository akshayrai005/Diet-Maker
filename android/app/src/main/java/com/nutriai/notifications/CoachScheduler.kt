package com.nutriai.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules the SMART COACH's data-driven nudges (spec Section 16) as daily exact alarms, separate
 * from the fixed-text reminders in [ReminderScheduler]. Two fire each day:
 *  - MIDDAY (15:00): a "starvation" check - if nothing has been logged yet, nudge the user to eat.
 *  - SUMMARY (21:00): an end-of-day summary that reads today's dashboard and says exactly how much
 *    protein / calories / water are still left, with a concrete suggestion to close the gap.
 *
 * The alarm only carries the MODE; [CoachReceiver] fetches the live numbers at fire time so the
 * message reflects the actual day. Same exact-alarm approach as the reminders, so nudges land on the
 * clock and aren't batched by Doze.
 */
object CoachScheduler {
    const val ACTION_MIDDAY = "com.nutriai.COACH_MIDDAY"
    const val ACTION_SUMMARY = "com.nutriai.COACH_SUMMARY"
    const val ACTION_GYM_CHECK = "com.nutriai.COACH_GYM_CHECK"
    const val ACTION_LATE_NIGHT = "com.nutriai.COACH_LATE_NIGHT"

    /** action → (hour of day, PendingIntent request code). */
    private val ALARMS: Map<String, Pair<Int, Int>> = mapOf(
        ACTION_MIDDAY to (15 to 810001),
        ACTION_SUMMARY to (21 to 810002),
        ACTION_GYM_CHECK to (21 to 810003),
        ACTION_LATE_NIGHT to (22 to 810004),
    )

    /** (Re)schedules all coach alarms for their next occurrence. Safe to call repeatedly. */
    fun schedule(context: Context) {
        ReminderNotifier.ensureChannel(context)
        ALARMS.keys.forEach { armNext(context, it) }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ALARMS.forEach { (action, cfg) ->
            val pi = PendingIntent.getBroadcast(
                context, cfg.second, Intent(context, CoachReceiver::class.java).setAction(action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
    }

    /** Arms the next occurrence of one coach alarm. Called on setup and re-armed by the receiver. */
    fun armNext(context: Context, action: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cfg = ALARMS[action] ?: return
        val hour = cfg.first
        val rc = cfg.second

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_MONTH, 1)

        val pi = PendingIntent.getBroadcast(
            context, rc, Intent(context, CoachReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }
}
