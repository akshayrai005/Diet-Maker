package com.nutriai.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules local reminder notifications via WorkManager — 100% on-device, no push service.
 *
 * Each reminder is a ONE-TIME job whose initial delay lands on the next occurrence of its target
 * local time; when it fires, [ReminderWorker] re-enqueues the next occurrence. This is deliberate:
 * a PeriodicWorkRequest cannot hit an exact clock time — after its first run it drifts every 24h
 * and Doze can defer it by hours, which made a 12:30 reminder fire at random times. Re-computing
 * the next occurrence on each fire keeps every nudge anchored to its clock time.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: ReminderPrefs,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Applies the given on/off map: schedules enabled groups, cancels disabled ones. */
    suspend fun apply(settings: Map<ReminderGroup, Boolean>) {
        settings.forEach { (group, enabled) ->
            if (enabled) scheduleGroup(group) else cancelGroup(group)
        }
    }

    suspend fun scheduleGroup(group: ReminderGroup) {
        ReminderWorker.ensureChannel(context)
        // The workout pre-alert is scheduled from the user's set time; the rest are fixed.
        val jobs = if (group == ReminderGroup.WORKOUT) {
            val (h, m) = prefs.workoutTime()
            listOf(ReminderCatalog.workoutJob(h, m))
        } else {
            ReminderCatalog.jobs(group)
        }
        // REPLACE: re-anchor to the next occurrence and cleanly migrate any stale periodic job
        // from an older app version. It never fires early — the delay is always to a future time.
        jobs.forEach { enqueue(context, it, ExistingWorkPolicy.REPLACE) }
    }

    fun cancelGroup(group: ReminderGroup) {
        ReminderCatalog.jobs(group).forEach { workManager.cancelUniqueWork(it.key) }
    }

    /**
     * Step-aware walk nudge. Unlike the clock-anchored reminders this is genuinely periodic
     * (~90 min), because it only reacts to Health Connect step deltas — exact timing doesn't matter,
     * and the worker itself gates on waking hours + movement so it never nags.
     */
    fun scheduleWalkNudge() {
        ReminderWorker.ensureChannel(context)
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

        /**
         * Enqueues a single one-time reminder for its next occurrence. [policy] is KEEP when
         * (re)applying settings (don't disturb an existing schedule) and REPLACE when the worker
         * re-schedules itself for the following day.
         */
        fun enqueue(context: Context, job: ReminderJob, policy: ExistingWorkPolicy) {
            val data = Data.Builder()
                .putString(ReminderWorker.KEY_JOB_KEY, job.key)
                .putString(ReminderWorker.KEY_TITLE, job.title)
                .putString(ReminderWorker.KEY_TEXT, job.text)
                .putInt(ReminderWorker.KEY_NOTIF_ID, job.key.hashCode())
                .putInt(ReminderWorker.KEY_TAB, job.tab)
                .putInt(ReminderWorker.KEY_HOUR, job.hour)
                .putInt(ReminderWorker.KEY_MINUTE, job.minute)
                .build()

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(initialDelayMs(job), TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(job.key, policy, request)
        }

        /** Milliseconds from now until the next occurrence of the job's local time (and weekday). */
        private fun initialDelayMs(job: ReminderJob): Long {
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
            return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0)
        }
    }
}
