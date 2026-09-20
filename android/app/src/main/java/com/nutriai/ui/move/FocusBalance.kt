package com.nutriai.ui.move

import android.content.Context
import com.nutriai.data.remote.dto.ExerciseLogDto
import com.nutriai.ui.move.ExerciseCatalog.Category as C
import kotlinx.coroutines.flow.MutableStateFlow

/** The muscle groups offered at the daily check-in. Each one covers one or more Library body parts. */
enum class TrainGroup(val label: String, val emoji: String, val cats: List<C>) {
    CHEST("Chest", "🫁", listOf(C.CHEST)),
    LATS("Lats", "🦅", listOf(C.LATS)),
    UPPER_BACK("Upper back", "🔙", listOf(C.UPPER_BACK)),
    TRAPS("Traps", "🔺", listOf(C.TRAPS)),
    NECK("Neck", "🧣", listOf(C.NECK)),
    LOWER_BACK("Lower back", "🪑", listOf(C.LOWER_BACK)),
    SHOULDERS("Shoulders", "🏔️", listOf(C.SHOULDERS)),
    BICEPS("Biceps", "💪", listOf(C.BICEPS)),
    TRICEPS("Triceps", "🔱", listOf(C.TRICEPS)),
    FOREARMS("Forearms", "✊", listOf(C.FOREARMS)),
    CORE("Core", "🎯", listOf(C.CORE)),
    GLUTES("Glutes", "🍑", listOf(C.GLUTES)),
    QUADS("Quads", "🦵", listOf(C.QUADS)),
    HAMSTRINGS("Hamstrings", "🦿", listOf(C.HAMSTRINGS)),
    CALVES("Calves", "🥾", listOf(C.CALVES)),
    INNER_THIGH("Inner thigh", "🔻", listOf(C.INNER_THIGH)),
    OUTER_THIGH("Outer thigh", "🔺", listOf(C.OUTER_THIGH)),
    CARDIO("Cardio", "❤️", listOf(C.CARDIO)),
    MOBILITY("Mobility", "🧘", listOf(C.MOBILITY)),
}

/**
 * What the user has really trained lately, turned into two answers:
 *  - which muscle group deserves attention today (least trained over 2 weeks, and not trained in the last 2 days), and
 *  - inside a body part, which region is behind (Chest: upper 8, middle 8, lower 3 -> lower first).
 */
object FocusBalance {
    class Report(
        /** sets logged in the last 30 days per body part and region */
        val regionSets: Map<Pair<C, String>, Int>,
        /** sets per group in the last 14 days */
        val groupSets14: Map<TrainGroup, Int>,
        /** days since the group was last trained (null = never in the window) */
        val groupLastDays: Map<TrainGroup, Int?>,
    ) {
        fun regionCount(cat: C, region: String): Int = regionSets[cat to region] ?: 0
        /** regions of [cat], least trained first */
        fun regionsBehindFirst(cat: C): List<String> = SubParts.forCategory(cat).sortedBy { regionCount(cat, it) }
        /** the region to start with, or null when they are all level */
        fun behindRegion(cat: C): String? {
            val regions = SubParts.forCategory(cat)
            if (regions.size < 2) return null
            val counts = regions.map { regionCount(cat, it) }
            return if (counts.min() < counts.max()) regions.minByOrNull { regionCount(cat, it) } else null
        }
    }

    private val byName: Map<String, ExerciseCatalog.Entry> by lazy {
        ExerciseCatalog.entries.associateBy { it.item.name.lowercase().trim() }
    }

    fun compute(logs: List<ExerciseLogDto>, now: java.time.Instant = java.time.Instant.now()): Report {
        val region = HashMap<Pair<C, String>, Int>()
        val sets14 = HashMap<TrainGroup, Int>()
        val last = HashMap<TrainGroup, Int>()
        for (l in logs) {
            val entry = byName[l.exerciseName.lowercase().trim()] ?: continue
            val cat = entry.category
            val group = TrainGroup.values().firstOrNull { cat in it.cats } ?: continue
            val n = (l.sets ?: 1).coerceAtLeast(1)
            val daysAgo = runCatching { java.time.Duration.between(java.time.Instant.parse(l.performedAt), now).toDays().toInt() }.getOrDefault(99)
            SubParts.classify(cat, entry.item.name)?.let { r -> region.merge(cat to r, n, Int::plus) }
            if (daysAgo <= 14) sets14.merge(group, n, Int::plus)
            last[group] = minOf(last[group] ?: 999, daysAgo)
        }
        return Report(region, sets14, TrainGroup.values().associateWith { last[it] })
    }

