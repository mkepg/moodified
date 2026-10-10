package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.ManualSleepEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class ManualSleepAnchoringTest {
    private val zone = ZoneOffset.UTC
    private val tuesday = LocalDate.of(2026, 10, 6)
    private val monday = tuesday.minusDays(1)

    private fun Long.local(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

    private fun range(
        start: LocalTime,
        end: LocalTime,
    ): Pair<LocalDateTime, LocalDateTime> {
        val (startMs, endMs) = ManualSleepAnchoring.toEpochRange(tuesday, start, end, zone)
        return startMs.local() to endMs.local()
    }

    @Test
    fun `an overnight session ends on the bar's date`() {
        val (start, end) = range(LocalTime.of(23, 0), LocalTime.of(7, 0))
        assertEquals(monday.atTime(23, 0), start)
        assertEquals(tuesday.atTime(7, 0), end)
    }

    @Test
    fun `an afternoon nap stays on the bar's date`() {
        val (start, end) = range(LocalTime.of(14, 0), LocalTime.of(15, 30))
        assertEquals(tuesday.atTime(14, 0), start)
        assertEquals(tuesday.atTime(15, 30), end)
    }

    @Test
    fun `an evening nap that ends before midnight stays on the bar's date`() {
        val (start, end) = range(LocalTime.of(18, 0), LocalTime.of(19, 0))
        assertEquals(tuesday.atTime(18, 0), start)
        assertEquals(tuesday.atTime(19, 0), end)
    }

    @Test
    fun `a session starting after midnight stays on the bar's date`() {
        val (start, end) = range(LocalTime.of(1, 0), LocalTime.of(9, 0))
        assertEquals(tuesday.atTime(1, 0), start)
        assertEquals(tuesday.atTime(9, 0), end)
    }

    @Test
    fun `a legacy entry that ends after its date is moved back a day`() {
        // Saved by the old sheet: Tuesday's bar, 11 PM Tuesday → 7 AM Wednesday.
        val legacy =
            ManualSleepEntry(
                id = 3,
                date = tuesday,
                startTimeMs = tuesday.atTime(23, 0).toInstant(zone).toEpochMilli(),
                endTimeMs = tuesday.plusDays(1).atTime(7, 0).toInstant(zone).toEpochMilli(),
            )
        val fixed = ManualSleepAnchoring.correctLegacy(legacy, zone)
        assertEquals(monday.atTime(23, 0), fixed.startTimeMs.local())
        assertEquals(tuesday.atTime(7, 0), fixed.endTimeMs.local())
        assertEquals(3, fixed.id)
    }

    @Test
    fun `a current entry is left alone`() {
        val current =
            ManualSleepEntry(
                date = tuesday,
                startTimeMs = monday.atTime(23, 0).toInstant(zone).toEpochMilli(),
                endTimeMs = tuesday.atTime(7, 0).toInstant(zone).toEpochMilli(),
            )
        assertEquals(current, ManualSleepAnchoring.correctLegacy(current, zone))
    }
}
