package com.nutriai.ui.wellness

// AUTO-GENERATED: a representative pose illustration per yoga pose, from the free yoga-api
// (github.com/alexcumplido/yoga-api, MIT; images CC0), served from Cloudinary. Keys are a canonical
// form of the pose name (parentheticals/"→"/"or"/"each side" stripped, punctuation removed, tokens
// sorted). Unmatched poses (Happy Baby, Legs-Up-the-Wall, Neck rolls) show a yoga emoji instead.
object YogaPoseMap {
    private val images: Map<String, String> = mapOf(
        "a salutation sun" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/a-salutation-sun.png",
        "angle bound reclining" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/angle-bound-reclining.png",
        "angle side" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/angle-side.png",
        "b salutation sun" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/b-salutation-sun.png",
        "boat" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/boat.png",
        "bridge" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/bridge.png",
        "cat cow" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/cat-cow.png",
        "cat cow seated" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/cat-cow-seated.png",
        "chair" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/chair.png",
        "crow" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/crow.png",
        "dog downward" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/dog-downward.png",
        "fold forward standing" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/fold-forward-standing.png",
        "i warrior" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/i-warrior.png",
        "ii warrior" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/ii-warrior.png",
        "low lunge" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/low-lunge.png",
        "mountain" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/mountain.png",
        "plank" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/plank.png",
        "plank side" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/plank-side.png",
        "reclining twist" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/reclining-twist.png",
        "savasana" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/savasana.png",
        "seated spinal twist" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/seated-spinal-twist.png",
        "supine twist" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/supine-twist.png",
        "wheel" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@ee09a8a10dbf0addb436af6b671ab6854bec7f18/yoga/wheel.png",
    )

    private fun canon(name: String): String {
        val c = name.lowercase()
            .replace(Regex("\\(.*?\\)"), " ").replace(Regex("→.*"), " ").replace(Regex("\\bor\\b.*"), " ")
            .replace(Regex("each side|hip opener|big breath|\\bpose\\b|\\bflow\\b"), " ")
            .replace(Regex("['’]"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        if (c.isEmpty()) return ""
        return c.split(Regex("\\s+")).filter { it.isNotEmpty() }.sorted().joinToString(" ")
    }

    /** Pose illustration url for a pose name, or null (→ show a yoga emoji fallback). */
    fun imageUrl(name: String): String? = images[canon(name)]
}
