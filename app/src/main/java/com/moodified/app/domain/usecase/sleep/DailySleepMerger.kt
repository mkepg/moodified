package com.moodified.app.domain.usecase.sleep

import com.moodified.app.core.utils.SleepTimeUtils
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepWindow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Combines the estimated night for one wake date with the sessions the user logged for it.
 *
 * A logged session that overlaps the estimated night is a correction and replaces it. Any other
 * logged session is a nap and adds to the total. Bedtime always comes from the night, never from
 * a nap, so an afternoon nap cannot skew the onset baseline or the consistency score.
 */
object DailySleepMerger {
    private const val NAP_WINDOW_START_HOUR = 12
    private const val NAP_WINDOW_END_HOUR = 18

    fun merge(
        date: LocalDate,
        estimate: SleepSegment?,
        manual: List<ManualSleepEntry>,
        zone: ZoneId,
    ): DailySleepSummary? {
        if (estimate == null && manual.isEmpty()) return null

        val sessions = manual.map { LoggedSession(it.startTimeMs.toLocal(zone), it.endTimeMs.toLocal(zone)) }
        val loggedNight =
            if (estimate != null) {
                sessions.filter { it.start < estimate.endTime && it.end > estimate.startTime }
            } else {
                // Without an estimate to compare against, afternoon sessions are naps.
                sessions.filterNot { it.start.hour in NAP_WINDOW_START_HOUR until NAP_WINDOW_END_HOUR }
            }
        val naps = sessions.filterNot { session -> loggedNight.any { it === session } }
        val keptEstimate = estimate?.takeIf { loggedNight.isEmpty() }

        val nightMinutes = keptEstimate?.totalSleepMinutes ?: loggedNight.sumOf { it.minutes }
        val napMinutes = naps.sumOf { it.minutes }
        val bedtime = keptEstimate?.startTime ?: loggedNight.minOfOrNull { it.start }

        return DailySleepSummary(
            date = date.toString(),
            totalSleepMinutes = nightMinutes + napMinutes,
            awakenings = keptEstimate?.awakenings ?: 0,
            sleepOnsetMinutes = bedtime?.let { SleepTimeUtils.minutesSince6PM(it) },
            isEstimated = keptEstimate != null,
            napMinutes = napMinutes,
            hasManualEntries = manual.isNotEmpty(),
            estimatedNight = estimate?.let { SleepWindow(it.startTime, it.endTime, it.totalSleepMinutes) },
        )
    }

    private class LoggedSession(
        val start: LocalDateTime,
        val end: LocalDateTime,
    ) {
        val minutes: Int get() = Duration.between(start, end).toMinutes().toInt()
    }

    private fun Long.toLocal(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
}
