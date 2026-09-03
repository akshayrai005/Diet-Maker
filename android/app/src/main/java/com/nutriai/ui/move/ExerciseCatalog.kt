package com.nutriai.ui.move

import com.nutriai.data.remote.dto.ExerciseItem

/**
 * A broad, browsable catalog of movements across EVERY training type - strength, bodyweight/
 * calisthenics, cardio, HIIT, core, mobility/stretching and yoga - so anyone training (especially at
 * the gym) can search for what they're actually doing and log it, even when it isn't in today's
 * generated plan. The aim of the app is to shape and build the body, so the library skews toward the
 * hypertrophy/strength staples while still covering fat-loss cardio, conditioning and recovery.
 *
 * Every entry is a plain [ExerciseItem] so it flows straight into the existing log dialog and the
 * exercise-demo GIF map (names are chosen to resolve in [ExerciseDemoMap]; unmatched ones fall back
 * to the bundled muscle diagram). If a movement isn't listed, the search sheet still lets the user log
 * it by name - nothing is off-limits.
 */
object ExerciseCatalog {

    /** Groups the library into user-friendly, filterable buckets shown as chips in the search sheet. */
    enum class Category(val label: String, val emoji: String) {
        ALL("All", "🔎"),
        CHEST("Chest", "💥"),
        BACK("Back", "🔙"),
        SHOULDERS("Shoulders", "🎯"),
        ARMS("Arms", "💪"),
        LEGS("Legs", "🦵"),
        GLUTES("Glutes", "🍑"),
        CORE("Core", "🧱"),
        CARDIO("Cardio", "🏃"),
        HIIT("HIIT", "🔥"),
        MOBILITY("Stretch", "🧘"),
        YOGA("Yoga", "🕉️"),
    }

    data class Entry(val item: ExerciseItem, val category: Category)

    private fun strength(name: String, muscle: String, equipment: String, cat: Category, reps: String = "8-12", sets: Int = 3) =
        Entry(ExerciseItem(name = name, sets = sets, reps = reps, type = "strength", muscleGroup = muscle, equipment = equipment), cat)

    private fun body(name: String, muscle: String, cat: Category, reps: String = "10-15", sets: Int = 3) =
        Entry(ExerciseItem(name = name, sets = sets, reps = reps, type = "strength", muscleGroup = muscle, equipment = "bodyweight"), cat)

    private fun cardio(name: String, reps: String = "20 min") =
        Entry(ExerciseItem(name = name, sets = 1, reps = reps, type = "cardio", muscleGroup = "cardio", equipment = "none"), Category.CARDIO)

    private fun hiit(name: String, reps: String) =
        Entry(ExerciseItem(name = name, sets = 1, reps = reps, type = "cardio", muscleGroup = "cardio", equipment = "none"), Category.HIIT)

    private fun core(name: String, reps: String = "12-15", sets: Int = 3) =
        Entry(ExerciseItem(name = name, sets = sets, reps = reps, type = "strength", muscleGroup = "abs", equipment = "bodyweight"), Category.CORE)

    private fun mobility(name: String, muscle: String, reps: String = "45 s") =
        Entry(ExerciseItem(name = name, sets = 1, reps = reps, type = "flexibility", muscleGroup = muscle, equipment = "bodyweight"), Category.MOBILITY)

    private fun yoga(name: String, muscle: String, reps: String = "60 s") =
        Entry(ExerciseItem(name = name, sets = 1, reps = reps, type = "flexibility", muscleGroup = muscle, equipment = "bodyweight"), Category.YOGA)

