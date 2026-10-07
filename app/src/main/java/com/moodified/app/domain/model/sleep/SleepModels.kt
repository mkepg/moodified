package com.moodified.app.domain.model.sleep

import java.time.LocalDate
import java.time.LocalDateTime

enum class SleepStatus {
    AWAKE,
    ASLEEP,
    UNKNOWN,
    ;

    fun displayLabel(): String =
        when (this) {
            AWAKE -> "Awake"
            ASLEEP -> "Asleep"
            UNKNOWN -> "Unknown"
        }
}

data class SleepSignal(
    val status: SleepStatus = SleepStatus.UNKNOWN,
    val confidence: Int = 0,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isTracking: Boolean = false,
    val hasActiveSession: Boolean = false,
)

data class SleepSegment(
    val id: Long = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val status: SleepStatus,
    val awakenings: Int = 0,
    val timeInBedMinutes: Int = 0,
    val totalSleepMinutes: Int = 0,
    val confidence: Int = 0,
)

data class DailySleepSummary(
    val date: String,
    val totalSleepMinutes: Int,
    val awakenings: Int,
    val sleepOnsetMinutes: Int? = null,
    val isEstimated: Boolean = false,
)

data class SleepTrends(
    val daysAnalyzed: Int,
    val averageSleepMinutes: Int,
    val totalSleepDebtMinutes: Int,
    val consistencyScore: Int,
    val sleepGoalMinutes: Int = 480,
    val inferredSleepGoalMinutes: Int = 480,
    val baselineSleepOnsetMinutes: Int? = null,
)

data class ManualSleepEntry(
    val id: Long = 0,
    val date: LocalDate,
    val startTimeMs: Long,
    val endTimeMs: Long,
)
