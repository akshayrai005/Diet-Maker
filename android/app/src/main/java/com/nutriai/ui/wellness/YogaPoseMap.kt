package com.nutriai.ui.wellness

// AUTO-GENERATED: a representative pose illustration per yoga pose, from the free yoga-api
// (github.com/alexcumplido/yoga-api, MIT; images CC0), served from Cloudinary. Keys are a canonical
// form of the pose name (parentheticals/"→"/"or"/"each side" stripped, punctuation removed, tokens
// sorted). Unmatched poses (Happy Baby, Legs-Up-the-Wall, Neck rolls) show a yoga emoji instead.
object YogaPoseMap {
    private val images: Map<String, String> = mapOf(
        "a salutation sun" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/a-salutation-sun.png",
        "angle bound reclining" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/angle-bound-reclining.png",
        "angle side" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/angle-side.png",
        "b salutation sun" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/b-salutation-sun.png",
        "boat" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/boat.png",
        "bridge" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/bridge.png",
        "cat cow" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/cat-cow.png",
        "cat cow seated" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/cat-cow-seated.png",
        "chair" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/chair.png",
        "crow" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/crow.png",
        "dog downward" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/dog-downward.png",
        "fold forward standing" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/fold-forward-standing.png",
        "i warrior" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/i-warrior.png",
        "ii warrior" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/ii-warrior.png",
        "low lunge" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/low-lunge.png",
        "mountain" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/mountain.png",
        "plank" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/plank.png",
        "plank side" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/plank-side.png",
        "reclining twist" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/reclining-twist.png",
        "savasana" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/savasana.png",
        "seated spinal twist" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/seated-spinal-twist.png",
        "supine twist" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/supine-twist.png",
        "wheel" to "https://cdn.jsdelivr.net/gh/akshayrai005/kaizen-exercise-media@22ed30c5976f144c4dbd79d81e1a96c36f7888cb/yoga/wheel.png",
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
