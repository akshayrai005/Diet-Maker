package com.nutriai.data

import android.content.Context

/** When the user trains on a given day (morning / afternoon / evening / night). The diet plan times the meals around it. */
object GymTimes {
    val options = listOf("morning" to "Morning", "afternoon" to "Afternoon", "evening" to "Evening", "night" to "Night")
    private const val PREFS = "kaizen_prefs"
    private const val KEY = "gym_times"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** date (YYYY-MM-DD) -> time slug, without entries older than yesterday. */
    fun all(c: Context): Map<String, String> {
        val cutoff = java.time.LocalDate.now().minusDays(1).toString()
        return prefs(c).getString(KEY, "").orEmpty().split(';').mapNotNull { e ->
            val p = e.split('=')
            if (p.size == 2 && p[0] >= cutoff && options.any { it.first == p[1] }) p[0] to p[1] else null
        }.toMap()
    }

    fun get(c: Context, date: String): String? = all(c)[date]

    /** Sets (or clears, with null) the gym time for [date]. */
    fun set(c: Context, date: String, time: String?) {
        val m = all(c).toMutableMap()
        if (time == null) m.remove(date) else m[date] = time
        prefs(c).edit().putString(KEY, m.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }
}
