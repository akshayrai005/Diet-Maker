package com.nutriai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vitalsStore by preferencesDataStore(name = "vitals")

/** Manually-entered vitals - used when a watch/band doesn't sync to Health Connect. */
data class ManualVitals(
    val heartRate: Int? = null,
    /** Self-rated stress, 1 (calm) … 5 (very stressed). */
    val stress: Int? = null,
    /** Self-rated muscle soreness, 1 (none) … 5 (can barely move). Feeds into plan softening. */
    val soreness: Int? = null,
    /** Manually-entered hours slept last night - used when the watch doesn't sync sleep either. */
    val sleepHours: Double? = null,
    val updatedAtMillis: Long = 0L,
)

/**
 * Persists user-entered resting heart rate + stress. Many watches (e.g. Fastrack) don't write to
 * Health Connect, so this lets the user log those numbers by hand and still see them on the
 * dashboard. Local-only, offline.
 */
@Singleton
class VitalsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val hrKey = intPreferencesKey("manual_hr")
    private val stressKey = intPreferencesKey("manual_stress")
    private val sorenessKey = intPreferencesKey("manual_soreness")
    private val sleepKey = floatPreferencesKey("manual_sleep_hours")
    private val updatedKey = longPreferencesKey("updated_at")

    val vitals: Flow<ManualVitals> = context.vitalsStore.data.map { p ->
        ManualVitals(
            heartRate = p[hrKey],
            stress = p[stressKey],
            soreness = p[sorenessKey],
            sleepHours = p[sleepKey]?.toDouble(),
            updatedAtMillis = p[updatedKey] ?: 0L,
        )
    }

    suspend fun save(heartRate: Int?, stress: Int?, soreness: Int? = null, sleepHours: Double? = null, nowMillis: Long) {
        context.vitalsStore.edit { p ->
            if (heartRate != null && heartRate > 0) p[hrKey] = heartRate else p.remove(hrKey)
            if (stress != null && stress in 1..5) p[stressKey] = stress else p.remove(stressKey)
            if (soreness != null && soreness in 1..5) p[sorenessKey] = soreness else p.remove(sorenessKey)
            if (sleepHours != null && sleepHours in 0.0..24.0) p[sleepKey] = sleepHours.toFloat() else p.remove(sleepKey)
            p[updatedKey] = nowMillis
        }
    }
}
