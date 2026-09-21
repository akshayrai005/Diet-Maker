package com.nutriai.ui.move

/**
 * Plain "how to perform it" guidance for any exercise, worked out from its name (and muscle group). It is written
 * per MOVEMENT TYPE (press, row, squat, curl...), so every one of the 1,300+ library exercises gets steps that
 * actually fit it instead of the same generic lines. General guidance, not a substitute for a coach or physio.
 */
object ExerciseGuide {
    data class Guide(val steps: List<String>, val mistakes: List<String>, val safety: String)

    private data class Rule(val match: Regex, val guide: Guide)

    private fun r(pattern: String, steps: List<String>, mistakes: List<String>, safety: String) =
        Rule(Regex(pattern, RegexOption.IGNORE_CASE), Guide(steps, mistakes, safety))

    private val RULES = listOf(
        // Dynamic warm-ups / mobility drills come first, so "leg swings" is never mistaken for a kettlebell swing or hinge.
        r("leg swing|arm swing|arm circle|hip circle|world.?s greatest|cat.?cow|thoracic|march|jog on spot|light cardio|shoulder roll|torso twist|inchworm|walkout|dynamic",
            listOf("Stand tall near a wall or rail for balance, with your abs lightly braced.",
                "Move slowly and smoothly through a comfortable range - swing, circle or reach without forcing it.",
                "Do 10-15 controlled reps (each side where it applies), gradually making the range a little bigger.",
                "Keep your torso still and breathe normally; stop short of any pinch or pain."),
            listOf("Bouncing or jerking to get more range", "Leaning the whole body instead of moving the joint", "Rushing through the reps"),
            "This is a warm-up, not a workout: the effort should feel easy. Skip any movement that hurts."),
        r("scapular",
            listOf("Start in a high plank with your arms straight and locked, hands under your shoulders.",
                "Keeping your elbows straight, let your chest sink between your shoulder blades so they squeeze together.",
                "Then push the floor away, spreading your shoulder blades wide and rounding your upper back slightly.",
                "Move only through your shoulder blades - your elbows never bend. Do 8-10 slow reps."),
            listOf("Bending the elbows like a normal push-up", "Sagging the hips", "Rushing without a pause at each end"),
            "Keep your neck long and your abs braced; stop if your wrists or shoulders pinch."),
        r("plank|hollow|dead bug|bird dog|rollout|wheel|pallof|stability",
            listOf("Get into position with your body in one straight line and your abs braced, as if about to be poked in the stomach.",
                "Keep your ribs down and your lower back neutral - do not let it sag or arch.",
                "Hold or move slowly, breathing steadily; never hold your breath.",
                "End the set the moment your form slips, not when the timer ends."),
            listOf("Hips sagging or piking up", "Holding your breath", "Rushing the reps"),
            "Stop if your lower back hurts. Build up from easier versions (knees down, shorter range)."),
        r("crunch|sit-up|sit up|v-up|leg raise|knee raise|russian|bicycle|flutter|twist",
            listOf("Lie or sit with your lower back supported and your neck relaxed (hands lightly by your head, not pulling it).",
                "Exhale and curl your ribs toward your hips - the movement comes from your abs, not by swinging.",
                "Pause for a second at the top and squeeze.",
                "Lower slowly under control; keep tension on your abs the whole time."),
            listOf("Pulling on the neck", "Using momentum", "Lifting the lower back off the floor with straight-leg raises"),
            "These build ab strength; visible abs come from overall fat loss - spot reduction does not work. Stop for lower-back pain."),
        r("bench press|chest press|push-up|push up|pushup|dip|fly|flye|pec deck|crossover|floor press|svend",
            listOf("Set up with your shoulder blades pulled back and down, feet planted, and your hands or the bar over your mid-chest.",
                "Lower slowly (about 2 seconds) until your upper arms are roughly 45 degrees from your body - elbows tucked, not flared wide.",
                "Press up powerfully, exhaling, until your arms are straight without shrugging your shoulders.",
                "Keep your wrists straight over your elbows and your lower back gently arched, glutes on the bench."),
            listOf("Flaring elbows out to 90 degrees", "Bouncing the weight off your chest", "Lifting hips or head off the bench"),
            "Use a spotter or safety arms for heavy barbell work; stop 1-2 reps before failure."),
        r("overhead press|shoulder press|military|arnold|push press|landmine press|handstand",
            listOf("Stand or sit tall with your abs braced and your glutes squeezed; hold the weight at shoulder height, forearms vertical.",
                "Press straight up (a slight arc around your face), exhaling, until your arms are fully extended overhead.",
                "Keep your ribs down - do not lean back to finish the rep.",
                "Lower slowly back to shoulder height with control."),
            listOf("Leaning back / arching the lower back", "Pressing the weight out in front of you", "Shrugging up to your ears"),
            "If your shoulder pinches, use a neutral grip (palms facing) or reduce the range."),
        r("lateral raise|side raise|front raise|rear delt|reverse fly|reverse pec|face pull|upright row|y raise|band pull apart",
            listOf("Stand tall with a soft bend in your elbows and a light weight - this is a control exercise, not a heavy one.",
                "Lift (or pull) until your arms reach about shoulder height, leading with your elbows, exhaling.",
                "Pause briefly at the top without shrugging.",
                "Lower slowly over about 2-3 seconds; do not swing your body."),
            listOf("Using momentum and body swing", "Shrugging the traps", "Going too heavy"),
            "Keep the load light; stop if you feel a pinch at the front of the shoulder."),
        r("pull-up|pull up|pullup|chin-up|chin up|pulldown|pull down|muscle-up",
            listOf("Take your grip (a little wider than shoulders for pull-ups, palms toward you for chin-ups) and start from a full hang or seated with your chest up.",
                "First pull your shoulder blades down, then drive your elbows down toward your hips.",
                "Pull until your chin clears the bar (or the bar reaches your upper chest), exhaling.",
                "Lower under control to a full stretch - no dropping."),
            listOf("Swinging or kipping", "Half range of motion", "Shrugging your shoulders up to your ears"),
            "Build up gradually to protect your elbows; use a band or the pulldown machine if you cannot do full reps yet."),
        r("row|rack pull|t-bar|seal row|pendlay",
            listOf("Hinge at the hips with a flat back (or sit tall with your chest supported) and let your arms hang long.",
                "Pull your elbows back and up toward your hips, squeezing your shoulder blades together, exhaling.",
                "Pause for a second with the weight at your torso.",
                "Lower slowly until your arms are fully extended; keep your torso still."),
            listOf("Rounding the back", "Jerking the weight up with your hips", "Pulling with your arms only, no shoulder-blade squeeze"),
            "Keep your spine neutral; reduce the weight if your back starts to round."),
        r("deadlift|romanian|good morning|hip thrust|glute bridge|bridge|nordic|hyperextension|back extension|pull through|kettlebell swing|kb swing",
            listOf("Stand (or set up) with the weight close to your body, feet hip-width, spine neutral, abs braced.",
                "Push your hips back like closing a car door with your bottom, keeping your back flat, until you feel a stretch in your hamstrings.",
                "Drive your hips forward and squeeze your glutes to stand tall (or to lift into the bridge), exhaling.",
                "Do not lean back or over-arch at the top; lower with control."),
            listOf("Rounding the lower back", "Letting the weight drift away from your legs", "Turning it into a squat"),
            "Learn the hip hinge with light weight first; stop the set the moment your back rounds."),
        r("squat|leg press|hack|wall sit|sissy|pistol",
            listOf("Stand with feet about shoulder-width, toes slightly out, chest up and abs braced.",
                "Sit down and back, bending hips and knees together, keeping your knees in line with your toes.",
                "Go as deep as you can with your heels down and back flat, then drive through your whole foot to stand, exhaling.",
                "Keep your torso upright and your knees from caving inward."),
            listOf("Knees collapsing inward", "Heels lifting off the floor", "Losing the brace and rounding at the bottom"),
            "Use safety pins for heavy squats; reduce depth if your knees or back hurt."),
        r("lunge|split squat|step-up|step up|bulgarian",
            listOf("Take a long enough step that your front shin stays close to vertical; stand tall with your abs braced.",
                "Lower straight down until both knees are bent about 90 degrees; the back knee hovers just above the floor.",
                "Push through your front heel to return, exhaling.",
                "Complete all reps on one leg, then switch; keep both sides equal."),
            listOf("Front knee collapsing inward", "Step too short, pushing the knee far past the toes", "Leaning forward"),
            "Hold a wall or rail if balance is a limit."),
        r("leg extension|leg curl|hamstring curl|calf|adduct|abduct|kickback|donkey",
            listOf("Adjust the seat or pad so the machine's pivot lines up with your joint.",
                "Move through the full range in a smooth, controlled way, exhaling as you work.",
                "Squeeze for a second at the end of the movement.",
                "Return slowly (about 2-3 seconds) - do not let the weight stack drop."),
            listOf("Bouncing or using momentum", "Cutting the range short"),
            "Stop for sharp joint pain; keep the load moderate."),
        r("curl|preacher|concentration|spider|drag",
            listOf("Stand or sit tall with your elbows pinned by your sides and your wrists straight.",
                "Curl the weight up by bending only at the elbow, exhaling, until your biceps are fully squeezed.",
                "Pause for a moment at the top.",
                "Lower slowly (2-3 seconds) all the way down to straight arms."),
            listOf("Swinging your torso", "Elbows drifting forward", "Dropping the weight on the way down"),
            "Lower the weight if your elbows or wrists ache."),
        r("tricep|triceps|pushdown|push down|skull|extension|jm press|close grip|close-grip|diamond|kickback",
            listOf("Keep your upper arms still and close to your body (or overhead, pointing straight up) with your elbows fixed.",
                "Straighten your arms by extending only at the elbow, exhaling, until fully locked out.",
                "Squeeze your triceps for a second at the end.",
                "Return slowly until your forearms are back at about 90 degrees or a comfortable stretch."),
            listOf("Elbows flaring or drifting", "Using your body weight to swing the weight", "Cutting the lockout short"),
            "Keep loads moderate on overhead and skull-crusher variations to protect your elbows."),
        r("shrug|farmer|carry|walk|suitcase",
            listOf("Stand tall with the weight at your sides, shoulders back and down, grip firm.",
                "Lift your shoulders straight up toward your ears (shrugs) or walk with steady, short steps (carries), exhaling.",
                "Pause at the top of a shrug; keep your ribs down and your posture tall on carries.",
                "Lower slowly; do not roll your shoulders."),
            listOf("Rolling the shoulders", "Craning your neck forward", "Leaning to one side on carries"),
            "Use straps only once your grip is the limiting factor."),
        r("run|jog|sprint|cycle|bike|row machine|rowing|treadmill|elliptical|jump|burpee|climber|jack|skater|high knees|hiit|cardio|swim|skipping",
            listOf("Warm up for 5 minutes at an easy pace first.",
                "Start at a pace where you could still talk in short sentences, then build gradually.",
                "Keep your posture tall, breathe rhythmically and land softly on jumping moves.",
                "Cool down for a few minutes and stretch lightly afterwards."),
            listOf("Starting too fast", "Skipping the warm-up", "Landing stiff-legged"),
            "Stop for chest pain, dizziness or unusual breathlessness and get medical advice. Calorie figures are estimates."),
        r("stretch|yoga|mobility|foam|pose|opener|rotation",
            listOf("Ease into the position slowly - you should feel a stretch, never pain.",
                "Breathe slowly and deeply; let the muscle relax a little more with each exhale.",
                "Hold for 20-40 seconds without bouncing.",
                "Release slowly and repeat on the other side."),
            listOf("Bouncing", "Holding your breath", "Pushing into sharp pain"),
            "Never stretch a cold muscle hard; warm up first."),
    )

