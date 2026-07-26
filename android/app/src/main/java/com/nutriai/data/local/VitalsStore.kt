package com.nutriai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    private val updatedKey = longPreferencesKey("updated_at")

    val vitals: Flow<ManualVitals> = context.vitalsStore.data.map { p ->
        ManualVitals(
            heartRate = p[hrKey],
            stress = p[stressKey],
            updatedAtMillis = p[updatedKey] ?: 0L,
        )
    }

    suspend fun save(heartRate: Int?, stress: Int?, nowMillis: Long) {
        context.vitalsStore.edit { p ->
            if (heartRate != null && heartRate > 0) p[hrKey] = heartRate else p.remove(hrKey)
            if (stress != null && stress in 1..5) p[stressKey] = stress else p.remove(stressKey)
            p[updatedKey] = nowMillis
        }
    }
}
