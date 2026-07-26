package com.nutriai.notifications

/** A single scheduled nudge. Times are local (device) time. */
data class ReminderJob(
    val key: String,
    val hour: Int,
    val minute: Int,
    val title: String,
    val text: String,
    /** Which home tab to open when the notification is tapped: 0 Home, 1 Plan, 2 Log. */
    val tab: Int = 0,
    /** 1=Mon … 7=Sun for weekly jobs; null = every day. */
    val weeklyDayIso: Int? = null,
)

/** Toggle groups the user controls in Settings. */
enum class ReminderGroup(val label: String, val subtitle: String) {
    MEALS("Meal reminders", "Breakfast, lunch & dinner windows"),
    WATER("Hydration reminders", "Gentle water nudges through the day"),
    WORKOUT("Workout pre-alert", "Fires 10 min before your set workout time"),
    WEIGH_IN("Weekly weigh-in", "A Monday-morning check-in nudge"),
}

/** The fixed catalog of reminders per group. Deterministic, zero-cost, all local. */
object ReminderCatalog {
    fun jobs(group: ReminderGroup): List<ReminderJob> = when (group) {
        // Meal windows: breakfast 8-10, lunch 12:30-2, dinner by 7-8:30 → remind at the window start.
        ReminderGroup.MEALS -> listOf(
            ReminderJob("meal_breakfast", 8, 0, "🍳 Breakfast (8-10 am)", "Fuel up and log your breakfast.", tab = 2),
            ReminderJob("meal_lunch", 12, 30, "🍽️ Lunch (12:30-2 pm)", "Time for lunch - log it to stay on target.", tab = 2),
            ReminderJob("meal_dinner", 19, 0, "🌙 Dinner (7-8:30 pm)", "Aim to finish dinner by 8:30. Log your meal.", tab = 2),
        )
        // Hydration nudges through waking hours (quiet hours 22:00-07:00 suppress any that land late).
        ReminderGroup.WATER -> listOf(9, 11, 13, 15, 17, 19).map { h ->
            ReminderJob("water_$h", h, 0, "💧 Hydration check", "Time for a glass of water - log it.", tab = 0)
        }
        ReminderGroup.WORKOUT -> listOf(workoutJob(18, 0))
        ReminderGroup.WEIGH_IN -> listOf(
            ReminderJob("weigh_in", 7, 30, "⚖️ Weekly weigh-in", "Log this week's weight to track your progress.", tab = 0, weeklyDayIso = 1),
        )
    }

    const val WORKOUT_KEY = "workout_prealert"

    /** The workout pre-alert job - fires 10 minutes before the given workout time (wraps past midnight). */
    fun workoutJob(hour: Int, minute: Int): ReminderJob {
        var m = minute - 10
        var h = hour
        if (m < 0) { m += 60; h = (h + 23) % 24 }
        return ReminderJob(
            WORKOUT_KEY, h, m,
            "🏋️ Get set for exercise",
            "Your workout is in 10 minutes - tap to open today's plan.",
            tab = 1,
        )
    }

    val allGroups: List<ReminderGroup> = ReminderGroup.entries

    /** All jobs across every group (used to look one up by key when rescheduling). */
    val allJobs: List<ReminderJob> = allGroups.flatMap { jobs(it) }

    fun jobByKey(key: String): ReminderJob? = allJobs.firstOrNull { it.key == key }
}
