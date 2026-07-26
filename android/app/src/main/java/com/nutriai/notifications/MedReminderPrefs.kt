package com.nutriai.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.medReminderStore by preferencesDataStore(name = "med_reminders")

/**
 * Remembers which "HH:mm" dose-reminder times are currently scheduled per medication, so a med's
 * reminders can be cancelled precisely (WorkManager cancels by unique-work key) when times change,
 * the med is toggled inactive, or it is deleted. WorkManager itself persists the enqueued jobs
 * across reboots; this store only tracks what's live so we can tear it down cleanly.
 */
@Singleton
class MedReminderPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val idsKey = stringSetPreferencesKey("med_ids")
    private fun timesKey(id: String) = stringSetPreferencesKey("times_$id")

    suspend fun scheduledTimes(id: String): Set<String> =
        context.medReminderStore.data.first()[timesKey(id)] ?: emptySet()

    suspend fun scheduledIds(): Set<String> =
        context.medReminderStore.data.first()[idsKey] ?: emptySet()

    suspend fun setTimes(id: String, times: Set<String>) {
        context.medReminderStore.edit { prefs ->
            if (times.isEmpty()) {
                prefs.remove(timesKey(id))
                prefs[idsKey] = (prefs[idsKey] ?: emptySet()) - id
            } else {
                prefs[timesKey(id)] = times
                prefs[idsKey] = (prefs[idsKey] ?: emptySet()) + id
            }
        }
    }

    suspend fun clear(id: String) = setTimes(id, emptySet())
}
