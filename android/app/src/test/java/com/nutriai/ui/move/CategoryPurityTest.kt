package com.nutriai.ui.move

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Picking a muscle must list that muscle's exercises - no back exercises under Chest, etc. */
class CategoryPurityTest {
    private val backMuscles = setOf("lats", "middle back", "lower back", "upper-back", "traps", "back", "spine")

    @Test
    fun chestListHasNoBackExercises() {
        val chest = ExerciseCatalog.entries.filter { it.category == ExerciseCatalog.Category.CHEST }
        val offenders = chest.filter { (it.item.muscleGroup ?: "").lowercase() in backMuscles }.map { it.item.name + " [" + it.item.muscleGroup + "]" }
        File("C:/Users/akshay/AppData/Local/Temp/chest_offenders.txt").writeText("CHEST " + chest.size + " exercises, offenders " + offenders.size + "\n" + offenders.joinToString("\n"))
        assertTrue("Chest contains back exercises: $offenders", offenders.isEmpty())
    }
}
