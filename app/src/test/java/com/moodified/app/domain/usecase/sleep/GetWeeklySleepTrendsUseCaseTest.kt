package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepSignal
import com.moodified.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GetWeeklySleepTrendsUseCaseTest {
    // --- Fake repository ---

    private class FakeSleepRepository(
        private val summaries: List<DailySleepSummary>,
    ) : SleepRepository {
        override fun observeLiveSignal(): Flow<SleepSignal> = flowOf(SleepSignal())

        override fun getSegmentsForDate(date: LocalDate): Flow<List<SleepSegment>> = flowOf(emptyList())

        override val isTracking: Boolean get() = false

        override fun startTracking(): Boolean = false

        override fun stopTracking() = Unit

        override fun pauseTracking() = Unit

        override fun getWeeklySummaries(endDate: LocalDate): Flow<List<DailySleepSummary>> = flowOf(summaries)

        override suspend fun persistSegments(segments: List<SleepSegment>) = Unit

        override suspend fun flushSleepDataToDb() = Unit

        override fun hasUsagePermission(): Boolean = false
    }

    private fun buildUseCase(summaries: List<DailySleepSummary>): GetWeeklySleepTrendsUseCase =
        GetWeeklySleepTrendsUseCase(FakeSleepRepository(summaries))

    private fun summary(
        date: String,
        totalSleepMinutes: Int,
        sleepOnsetMinutes: Int? = null,
    ) = DailySleepSummary(
        date = date,
        totalSleepMinutes = totalSleepMinutes,
        awakenings = 0,
        sleepOnsetMinutes = sleepOnsetMinutes,
        isEstimated = false,
    )

    // --- inferredSleepGoalMinutes: trimmed mean when ≥5 nights ---

    @Test
    fun `inferredSleepGoalMinutes returns trimmed mean when 5 or more nights available`() =
        runTest {
            // 6 nights: 300, 400, 420, 440, 460, 900
            // Drop lowest (300) and highest (900) → (400+420+440+460)/4 = 430
            val summaries =
                listOf(
                    summary("2026-09-24", 300),
                    summary("2026-09-25", 400),
                    summary("2026-09-26", 420),
                    summary("2026-09-27", 440),
                    summary("2026-09-28", 460),
                    summary("2026-09-29", 900),
                )
            val useCase = buildUseCase(summaries)
            val trends = useCase(LocalDate.of(2026, 9, 29)).first()
            assertEquals(430, trends?.inferredSleepGoalMinutes)
        }

    // --- inferredSleepGoalMinutes: 480 fallback when <5 nights ---

    @Test
    fun `inferredSleepGoalMinutes returns 480 fallback when fewer than 5 nights available`() =
        runTest {
            val summaries =
                listOf(
                    summary("2026-09-27", 400),
                    summary("2026-09-28", 420),
                    summary("2026-09-29", 440),
                )
            val useCase = buildUseCase(summaries)
            val trends = useCase(LocalDate.of(2026, 9, 29)).first()
            assertEquals(480, trends?.inferredSleepGoalMinutes)
        }

    // --- baselineSleepOnsetMinutes: null when no onset data ---

    @Test
    fun `baselineSleepOnsetMinutes is null when no onset data available`() =
        runTest {
            val summaries =
                listOf(
                    summary("2026-09-27", 420, sleepOnsetMinutes = null),
                    summary("2026-09-28", 440, sleepOnsetMinutes = null),
                    summary("2026-09-29", 460, sleepOnsetMinutes = null),
                )
            val useCase = buildUseCase(summaries)
            val trends = useCase(LocalDate.of(2026, 9, 29)).first()
            assertNull(trends?.baselineSleepOnsetMinutes)
        }

    // --- baselineSleepOnsetMinutes: converges toward consistent pattern ---

    @Test
    fun `baselineSleepOnsetMinutes converges toward consistent nightly pattern`() =
        runTest {
            // 7 nights all with sleepOnsetMinutes = 300 (11PM = 300 min past 6PM)
            // Starting from 300 and applying EMA with consistent input → should stay near 300
            val summaries =
                (0 until 7).map { i ->
                    summary("2026-09-${23 + i}", 420, sleepOnsetMinutes = 300)
                }
            val useCase = buildUseCase(summaries)
            val trends = useCase(LocalDate.of(2026, 9, 29)).first()
            val onset = trends?.baselineSleepOnsetMinutes
            assertTrue(
                "Expected onset near 300, got $onset",
                onset != null && kotlin.math.abs(onset - 300) <= 30,
            )
        }

    // --- Direct unit tests for pure helper functions (no coroutines needed) ---

    @Test
    fun `computeInferredSleepGoal trims outliers and averages remaining nights`() {
        val useCase = buildUseCase(emptyList())
        val summaries =
            listOf(
                summary("2026-09-24", 300),
                summary("2026-09-25", 400),
                summary("2026-09-26", 420),
                summary("2026-09-27", 440),
                summary("2026-09-28", 460),
                summary("2026-09-29", 900),
            )
        assertEquals(430, useCase.computeInferredSleepGoal(summaries))
    }

    @Test
    fun `computeInferredSleepGoal returns 480 for exactly 4 nights`() {
        val useCase = buildUseCase(emptyList())
        val summaries =
            listOf(
                summary("2026-09-26", 400),
                summary("2026-09-27", 420),
                summary("2026-09-28", 440),
                summary("2026-09-29", 460),
            )
        assertEquals(480, useCase.computeInferredSleepGoal(summaries))
    }

    @Test
    fun `computeBaselineOnset returns null for empty onset list`() {
        val useCase = buildUseCase(emptyList())
        val summaries =
            listOf(
                summary("2026-09-27", 420),
                summary("2026-09-28", 440),
            )
        assertNull(useCase.computeBaselineOnset(summaries))
    }

    @Test
    fun `computeBaselineOnset returns onset value for single night`() {
        val useCase = buildUseCase(emptyList())
        val summaries = listOf(summary("2026-09-29", 420, sleepOnsetMinutes = 300))
        assertEquals(300, useCase.computeBaselineOnset(summaries))
    }
}
