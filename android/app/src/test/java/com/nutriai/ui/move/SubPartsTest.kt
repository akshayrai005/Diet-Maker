package com.nutriai.ui.move

import com.nutriai.ui.move.ExerciseCatalog.Category as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubPartsTest {
    @Test
    fun knownExercisesLandInTheRightRegion() {
        assertEquals("Upper", SubParts.classify(C.CHEST, "Incline Dumbbell Press"))
        assertEquals("Lower", SubParts.classify(C.CHEST, "Decline Bench Press"))
        assertEquals("Middle", SubParts.classify(C.CHEST, "Barbell Bench Press"))
        assertEquals("Side", SubParts.classify(C.SHOULDERS, "Dumbbell Lateral Raise"))
        assertEquals("Rear", SubParts.classify(C.SHOULDERS, "Reverse Pec Deck"))
        assertEquals("Front", SubParts.classify(C.SHOULDERS, "Overhead Press"))
        assertEquals("Obliques", SubParts.classify(C.CORE, "Russian Twist"))
        assertEquals("Lower", SubParts.classify(C.CORE, "Hanging Leg Raise"))
        assertEquals("Upper", SubParts.classify(C.CORE, "Cable Crunch"))
        assertEquals("Overhead", SubParts.classify(C.TRICEPS, "Skull Crushers"))
        assertEquals("Hammer & reverse", SubParts.classify(C.BICEPS, "Hammer Curl"))
    }

    @Test
    fun everyExerciseHasARegion_andNoChipIsEmpty() {
        val sb = StringBuilder()
        for (cat in BodyParts.tiles) {
            val chips = SubParts.forCategory(cat)
            if (chips.isEmpty()) continue
            val inCat = ExerciseCatalog.entries.filter { it.category == cat }
            val counts = chips.associateWith { c -> inCat.count { SubParts.classify(cat, it.item.name) == c } }
            sb.append(cat.label + " (" + inCat.size + "): " + counts.entries.joinToString(", ") { it.key + "=" + it.value } + "\n")
            assertEquals(cat.label + ": every exercise must be in a listed region", inCat.size, counts.values.sum())
            for ((c, n) in counts) assertTrue(cat.label + " / " + c + " is empty", n >= 1)
        }
        File("C:/Users/akshay/AppData/Local/Temp/subparts_counts.txt").writeText(sb.toString())
    }
}
