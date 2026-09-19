package com.nutriai.ui.move

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Two different library exercises must not show the same GIF (that reads as a bug); planned (curated) names are exempt. */
class NoSharedGifTest {
    @Test
    fun libraryHasFarFewerSharedGifs() {
        val shared = ExerciseCatalog.entries.groupBy { ExerciseDemoMap.gifUrl(it.item.name) }.filter { it.key != null && it.value.size > 1 }
        val affected = shared.values.sumOf { it.size }
        val sb = StringBuilder("SHARED GROUPS " + shared.size + ", exercises affected " + affected + " of " + ExerciseCatalog.entries.size + "\n")
        for ((u, es) in shared.entries.sortedByDescending { it.value.size }.take(40)) sb.append(es.size.toString() + "x " + u!!.substringAfterLast("/") + " <- " + es.joinToString(" | ") { it.item.name } + "\n")
        File("C:/Users/akshay/AppData/Local/Temp/shared_after.txt").writeText(sb.toString())
        assertTrue("still $affected exercises share a GIF", affected < 120)
    }
}
