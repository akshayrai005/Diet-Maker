package com.nutriai.ui.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Both GIF sets must be served from the SAME repo snapshot, and overrides that point into anatome-gifs/ must resolve under it. */
class MediaAddressTest {
    @Test
    fun oneSnapshotForEverything() {
        val ours = ExerciseDemoMap.BASE.substringAfter("@").substringBefore("/")
        val anatome = ExerciseDemoMapAnatome.BASE.substringAfter("@").substringBefore("/")
        assertEquals("our GIFs and the Anatome GIFs must use the same snapshot", ours, anatome)
        assertTrue(ExerciseDemoMap.gifUrl("Chest Dips")!!.startsWith(ExerciseDemoMap.BASE + "anatome-gifs/"))
    }
}
