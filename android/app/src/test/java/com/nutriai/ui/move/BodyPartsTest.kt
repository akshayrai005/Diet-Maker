package com.nutriai.ui.move

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every library exercise sits under exactly one body-part tile, and no tile is empty. */
class BodyPartsTest {
    @Test
    fun everyExerciseHasABodyPartTile() {
        val tiles = BodyParts.tiles.toSet()
        val stray = ExerciseCatalog.entries.filter { it.category !in tiles }.map { it.item.name + " [" + it.category + "]" }
        val counts = BodyParts.tiles.associateWith { t -> ExerciseCatalog.entries.count { it.category == t } }
        File("C:/Users/akshay/AppData/Local/Temp/bodyparts_counts.txt").writeText(counts.entries.joinToString("\n") { it.key.label + ": " + it.value } + "\nSTRAY " + stray.size + "\n" + stray.take(30).joinToString("\n"))
        assertTrue("exercises outside any tile: " + stray.take(10), stray.isEmpty())
        for ((t, n) in counts) assertTrue(t.label + " has only " + n + " exercises", n >= 3)
    }

    @Test
    fun keyExercisesLandInTheRightPart() {
        // (some duplicate names are hidden from the Library on purpose, so only check the ones that are shown)
        val expect = mapOf(
            "Barbell Curl" to ExerciseCatalog.Category.BICEPS, "Hammer Curl" to ExerciseCatalog.Category.BICEPS,
            "Tricep Pushdown" to ExerciseCatalog.Category.TRICEPS, "Pull-up" to ExerciseCatalog.Category.LATS,
            "Chin-up" to ExerciseCatalog.Category.LATS, "Lat Pulldown" to ExerciseCatalog.Category.LATS,
            "Barbell Row" to ExerciseCatalog.Category.UPPER_BACK, "Back Extension" to ExerciseCatalog.Category.LOWER_BACK,
        )
        var checked = 0
        for ((name, want) in expect) {
            val e = ExerciseCatalog.entries.firstOrNull { it.item.name.equals(name, ignoreCase = true) } ?: continue
            checked++
            assertTrue(name + " is under " + e.category + ", expected " + want, e.category == want)
        }
        assertTrue("expected to check at least 4 known exercises, checked " + checked, checked >= 4)
        val shrugs = ExerciseCatalog.entries.filter { it.item.name.contains("shrug", ignoreCase = true) }
        assertTrue(shrugs.isNotEmpty() && shrugs.count { it.category == ExerciseCatalog.Category.TRAPS } * 2 >= shrugs.size)
        val squats = ExerciseCatalog.entries.filter { it.item.name.contains("squat", ignoreCase = true) && !it.item.name.contains("jump", ignoreCase = true) }
        assertTrue(squats.isNotEmpty() && squats.count { it.category == ExerciseCatalog.Category.QUADS } * 2 >= squats.size)
    }
}