    /** Groups worth training today, best first. Anything trained in the last 2 days is left out so it can recover. */
    fun suggest(report: Report?): List<TrainGroup> {
        if (report == null) return emptyList()
        return TrainGroup.values()
            .filter { it != TrainGroup.CARDIO && it != TrainGroup.MOBILITY && (report.groupLastDays[it] ?: 99) >= 2 }
            .sortedWith(compareBy<TrainGroup> { report.groupSets14[it] ?: 0 }.thenByDescending { report.groupLastDays[it] ?: 99 })
            .take(2)
    }

    fun reason(report: Report?, g: TrainGroup): String {
        val last = report?.groupLastDays?.get(g)
        return when {
            last == null -> "not trained in 30 days"
            else -> "last trained $last day${if (last == 1) "" else "s"} ago"
        }
    }
}

/** What the user chose today (yes/no, groups, exercises). The eating day rolls over at 4 am, like the food log. */
object SessionStore {
    private const val PREFS = "kaizen_prefs"
    /** Set by the check-in when the user chose groups; the Move screen opens the exercise picker and clears it. */
    val builderRequest = MutableStateFlow(false)
    val picksFlow = MutableStateFlow<List<String>>(emptyList())
    val groupsFlow = MutableStateFlow<List<TrainGroup>>(emptyList())
    /** The body part the Library should open on (set by the check-in, consumed by the Library). */
    val libraryCat = MutableStateFlow<ExerciseCatalog.Category?>(null)

    private fun dayKey(): String = java.time.LocalDateTime.now().minusHours(4).toLocalDate().toString()
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun needsCheckIn(c: Context): Boolean = prefs(c).getString("ck_day", null) != dayKey()

    /** True when today's check-in was answered "Not today" (a rest day, so no workout-timed meals). */
    fun saidNoToday(c: Context): Boolean {
        val p = prefs(c)
        return p.getString("ck_day", null) == dayKey() && !p.getBoolean("ck_yes", false)
    }

    fun answer(c: Context, yes: Boolean) {
        prefs(c).edit().putString("ck_day", dayKey()).putBoolean("ck_yes", yes).apply()
    }

    /** Picks planned for tomorrow's session (added from the Library with "+ Tomorrow" or from the Tomorrow tab). */
    val tomorrowFlow = MutableStateFlow<List<String>>(emptyList())

    private fun today(): java.time.LocalDate = java.time.LocalDateTime.now().minusHours(4).toLocalDate()
    private fun picksKey(tomorrow: Boolean) = "picks_" + today().plusDays(if (tomorrow) 1 else 0).toString()
    private fun readPicks(c: Context, tomorrow: Boolean): List<String> =
        prefs(c).getString(picksKey(tomorrow), "").orEmpty().split('|').filter { it.isNotBlank() }

    fun refresh(c: Context) {
        val p = prefs(c)
        // one-time carry-over of the old single "ck_picks" list (it belonged to the current eating day)
        if (p.contains("ck_picks")) {
            val old = p.getString("ck_picks", "").orEmpty()
            val e = p.edit().remove("ck_picks")
            if (old.isNotBlank() && p.getString("ck_day", null) == dayKey() && !p.contains(picksKey(false))) e.putString(picksKey(false), old)
            e.apply()
        }
        // forget plans older than today
        val cutoff = today().toString()
        val stale = p.all.keys.filter { it.startsWith("picks_") && it.removePrefix("picks_") < cutoff }
        if (stale.isNotEmpty()) p.edit().apply { stale.forEach { remove(it) } }.apply()
        picksFlow.value = readPicks(c, false)
        tomorrowFlow.value = readPicks(c, true)
        groupsFlow.value = if (p.getString("ck_day", null) == dayKey() && p.getBoolean("ck_yes", false))
            p.getString("ck_groups", "").orEmpty().split('|').mapNotNull { n -> TrainGroup.values().firstOrNull { it.name == n } }
        else emptyList()
    }

    fun saveGroups(c: Context, groups: List<TrainGroup>) {
        prefs(c).edit().putString("ck_day", dayKey()).putBoolean("ck_yes", true).putString("ck_groups", groups.joinToString("|") { it.name }).apply()
        refresh(c)
    }

    fun addPicks(c: Context, names: List<String>, tomorrow: Boolean = false) {
        val merged = (readPicks(c, tomorrow) + names).distinct()
        prefs(c).edit().putString(picksKey(tomorrow), merged.joinToString("|")).apply()
        refresh(c)
    }

    fun clearPicks(c: Context, tomorrow: Boolean = false) {
        prefs(c).edit().remove(picksKey(tomorrow)).apply()
        refresh(c)
    }

    fun removePick(c: Context, name: String, tomorrow: Boolean = false) {
        val left = readPicks(c, tomorrow).filter { it != name }
        prefs(c).edit().putString(picksKey(tomorrow), left.joinToString("|")).apply()
        refresh(c)
    }
}
