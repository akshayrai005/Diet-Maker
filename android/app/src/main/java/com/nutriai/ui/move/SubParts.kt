package com.nutriai.ui.move

import com.nutriai.ui.move.ExerciseCatalog.Category as C

/**
 * Narrows a body part down to the region an exercise works: Chest -> Upper / Middle / Lower, Shoulders -> Front / Side / Rear,
 * Abs -> Upper / Lower / Obliques / Stability, and so on. Decided from the exercise name. Every exercise in a part lands in exactly
 * one sub-part (the last rule of each list is the default), so nothing disappears when a sub-part is chosen.
 */
object SubParts {
    class Sub(val label: String, private val pattern: String?) {
        private val re = pattern?.let { Regex(it) }
        fun matches(lowerName: String) = re == null || re.containsMatchIn(lowerName)
    }

    /** First matching sub-part wins; a Sub with a null pattern is the default and must be last. */
    private val rules: Map<C, List<Sub>> = mapOf(
        C.CHEST to listOf(
            Sub("Upper", "incline|upper|high.to.low|low.to.high|reverse.grip"),
            Sub("Lower", "decline|dip|lower|high.cable|low.cable|pullover"),
            Sub("Middle", null),
        ),
        C.SHOULDERS to listOf(
            Sub("Rear", "rear|reverse fly|reverse pec|face pull|bent.?over (lateral|raise)|y.raise|prone|t.raise|rotation"),
            Sub("Side", "lateral|side raise|upright row|leaning|side.lying"),
            Sub("Front", null),
        ),
        C.CORE to listOf(
            Sub("Obliques", "twist|side bend|side plank|oblique|woodchop|wood chop|windshield|russian|bicycle|side crunch|side.lying"),
            Sub("Lower", "leg raise|reverse crunch|knee raise|hip raise|flutter|scissor|jackknife|lower|toe touch|v-up|tuck"),
            Sub("Stability", "plank|hollow|dead bug|bird dog|stir|hold|bridge|body saw|mountain|climber|rollout|roller|wheel"),
            Sub("Upper", null),
        ),
        C.BICEPS to listOf(
            Sub("Hammer & reverse", "hammer|reverse|zottman|brachial"),
            Sub("Peak", "concentration|preacher|spider|21"),
            Sub("Long head", "incline|drag|bayesian|behind"),
            Sub("Mass builders", null),
        ),
        C.TRICEPS to listOf(
            Sub("Overhead", "overhead|skull|french|behind.the.head|lying|above head"),
            Sub("Pushdowns & kickbacks", "pushdown|push down|pressdown|kickback|kick back"),
            Sub("Presses & dips", null),
        ),
        C.FOREARMS to listOf(
            Sub("Grip & carries", "farmer|grip|hang|pinch|carry|towel|hold|squeeze"),
            Sub("Reverse", "reverse|extension|behind"),
            Sub("Wrist curls", null),
        ),
        C.LATS to listOf(
            Sub("Isolation", "pullover|straight.arm|prayer"),
            Sub("Rows", "row"),
            Sub("Pull-ups & pulldowns", null),
        ),
        C.UPPER_BACK to listOf(
            Sub("Rear delts & scapula", "face pull|rear|reverse fly|shrug|y.raise|scapul|retraction|snow"),
            Sub("Rows", null),
        ),
        C.QUADS to listOf(
            Sub("Lunges & split squats", "lunge|split|step"),
            Sub("Machines & extensions", "press|extension|hack|sissy|machine|lever"),
            Sub("Squats", null),
        ),
        C.HAMSTRINGS to listOf(
            Sub("Curls", "curl|glute.ham|nordic|slide"),
            Sub("Hinges", null),
        ),
        C.GLUTES to listOf(
            Sub("Kickbacks & abduction", "kick|abduct|donkey|hydrant|clam|band walk|monster|side.lying"),
            Sub("Bridges & thrusts", "thrust|bridge"),
            Sub("Squats & lunges", null),
        ),
        C.CALVES to listOf(
            Sub("Seated", "seated"),
            Sub("Standing", "standing|donkey"),
            Sub("Other", null),
        ),
    )

    /** The sub-parts shown as chips for a body part (in display order), or empty when it isn't split further. */
    fun forCategory(cat: C): List<String> = orderFor(cat)

    private fun orderFor(cat: C): List<String> {
        val subs = rules[cat] ?: return emptyList()
        // display order reads top -> bottom / front -> back, not rule-priority order
        val wanted = when (cat) {
            C.CHEST -> listOf("Upper", "Middle", "Lower")
            C.SHOULDERS -> listOf("Front", "Side", "Rear")
            C.CORE -> listOf("Upper", "Lower", "Obliques", "Stability")
            C.BICEPS -> listOf("Mass builders", "Peak", "Long head", "Hammer & reverse")
            C.TRICEPS -> listOf("Overhead", "Pushdowns & kickbacks", "Presses & dips")
            C.FOREARMS -> listOf("Wrist curls", "Reverse", "Grip & carries")
            C.LATS -> listOf("Pull-ups & pulldowns", "Rows", "Isolation")
            C.UPPER_BACK -> listOf("Rows", "Rear delts & scapula")
            C.QUADS -> listOf("Squats", "Lunges & split squats", "Machines & extensions")
            C.HAMSTRINGS -> listOf("Hinges", "Curls")
            C.GLUTES -> listOf("Bridges & thrusts", "Squats & lunges", "Kickbacks & abduction")
            C.CALVES -> listOf("Standing", "Seated", "Other")
            else -> subs.map { it.label }
        }
        return wanted
    }

    /** Which sub-part of [cat] this exercise belongs to, or null when the part isn't split further. */
    fun classify(cat: C, exerciseName: String): String? {
        val subs = rules[cat] ?: return null
        val n = exerciseName.lowercase()
        return subs.first { it.matches(n) }.label
    }
}
