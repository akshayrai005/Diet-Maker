package com.nutriai.ui.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every exercise the server can put in a plan must have a demo GIF. The names are read straight from the generator
 * source, so adding an exercise with no GIF makes this fail instead of showing a blank card on the phone.
 */
class PlannedExercisesHaveGifTest {
    private val generator = listOf("../server/src/modules/exercise/workoutGenerator.ts", "../../server/src/modules/exercise/workoutGenerator.ts")
        .map(::File).first { it.exists() }

    private fun plannedNames(): List<String> {
        val src = generator.readText()
        val single = "[wscm]\\(\\s*'((?:[^'\\\\]|\\\\.)*)'\\s*,"
        val double = "[wscm]\\(\\s*\"([^\"]+)\"\\s*,"
        val objects = "name:\\s*'((?:[^'\\\\]|\\\\.)*)'"
        val names = mutableListOf<String>()
        for (p in listOf(single, double, objects)) Regex(p).findAll(src).forEach { names += it.groupValues[1] }
        return names.map { it.replace("\\'", "'") }.filter { it.isNotBlank() }.distinct()
    }

    @Test
    fun everyPlannedExerciseHasADemoGif() {
        val names = plannedNames()
        assertTrue("expected to read 150+ exercise names from the generator, got ${names.size}", names.size > 150)
        val missing = names.filter { ExerciseDemoMap.gifUrl(it) == null }
        assertTrue("Planned exercises with no GIF: $missing", missing.isEmpty())
    }

    @Test
    fun fixedMatchesStayOnTheRightMovement() {
        fun id(name: String) = ExerciseDemoMap.gifUrl(name)?.substringAfterLast("/")?.removeSuffix(".gif")
        assertEquals("band-horizontal-pallof-press", id("Band horizontal pallof press"))
        assertEquals("band-seated-twist", id("Band seated twist"))
        assertEquals("dumbbell-side-bend", id("Dumbbell side bend"))
        assertEquals("world-greatest-stretch", id("World's greatest stretch"))
        assertEquals("glute-bridge-march", id("Glute bridge march"))
        assertEquals("rear-deltoid-stretch", id("Rear deltoid stretch"))
        assertEquals("ankle-circles", id("Ankle circles"))
        assertEquals("jackknife-sit-up", id("Jackknife sit-up"))
    }
}
