package com.nutriai.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nutriai.BuildConfig
import com.nutriai.data.local.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Evening "you're under-logged" nudge - the productivity gap the user asked to close: the app
 * otherwise never proactively tells you you're behind on calories, only shows it passively on the
 * dashboard if you happen to open it. Runs periodically (~60 min); only acts inside the 8-9pm local
 * window, at most once per day, and only when the user is genuinely under-logged (< 50% of target)
 * - never nags someone who's on track or has already eaten enough.
 *
 * Deliberately NOT built on the full NutriApi/Retrofit/Hilt stack (no Hilt-Work wiring exists in
 * this app yet, same reasoning as [WalkNudgeWorker]) - a bare OkHttp GET + minimal JSON parse is
 * enough for a single read-only field and avoids adding a whole DI path for one worker.
 */
class EveningNutritionNudgeWorker(
    context: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = ReminderPrefs(applicationContext)
        if (!prefs.isEveningNudgeEnabled()) return Result.success()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour != EVENING_HOUR) return Result.success() // only the 8-9pm window

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        if (prefs.eveningNudgeLastDate() == todayIso) return Result.success() // already sent today

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return Result.success()

        val tokenStore = TokenStore(applicationContext)
        val token = tokenStore.accessToken() ?: return Result.success() // not logged in

        val dashboard = withContext(Dispatchers.IO) { runCatching { fetchDashboard(token) }.getOrNull() }
            ?: return Result.retry() // transient network failure - try again on the next periodic tick

        val consumed = dashboard.optJSONObject("calories")?.optDouble("consumed")
        val target = dashboard.optJSONObject("calories")?.optDouble("target")
        if (consumed == null || target == null || target <= 0) return Result.success() // no target set yet

        if (consumed >= target * UNDER_LOGGED_FRACTION) return Result.success() // on track - no nudge

        ReminderNotifier.ensureChannel(applicationContext)
        val remaining = (target - consumed).toInt().coerceAtLeast(0)
        val text = "You've logged only ${consumed.toInt()} of ${target.toInt()} kcal today - about $remaining kcal left to hit your target. Log dinner to stay on track."
        ReminderNotifier.post(
            applicationContext,
            jobKey = "evening_nutrition_nudge",
            title = "🍽️ Still short on today's calories",
            text = text,
            notifId = NOTIF_ID,
            tab = 2, // Diet tab
        )
        prefs.setEveningNudgeLastDate(todayIso)
        return Result.success()
    }

    private fun fetchDashboard(token: String): JSONObject {
        val base = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS) // Render free tier can cold-start
            .build()
        val request = Request.Builder()
            .url("${base}dashboard")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) error("dashboard fetch failed: ${resp.code}")
            return JSONObject(body).getJSONObject("dashboard")
        }
    }

    companion object {
        const val UNIQUE_NAME = "kaizen_evening_nutrition_nudge"
        private const val NOTIF_ID = 990002
        private const val EVENING_HOUR = 20 // 8pm local
        private const val UNDER_LOGGED_FRACTION = 0.5
    }
}
