package com.nutriai.util

import java.util.Calendar

/** Single source of truth for which meal a log belongs to, from the clock (user wakes 8am, sleeps midnight). */
object MealSlot {
    fun now(): String {
        val minutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        return when {
            minutes < 11 * 60 -> "breakfast"
            minutes < 13 * 60 -> "midmorning"
            minutes < 16 * 60 -> "lunch"
            minutes < 19 * 60 -> "eveningsnack"
            minutes < 22 * 60 -> "dinner"
            else -> "bedtime"
        }
    }
}