    /** The full catalog, grouped by category (the pre-search order). */
    val entries: List<Entry> = listOf(
        // ---- Chest ----
        strength("Barbell Bench Press", "pectorals", "barbell", Category.CHEST),
        strength("Incline Barbell Bench Press", "pectorals", "barbell", Category.CHEST),
        strength("Decline Bench Press", "pectorals", "barbell", Category.CHEST),
        strength("Dumbbell Bench Press", "pectorals", "dumbbell", Category.CHEST),
        strength("Incline Dumbbell Press", "pectorals", "dumbbell", Category.CHEST),
        strength("Dumbbell Fly", "pectorals", "dumbbell", Category.CHEST, reps = "10-15"),
        strength("Incline Dumbbell Fly", "pectorals", "dumbbell", Category.CHEST, reps = "10-15"),
        strength("Cable Crossover", "pectorals", "cable", Category.CHEST, reps = "12-15"),
        strength("Chest Press Machine", "pectorals", "machine", Category.CHEST),
        strength("Pec Deck Machine", "pectorals", "machine", Category.CHEST, reps = "12-15"),
        body("Push-up", "pectorals", Category.CHEST, reps = "10-20"),
        body("Incline Push-up", "pectorals", Category.CHEST, reps = "12-20"),
        body("Decline Push-up", "pectorals", Category.CHEST, reps = "10-15"),
        body("Diamond Push-up", "triceps", Category.CHEST, reps = "8-15"),
        body("Chest Dips", "pectorals", Category.CHEST, reps = "8-12"),

        // ---- Back ----
        strength("Deadlift", "glutes", "barbell", Category.BACK, reps = "5-8"),
        strength("Barbell Row", "upper-back", "barbell", Category.BACK),
        strength("Bent Over Row", "upper-back", "barbell", Category.BACK),
        strength("Pendlay Row", "upper-back", "barbell", Category.BACK),
        strength("T-Bar Row", "upper-back", "barbell", Category.BACK),
        strength("Seated Cable Row", "upper-back", "cable", Category.BACK, reps = "10-12"),
        strength("Lat Pulldown", "lats", "cable", Category.BACK, reps = "10-12"),
        strength("Close Grip Lat Pulldown", "lats", "cable", Category.BACK, reps = "10-12"),
        strength("Straight Arm Pulldown", "lats", "cable", Category.BACK, reps = "12-15"),
        strength("Single Arm Dumbbell Row", "upper-back", "dumbbell", Category.BACK, reps = "10-12"),
        strength("Chest Supported Row", "upper-back", "dumbbell", Category.BACK, reps = "10-12"),
        strength("Back Extension", "spine", "machine", Category.BACK, reps = "12-15"),
        body("Pull-up", "lats", Category.BACK, reps = "6-12"),
        body("Chin-up", "biceps", Category.BACK, reps = "6-12"),
        body("Inverted Row", "upper-back", Category.BACK, reps = "10-15"),
        body("Superman", "spine", Category.BACK, reps = "12-15"),

        // ---- Shoulders ----
        strength("Overhead Press", "delts", "barbell", Category.SHOULDERS),
        strength("Dumbbell Shoulder Press", "delts", "dumbbell", Category.SHOULDERS),
        strength("Arnold Press", "delts", "dumbbell", Category.SHOULDERS, reps = "10-12"),
        strength("Lateral Raise", "delts", "dumbbell", Category.SHOULDERS, reps = "12-15"),
        strength("Cable Lateral Raise", "delts", "cable", Category.SHOULDERS, reps = "12-15"),
        strength("Front Raise", "delts", "dumbbell", Category.SHOULDERS, reps = "12-15"),
        strength("Rear Delt Fly", "delts", "dumbbell", Category.SHOULDERS, reps = "12-15"),
        strength("Reverse Pec Deck", "delts", "machine", Category.SHOULDERS, reps = "12-15"),
        strength("Face Pull", "delts", "cable", Category.SHOULDERS, reps = "12-15"),
        strength("Upright Row", "delts", "barbell", Category.SHOULDERS, reps = "10-12"),
        strength("Barbell Shrug", "traps", "barbell", Category.SHOULDERS, reps = "10-15"),
        strength("Dumbbell Shrug", "traps", "dumbbell", Category.SHOULDERS, reps = "10-15"),
        body("Pike Push-up", "delts", Category.SHOULDERS, reps = "8-15"),

        // ---- Arms ----
        strength("Barbell Curl", "biceps", "barbell", Category.ARMS, reps = "8-12"),
        strength("EZ Bar Curl", "biceps", "barbell", Category.ARMS, reps = "10-12"),
        strength("Dumbbell Curl", "biceps", "dumbbell", Category.ARMS, reps = "10-12"),
        strength("Hammer Curl", "biceps", "dumbbell", Category.ARMS, reps = "10-12"),
        strength("Incline Dumbbell Curl", "biceps", "dumbbell", Category.ARMS, reps = "10-12"),
        strength("Preacher Curl", "biceps", "barbell", Category.ARMS, reps = "10-12"),
        strength("Concentration Curl", "biceps", "dumbbell", Category.ARMS, reps = "10-12"),
        strength("Cable Curl", "biceps", "cable", Category.ARMS, reps = "12-15"),
        strength("Tricep Pushdown", "triceps", "cable", Category.ARMS, reps = "12-15"),
        strength("Rope Pushdown", "triceps", "cable", Category.ARMS, reps = "12-15"),
        strength("Overhead Tricep Extension", "triceps", "dumbbell", Category.ARMS, reps = "10-12"),
        strength("Skullcrusher", "triceps", "barbell", Category.ARMS, reps = "10-12"),
        strength("Close Grip Bench Press", "triceps", "barbell", Category.ARMS),
        strength("Wrist Curl", "forearms", "dumbbell", Category.ARMS, reps = "15-20"),
        body("Bench Dips", "triceps", Category.ARMS, reps = "10-15"),

        // ---- Legs ----
        strength("Barbell Back Squat", "quads", "barbell", Category.LEGS, reps = "6-10"),
        strength("Front Squat", "quads", "barbell", Category.LEGS, reps = "6-10"),
        strength("Goblet Squat", "quads", "dumbbell", Category.LEGS, reps = "10-12"),
        strength("Leg Press", "quads", "machine", Category.LEGS, reps = "10-15"),
        strength("Hack Squat", "quads", "machine", Category.LEGS, reps = "8-12"),
        strength("Romanian Deadlift", "hamstrings", "barbell", Category.LEGS, reps = "8-12"),
        strength("Stiff Leg Deadlift", "hamstrings", "barbell", Category.LEGS, reps = "8-12"),
        strength("Leg Extension", "quads", "machine", Category.LEGS, reps = "12-15"),
        strength("Lying Leg Curl", "hamstrings", "machine", Category.LEGS, reps = "12-15"),
        strength("Seated Leg Curl", "hamstrings", "machine", Category.LEGS, reps = "12-15"),
        strength("Walking Lunge", "quads", "dumbbell", Category.LEGS, reps = "10-12"),
        strength("Bulgarian Split Squat", "quads", "dumbbell", Category.LEGS, reps = "8-12"),
        strength("Standing Calf Raise", "calves", "machine", Category.LEGS, reps = "15-20"),
        strength("Seated Calf Raise", "calves", "machine", Category.LEGS, reps = "15-20"),
        body("Bodyweight Squat", "quads", Category.LEGS, reps = "15-25"),
        body("Jump Squat", "quads", Category.LEGS, reps = "12-15"),
        body("Wall Sit", "quads", Category.LEGS, reps = "45 s"),
        body("Calf Raise", "calves", Category.LEGS, reps = "15-25"),

        // ---- Glutes ----
        strength("Barbell Hip Thrust", "glutes", "barbell", Category.GLUTES, reps = "10-12"),
        strength("Cable Kickback", "glutes", "cable", Category.GLUTES, reps = "12-15"),
        strength("Sumo Deadlift", "glutes", "barbell", Category.GLUTES, reps = "6-10"),
        strength("Hip Abduction Machine", "glutes", "machine", Category.GLUTES, reps = "15-20"),
        body("Glute Bridge", "glutes", Category.GLUTES, reps = "15-20"),
        body("Single Leg Glute Bridge", "glutes", Category.GLUTES, reps = "12-15"),
        body("Donkey Kick", "glutes", Category.GLUTES, reps = "15-20"),
        body("Fire Hydrant", "glutes", Category.GLUTES, reps = "15-20"),
        body("Curtsy Lunge", "glutes", Category.GLUTES, reps = "10-12"),

        // ---- Core ----
        core("Plank", reps = "30-60 s"),
        core("Side Plank", reps = "30-45 s"),
        core("Crunch", reps = "15-25"),
        core("Reverse Crunch", reps = "12-20"),
        core("Bicycle Crunch", reps = "20-30"),
        core("Hanging Leg Raise", reps = "10-15"),
        core("Lying Leg Raise", reps = "12-20"),
        core("Russian Twist", reps = "20-30"),
        core("Mountain Climbers", reps = "30-45 s"),
        core("Dead Bug", reps = "10-12"),
        core("Bird Dog", reps = "10-12"),
        core("Cable Crunch", reps = "12-15"),
        core("Ab Wheel Rollout", reps = "8-12"),
        core("Flutter Kicks", reps = "30-45 s"),
        core("Hollow Hold", reps = "20-40 s"),

        // ---- Cardio ----
        cardio("Treadmill Run", reps = "20-30 min"),
        cardio("Incline Treadmill Walk", reps = "20-30 min"),
        cardio("Outdoor Running", reps = "20-40 min"),
        cardio("Brisk Walking", reps = "30-45 min"),
        cardio("Cycling", reps = "20-40 min"),
        cardio("Stationary Bike", reps = "20-30 min"),
        cardio("Rowing Machine", reps = "15-20 min"),
        cardio("Elliptical", reps = "20-30 min"),
        cardio("Stair Climber", reps = "15-20 min"),
        cardio("Jump Rope", reps = "10-15 min"),
        cardio("Swimming", reps = "20-30 min"),

        // ---- HIIT / conditioning ----
        hiit("Burpees", reps = "4 x 15"),
        hiit("High Knees", reps = "8 x 30 s"),
        hiit("Jumping Jacks", reps = "8 x 30 s"),
        hiit("Battle Ropes", reps = "8 x 30 s"),
        hiit("Kettlebell Swing", reps = "5 x 15"),
        hiit("Box Jumps", reps = "5 x 10"),
        hiit("Sprints", reps = "8 x 20 s"),
        hiit("Sled Push", reps = "6 x 20 s"),
        hiit("Thrusters", reps = "5 x 12"),
        hiit("Tabata Circuit", reps = "8 x 20 s"),

        // ---- Mobility / stretching ----
        mobility("Cat-Cow Stretch", "spine"),
        mobility("World's Greatest Stretch", "hamstrings"),
        mobility("Hip Flexor Stretch", "glutes"),
        mobility("Hamstring Stretch", "hamstrings"),
        mobility("Quad Stretch", "quads"),
        mobility("Shoulder Stretch", "delts"),
        mobility("Chest Doorway Stretch", "pectorals"),
        mobility("Thoracic Rotation", "spine"),
        mobility("Ankle Mobility Drill", "calves"),
        mobility("Foam Rolling", "hamstrings", reps = "5 min"),

        // ---- Yoga ----
        yoga("Downward Dog", "hamstrings"),
        yoga("Child's Pose", "spine"),
        yoga("Cobra Pose", "spine"),
        yoga("Warrior II", "quads"),
        yoga("Triangle Pose", "hamstrings"),
        yoga("Bridge Pose", "glutes"),
        yoga("Pigeon Pose", "glutes"),
        yoga("Seated Forward Fold", "hamstrings"),
        yoga("Sun Salutation", "spine", reps = "5 rounds"),
        yoga("Corpse Pose", "spine", reps = "5 min"),
    )

