package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.ManualSleepEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Places a logged session on the clock. A sleep bar is labelled with the day the user woke up,
 * so a session that crosses midnight starts the evening before the bar's date and ends on it.
 * A session that does not cross midnight, such as a nap, lies entirely on the bar's date.
 */
object ManualSleepAnchoring {
    fun toEpochRange(
        wakeDate: LocalDate,
        start: LocalTime,
        end: LocalTime,
        zone: ZoneId,
    ): Pair<Long, Long> {
        val startDate = if (end <= start) wakeDate.minusDays(1) else wakeDate
        return startDate.atTime(start).toEpochMs(zone) to wakeDate.atTime(end).toEpochMs(zone)
    }

    /**
     * Earlier versions started every session on the bar's date, so an overnight session ended the
     * day after it. The current rule never ends a session after its date, which identifies those
     * entries; shifting them back one day puts them where the user meant.
     */
    fun correctLegacy(
        entry: ManualSleepEntry,
        zone: ZoneId,
    ): ManualSleepEntry {
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.endTimeMs), zone)
        if (!end.toLocalDate().isAfter(entry.date)) return entry
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.startTimeMs), zone)
        return entry.copy(
            startTimeMs = start.minusDays(1).toEpochMs(zone),
            endTimeMs = end.minusDays(1).toEpochMs(zone),
        )
    }

    private fun LocalDateTime.toEpochMs(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()
}
