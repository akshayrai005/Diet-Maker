package com.nutriai.ui.move

import android.content.Context
import org.json.JSONObject

/**
 * Exercise-specific, step-by-step instructions, bundled with the app (assets/exercise_instructions.json, ~550 KB,
 * keyed by the GIF dataset id) so nothing is fetched at runtime. Source: the public "exercise-library" dataset.
 * Covers ~84% of the library; everything else falls back to [ExerciseGuide]'s movement-type steps.
 */
object ExerciseInstructions {
    @Volatile private var table: Map<String, List<String>>? = null

    private fun load(context: Context): Map<String, List<String>> {
        table?.let { return it }
        val parsed = try {
            val text = context.assets.open("exercise_instructions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            buildMap {
                for (key in json.keys()) {
                    val arr = json.getJSONArray(key)
                    put(key, List(arr.length()) { arr.getString(it) })
                }
            }
        } catch (_: Exception) {
            emptyMap() // missing/corrupt asset must never break the popup - the generic guide is shown instead
        }
        table = parsed
        return parsed
    }

    internal fun slugOf(text: String): String =
        text.trim().lowercase()
            .replace(Regex("[\\u2019']s\\b"), "") // world's -> world
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** The dataset's own steps for this exercise, or null when it has none. Combined names ("A / B", "A + B") try each part. */
    fun forName(context: Context, name: String): List<String>? {
        val candidates = listOf(name) + name.split("/", " + ", " & ").map { it.trim() }.filter { it.isNotEmpty() && it != name }
        for (c in candidates) {
            val id = ExerciseDemoMapFull.exactSlugToId[slugOf(c)] ?: continue
            load(context)[id]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }
}
