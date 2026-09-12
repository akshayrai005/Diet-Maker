package com.nutriai.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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

    /** All permissions requested at once so one grant covers steps, heart rate, sleep, BP and SpO2. */
    val readPermissions: Set<String> = stepPermissions + heartPermissions + sleepPermissions + bpPermissions + spo2Permissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    private suspend fun granted(perms: Set<String>): Boolean {
        val client = clientOrNull() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(perms)
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
            val records = readAllStepsRecords(client, start, Instant.now())
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
            val records = readAllStepsRecords(client, start, end)
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
}