    private val BY_GROUP = mapOf(
        "chest" to "bench press", "back" to "row", "shoulders" to "shoulder press", "delts" to "lateral raise",
        "biceps" to "curl", "triceps" to "triceps extension", "legs" to "squat", "quads" to "squat", "glutes" to "hip thrust",
        "hamstrings" to "romanian deadlift", "calves" to "calf raise", "abs" to "crunch", "core" to "plank",
        "lats" to "pulldown", "upper-back" to "row", "forearms" to "curl", "traps" to "shrug", "cardio" to "run",
    )

    private val GENERIC = Guide(
        steps = listOf("Set up in a stable position with your abs braced and your posture tall.",
            "Move slowly through the full range you can control, exhaling on the effort.",
            "Pause briefly at the hardest point, then return under control.",
            "Choose a weight you can lift for the target reps with 1-2 reps to spare."),
        mistakes = listOf("Rushing the reps", "Holding your breath", "Using momentum instead of the muscle"),
        safety = "Stop if anything hurts sharply. This is general guidance, not a substitute for a coach, physiotherapist or doctor.",
    )

    private val SECONDARY_RULES: List<Pair<Regex, List<String>>> = listOf(
        "leg swing" to listOf("hip flexors", "glutes"),
        "arm circle|shoulder roll" to listOf("shoulders", "upper back"),
        "hip circle|world.?s greatest" to listOf("glutes", "core", "hamstrings"),
        "cat.?cow|thoracic|inchworm|walkout" to listOf("core", "shoulders"),
        "march|jog on spot|light cardio|jumping jack|high knees" to listOf("calves", "hip flexors", "core"),
        "bench press|chest press|push-up|push up|pushup|dip|floor press" to listOf("triceps", "front delts"),
        "fly|flye|pec deck|crossover" to listOf("front delts", "biceps"),
        "overhead press|shoulder press|military|arnold|push press" to listOf("triceps", "upper chest", "core"),
        "lateral raise|side raise|front raise|upright row" to listOf("traps", "forearms"),
        "rear delt|reverse fly|reverse pec|face pull|band pull apart" to listOf("traps", "rhomboids"),
        "pull-up|pull up|pullup|chin-up|chin up|pulldown|pull down" to listOf("biceps", "rear delts", "forearms"),
        "row|rack pull|t-bar|pendlay" to listOf("biceps", "rear delts", "lower back"),
        "kettlebell swing|kb swing" to listOf("glutes", "hamstrings", "core"),
        "deadlift|romanian|good morning|hyperextension|back extension" to listOf("glutes", "hamstrings", "lower back"),
        "hip thrust|glute bridge|bridge" to listOf("hamstrings", "core"),
        "squat|leg press|hack|wall sit|sissy|pistol" to listOf("glutes", "hamstrings", "core"),
        "lunge|split squat|step-up|step up" to listOf("glutes", "hamstrings", "core"),
        "leg curl|hamstring curl" to listOf("calves", "glutes"),
        "leg extension" to listOf("hip flexors"),
        "calf" to listOf("ankle stabilizers"),
        "curl|preacher|concentration|spider|drag" to listOf("forearms", "front delts"),
        "tricep|triceps|pushdown|push down|skull|extension|jm press|close grip|close-grip|diamond|kickback" to listOf("shoulders", "chest"),
        "shrug|farmer|carry|suitcase" to listOf("upper back", "forearms", "core"),
        "plank|hollow|dead bug|bird dog|pallof|stability" to listOf("shoulders", "glutes", "obliques"),
        "rollout|wheel" to listOf("lats", "shoulders", "obliques"),
        "crunch|sit-up|sit up|v-up|flutter" to listOf("obliques", "hip flexors"),
        "leg raise|knee raise" to listOf("hip flexors", "obliques"),
        "russian|bicycle|twist" to listOf("obliques", "hip flexors"),
        "run|jog|sprint|cycle|bike|treadmill|elliptical|jump|burpee|climber|skater|hiit|cardio|swim|skipping" to listOf("calves", "glutes", "core"),
        "stretch|yoga|mobility|foam|pose" to listOf("core", "hips"),
    ).map { (p, l) -> Regex(p, RegexOption.IGNORE_CASE) to l }

