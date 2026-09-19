package com.nutriai.ui.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Library must show BOTH exercise sets - and our own GIFs must always win where the two overlap. */
class AllExercisesShownTest {
    private fun slug(n: String) = n.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    @Test
    fun libraryHasBothSets() {
        val names = ExerciseCatalog.entries.map { it.item.name.lowercase() }
        println("LIBRARY exercises shown: ${names.size} (ours ${ExerciseCatalogGifDb.entries.size}, Anatome-only ${ExerciseCatalogAnatome.entries.size})")
        assertEquals("no duplicate names", names.size, names.toSet().size)
        assertTrue("expected 2,000+ exercises, got ${names.size}", names.size > 1900)
        val shown = names.toSet()
        val hidden = ExerciseDemoOverrides.hiddenFromLibrary
        fun slug(n: String) = n.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        assertTrue(ExerciseCatalogGifDb.entries.all { it.item.name.lowercase() in shown || slug(it.item.name) in hidden })
        assertTrue(ExerciseCatalogAnatome.entries.all { it.item.name.lowercase() in shown || slug(it.item.name) in hidden })
    }

    @Test
    fun anatomeExercisesUseTheirOwnGif_andOursIsNeverReplaced() {
        val noGif = ExerciseCatalog.entries.map { it.item.name }.filter { ExerciseDemoMap.gifUrl(it) == null }
        File("C:/Users/akshay/AppData/Local/Temp/library_no_gif.txt").writeText("COUNT " + noGif.size + "\n" + noGif.joinToString("\n"))
        val anatomeGifs = ExerciseCatalogAnatome.entries.count { "/anatome-gifs/" in (ExerciseDemoMap.gifUrl(it.item.name) ?: "") }
        println("Anatome-only exercises using an Anatome GIF: $anatomeGifs of ${ExerciseCatalogAnatome.entries.size}")
        assertTrue("Anatome exercises should use their own GIFs unless we already have them", anatomeGifs > 600)
        // an exercise our set already has must keep OUR gif
        for (e in ExerciseCatalogGifDb.entries) {
            val url = ExerciseDemoMap.gifUrl(e.item.name) ?: continue
            assertFalse("${e.item.name} must keep our own GIF", "/anatome-gifs/" in url)
        }
    }

    @Test
    fun anatomeAssetsAreBundled() {
        val f = listOf("src/main/assets/anatome_instructions.json", "app/src/main/assets/anatome_instructions.json").map(::File).first { it.exists() }
        val keys = Regex("\"([a-z0-9\\-]+)\":\\[").findAll(f.readText()).map { it.groupValues[1] }.toSet()
        println("Anatome instructions bundled for ${keys.size} exercises")
        assertTrue(keys.size > 700)
        val names = ExerciseCatalogAnatome.entries.map { slug(it.item.name) }.toSet()
        assertTrue("most instruction keys must match a catalog exercise", keys.count { it in names } > 700)
    }
}
