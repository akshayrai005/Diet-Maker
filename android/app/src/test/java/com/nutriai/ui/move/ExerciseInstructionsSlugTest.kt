package com.nutriai.ui.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseInstructionsSlugTest {
    @Test
    fun apostrophesAndCombinedNamesResolveToDatasetSlugs() {
        assertEquals("world-greatest-stretch", ExerciseInstructions.slugOf("world’s greatest stretch"))
        assertEquals("world-greatest-stretch", ExerciseInstructions.slugOf("World's greatest stretch"))
        assertEquals("barbell-bench-press", ExerciseInstructions.slugOf("Barbell Bench Press"))
        assertTrue(ExerciseDemoMapFull.exactSlugToId.containsKey("world-greatest-stretch"))
    }

    @Test
    fun bundledInstructionsCoverMostOfTheLibrary() {
        val asset = listOf("src/main/assets/exercise_instructions.json", "app/src/main/assets/exercise_instructions.json")
            .map(::File).first { it.exists() }
        val text = asset.readText()
        val keys = Regex("\"([a-z\\-]+/[a-z0-9\\-]+)\":\\[").findAll(text).map { it.groupValues[1] }.toSet()
        assertTrue("expected 1,000+ exercises with steps, got ${keys.size}", keys.size > 1000)
        assertTrue(keys.contains("hamstrings/world-greatest-stretch"))
    }
}