    private val SECONDARY_BY_GROUP = mapOf(
        "chest" to listOf("triceps", "front delts"), "pectorals" to listOf("triceps", "front delts"),
        "back" to listOf("biceps", "rear delts"), "lats" to listOf("biceps", "rear delts"), "upper-back" to listOf("biceps", "rear delts"),
        "shoulders" to listOf("triceps", "traps"), "delts" to listOf("triceps", "traps"),
        "biceps" to listOf("forearms"), "triceps" to listOf("shoulders"), "forearms" to listOf("biceps"),
        "legs" to listOf("glutes", "hamstrings", "core"), "quads" to listOf("glutes", "hamstrings"),
        "hamstrings" to listOf("glutes", "lower back"), "glutes" to listOf("hamstrings", "core"), "calves" to listOf("ankle stabilizers"),
        "abs" to listOf("obliques", "hip flexors"), "core" to listOf("obliques", "hip flexors"), "traps" to listOf("upper back", "forearms"),
        "cardio" to listOf("calves", "glutes", "core"),
    )

    /** Supporting muscles for an exercise when the dataset lists none: worked out from the movement, then from the main muscle. */
    fun secondaryFor(name: String, muscleGroup: String? = null): List<String> {
        SECONDARY_RULES.firstOrNull { it.first.containsMatchIn(name) }?.let { return it.second }
        return muscleGroup?.lowercase()?.let { SECONDARY_BY_GROUP[it] }.orEmpty()
    }

