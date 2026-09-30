package com.moodified.app.domain.usecase.inference

import com.moodified.app.data.local.datasource.CalibrationPreferencesDataSource
import com.moodified.app.domain.model.inference.CalibrationWeights
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.inference.InferenceDomain
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.inference.ScoringEvent
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FeedbackCalibrationUseCaseTest {

    // --- In-memory fake for CalibrationPreferencesDataSource ---
    // CalibrationPreferencesDataSource requires Android Context so we use MockK.

    private val mockCalibrationSource = mockk<CalibrationPreferencesDataSource>(relaxed = true)
    private val mockBuildSnapshot = mockk<BuildDailyBehaviorSnapshotUseCase>()
    private val mockEngine = mockk<RuleBasedMoodInferenceEngine>()

    private val useCase = FeedbackCalibrationUseCase(
        buildDailyBehaviorSnapshot = mockBuildSnapshot,
        inferenceEngine = mockEngine,
        calibrationSource = mockCalibrationSource,
    )

    private val defaultWeights = CalibrationWeights()

    // Helpers

    private fun snapshotWithCompleteness(score: Int): DailyBehaviorSnapshot =
        DailyBehaviorSnapshot(
            targetDate = LocalDate.now(),
            sleepSummary = null,
            activitySummary = null,
            interactionSummary = null,
            moodEntries = emptyList(),
            dataCompletenessScore = score,
        )

    private fun inferredState(
        valence: Valence,
        confidence: Int,
        events: List<ScoringEvent> = emptyList(),
    ): InferredMoodState =
        InferredMoodState(
            valence = valence,
            arousal = Arousal.MID,
            interpretationLabel = "",
            confidenceScore = confidence,
            explainabilityString = "",
            scoringEvents = events,
        )

    // --- Test 1: same valence → no update ---

    @Test
    fun `when predicted valence equals manual valence weights are not updated`() = runTest {
        val entry = MoodEntry(
            valence = Valence.POSITIVE,
            arousal = Arousal.MID,
            isManual = true,
            timestamp = LocalDateTime.now(),
        )
        every { mockBuildSnapshot(any()) } returns flowOf(snapshotWithCompleteness(60))
        every { mockCalibrationSource.flow() } returns flowOf(defaultWeights)
        every { mockEngine(any()) } returns inferredState(Valence.POSITIVE, confidence = 50)

        useCase(entry)

        coVerify(exactly = 0) { mockCalibrationSource.update(any()) }
    }

    // --- Test 2: too optimistic + positive sleep contributor → sleepMultiplier decreases ---

    @Test
    fun `when predicted is too optimistic positive sleep contributor multiplier decreases`() = runTest {
        val entry = MoodEntry(
            valence = Valence.NEGATIVE,
            arousal = Arousal.LOW,
            isManual = true,
            timestamp = LocalDateTime.now(),
        )
        every { mockBuildSnapshot(any()) } returns flowOf(snapshotWithCompleteness(60))
        every { mockCalibrationSource.flow() } returns flowOf(defaultWeights)
        every { mockEngine(any()) } returns inferredState(
            valence = Valence.POSITIVE,
            confidence = 60,
            events = listOf(
                ScoringEvent(
                    description = "solid, restful sleep",
                    valenceDelta = 15,
                    domain = InferenceDomain.SLEEP,
                ),
            ),
        )

        val capturedWeights = slot<CalibrationWeights>()
        coEvery { mockCalibrationSource.update(capture(capturedWeights)) } returns Unit

        useCase(entry)

        coVerify(exactly = 1) { mockCalibrationSource.update(any()) }
        val updated = capturedWeights.captured
        assertTrue(
            "sleepMultiplier should decrease below 1.0f, was ${updated.sleepMultiplier}",
            updated.sleepMultiplier < 1.0f,
        )
        assertEquals(
            "activityMultiplier should remain 1.0f (activity did not contribute)",
            1.0f,
            updated.activityMultiplier,
        )
        assertEquals(1, updated.feedbackCount)
    }

    // --- Test 3: confidence below threshold → no update ---

    @Test
    fun `calibration is skipped when confidence is below threshold`() = runTest {
        val entry = MoodEntry(
            valence = Valence.POSITIVE,
            arousal = Arousal.MID,
            isManual = true,
            timestamp = LocalDateTime.now(),
        )
        every { mockBuildSnapshot(any()) } returns flowOf(snapshotWithCompleteness(60))
        every { mockCalibrationSource.flow() } returns flowOf(defaultWeights)
        every { mockEngine(any()) } returns inferredState(Valence.NEGATIVE, confidence = 20)

        useCase(entry)

        coVerify(exactly = 0) { mockCalibrationSource.update(any()) }
    }

    // --- Test 4: snapshot completeness below threshold → engine NOT called, no update ---

    @Test
    fun `calibration is skipped when snapshot completeness is below threshold`() = runTest {
        val entry = MoodEntry(
            valence = Valence.POSITIVE,
            arousal = Arousal.MID,
            isManual = true,
            timestamp = LocalDateTime.now(),
        )
        every { mockBuildSnapshot(any()) } returns flowOf(snapshotWithCompleteness(10))

        useCase(entry)

        verify(exactly = 0) { mockEngine(any()) }
        coVerify(exactly = 0) { mockCalibrationSource.update(any()) }
    }

    // --- Test 5: non-manual entry → no update ---

    @Test
    fun `calibration is skipped for non-manual entries`() = runTest {
        val entry = MoodEntry(
            valence = Valence.POSITIVE,
            arousal = Arousal.MID,
            isManual = false,
            timestamp = LocalDateTime.now(),
        )

        useCase(entry)

        coVerify(exactly = 0) { mockCalibrationSource.update(any()) }
        verify(exactly = 0) { mockBuildSnapshot(any()) }
    }
}
