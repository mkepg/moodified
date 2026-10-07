package com.moodified.app.data.repository

import com.moodified.app.data.local.entity.sleep.ManualSleepEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepRepositoryManualEntryTest {
    @Test
    fun `buildManualSummary logic sums durations from two entries on same date`() {
        val date = LocalDate.of(2026, 10, 7)
        val zone = ZoneId.systemDefault()

        val overnightStart = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val overnightEnd = date.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val napStart = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val napEnd = date.atTime(15, 30).atZone(zone).toInstant().toEpochMilli()

        val entries =
            listOf(
                ManualSleepEntryEntity(id = 1, date = date.toString(), startTimeMillis = overnightStart, endTimeMillis = overnightEnd),
                ManualSleepEntryEntity(id = 2, date = date.toString(), startTimeMillis = napStart, endTimeMillis = napEnd),
            )

        val totalMinutes = entries.sumOf { ((it.endTimeMillis - it.startTimeMillis) / 60_000L).toInt() }
        assertEquals(570, totalMinutes) // 480 min overnight + 90 min nap
    }

    @Test
    fun `end-time before start-time on same calendar day crosses midnight`() {
        val date = LocalDate.of(2026, 10, 7)
        val zone = ZoneId.systemDefault()

        val startLt = java.time.LocalTime.of(23, 0)
        val endLt = java.time.LocalTime.of(7, 0)

        val startMs = date.atTime(startLt).atZone(zone).toInstant().toEpochMilli()
        val endDate = if (endLt < startLt) date.plusDays(1) else date
        val endMs = endDate.atTime(endLt).atZone(zone).toInstant().toEpochMilli()

        val durationMin = ((endMs - startMs) / 60_000L).toInt()
        assertEquals(480, durationMin) // 8 hours = 480 min
    }

    @Test
    fun `manual entry date string matches LocalDate toString format`() {
        val date = LocalDate.of(2026, 10, 7)
        val entity =
            ManualSleepEntryEntity(
                id = 1,
                date = date.toString(),
                startTimeMillis = 0L,
                endTimeMillis = 0L,
            )
        assertEquals("2026-10-07", entity.date)
        assertEquals(date, LocalDate.parse(entity.date))
    }

    @Test
    fun `toDomain round-trips through fromDomain`() {
        val date = LocalDate.of(2026, 10, 7)
        val original =
            ManualSleepEntryEntity(
                id = 42,
                date = date.toString(),
                startTimeMillis = 1_000_000L,
                endTimeMillis = 2_000_000L,
            )
        val domain = original.toDomain()
        val roundTripped = ManualSleepEntryEntity.fromDomain(domain)

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.date, roundTripped.date)
        assertEquals(original.startTimeMillis, roundTripped.startTimeMillis)
        assertEquals(original.endTimeMillis, roundTripped.endTimeMillis)
    }
}
