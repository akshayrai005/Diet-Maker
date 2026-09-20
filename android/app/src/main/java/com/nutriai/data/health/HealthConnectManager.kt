package com.nutriai.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads steps, heart rate and sleep from Health Connect - the hub that fitness bands and
 * smartwatches (Fastrack, boAt, Noise, Wear OS, Fitbit-via-Health-Connect, etc.) sync into.
 * Fully optional and free: every call degrades to null/0 when Health Connect is unavailable,
 * the metric isn't synced, or the read permission hasn't been granted.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val stepPermissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    private val heartPermissions = setOf(HealthPermission.getReadPermission(HeartRateRecord::class))
    private val sleepPermissions = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
    private val bpPermissions = setOf(HealthPermission.getReadPermission(BloodPressureRecord::class))
    private val spo2Permissions = setOf(HealthPermission.getReadPermission(OxygenSaturationRecord::class))
    private val tempPermissions = setOf(HealthPermission.getReadPermission(BodyTemperatureRecord::class))

    private val extraPermissions = setOf(
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
    )

    /** All permissions requested at once so one grant covers steps, heart rate, sleep, BP, SpO2, temp and the body/activity extras. */
    val readPermissions: Set<String> = stepPermissions + heartPermissions + sleepPermissions + bpPermissions + spo2Permissions + tempPermissions + extraPermissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    private suspend fun granted(perms: Set<String>): Boolean {
        val client = clientOrNull() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(perms)
    }

    /** Health Connect permissions the app wants that are not granted yet (empty when everything is allowed or HC is missing). */
    suspend fun missingPermissions(): Set<String> {
        val client = clientOrNull() ?: return emptySet()
        return readPermissions - client.permissionController.getGrantedPermissions()
    }

    /** Today's extra Health Connect numbers for the dashboard (each null when not granted or no data yet). */
    data class Extras(
        val distanceKm: Double? = null,
        val caloriesBurned: Int? = null,
        val exerciseSessions: Int? = null,
        val exerciseMinutes: Int? = null,
        val topSpeedKmh: Double? = null,
        val restingHr: Int? = null,
        val weightKg: Double? = null,
        val heightCm: Double? = null,
        val bmrKcal: Int? = null,
    ) {
        val any: Boolean get() = listOf(distanceKm, caloriesBurned, exerciseSessions, topSpeedKmh, weightKg, heightCm, bmrKcal).any { it != null }
    }

    suspend fun readExtras(): Extras {
        val client = clientOrNull() ?: return Extras()
        val have = client.permissionController.getGrantedPermissions()
        fun ok(c: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getReadPermission(c) in have
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val today = androidx.health.connect.client.time.TimeRangeFilter.between(start, now)
        val recent = androidx.health.connect.client.time.TimeRangeFilter.between(now.minus(java.time.Duration.ofDays(365)), now)

        suspend fun <T : Any> safe(block: suspend () -> T?): T? = try { block() } catch (e: Exception) { null }

        val distance = if (ok(DistanceRecord::class)) safe {
            client.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), today))[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
        } else null
        val calories = if (ok(TotalCaloriesBurnedRecord::class)) safe {
            client.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), today))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toInt()
        } else null
        val sessions = if (ok(ExerciseSessionRecord::class)) safe {
            client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(ExerciseSessionRecord::class, today)).records
        } else null
        val topSpeed = if (ok(SpeedRecord::class)) safe {
            client.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(SpeedRecord.SPEED_MAX), today))[SpeedRecord.SPEED_MAX]?.inKilometersPerHour
        } else null
        val resting = if (ok(RestingHeartRateRecord::class)) safe {
            client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(RestingHeartRateRecord::class, recent, ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.beatsPerMinute?.toInt()
        } else null
        val weight = if (ok(WeightRecord::class)) safe {
            client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(WeightRecord::class, recent, ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.weight?.inKilograms
        } else null
        val height = if (ok(HeightRecord::class)) safe {
            client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(HeightRecord::class, recent, ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.height?.inMeters?.times(100)
        } else null
        val bmr = if (ok(BasalMetabolicRateRecord::class)) safe {
            client.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(BasalMetabolicRateRecord::class, recent, ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.basalMetabolicRate?.inKilocaloriesPerDay?.toInt()
        } else null

        return Extras(
            distanceKm = distance?.takeIf { it > 0 },
            caloriesBurned = calories?.takeIf { it > 0 },
            exerciseSessions = sessions?.size?.takeIf { it > 0 },
            exerciseMinutes = sessions?.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }?.toInt()?.takeIf { it > 0 },
            topSpeedKmh = topSpeed?.takeIf { it > 0 },
            restingHr = resting,
            weightKg = weight,
            heightCm = height,
            bmrKcal = bmr,
        )
    }

    /** True if at least the steps permission is granted (used to show the connected state). */
    suspend fun hasStepPermission(): Boolean = granted(stepPermissions)

    /**
     * Reads every StepsRecord in [start, end), following pagination. Health Connect's own
     * COUNT_TOTAL aggregate merges/dedupes overlapping records across sources (phone + watch),
     * which can under-report when the phone sat still (e.g. on a rack) while a watch tracked the
     * walk - so we read raw records instead and let the caller pick the best source per day.
     */
    private suspend fun readAllStepsRecords(client: HealthConnectClient, start: Instant, end: Instant): List<StepsRecord> {
        val all = mutableListOf<StepsRecord>()
        var pageToken: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                ),
            )
            all.addAll(resp.records)
            pageToken = resp.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return all
    }

    /** Watch-written records only when any exist (user always wears a watch); otherwise everything. */
    private fun preferWatch(records: List<StepsRecord>): List<StepsRecord> {
        val watch = records.filter { it.metadata.device?.type == androidx.health.connect.client.records.metadata.Device.TYPE_WATCH }
        return watch.ifEmpty { records }
    }

    /**
     * Total steps today (device local day). When multiple sources report (e.g. phone + a synced
     * watch), takes the HIGHEST per-source total rather than Health Connect's merged total, so a
     * watch worn on a walk (while the phone sat on a rack) isn't undercounted.
     */
    suspend fun readTodaySteps(): Long {
        val client = clientOrNull() ?: return 0L
        if (!granted(stepPermissions)) return 0L
        return try {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().atStartOfDay(zone).toInstant()
            val records = preferWatch(readAllStepsRecords(client, start, Instant.now()))
            records.groupBy { it.metadata.dataOrigin.packageName }
                .maxOfOrNull { (_, recs) -> recs.sumOf { it.count } } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Steps per day for the last `days` days, keyed by local date "YYYY-MM-DD" - the HIGHEST
     * per-source total for that day (phone vs. watch), not a merged total. Empty when
     * unavailable/denied. Powers the step-history chart (Health Connect keeps ~30 days locally).
     */
    suspend fun readDailySteps(days: Int): Map<String, Long> {
        val client = clientOrNull() ?: return emptyMap()
        if (!granted(stepPermissions)) return emptyMap()
        return try {
            val zone = ZoneId.systemDefault()
            val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()
            val start = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
            val records = preferWatch(readAllStepsRecords(client, start, end))
            records
                .groupBy { it.startTime.atZone(zone).toLocalDate().toString() to it.metadata.dataOrigin.packageName }
                .mapValues { (_, recs) -> recs.sumOf { it.count } }
                .entries
                .groupBy({ it.key.first }, { it.value })
                .mapValues { (_, totals) -> totals.max() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Most recent heart-rate reading (bpm) in the last 24h - e.g. synced from a watch. */
    suspend fun readLatestHeartRate(): Int? {
        val client = clientOrNull() ?: return null
        if (!granted(heartPermissions)) return null
        return try {
            val end = Instant.now()
            val start = end.minus(Duration.ofHours(24))
            val resp = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(start, end)),
            )
            resp.records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    /** Last sleep session's duration in hours (looking back ~36h). Null if none/denied. */
    suspend fun readLastSleepHours(): Double? {
        val client = clientOrNull() ?: return null
        if (!granted(sleepPermissions)) return null
        return try {
            val end = Instant.now()
            val start = end.minus(Duration.ofHours(36))
            val resp = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end)),
            )
            val latest = resp.records.maxByOrNull { it.endTime } ?: return null
            val minutes = Duration.between(latest.startTime, latest.endTime).toMinutes()
            if (minutes <= 0) null else Math.round(minutes / 6.0) / 10.0
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Most recent blood-pressure reading (systolic/diastolic mmHg) in the last 24h, if the
     * watch's own app (e.g. Fastrack) writes BloodPressureRecord to Health Connect. Note: Health
     * Connect has no standard "stress score" data type - stress stays proprietary to each watch's
     * own app and can't be read here, which is why this app's stress field is manual self-entry.
     */
    suspend fun readLatestBloodPressure(): Pair<Int, Int>? {
        val client = clientOrNull() ?: return null
        if (!granted(bpPermissions)) return null
        return try {
            val end = Instant.now()
            val start = end.minus(Duration.ofHours(24))
            val resp = client.readRecords(
                ReadRecordsRequest(BloodPressureRecord::class, TimeRangeFilter.between(start, end)),
            )
            val latest = resp.records.maxByOrNull { it.time } ?: return null
            latest.systolic.inMillimetersOfMercury.toInt() to latest.diastolic.inMillimetersOfMercury.toInt()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Most recent blood-oxygen (SpO2 %) reading in the last 24h. Many budget fitness watches
     * (e.g. Fastrack) measure SpO2 but not blood pressure - this is the real signal to show when
     * BP isn't available from the paired device.
     */
    suspend fun readLatestOxygenSaturation(): Int? {
        val client = clientOrNull() ?: return null
        if (!granted(spo2Permissions)) return null
        return try {
            val end = Instant.now()
            val start = end.minus(Duration.ofHours(24))
            val resp = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(start, end)),
            )
            val latest = resp.records.maxByOrNull { it.time } ?: return null
            latest.percentage.value.toInt()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Most recent body temperature (°C) reading in the last 24h - a third fallback for that grid
     * slot when a watch has neither BP nor SpO2 but does measure skin/body temperature.
     */
    suspend fun readLatestBodyTemperature(): Double? {
        val client = clientOrNull() ?: return null
        if (!granted(tempPermissions)) return null
        return try {
            val end = Instant.now()
            val start = end.minus(Duration.ofHours(24))
            val resp = client.readRecords(
                ReadRecordsRequest(BodyTemperatureRecord::class, TimeRangeFilter.between(start, end)),
            )
            val latest = resp.records.maxByOrNull { it.time } ?: return null
            Math.round(latest.temperature.inCelsius * 10) / 10.0
        } catch (e: Exception) {
            null
        }
    }
}
