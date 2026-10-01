package com.moodified.app.domain.usecase.inference

import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.inference.CalibrationWeights
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.inference.InferenceDomain
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.inference.ScoringEvent
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

class RuleBasedMoodInferenceEngine
    @Inject
    constructor(
        private val interpreter: MoodStateInterpreter,
        private val explainer: MoodExplainabilityGenerator,
    ) {
        operator fun invoke(
            snapshot: DailyBehaviorSnapshot,
            calibration: CalibrationWeights = CalibrationWeights(),
        ): InferredMoodState {
            if (snapshot.moodEntries.isNotEmpty()) return deriveFromManualEntries(snapshot)

            if (snapshot.dataCompletenessScore < InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE) {
                return generateFallbackState(snapshot)
            }

            return runPassiveInference(snapshot, calibration)
        }

        private fun runPassiveInference(
            snapshot: DailyBehaviorSnapshot,
            calibration: CalibrationWeights,
        ): InferredMoodState {
            val scoringEvents = mutableListOf<ScoringEvent>()

            // 1. Sleep Evaluation
            val sleepGoal = snapshot.sleepTrends?.inferredSleepGoalMinutes ?: InferenceConstants.GOOD_SLEEP_MINUTES_MIN

            val sleepBaseline =
                snapshot.sleepTrends?.averageSleepMinutes?.takeIf { it > 0 }
                    ?: sleepGoal

            val rawDynamicPoor = (sleepBaseline * InferenceConstants.DYNAMIC_POOR_SLEEP_MULTIPLIER).toInt()
            val dynamicPoorSleepThreshold =
                if (rawDynamicPoor > sleepGoal) {
                    sleepGoal
                } else {
                    rawDynamicPoor.coerceAtLeast(180)
                }

            val dynamicGoodSleepMin =
                (sleepBaseline * InferenceConstants.DYNAMIC_GOOD_SLEEP_MIN_MULTIPLIER)
                    .toInt().coerceAtLeast(240)

            // 2. Activity Evaluation
            val stepBaseline = snapshot.activityTrends?.averageSteps?.takeIf { it > 0 } ?: InferenceConstants.HIGH_STEPS_THRESHOLD
            val dynamicHighSteps = (stepBaseline * InferenceConstants.DYNAMIC_HIGH_STEPS_MULTIPLIER).toInt()

            val activeMinBaseline = snapshot.activityTrends?.averageActiveMinutes?.takeIf { it > 0 } ?: InferenceConstants.HIGH_ACTIVITY_MINUTES
            val dynamicHighActive = (activeMinBaseline * InferenceConstants.DYNAMIC_HIGH_ACTIVITY_MULTIPLIER).toInt()

            val highScreenThreshold =
                snapshot.interactionTrends?.averageScreenTimeMinutes
                    ?.times(InferenceConstants.DYNAMIC_HIGH_SCREEN_TIME_MULTIPLIER)?.toInt()
                    ?: InferenceConstants.HIGH_SCREEN_TIME_MINUTES
            val highLateNightThreshold =
                snapshot.interactionTrends?.averageLateNightMinutes
                    ?.let {
                        if (it > 0) (it * InferenceConstants.DYNAMIC_HIGH_LATE_NIGHT_MULTIPLIER).toInt() else InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD
                    }
                    ?: InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD

            // Digital fatigue captures chronic-day screen habits (heavy total use or
            // restless-scrolling patterns). Late-night usage is handled by its own direct
            // rule in the interaction block — keeping it out of this gate avoids
            // compound-penalizing a single late-night signal in two places.
            val isDigitallyFatigued =
                snapshot.interactionSummary?.let {
                    it.totalScreenTimeMinutes > highScreenThreshold ||
                        (it.sessionCount > InferenceConstants.HIGH_SESSION_COUNT && it.averageSessionDurationMinutes < InferenceConstants.SHORT_SESSION_DURATION_MINUTES)
                } ?: false

            val isSleepDeprived = (snapshot.sleepSummary?.totalSleepMinutes ?: 999) < dynamicPoorSleepThreshold

            // --- Apply Sleep Rules ---
            // Sleep "quality" was previously gated on sleepEfficiencyPercent (totalSleep /
            // timeInBed). Passive tracking uses screen-off as the sleep proxy, so those two
            // values were nearly always equal and efficiency was ~100% — a useless signal.
            // Awakening count is the direct, well-measured proxy for restless sleep.
            snapshot.sleepSummary?.let { sleep ->
                if (isSleepDeprived) {
                    scoringEvents += ScoringEvent("shorter sleep than your usual", -12, -8, domain = InferenceDomain.SLEEP)
                } else if (sleep.totalSleepMinutes >= dynamicGoodSleepMin && sleep.awakenings <= 1) {
                    scoringEvents += ScoringEvent("solid, restful sleep", 15, 0, domain = InferenceDomain.SLEEP)
                }

                // Restless-sleep event fires independently so short + fragmented nights get
                // both explanations. Weight is halved when already flagged as deprived —
                // the deprivation penalty already covers most of the impact.
                if (sleep.awakenings >= InferenceConstants.AWAKENING_THRESHOLD) {
                    val valencePenalty = if (isSleepDeprived) 4 else 8
                    val arousalPenalty = if (isSleepDeprived) 3 else 5
                    val description =
                        if (isSleepDeprived) {
                            "and fragmented with frequent awakenings on top"
                        } else {
                            "restless sleep with frequent awakenings"
                        }
                    scoringEvents += ScoringEvent(description, -valencePenalty, -arousalPenalty, domain = InferenceDomain.SLEEP)
                }

                // Late bedtime signal. Onset past the personal baseline + offset tends to
                // depress next-day valence and slightly elevate arousal (residual wired
                // feeling from delayed wind-down).
                val lateBedtimeThreshold =
                    (
                        snapshot.sleepTrends?.baselineSleepOnsetMinutes
                            ?: InferenceConstants.LATE_BEDTIME_MINUTES
                    ) + InferenceConstants.LATE_BEDTIME_OFFSET_MINUTES
                val onset = sleep.sleepOnsetMinutes
                if (onset != null && onset >= lateBedtimeThreshold) {
                    scoringEvents += ScoringEvent("unusually late bedtime", -5, 3, domain = InferenceDomain.SLEEP)
                }
            }

            // --- Apply Activity Rules ---
            snapshot.activitySummary?.let { activity ->
                val vigorousMins = activity.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0
                val commuteMins = activity.minutesPerIntensityBand[ActivityIntensity.IN_VEHICLE] ?: 0

                if (activity.activeMinutes > dynamicHighActive) {
                    if (isSleepDeprived) {
                        scoringEvents += ScoringEvent("pushing hard on low sleep", -5, -10, domain = InferenceDomain.ACTIVITY)
                    } else {
                        scoringEvents += ScoringEvent("higher physical activity than usual", 15, 20, domain = InferenceDomain.ACTIVITY)
                    }
                }

                if (activity.sedentaryMinutes > InferenceConstants.SEDENTARY_MINUTES_THRESHOLD) {
                    if (isDigitallyFatigued) {
                        scoringEvents += ScoringEvent("prolonged inactivity with digital fatigue", -8, -12, domain = InferenceDomain.SCREEN)
                    } else {
                        scoringEvents += ScoringEvent("prolonged period of focus or rest", 0, -5, domain = InferenceDomain.ACTIVITY)
                    }
                }

                if (vigorousMins > InferenceConstants.VIGOROUS_MINUTES_THRESHOLD) {
                    // Vigorous intensity gets its own small valence + residual arousal bump,
                    // decayed as the workout recedes into the day. Ensures short HIIT
                    // sessions still register as a mood positive even when total active
                    // minutes stay below the "high activity" threshold.
                    val estimatedHoursElapsed = 6f
                    val decayedArousal = applyFloorDecay(15, estimatedHoursElapsed)
                    val decayedValence = applyFloorDecay(6, estimatedHoursElapsed)
                    scoringEvents += ScoringEvent("vigorous exercise earlier today", decayedValence, decayedArousal, domain = InferenceDomain.ACTIVITY)
                }

                if (activity.totalSteps > dynamicHighSteps) {
                    scoringEvents += ScoringEvent("surpassed your usual step count", 5, 0, domain = InferenceDomain.ACTIVITY)
                }

                // Ratio semantics: vehicle time as a share of the *whole* tracked day
                // (including in-vehicle itself). Professional drivers spend > 25% of tracked
                // minutes in transit and shouldn't be penalized for their job — only genuine
                // commuters (long trip but small share of the day) get the mood hit.
                val totalTrackedMinutes = activity.activeMinutes + activity.sedentaryMinutes + commuteMins
                val vehicleRatio = if (totalTrackedMinutes > 0) commuteMins.toFloat() / totalTrackedMinutes else 0f
                if (commuteMins > InferenceConstants.LONG_COMMUTE_MINUTES && vehicleRatio < 0.25f) {
                    scoringEvents += ScoringEvent("long commute", -5, 0, domain = InferenceDomain.ACTIVITY)
                }
            }

            // --- Apply Interaction Rules ---
            snapshot.interactionSummary?.let { interaction ->
                if (interaction.lateNightUsageMinutes > highLateNightThreshold) {
                    scoringEvents += ScoringEvent("late-night screen usage", -10, 5, domain = InferenceDomain.SCREEN)
                }
            }

            // --- Apply Trend Rules (Sleep Debt & Consistency) ---
            snapshot.sleepTrends?.let { trends ->
                if (trends.totalSleepDebtMinutes > 0) {
                    var logisticPenalty =
                        applyLogisticCurve(
                            x = trends.totalSleepDebtMinutes.toFloat(),
                            maxPenalty = InferenceConstants.LOGISTIC_MAX_PENALTY,
                            steepness = InferenceConstants.LOGISTIC_STEEPNESS,
                            midpoint = InferenceConstants.LOGISTIC_MIDPOINT,
                        )

                    val todaySleep = snapshot.sleepSummary?.totalSleepMinutes ?: 0
                    if (todaySleep >= dynamicGoodSleepMin) {
                        logisticPenalty /= 3
                    }

                    if (logisticPenalty > 0) {
                        scoringEvents += ScoringEvent("lingering fatigue from accumulated sleep debt", -logisticPenalty, 0, domain = InferenceDomain.SLEEP)
                    }
                }
            }

            snapshot.activityTrends?.let { trends ->
                if (trends.consistencyScore < InferenceConstants.LOW_ACTIVITY_CONSISTENCY_THRESHOLD) {
                    scoringEvents += ScoringEvent("low activity consistency this week", -5, 0, domain = InferenceDomain.ACTIVITY)
                }
            }

            // Sleep consistency bonus
            val sleepConsistency = snapshot.sleepTrends?.consistencyScore ?: 0
            if (sleepConsistency > InferenceConstants.CONSISTENCY_BONUS_THRESHOLD) {
                scoringEvents +=
                    ScoringEvent(
                        description = "consistent sleep schedule this week",
                        valenceDelta = InferenceConstants.SLEEP_CONSISTENCY_BONUS,
                        domain = InferenceDomain.SLEEP,
                    )
            }

            // Activity consistency bonus
            val activityConsistency = snapshot.activityTrends?.consistencyScore ?: 0
            if (activityConsistency > InferenceConstants.CONSISTENCY_BONUS_THRESHOLD) {
                scoringEvents +=
                    ScoringEvent(
                        description = "consistent activity rhythm this week",
                        valenceDelta = InferenceConstants.ACTIVITY_CONSISTENCY_BONUS,
                        domain = InferenceDomain.ACTIVITY,
                    )
            }

            // Apply per-domain calibration multipliers
            val calibratedEvents =
                scoringEvents.map { event ->
                    val multiplier =
                        when (event.domain) {
                            InferenceDomain.SLEEP -> calibration.sleepMultiplier
                            InferenceDomain.ACTIVITY -> calibration.activityMultiplier
                            InferenceDomain.SCREEN -> calibration.screenMultiplier
                            InferenceDomain.OTHER -> 1.0f
                        }
                    event.copy(
                        valenceDelta = (event.valenceDelta * multiplier).roundToInt(),
                        arousalDelta = (event.arousalDelta * multiplier).roundToInt(),
                    )
                }
            val valenceScore = (InferenceConstants.BASE_SCORE + calibratedEvents.sumOf { it.valenceDelta }).coerceIn(0, 100)
            val arousalScore = (InferenceConstants.BASE_SCORE + calibratedEvents.sumOf { it.arousalDelta }).coerceIn(0, 100)

            // Clamp before mapping: unclamped scores work correctly for threshold-based
            // classification, but bounded scores keep the ranges honest for any future
            // fine-grained interpretation (e.g. a valenceScore of -20 shouldn't linguistically
            // outrank one of 30 — both are just NEGATIVE).
            val finalValence = mapScoreToValence(valenceScore)
            val finalArousal = mapScoreToArousal(arousalScore)
            val sortedEvents = calibratedEvents.sortedByDescending { abs(it.valenceDelta) + abs(it.arousalDelta) }

            return InferredMoodState(
                valence = finalValence,
                arousal = finalArousal,
                interpretationLabel = interpreter.interpret(finalValence, finalArousal),
                confidenceScore = calculateConfidence(snapshot),
                explainabilityString = explainer.generateExplanation(sortedEvents),
                scoringEvents = sortedEvents,
                isFallback = false,
            )
        }

        private fun applyFloorDecay(
            initialImpact: Int,
            hoursElapsed: Float,
            decayConstant: Float = InferenceConstants.TRANSIENT_DECAY_RATE,
            floorRatio: Float = InferenceConstants.TRANSIENT_FLOOR_RATIO,
        ): Int {
            if (hoursElapsed <= 0f) return initialImpact
            val absImpact = abs(initialImpact).toFloat()
            val floor = absImpact * floorRatio
            val exponent = (-decayConstant * hoursElapsed).coerceIn(-50f, 0f)
            val decayed = absImpact * exp(exponent)
            val finalAbs = max(floor, decayed).toInt()
            return if (initialImpact < 0) -finalAbs else finalAbs
        }

        private fun applyLogisticCurve(
            x: Float,
            maxPenalty: Float,
            steepness: Float,
            midpoint: Float,
        ): Int {
            if (x <= 0f) return 0
            val exponent = (-steepness * (x - midpoint)).coerceIn(-50f, 50f)
            val penalty = maxPenalty / (1.0f + exp(exponent))
            return penalty.toInt().coerceIn(0, maxPenalty.toInt())
        }

        private fun deriveFromManualEntries(snapshot: DailyBehaviorSnapshot): InferredMoodState {
            val entries = snapshot.moodEntries
            val primary = entries.maxByOrNull { it.timestamp } ?: entries.first()

            val finalValence = primary.valence
            val finalArousal = primary.arousal

            val explainabilityString: String
            if (entries.size == 1) {
                explainabilityString = "Based on the moment you took to reflect today."
            } else {
                val valenceCounts = entries.groupingBy { it.valence }.eachCount()
                val arousalCounts = entries.groupingBy { it.arousal }.eachCount()

                val firstEntry = entries.minByOrNull { it.timestamp } ?: primary
                val lastEntry = primary

                val majorityValenceCount = valenceCounts.maxByOrNull { it.value }?.value ?: 0
                val modalValence = valenceCounts.maxByOrNull { it.value }?.key ?: primary.valence

                val valenceImproved = lastEntry.valence > firstEntry.valence
                val valenceDeclined = lastEntry.valence < firstEntry.valence
                val energySpiked = lastEntry.arousal > firstEntry.arousal
                val energyDropped = lastEntry.arousal < firstEntry.arousal

                explainabilityString =
                    when {
                        valenceCounts.size == 1 && arousalCounts.size == 1 -> {
                            "You've been feeling exactly this way across all ${entries.size} check-ins today."
                        }
                        majorityValenceCount >= (entries.size - 1) && entries.size >= 3 -> {
                            if (modalValence == lastEntry.valence) {
                                "Despite a slight shift earlier, you've mostly hovered around this feeling today."
                            } else {
                                "You mostly hovered around a different feeling today, but ultimately settled here."
                            }
                        }
                        valenceCounts.size == 1 && energyDropped -> {
                            "Your mood stayed steady, but your energy levels have wound down since your first check-in."
                        }
                        valenceCounts.size == 1 && energySpiked -> {
                            "Your mood stayed steady, and your energy levels have picked up since your first check-in."
                        }
                        valenceImproved -> {
                            "Your mood has steadily lifted since your first check-in today."
                        }
                        valenceDeclined -> {
                            "Your mood has dipped a bit since your earlier check-ins."
                        }
                        else -> {
                            "Your energy has fluctuated across your ${entries.size} check-ins, ultimately settling here."
                        }
                    }
            }

            val confidence = (40 + entries.size.coerceAtMost(3) * 15).coerceAtMost(100)

            return InferredMoodState(
                valence = finalValence,
                arousal = finalArousal,
                interpretationLabel = interpreter.interpret(finalValence, finalArousal),
                confidenceScore = confidence,
                explainabilityString = explainabilityString,
                isFallback = false,
            )
        }

        private fun generateFallbackState(snapshot: DailyBehaviorSnapshot) =
            InferredMoodState(
                valence = Valence.NEUTRAL,
                arousal = Arousal.MID,
                // Updated from "Neutral / Unknown"
                interpretationLabel = "Still finding your rhythm?",
                confidenceScore = snapshot.dataCompletenessScore,
                explainabilityString = "Insufficient data to form a confident inference.",
                isFallback = true,
            )

        private fun calculateConfidence(snapshot: DailyBehaviorSnapshot): Int {
            var score = snapshot.dataCompletenessScore
            if (snapshot.sleepSummary?.isEstimated == true) score -= InferenceConstants.ESTIMATED_SLEEP_PENALTY
            if (snapshot.activitySummary?.isPartialDay == true) score -= InferenceConstants.PARTIAL_DAY_ACTIVITY_PENALTY

            val bonusEntries = snapshot.moodEntries.size.coerceAtMost(InferenceConstants.MANUAL_ENTRY_BONUS_MAX_ENTRIES)
            score += bonusEntries * InferenceConstants.MANUAL_ENTRY_BONUS_PER_ENTRY

            return score.coerceIn(0, 100)
        }

        private fun mapScoreToValence(score: Int): Valence =
            when {
                score < InferenceConstants.VALENCE_NEGATIVE_THRESHOLD -> Valence.NEGATIVE
                score > InferenceConstants.VALENCE_POSITIVE_THRESHOLD -> Valence.POSITIVE
                else -> Valence.NEUTRAL
            }

        private fun mapScoreToArousal(score: Int): Arousal =
            when {
                score < InferenceConstants.AROUSAL_LOW_THRESHOLD -> Arousal.LOW
                score > InferenceConstants.AROUSAL_HIGH_THRESHOLD -> Arousal.HIGH
                else -> Arousal.MID
            }
    }