    /**
     * Mistakes that belong to THIS variation of the movement (the angle, the equipment, the grip, one arm or two...). They are put ahead of the
     * movement-type mistakes, so two presses no longer show the same list: an incline press warns about the bench angle, a decline press about
     * the head-down position, a dumbbell version about uneven arms, and so on.
     */
    private val VARIATION_MISTAKES: List<Pair<Regex, List<String>>> = listOf(
        Regex("incline") to listOf("Setting the bench too steep, so the shoulders take over from the upper chest (30-45 degrees is enough)", "Letting the bar or dumbbells drift toward your face instead of over your upper chest"),
        Regex("decline") to listOf("Sliding down the pad or letting your feet slip out of the leg holders", "Lowering the weight toward your neck instead of your lower chest", "Rushing the rep because the head-down position feels awkward"),
        Regex("reverse.?grip") to listOf("Gripping too wide, which strains the wrists and shoulders", "Letting the wrists bend back under the bar"),
        Regex("close.?grip|narrow") to listOf("Bringing the hands so close that the wrists are stressed (about shoulder width is enough)", "Letting the elbows flare instead of staying tucked"),
        Regex("wide") to listOf("Going so wide that the shoulders feel pinched at the bottom", "Cutting the range short because the grip is too wide"),
        Regex("dumbbell|dumb.?bell") to listOf("Letting the two dumbbells drift apart or move unevenly", "Swinging the weights up with momentum instead of controlling both sides", "Clanging the dumbbells together at the top"),
        Regex("barbell") to listOf("Gripping the bar unevenly so one side sits higher", "Letting the bar drift away from your body's line"),
        Regex("cable") to listOf("Standing too close to the stack so the cable pulls at the wrong angle", "Letting the weight stack touch down between reps and losing tension", "Using body swing to move the handle"),
        Regex("machine|lever|smith") to listOf("Leaving the seat or pad set for someone else - adjust it so the joint lines up with the machine's pivot", "Letting the weight stack slam back at the end of each rep"),
        Regex("band|resistance band") to listOf("Letting the band snap back instead of controlling the return", "Using a band that is too light or too heavy for the full range"),
        Regex("single|one.?arm|one.?leg|unilateral|alternating") to listOf("Twisting or leaning the body to lift the weight", "Doing many more reps on the strong side than the weak one"),
        Regex("seated|sitting") to listOf("Slouching or rounding the lower back against the seat", "Bouncing off the seat to start the rep"),
        Regex("standing") to listOf("Locking the knees or leaning back to cheat the weight up", "Letting the hips shift forward as you tire"),
        Regex("lying|prone|supine") to listOf("Letting the lower back lift off the bench or floor", "Dropping the weight fast instead of a slow lowering phase"),
        Regex("bent.?over") to listOf("Standing up as you tire so it turns into a shrug", "Rounding the upper back instead of keeping it flat"),
        Regex("hammer") to listOf("Rotating the wrists during the rep instead of keeping the palms facing each other", "Swinging the elbows forward"),
        Regex("preacher") to listOf("Lifting the upper arm off the pad", "Dropping the weight hard at the bottom and straining the elbow"),
        Regex("concentration") to listOf("Resting the elbow on a moving knee instead of the inside of your thigh", "Leaning back to help the curl"),
        Regex("overhead") to listOf("Arching the lower back to finish the rep", "Letting the elbows drift wide or back"),
        Regex("skull|crusher") to listOf("Letting the elbows flare out to the sides", "Lowering the weight toward the forehead too fast"),
        Regex("goblet") to listOf("Letting the weight pull your chest forward and your heels lift", "Holding the weight away from your chest"),
        Regex("sumo") to listOf("Letting the knees cave inward instead of tracking over the toes", "Rounding the back as you pull"),
        Regex("romanian|stiff.?leg|rdl") to listOf("Bending the knees so it becomes a squat", "Letting the bar drift away from your legs", "Going lower than your back can stay flat"),
        Regex("bulgarian|split") to listOf("Standing too close so the front knee travels far past the toes", "Leaning the torso forward and losing balance"),
        Regex("pull.?up|chin.?up") to listOf("Kipping or swinging to get above the bar", "Stopping short of a full hang at the bottom", "Shrugging the shoulders up to the ears"),
        Regex("pulldown|pull.?down") to listOf("Leaning far back and turning it into a row", "Pulling the bar behind the neck", "Letting the shoulders shrug up as you pull"),
        Regex("row") to listOf("Jerking the weight with the lower back", "Shrugging the shoulders instead of squeezing the shoulder blades"),
        Regex("curl") to listOf("Swinging the elbows forward or the back backward to lift", "Dropping the weight quickly instead of lowering it slowly"),
        Regex("shrug") to listOf("Rolling the shoulders in circles", "Bending the elbows to pull the weight up"),
        Regex("lateral|side raise") to listOf("Raising the weight higher than the shoulders", "Leading with the hands so the traps take over the delts"),
        Regex("calf") to listOf("Bouncing at the bottom instead of pausing", "Rolling the ankles outward or inward"),
        Regex("lunge|step.?up") to listOf("Taking too short a step so the knee pushes far past the toes", "Letting the front knee cave inward"),
        Regex("crunch|sit.?up") to listOf("Pulling on the neck with the hands", "Hurrying instead of squeezing the abs at the top"),
    )

    /** A guide for any exercise; never null. Mistakes are specific to the variation first, then the movement type. */
    fun forName(name: String, muscleGroup: String? = null): Guide = withVariation(name, baseFor(name, muscleGroup))

    private fun baseFor(name: String, muscleGroup: String?): Guide {
        val n = name.trim()
        RULES.firstOrNull { it.match.containsMatchIn(n) }?.let { return it.guide }
        val proxy = muscleGroup?.lowercase()?.let { BY_GROUP[it] }
        if (proxy != null) RULES.firstOrNull { it.match.containsMatchIn(proxy) }?.let { return it.guide }
        return GENERIC
    }

    private fun withVariation(name: String, g: Guide): Guide {
        val n = name.lowercase()
        val extra = VARIATION_MISTAKES.filter { it.first.containsMatchIn(n) }.flatMap { it.second }
        if (extra.isEmpty()) return g
        // up to three specific ones first (spread across the matching variations), then the general movement ones, five in total
        val perGroup = VARIATION_MISTAKES.filter { it.first.containsMatchIn(n) }.map { it.second.first() }
        val specific = (perGroup + extra).distinct().take(3)
        val merged = (specific + g.mistakes).distinct().take(5)
        return g.copy(mistakes = merged)
    }
}