    /** Convenience: just the items (whole library) for callers that don't need the category. */
    val all: List<ExerciseItem> = entries.map { it.item }

    val categories: List<Category> = Category.entries

    /**
     * Case-insensitive search over exercise name and muscle group, optionally scoped to a [category].
     * A blank query returns everything in the (optionally filtered) category, so the sheet always has
     * content. Name-prefix matches (e.g. "ben" → "Bench Press") are ranked above mid-word ones.
     */
    fun search(query: String, category: Category = Category.ALL): List<ExerciseItem> {
        val scoped = if (category == Category.ALL) entries else entries.filter { it.category == category }
        val q = query.trim().lowercase()
        val matched = if (q.isEmpty()) {
            scoped
        } else {
            scoped.filter { e ->
                e.item.name.lowercase().contains(q) || (e.item.muscleGroup?.lowercase()?.contains(q) == true)
            }.sortedBy { if (it.item.name.lowercase().startsWith(q)) 0 else 1 }
        }
        return matched.map { it.item }
    }

    /** Wraps a free-typed name the user searched for but didn't find, so they can still log it. */
    fun custom(name: String): ExerciseItem =
        ExerciseItem(name = name.trim(), sets = 3, reps = "10", type = "strength", muscleGroup = null, equipment = null)
}
