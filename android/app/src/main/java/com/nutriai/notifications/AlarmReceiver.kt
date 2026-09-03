package com.nutriai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when an exact alarm ([ReminderScheduler]) lands on a reminder's clock time. It posts the
 * notification and immediately re-arms the SAME reminder for its next occurrence (tomorrow / next
 * weekly day). This exact-alarm + self-re-arm loop is what fixes the old WorkManager behaviour where
 * Doze batched every delayed job and released them together in one maintenance window - so reminders
 * all arrived at once, or late. Exact alarms fire at the real clock time, one at a time.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderNotifier.KEY_JOB_KEY).orEmpty()
        val title = intent.getStringExtra(ReminderNotifier.KEY_TITLE) ?: return
        val text = intent.getStringExtra(ReminderNotifier.KEY_TEXT).orEmpty()
        val notifId = intent.getIntExtra(ReminderNotifier.KEY_NOTIF_ID, title.hashCode())
        val tab = intent.getIntExtra(ReminderNotifier.KEY_TAB, 0)

        ReminderNotifier.post(context, key, title, text, notifId, tab)

        // Re-arm the next occurrence. The workout pre-alert re-reads the user's stored time (a suspend
        // DataStore read, hence goAsync + coroutine); med doses re-arm from the hour/minute carried on
        // the intent; everything else from the fixed catalog.
        when {
            key == ReminderCatalog.WORKOUT_KEY -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val (h, m) = ReminderPrefs(context).workoutTime()
                        runCatching { ReminderScheduler.schedule(context, ReminderCatalog.workoutJob(h, m)) }
                    } finally {
                        pending.finish()
                    }
                }
            }
            key.startsWith(ReminderNotifier.MED_KEY_PREFIX) -> {
                val h = intent.getIntExtra(ReminderNotifier.KEY_HOUR, -1)
                val m = intent.getIntExtra(ReminderNotifier.KEY_MINUTE, -1)
                if (h in 0..23 && m in 0..59) {
                    runCatching { ReminderScheduler.schedule(context, ReminderJob(key, h, m, title, text, tab = tab)) }
                }
            }
            else -> ReminderCatalog.jobByKey(key)?.let { runCatching { ReminderScheduler.schedule(context, it) } }
        }
    }
}
