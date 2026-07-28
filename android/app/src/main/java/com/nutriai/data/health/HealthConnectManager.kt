package com.nutriai.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
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

    /** All permissions requested at once so one grant covers steps, heart rate and sleep. */
    val readPermissions: Set<String> = stepPermissions + heartPermissions + sleepPermissions

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

    /** Total steps today (device local day), de-duplicated across sources. 0 if unavailable/denied. */
    suspend fun readTodaySteps(): Long {
        val client = clientOrNull() ?: return 0L
        if (!granted(stepPermissions)) return 0L
        return try {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().atStartOfDay(zone).toInstant()
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                ),
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Steps per day for the last `days` days, keyed by local date "YYYY-MM-DD". Empty when
     * unavailable/denied. Powers the step-history chart (Health Connect keeps ~30 days locally).
     */
    suspend fun readDailySteps(days: Int): Map<String, Long> {
        val client = clientOrNull() ?: return emptyMap()
        if (!granted(stepPermissions)) return emptyMap()
        return try {
            val end = java.time.LocalDateTime.now()
            val start = end.toLocalDate().minusDays((days - 1).toLong()).atStartOfDay()
            val response = client.aggregateGroupByPeriod(
                androidx.health.connect.client.request.AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = java.time.Period.ofDays(1),
                ),
            )
            response.associate { bucket ->
                bucket.startTime.toLocalDate().toString() to (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
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
}
