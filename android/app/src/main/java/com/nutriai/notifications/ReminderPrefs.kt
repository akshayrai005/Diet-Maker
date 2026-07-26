package com.nutriai.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderStore by preferencesDataStore(name = "reminders")

/** Persisted on/off state per reminder group. Meals + water default ON on first run. */
@Singleton
class ReminderPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun key(group: ReminderGroup) = booleanPreferencesKey("enabled_${group.name}")

    private fun default(group: ReminderGroup) = group != ReminderGroup.WEIGH_IN

    val settings: Flow<Map<ReminderGroup, Boolean>> = context.reminderStore.data.map { prefs ->
        ReminderCatalog.allGroups.associateWith { g -> prefs[key(g)] ?: default(g) }
    }

    suspend fun isEnabled(group: ReminderGroup): Boolean =
        context.reminderStore.data.first()[key(group)] ?: default(group)

    suspend fun setEnabled(group: ReminderGroup, enabled: Boolean) {
        context.reminderStore.edit { it[key(group)] = enabled }
    }

    suspend fun snapshot(): Map<ReminderGroup, Boolean> = settings.first()

    // ---- Workout pre-alert time (local) ----
    private val workoutHourKey = intPreferencesKey("workout_hour")
    private val workoutMinKey = intPreferencesKey("workout_min")

    /** The user's set workout time (hour, minute); defaults to 18:00. The pre-alert fires 10 min before. */
    suspend fun workoutTime(): Pair<Int, Int> {
        val p = context.reminderStore.data.first()
        return (p[workoutHourKey] ?: 18) to (p[workoutMinKey] ?: 0)
    }

    suspend fun setWorkoutTime(hour: Int, minute: Int) {
        context.reminderStore.edit { it[workoutHourKey] = hour; it[workoutMinKey] = minute }
    }

    // ---- Step-aware walk nudge ----
    private val walkEnabledKey = booleanPreferencesKey("walk_enabled")
    private val walkLastStepsKey = longPreferencesKey("walk_last_steps")

    val walkEnabled: Flow<Boolean> = context.reminderStore.data.map { it[walkEnabledKey] ?: false }

    suspend fun isWalkEnabled(): Boolean = context.reminderStore.data.first()[walkEnabledKey] ?: false

    suspend fun setWalkEnabled(enabled: Boolean) {
        context.reminderStore.edit { it[walkEnabledKey] = enabled }
    }

    /** The step count at the previous walk-nudge check (to detect movement since then). */
    suspend fun walkLastSteps(): Long = context.reminderStore.data.first()[walkLastStepsKey] ?: 0L

    suspend fun setWalkLastSteps(steps: Long) {
        context.reminderStore.edit { it[walkLastStepsKey] = steps }
    }
}
