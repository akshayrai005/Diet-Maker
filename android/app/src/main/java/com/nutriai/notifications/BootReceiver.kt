package com.nutriai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms all enabled reminders after a reboot or app update. WorkManager one-time jobs don't
 * survive a device restart, so without this a phone reboot would silently drop every scheduled
 * nudge until the user next opened the app.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var prefs: ReminderPrefs

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.apply(prefs.snapshot())
                CoachScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
