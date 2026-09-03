package com.nutriai.notifications

import android.content.Context
import com.nutriai.data.remote.dto.MedicationDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules daily local dose reminders for active medications/supplements, reusing the existing
 * exact-alarm clock-anchored + self-re-arm pattern ([ReminderScheduler.schedule] / [AlarmReceiver]).
 * Each (med, time) pair is its own alarm keyed `med::{id}::{HH:mm}`, so a single dose can be cancelled
 * without touching the others. Notifications post on the shared HIGH channel
 * ([ReminderNotifier.CHANNEL_ID]); dose reminders are important so they are not suppressed by quiet
 * hours (only hydration nudges are).
 */
@Singleton
class MedReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: MedReminderPrefs,
) {
    private fun keyFor(id: String, time: String) = "${ReminderNotifier.MED_KEY_PREFIX}$id::$time"

    /** Parses "HH:mm" → (hour, minute) if valid, else null. */
    private fun parse(time: String): Pair<Int, Int>? {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h to m else null
    }

    /** Schedules/refreshes a single med's dose reminders; cancels any of its times that were dropped. */
    suspend fun scheduleMed(med: MedicationDto) {
        if (!med.active || med.times.isEmpty()) {
            cancelMed(med.id)
            return
        }
        ReminderNotifier.ensureChannel(context)
        val valid = med.times.filter { parse(it) != null }.toSet()
        val previous = prefs.scheduledTimes(med.id)
        // Cancel times that are no longer part of this med.
        (previous - valid).forEach { ReminderScheduler.cancel(context, keyFor(med.id, it)) }
        // (Re)schedule current times as exact alarms anchored to each dose time.
        val dose = med.dose?.takeIf { it.isNotBlank() }
        valid.forEach { time ->
            val (h, m) = parse(time) ?: return@forEach
            val job = ReminderJob(
                key = keyFor(med.id, time),
                hour = h,
                minute = m,
                title = "💊 ${med.name}",
                text = "Time for your ${dose ?: med.name}. Tap to see your medicines.",
                tab = 0,
            )
            ReminderScheduler.schedule(context, job)
        }
        prefs.setTimes(med.id, valid)
    }

    /** Cancels every scheduled dose reminder for a med (toggled inactive or deleted). */
    suspend fun cancelMed(id: String) {
        prefs.scheduledTimes(id).forEach { ReminderScheduler.cancel(context, keyFor(id, it)) }
        prefs.clear(id)
    }

    /**
     * Reconciles the whole medication list with what's scheduled: cancels reminders for meds that
     * are gone or now inactive, and (re)schedules the active ones with times. Call after loading meds.
     */
    suspend fun sync(meds: List<MedicationDto>) {
        val activeWithTimes = meds.filter { it.active && it.times.isNotEmpty() }
        val activeIds = activeWithTimes.map { it.id }.toSet()
        (prefs.scheduledIds() - activeIds).forEach { cancelMed(it) }
        activeWithTimes.forEach { scheduleMed(it) }
    }
}
