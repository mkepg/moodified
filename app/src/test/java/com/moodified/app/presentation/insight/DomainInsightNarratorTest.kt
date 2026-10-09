package com.moodified.app.presentation.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainInsightNarratorTest {
    private val narrator = DomainInsightNarrator()

    private fun day(n: Int): LocalDate = LocalDate.of(2026, 10, n)

    private fun <T> series(vararg values: T): Map<LocalDate, T> = values.mapIndexed { index, value -> day(index + 1) to value }.toMap()

    // --- The reported bug: mood claims without logged moods --------------------

    @Test
    fun `sleep never mentions mood when no moods are logged`() {
        val result =
            narrator.sleep(
                moodReady = false,
                mood = emptyMap(),
                sleepMinutes = series(360, 420, 300, 480, 240, 400, 380),
                lateNightMinutes = series(10, 5, 90, 0, 120, 15, 20),
            )
        assertNotNull(result)
        assertTrue("Leaked mood copy: $result", !result!!.contains("mood", ignoreCase = true))
    }

    @Test
    fun `screen never mentions mood when no moods are logged`() {
        val result =
            narrator.screen(
                moodReady = false,
                mood = emptyMap(),
                screenMinutes = series(240, 300, 600, 180, 480, 200, 360),
                steps = series(8000, 7000, 2000, 9000, 3000, 8500, 6000),
            )
        assertNotNull(result)
        assertTrue("Leaked mood copy: $result", !result!!.contains("mood", ignoreCase = true))
    }

    @Test
    fun `activity never mentions mood when no moods are logged`() {
        val result =
            narrator.activity(
                moodReady = false,
                mood = emptyMap(),
                steps = series(8000, 7000, 2000, 9000, 3000, 8500, 6000),
                sleepMinutes = series(360, 420, 300, 480, 240, 400, 380),
            )
        assertNotNull(result)
        assertTrue("Leaked mood copy: $result", !result!!.contains("mood", ignoreCase = true))
    }

    @Test
    fun `mood copy is withheld even when mood data exists but threshold is unmet`() {
        // Two logged days is below the 3-day gate, so moodReady stays false.
        val mood = mapOf(day(1) to 1.0f, day(2) to 0.0f)
        val result =
            narrator.sleep(
                moodReady = false,
                mood = mood,
                sleepMinutes = series(360, 420, 300, 480, 240, 400, 380),
                lateNightMinutes = series(10, 5, 90, 0, 120, 15, 20),
            )
        assertTrue("Leaked mood copy: $result", !result!!.contains("mood", ignoreCase = true))
    }

    // --- Mood path, once unlocked ----------------------------------------------

    @Test
    fun `sleep reports mood correlation once moods are logged`() {
        // Long nights carry high mood, short nights low mood.
        val mood =
            mapOf(
                day(1) to 1.0f,
                day(2) to 1.0f,
                day(3) to 1.0f,
                day(4) to 0.0f,
                day(5) to 0.0f,
                day(6) to 0.0f,
            )
        val sleep = mapOf(day(1) to 480, day(2) to 500, day(3) to 460, day(4) to 240, day(5) to 200, day(6) to 260)
        val result = narrator.sleep(moodReady = true, mood = mood, sleepMinutes = sleep, lateNightMinutes = emptyMap())
        assertEquals("Your 3 longest sleep nights lined up with better mood this week.", result)
    }

    // --- Cross-domain fallback ---------------------------------------------------

    @Test
    fun `sleep links short nights to late-night screen use`() {
        // Heavy late-night use on the three shortest nights.
        val sleep = mapOf(day(1) to 480, day(2) to 500, day(3) to 460, day(4) to 180, day(5) to 200, day(6) to 160)
        val lateNight = mapOf(day(1) to 5, day(2) to 10, day(3) to 5, day(4) to 120, day(5) to 140, day(6) to 150)
        val result = narrator.sleep(moodReady = false, mood = emptyMap(), sleepMinutes = sleep, lateNightMinutes = lateNight)
        assertEquals("Your shortest nights were the ones with the most late-night screen use.", result)
    }

    @Test
    fun `screen links heavy days to moving less`() {
        val screen = mapOf(day(1) to 120, day(2) to 150, day(3) to 130, day(4) to 600, day(5) to 650, day(6) to 580)
        val steps = mapOf(day(1) to 9000, day(2) to 9500, day(3) to 8800, day(4) to 1500, day(5) to 1200, day(6) to 1800)
        val result = narrator.screen(moodReady = false, mood = emptyMap(), screenMinutes = screen, steps = steps)
        assertEquals("You moved less on your heaviest screen-use days.", result)
    }

    // --- Descriptive fallback ----------------------------------------------------

    @Test
    fun `sleep falls back to a highlight when there are too few overlapping days`() {
        val sleep = mapOf(day(1) to 549, day(2) to 125)
        val result = narrator.sleep(moodReady = false, mood = emptyMap(), sleepMinutes = sleep, lateNightMinutes = emptyMap())
        assertNotNull(result)
        assertTrue("Expected both extremes, got: $result", result!!.contains("9h 9m") && result.contains("2h 5m"))
    }

    @Test
    fun `sleep highlight collapses to one night when only one is tracked`() {
        val result =
            narrator.sleep(
                moodReady = false,
                mood = emptyMap(),
                sleepMinutes = mapOf(day(1) to 549),
                lateNightMinutes = emptyMap(),
            )
        assertEquals("Your longest night this week was 9h 9m on Thursday.", result)
    }

    @Test
    fun `returns null when the domain has no data at all`() {
        assertNull(narrator.sleep(false, emptyMap(), emptyMap(), emptyMap()))
        assertNull(narrator.activity(false, emptyMap(), emptyMap(), emptyMap()))
        assertNull(narrator.screen(false, emptyMap(), emptyMap(), emptyMap()))
    }

    // --- correlate() itself --------------------------------------------------------

    @Test
    fun `correlate needs at least three overlapping days`() {
        val outcome = mapOf(day(1) to 1.0f, day(2) to 0.0f)
        val driver = mapOf(day(1) to 10f, day(2) to 20f)
        assertNull(correlate(outcome, driver))
    }

    @Test
    fun `correlate ignores days where the driver is zero`() {
        val outcome = mapOf(day(1) to 1.0f, day(2) to 1.0f, day(3) to 0.0f, day(4) to 0.0f)
        val driver = mapOf(day(1) to 0f, day(2) to 0f, day(3) to 30f, day(4) to 40f)
        // Only two days survive the zero-driver filter, which is under the minimum.
        assertNull(correlate(outcome, driver))
    }

    @Test
    fun `correlate returns NONE when every driver value is identical`() {
        val outcome = mapOf(day(1) to 1.0f, day(2) to 0.5f, day(3) to 0.0f)
        val driver = mapOf(day(1) to 20f, day(2) to 20f, day(3) to 20f)
        assertEquals(Correlation.NONE, correlate(outcome, driver)?.direction)
    }

    @Test
    fun `correlate reports NONE for a difference under the threshold`() {
        val outcome = mapOf(day(1) to 0.52f, day(2) to 0.51f, day(3) to 0.49f, day(4) to 0.48f)
        val driver = mapOf(day(1) to 10f, day(2) to 20f, day(3) to 30f, day(4) to 40f)
        assertEquals(Correlation.NONE, correlate(outcome, driver)?.direction)
    }
}
