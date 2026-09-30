package com.moodified.app.domain.usecase.inference

import com.moodified.app.data.local.datasource.CalibrationPreferencesDataSource
import com.moodified.app.domain.model.inference.InferenceDomain
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.usecase.inference.InferenceConstants.CALIBRATION_BASE_NUDGE
import com.moodified.app.domain.usecase.inference.InferenceConstants.CALIBRATION_DECAY_RATE
import com.moodified.app.domain.usecase.inference.InferenceConstants.CALIBRATION_MIN_COMPLETENESS
import com.moodified.app.domain.usecase.inference.InferenceConstants.CALIBRATION_MIN_CONFIDENCE
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class FeedbackCalibrationUseCase
    @Inject
    constructor(
        private val buildDailyBehaviorSnapshot: BuildDailyBehaviorSnapshotUseCase,
        private val inferenceEngine: RuleBasedMoodInferenceEngine,
        private val calibrationSource: CalibrationPreferencesDataSource,
    ) {
        suspend operator fun invoke(manualEntry: MoodEntry) {
            if (!manualEntry.isManual) return

            val today = manualEntry.timestamp.toLocalDate()
            val snapshot = buildDailyBehaviorSnapshot(today).first()

            if (snapshot.dataCompletenessScore < CALIBRATION_MIN_COMPLETENESS) return

            val currentWeights = calibrationSource.flow().first()
            val predicted = inferenceEngine(snapshot, currentWeights)

            if (predicted.confidenceScore < CALIBRATION_MIN_CONFIDENCE) return

            val predictedOrdinal = predicted.valence.ordinal
            val manualOrdinal = manualEntry.valence.ordinal

            if (predictedOrdinal == manualOrdinal) return

            val tooOptimistic = predictedOrdinal > manualOrdinal
            val nudge = CALIBRATION_BASE_NUDGE / (1f + currentWeights.feedbackCount * CALIBRATION_DECAY_RATE)

            fun netValenceFor(domain: InferenceDomain) =
                predicted.scoringEvents.filter { it.domain == domain }.sumOf { it.valenceDelta }

            val sleepNet = netValenceFor(InferenceDomain.SLEEP)
            val activityNet = netValenceFor(InferenceDomain.ACTIVITY)
            val screenNet = netValenceFor(InferenceDomain.SCREEN)

            fun adjustMultiplier(current: Float, netContribution: Int): Float {
                val contributedWrongly = if (tooOptimistic) netContribution > 0 else netContribution < 0
                return if (contributedWrongly) (current - nudge).coerceIn(0.5f, 1.5f) else current
            }

            val updated = currentWeights.copy(
                sleepMultiplier = adjustMultiplier(currentWeights.sleepMultiplier, sleepNet),
                activityMultiplier = adjustMultiplier(currentWeights.activityMultiplier, activityNet),
                screenMultiplier = adjustMultiplier(currentWeights.screenMultiplier, screenNet),
                feedbackCount = currentWeights.feedbackCount + 1,
            ).withLastUpdated(today)

            calibrationSource.update(updated)
        }
    }
