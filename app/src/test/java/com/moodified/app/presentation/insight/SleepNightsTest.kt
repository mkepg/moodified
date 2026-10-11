package com.moodified.app.presentation.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SleepNightsTest {
    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun `a wake date belongs to the night before it`() {
        assertEquals(LocalDate.of(2026, 10, 11), SleepNights.nightOf(monday))
    }

    @Test
    fun `tonight is still in progress before 6 AM`() {
        assertTrue(SleepNights.isInProgress(monday, monday.atTime(0, 40)))
        assertTrue(SleepNights.isInProgress(monday, monday.atTime(5, 59)))
    }

    @Test
    fun `tonight is over from 6 AM`() {
        assertFalse(SleepNights.isInProgress(monday, monday.atTime(6, 0)))
    }

    @Test
    fun `past nights are never in progress`() {
        assertFalse(SleepNights.isInProgress(monday.minusDays(1), monday.atTime(0, 40)))
    }
}
