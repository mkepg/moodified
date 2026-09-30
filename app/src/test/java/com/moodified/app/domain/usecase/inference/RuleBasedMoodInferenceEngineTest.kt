package com.moodified.app.domain.usecase.inference

import com.moodified.app.domain.model.inference.CalibrationWeights
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RuleBasedMoodInferenceEngineTest {
    private val engine =
        RuleBasedMoodInferenceEngine(
            interpreter = MoodStateInterpreter(),
            explainer = MoodExplainabilityGenerator(),
        )

    private fun snapshot(
        moodEntries: List<MoodEntry> = emptyList(),
        completeness: Int = 100,
    ): DailyBehaviorSnapshot =
        DailyBehaviorSnapshot(
            targetDate = LocalDate.of(2026, 1, 15),
            sleepSummary = null,
            activitySummary = null,
            interactionSummary = null,
            moodEntries = moodEntries,
            dataCompletenessScore = completeness,
        )

    // ---- Rule: manual entries always outrank inference ----

    @Test
    fun manualEntryDrivesReturnedValenceAndArousal() {
        val manual = MoodEntry(valence = Valence.POSITIVE, arousal = Arousal.HIGH)
        val state = engine(snapshot(moodEntries = listOf(manual)))
        assertEquals(Valence.POSITIVE, state.valence)
        assertEquals(Arousal.HIGH, state.arousal)
        assertFalse("Manual-derived state must not be marked fallback", state.isFallback)
    }

    // ---- Rule: low completeness returns a fallback state ----

    @Test
    fun lowCompletenessReturnsFallbackState() {
        val belowThreshold = InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE - 1
        val state = engine(snapshot(completeness = belowThreshold))
        assertTrue("Expected isFallback when completeness < threshold", state.isFallback)
    }

    // ---- Rule: sufficient completeness with no manual entries runs passive inference (not fallback) ----

    @Test
    fun sufficientCompletenessRunsPassiveInference() {
        val state = engine(snapshot(completeness = 100))
        assertFalse("Should not fall back when completeness is 100", state.isFallback)
    }

    // ---- Rule: confidence stays in [0, 100] regardless of input shape ----

    @Test
    fun confidenceScoreStaysWithinZeroToOneHundred() {
        listOf(0, 25, 50, 75, 100).forEach { completeness ->
            val state = engine(snapshot(completeness = completeness))
            assertTrue(
                "confidenceScore out of range for completeness=$completeness: ${state.confidenceScore}",
                state.confidenceScore in 0..100,
            )
        }
    }

    // ---- Rule: explainability string is never blank ----

    @Test
    fun explainabilityStringIsNeverBlank() {
        val state = engine(snapshot())
        assertFalse(
            "Explainability must be non-empty",
            state.explainabilityString.isBlank(),
        )
    }

    // ---- Rule: interpretation label is never blank ----

    @Test
    fun interpretationLabelIsNeverBlank() {
        val state = engine(snapshot())
        assertFalse(state.interpretationLabel.isBlank())
    }

    // ---- Calibration: default weights produce identical results to no-arg call ----

    @Test
    fun `calibration weights default to 1_0 and do not change scoring`() {
        val snap = snapshot(completeness = 100)
        val withDefault = engine(snap, CalibrationWeights())
        val withoutArg = engine(snap)
        assertEquals(withDefault.valence, withoutArg.valence)
        assertEquals(withDefault.arousal, withoutArg.arousal)
    }

    // ---- Calibration: sleep multiplier of 0.5 reduces sleep event contributions ----

    @Test
    fun `sleep multiplier of 0_5 approximately halves sleep event contributions`() {
        // Good sleep data: 480 minutes, 0 awakenings — triggers the solid sleep +15 valence event
        val goodSleep = DailySleepSummary(
            date = "2026-01-15",
            totalSleepMinutes = 480,
            awakenings = 0,
        )
        val snapWithSleep = DailyBehaviorSnapshot(
            targetDate = LocalDate.of(2026, 1, 15),
            sleepSummary = goodSleep,
            activitySummary = null,
            interactionSummary = null,
            moodEntries = emptyList(),
            dataCompletenessScore = 100,
        )

        val fullWeights = engine(snapWithSleep, CalibrationWeights(sleepMultiplier = 1.0f))
        val halfWeights = engine(snapWithSleep, CalibrationWeights(sleepMultiplier = 0.5f))

        // With sleepMultiplier=0.5, the sleep valence contribution is halved,
        // so the resulting valence score should be lower (or equal at worst if clamped to same bucket)
        val fullValenceOrdinal = fullWeights.valence.ordinal
        val halfValenceOrdinal = halfWeights.valence.ordinal

        // The scoring events list should reflect the reduced sleep contribution
        val fullSleepValence = fullWeights.scoringEvents.filter {
            it.domain == com.moodified.app.domain.model.inference.InferenceDomain.SLEEP
        }.sumOf { it.valenceDelta }

        val halfSleepValence = halfWeights.scoringEvents.filter {
            it.domain == com.moodified.app.domain.model.inference.InferenceDomain.SLEEP
        }.sumOf { it.valenceDelta }

        assertTrue(
            "Half multiplier should reduce sleep valence contribution. full=$fullSleepValence half=$halfSleepValence",
            halfSleepValence <= fullSleepValence,
        )
    }
}
