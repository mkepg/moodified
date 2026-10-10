package com.moodified.app.domain.usecase.sleep

import com.moodified.app.core.utils.SleepTimeUtils
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DailySleepMergerTest {
    private val zone = ZoneOffset.UTC
    private val wakeDate = LocalDate.of(2026, 10, 7)

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ): LocalDateTime = date.atTime(hour, minute)

    private fun entry(
        start: LocalDateTime,
        end: LocalDateTime,
    ) = ManualSleepEntry(
        date = wakeDate,
        startTimeMs = start.toInstant(zone).toEpochMilli(),
        endTimeMs = end.toInstant(zone).toEpochMilli(),
    )

    // Estimated night: 11 PM → 6 AM, 7h asleep, 2 awakenings.
    private val estimate =
        SleepSegment(
            startTime = at(wakeDate.minusDays(1), 23),
            endTime = at(wakeDate, 6),
            status = SleepStatus.ASLEEP,
            awakenings = 2,
            totalSleepMinutes = 420,
        )

    private val nap = entry(at(wakeDate, 14), at(wakeDate, 15, 30))
    private val correction = entry(at(wakeDate, 0), at(wakeDate, 8))

    private fun merge(
        estimate: SleepSegment?,
        vararg manual: ManualSleepEntry,
    ) = DailySleepMerger.merge(wakeDate, estimate, manual.toList(), zone)

    @Test
    fun `a nap adds to the estimated night instead of replacing it`() {
        val summary = merge(estimate, nap)!!
        assertEquals(510, summary.totalSleepMinutes)
        assertEquals(90, summary.napMinutes)
        assertTrue(summary.isEstimated)
        assertTrue(summary.hasManualEntries)
        assertEquals(2, summary.awakenings)
    }

    @Test
    fun `a nap does not move bedtime`() {
        val summary = merge(estimate, nap)!!
        assertEquals(SleepTimeUtils.minutesSince6PM(estimate.startTime), summary.sleepOnsetMinutes)
    }

    @Test
    fun `a session overlapping the estimate replaces it`() {
        val summary = merge(estimate, correction)!!
        assertEquals(480, summary.totalSleepMinutes)
        assertEquals(0, summary.napMinutes)
        assertFalse(summary.isEstimated)
        assertEquals(0, summary.awakenings)
        assertEquals(SleepTimeUtils.minutesSince6PM(at(wakeDate, 0)), summary.sleepOnsetMinutes)
    }

    @Test
    fun `a correction and a nap together replace the night and add the nap`() {
        val summary = merge(estimate, correction, nap)!!
        assertEquals(570, summary.totalSleepMinutes)
        assertEquals(90, summary.napMinutes)
        assertFalse(summary.isEstimated)
        assertEquals(SleepTimeUtils.minutesSince6PM(at(wakeDate, 0)), summary.sleepOnsetMinutes)
    }

    @Test
    fun `the estimated night is reported even when it was replaced`() {
        val night = merge(estimate, correction)!!.estimatedNight
        assertNotNull(night)
        assertEquals(estimate.startTime, night!!.start)
        assertEquals(estimate.endTime, night.end)
        assertEquals(420, night.sleepMinutes)
    }

    @Test
    fun `an estimate alone is unchanged`() {
        val summary = merge(estimate)!!
        assertEquals(420, summary.totalSleepMinutes)
        assertTrue(summary.isEstimated)
        assertFalse(summary.hasManualEntries)
    }

    @Test
    fun `with no estimate an afternoon nap has no bedtime`() {
        val summary = merge(null, nap)!!
        assertEquals(90, summary.totalSleepMinutes)
        assertEquals(90, summary.napMinutes)
        assertNull(summary.sleepOnsetMinutes)
        assertFalse(summary.isEstimated)
        assertNull(summary.estimatedNight)
    }

    @Test
    fun `with no estimate a logged overnight session is the night`() {
        val night = entry(at(wakeDate.minusDays(1), 23), at(wakeDate, 7))
        val summary = merge(null, night, nap)!!
        assertEquals(570, summary.totalSleepMinutes)
        assertEquals(90, summary.napMinutes)
        assertEquals(SleepTimeUtils.minutesSince6PM(at(wakeDate.minusDays(1), 23)), summary.sleepOnsetMinutes)
    }

    @Test
    fun `nothing estimated and nothing logged yields no summary`() {
        assertNull(merge(null))
    }
}
