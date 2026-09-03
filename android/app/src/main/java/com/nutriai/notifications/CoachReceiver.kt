package com.nutriai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.Dashboard
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fires for the smart-coach alarms ([CoachScheduler]). At fire time it fetches today's dashboard and
 * posts a DATA-DRIVEN nudge (not fixed text): a starvation check at midday, or an end-of-day summary
 * of what's still left to eat/drink. Always re-arms the same alarm for tomorrow.
 */
class CoachReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoachEntryPoint { fun repository(): AppRepository }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repo = EntryPointAccessors.fromApplication(context.applicationContext, CoachEntryPoint::class.java).repository()
                when (action) {
                    CoachScheduler.ACTION_MIDDAY -> repo.dashboard().getOrNull()?.let { maybePostStarvation(context, it) }
                    CoachScheduler.ACTION_SUMMARY -> repo.dashboard().getOrNull()?.let { postSummary(context, it) }
                    CoachScheduler.ACTION_LATE_NIGHT -> postLateNight(context, repo.dashboard().getOrNull())
                    CoachScheduler.ACTION_GYM_CHECK -> maybePostMissedGym(context, repo)
                }
            } finally {
                // Re-arm tomorrow regardless of whether the fetch succeeded.
                CoachScheduler.armNext(context, action)
                pending.finish()
            }
        }
    }

    private fun maybePostStarvation(context: Context, dash: Dashboard) {
        if (dash.fastingToday) return // fasting day - don't nag to eat.
        val consumed = dash.calories.consumed
        if (consumed <= 1.0) {
            ReminderNotifier.post(
                context,
                jobKey = "coach_midday",
                title = "🍽️ Have you eaten today?",
                text = "Nothing logged yet. Skipping meals stalls muscle growth - eat something now, even a small snack, and log it.",
                notifId = "coach_midday".hashCode(),
                tab = 2,
            )
        }
    }

    private fun postSummary(context: Context, dash: Dashboard) {
        val proteinLeft = remaining(dash.protein.target, dash.protein.consumed)
        val kcalLeft = dash.calories.remaining ?: remaining(dash.calories.target, dash.calories.consumed)
        val waterLeftMl = remaining(dash.water.targetMl ?: dash.water.target, dash.water.consumedMl ?: dash.water.consumed)

        val text = when {
            dash.fastingToday ->
                "Fast day today - break it gently with khichdi or curd rice, and rehydrate well. Back to plan tomorrow. 💪"
            (proteinLeft ?: 0.0) < 5 && (kcalLeft ?: 0.0) < 100 ->
                "On target today - ${(dash.protein.consumed ?: 0.0).roundToInt()}g protein in. Great work, keep the streak going. 🔥"
            else -> buildString {
                append("Still to go today: ")
                val bits = mutableListOf<String>()
                proteinLeft?.takeIf { it >= 5 }?.let { bits.add("${it.roundToInt()}g protein") }
                kcalLeft?.takeIf { it >= 100 }?.let { bits.add("${it.roundToInt()} kcal") }
                waterLeftMl?.takeIf { it >= 250 }?.let { bits.add("${(it / 250).roundToInt()} glass water") }
                append(bits.joinToString(", "))
                append(". ")
                if ((proteinLeft ?: 0.0) >= 20) append("Have 150g chicken/paneer or a scoop of whey before bed to repair muscle.")
                else if ((waterLeftMl ?: 0.0) >= 500) append("Sip some water before bed.")
                else append("A small high-protein snack will close the gap.")
            }
        }
        ReminderNotifier.post(
            context,
            jobKey = "coach_summary",
            title = "📊 Today's summary",
            text = text,
            notifId = "coach_summary".hashCode(),
            tab = 0,
        )
    }

    /** Late-night nudge to keep the last meal light (skipped on a fast day). */
    private fun postLateNight(context: Context, dash: Dashboard?) {
        if (dash?.fastingToday == true) return
        ReminderNotifier.post(
            context,
            jobKey = "coach_latenight",
            title = "🌙 Eating late?",
            text = "Keep it light this late - curd or eggs only, skip the roti. A heavy meal before bed hurts sleep and recovery.",
            notifId = "coach_latenight".hashCode(),
            tab = 2,
        )
    }

    /** If today is a scheduled training day but no workout was logged, nudge a quick home session. */
    private suspend fun maybePostMissedGym(context: Context, repo: AppRepository) {
        val env = repo.exercisePlanFull().getOrNull() ?: return
        val days = env.plan.days
        val todayDay = days.firstOrNull { it.label == "Today" } ?: return
        val hasContent = todayDay.exercises.isNotEmpty() || todayDay.warmup.isNotEmpty() ||
            todayDay.core.isNotEmpty() || todayDay.cardio != null || todayDay.cooldown.isNotEmpty()
        if (todayDay.rest || !hasContent) return // rest day - nothing to miss.

        val logged = repo.exerciseLogs(java.time.LocalDate.now().toString()).getOrNull().orEmpty()
        if (logged.isNotEmpty()) return // already trained today.

        ReminderNotifier.post(
            context,
            jobKey = "coach_gym",
            title = "🏋️ No workout logged today",
            text = "Even 3 sets of push-ups at home keep your progress going. Takes 5 minutes. 💪",
            notifId = "coach_gym".hashCode(),
            tab = 1,
        )
    }

    /** target − consumed, floored at 0; null when there's no target to compare against. */
    private fun remaining(target: Double?, consumed: Double?): Double? {
        if (target == null || target <= 0.0) return null
        return (target - (consumed ?: 0.0)).coerceAtLeast(0.0)
    }
}
