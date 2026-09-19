package com.nutriai.ui.move

import com.nutriai.ui.move.ExerciseCatalog.Category as C

/**
 * Puts every library exercise under exactly ONE body part (Lats, Upper back, Biceps, Triceps, Forearms, Quads, Hamstrings, Calves...)
 * instead of the broad Back / Arms / Legs groups, so a muscle's exercises are easy to find and log. Decided from the exercise's
 * recorded muscle first, then its name. Cardio and Stretch stay as they are (HIIT joins Cardio, Yoga joins Stretch).
 */
object BodyParts {
    private fun byName(n: String): ExerciseCatalog.Category? = when {
        Regex("pull-?up|chin-?up|pulldown|pull down|pullover|lat prayer|straight arm").containsMatchIn(n) -> C.LATS
        Regex("shrug").containsMatchIn(n) -> C.TRAPS
        Regex("wrist|forearm|grip|farmer|plate pinch").containsMatchIn(n) -> C.FOREARMS
        Regex("tricep|pushdown|push down|skull|kickback|dips?\\b|close.grip (bench|press|push)|jm press|overhead extension").containsMatchIn(n) -> C.TRICEPS
        Regex("calf").containsMatchIn(n) -> C.CALVES
        Regex("leg curl|hamstring|romanian|stiff.leg|good morning|deadlift|glute.ham|nordic").containsMatchIn(n) -> C.HAMSTRINGS
        Regex("hip thrust|glute|bridge|donkey|fire hydrant|frog").containsMatchIn(n) -> C.GLUTES
        Regex("abduct").containsMatchIn(n) -> C.OUTER_THIGH
        Regex("adduct").containsMatchIn(n) -> C.INNER_THIGH
        Regex("squat|lunge|leg press|leg extension|step.?up|sissy|hack").containsMatchIn(n) -> C.QUADS
        Regex("curl|preacher|concentration").containsMatchIn(n) -> C.BICEPS
        Regex("back extension|hyperextension|superman|jefferson").containsMatchIn(n) -> C.LOWER_BACK
        Regex("row|face pull|rear delt|reverse fly").containsMatchIn(n) -> C.UPPER_BACK
        else -> null
    }

    private fun byMuscle(m: String): ExerciseCatalog.Category? = when {
        "bicep" in m -> C.BICEPS
        "tricep" in m -> C.TRICEPS
        "forearm" in m -> C.FOREARMS
        "trap" in m -> C.TRAPS
        "levator" in m || "neck" in m -> C.NECK
        "spine" in m || "lower back" in m || "lower-back" in m || "erector" in m -> C.LOWER_BACK
        Regex("\\blats?\\b").containsMatchIn(m) -> C.LATS
        "upper-back" in m || "upper back" in m || "middle back" in m || "rhomboid" in m -> C.UPPER_BACK
        "serratus" in m || "pector" in m || "chest" in m -> C.CHEST
        "delt" in m || "shoulder" in m -> C.SHOULDERS
        "abs" == m || "abdominal" in m || "oblique" in m || "core" in m -> C.CORE
        "quad" in m -> C.QUADS
        "hamstring" in m -> C.HAMSTRINGS
        "calf" in m || "calves" in m -> C.CALVES
        "abduct" in m -> C.OUTER_THIGH
        "adduct" in m -> C.INNER_THIGH
        "glute" in m -> C.GLUTES
        else -> null
    }

    fun categoryFor(e: ExerciseCatalog.Entry): ExerciseCatalog.Category {
        val c = e.category
        if (c == C.HIIT) return C.CARDIO
        if (c == C.YOGA) return C.MOBILITY
        if (c == C.CARDIO || c == C.MOBILITY) return c
        val n = e.item.name.lowercase()
        val m = (e.item.muscleGroup ?: "").lowercase()
        // Chest and shoulder exercises the source already filed there keep that home (a "Dips - Chest Version" is chest, not triceps).
        if (c == C.SHOULDERS && byName(n) == C.TRAPS) return C.TRAPS // shrugs are filed under shoulders at the source but train the traps
        if (c == C.CHEST || c == C.SHOULDERS) return c
        val core = c == C.CORE
        if (core && byMuscle(m) in setOf(C.LOWER_BACK, C.CORE, null)) return byMuscle(m) ?: C.CORE
        val fromName = byName(n)
        val fromMuscle = byMuscle(m)
        return when {
            // a name that clearly names the movement wins for the ambiguous cases (chin-up is lats even though it trains biceps)
            fromName == C.LATS || fromName == C.TRAPS || fromName == C.CALVES -> fromName
            fromMuscle != null && fromMuscle != C.CHEST && fromMuscle != C.SHOULDERS -> fromMuscle
            fromName != null -> fromName
            c == C.ARMS -> C.BICEPS
            c == C.LEGS -> C.QUADS
            c == C.BACK -> C.UPPER_BACK
            c == C.GLUTES -> C.GLUTES
            else -> c
        }
    }

    /** The categories shown as tiles in the Library, in order. */
    val tiles: List<ExerciseCatalog.Category> = listOf(
        C.CHEST, C.LATS, C.UPPER_BACK, C.TRAPS, C.NECK, C.LOWER_BACK, C.SHOULDERS, C.BICEPS, C.TRICEPS, C.FOREARMS,
        C.CORE, C.GLUTES, C.QUADS, C.HAMSTRINGS, C.CALVES, C.INNER_THIGH, C.OUTER_THIGH, C.CARDIO, C.MOBILITY,
    )
}
