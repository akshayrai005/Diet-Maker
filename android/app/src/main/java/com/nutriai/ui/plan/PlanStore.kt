package com.nutriai.ui.plan

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** One planned food item — totals for the whole item (not per-100g). */
@Serializable
data class PlanFood(val name: String, val kcal: Double, val proteinG: Double, val slot: String = "breakfast")

/** One planned exercise; [done] is ticked on the actual day for adherence. */
@Serializable
data class PlanExercise(
    val name: String,
    val sets: Int = 3,
    val reps: String = "8-12",
    val muscleGroup: String? = null,
    val done: Boolean = false,
)

/** A full next-day plan, stored on-device keyed by date (yyyy-MM-dd). */
@Serializable
data class DayPlan(
    val date: String,
    val trainerNotes: String = "",
    val foods: List<PlanFood> = emptyList(),
    val exercises: List<PlanExercise> = emptyList(),
    val bedtime: String = "23:00",
    val waketime: String = "07:00",
    val aiReview: String = "",
) {
    val plannedKcal get() = foods.sumOf { it.kcal }
    val plannedProtein get() = foods.sumOf { it.proteinG }
}

private val Context.planStore by preferencesDataStore(name = "day_plans")

/**
 * On-device persistence for next-day plans (spec Section 21). Each date's [DayPlan] is stored as a
 * JSON string under its own key, so plans are independent and survive app restarts without a backend.
 */
@Singleton
class PlanStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun key(date: String) = stringPreferencesKey("plan_$date")

    suspend fun load(date: String): DayPlan? {
        val raw = context.planStore.data.first()[key(date)] ?: return null
        return runCatching { json.decodeFromString<DayPlan>(raw) }.getOrNull()
    }

    suspend fun save(plan: DayPlan) {
        context.planStore.edit { it[key(plan.date)] = json.encodeToString(DayPlan.serializer(), plan) }
    }
}
