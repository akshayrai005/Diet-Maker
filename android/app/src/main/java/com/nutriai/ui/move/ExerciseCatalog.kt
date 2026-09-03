package com.nutriai.ui.move

import com.nutriai.data.remote.dto.ExerciseItem

/**
 * A browsable catalog of common gym & bodyweight movements so anyone training - especially at the
 * gym - can SEARCH for what they're actually doing and log it, even when it isn't in today's generated
 * plan. Every entry is a plain [ExerciseItem] so it flows straight into the existing log dialog and the
 * exercise-demo GIF map (names are chosen to resolve in [ExerciseDemoMap]).
 *
 * Deliberately curated (not exhaustive): the movements people log most, across the major muscle groups
 * and equipment. If someone does something not listed, the search sheet still lets them log it by name.
 */
object ExerciseCatalog {

    private fun strength(name: String, muscle: String, equipment: String, reps: String = "8-12", sets: Int = 3) =
        ExerciseItem(name = name, sets = sets, reps = reps, type = "strength", muscleGroup = muscle, equipment = equipment)

    private fun body(name: String, muscle: String, reps: String = "10-15", sets: Int = 3) =
        ExerciseItem(name = name, sets = sets, reps = reps, type = "strength", muscleGroup = muscle, equipment = "bodyweight")

    private fun cardio(name: String, reps: String = "20 min") =
        ExerciseItem(name = name, sets = 1, reps = reps, type = "cardio", muscleGroup = "cardio", equipment = "none")

    private fun core(name: String, reps: String = "12-15", sets: Int = 3) =
        ExerciseItem(name = name, sets = sets, reps = reps, type = "strength", muscleGroup = "abs", equipment = "bodyweight")

    /** The full catalog, ordered roughly by muscle group for a tidy default (pre-search) list. */
    val all: List<ExerciseItem> = listOf(
        // Chest
        strength("Barbell Bench Press", "pectorals", "barbell"),
        strength("Incline Barbell Bench Press", "pectorals", "barbell"),
        strength("Dumbbell Bench Press", "pectorals", "dumbbell"),
        strength("Incline Dumbbell Press", "pectorals", "dumbbell"),
        strength("Dumbbell Fly", "pectorals", "dumbbell", reps = "10-15"),
        strength("Cable Crossover", "pectorals", "cable", reps = "12-15"),
        strength("Chest Press Machine", "pectorals", "machine"),
        body("Push-up", "pectorals", reps = "10-20"),
        body("Dips", "pectorals", reps = "8-12"),

        // Back
        strength("Deadlift", "glutes", "barbell", reps = "5-8"),
        strength("Barbell Row", "upper-back", "barbell"),
        strength("Bent Over Row", "upper-back", "barbell"),
        strength("T-Bar Row", "upper-back", "barbell"),
        strength("Seated Cable Row", "upper-back", "cable", reps = "10-12"),
        strength("Lat Pulldown", "lats", "cable", reps = "10-12"),
        strength("Single Arm Dumbbell Row", "upper-back", "dumbbell", reps = "10-12"),
        body("Pull-up", "lats", reps = "6-12"),
        body("Chin-up", "biceps", reps = "6-12"),

        // Shoulders
        strength("Overhead Press", "delts", "barbell"),
        strength("Dumbbell Shoulder Press", "delts", "dumbbell"),
        strength("Arnold Press", "delts", "dumbbell", reps = "10-12"),
        strength("Lateral Raise", "delts", "dumbbell", reps = "12-15"),
        strength("Front Raise", "delts", "dumbbell", reps = "12-15"),
        strength("Rear Delt Fly", "delts", "dumbbell", reps = "12-15"),
        strength("Face Pull", "delts", "cable", reps = "12-15"),
        strength("Barbell Shrug", "traps", "barbell", reps = "10-15"),

        // Biceps
        strength("Barbell Curl", "biceps", "barbell", reps = "8-12"),
        strength("Dumbbell Curl", "biceps", "dumbbell", reps = "10-12"),
        strength("Hammer Curl", "biceps", "dumbbell", reps = "10-12"),
        strength("Preacher Curl", "biceps", "barbell", reps = "10-12"),
        strength("Concentration Curl", "biceps", "dumbbell", reps = "10-12"),
        strength("Cable Curl", "biceps", "cable", reps = "12-15"),

        // Triceps
        strength("Tricep Pushdown", "triceps", "cable", reps = "12-15"),
        strength("Overhead Tricep Extension", "triceps", "dumbbell", reps = "10-12"),
        strength("Skullcrusher", "triceps", "barbell", reps = "10-12"),
        strength("Close Grip Bench Press", "triceps", "barbell"),
        body("Bench Dips", "triceps", reps = "10-15"),

        // Legs
        strength("Barbell Back Squat", "quads", "barbell", reps = "6-10"),
        strength("Front Squat", "quads", "barbell", reps = "6-10"),
        strength("Leg Press", "quads", "machine", reps = "10-15"),
        strength("Romanian Deadlift", "hamstrings", "barbell", reps = "8-12"),
        strength("Leg Extension", "quads", "machine", reps = "12-15"),
        strength("Leg Curl", "hamstrings", "machine", reps = "12-15"),
        strength("Walking Lunge", "quads", "dumbbell", reps = "10-12"),
        strength("Bulgarian Split Squat", "quads", "dumbbell", reps = "8-12"),
        strength("Hip Thrust", "glutes", "barbell", reps = "10-12"),
        strength("Calf Raise", "calves", "machine", reps = "15-20"),
        body("Bodyweight Squat", "quads", reps = "15-25"),
        body("Glute Bridge", "glutes", reps = "15-20"),

        // Core
        core("Plank", reps = "30-60 s"),
        core("Crunch", reps = "15-25"),
        core("Hanging Leg Raise", reps = "10-15"),
        core("Russian Twist", reps = "20-30"),
        core("Mountain Climbers", reps = "30-45 s"),
        core("Bicycle Crunch", reps = "20-30"),
        core("Dead Bug", reps = "10-12"),

        // Cardio
        cardio("Treadmill Run", reps = "20-30 min"),
        cardio("Cycling", reps = "20-30 min"),
        cardio("Rowing Machine", reps = "15-20 min"),
        cardio("Elliptical", reps = "20-30 min"),
        cardio("Stair Climber", reps = "15-20 min"),
        cardio("Jump Rope", reps = "10-15 min"),
        cardio("Burpees", reps = "3 x 15"),
    )

    /**
     * Case-insensitive search over exercise name and muscle group. A blank query returns the whole
     * catalog (so the sheet has content the moment it opens). Matches are ranked so name-prefix hits
     * (e.g. "ben" → "Bench Press") surface above mid-word ones.
     */
    fun search(query: String): List<ExerciseItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all
            .filter { it.name.lowercase().contains(q) || (it.muscleGroup?.lowercase()?.contains(q) == true) }
            .sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
    }

    /** Wraps a free-typed name the user searched for but didn't find, so they can still log it. */
    fun custom(name: String): ExerciseItem =
        ExerciseItem(name = name.trim(), sets = 3, reps = "10", type = "strength", muscleGroup = null, equipment = null)
}
