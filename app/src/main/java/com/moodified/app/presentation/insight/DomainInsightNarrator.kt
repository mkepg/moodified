package com.moodified.app.presentation.insight

import com.moodified.app.core.utils.DateTimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

internal enum class Correlation { POSITIVE, NEGATIVE, NONE }

internal data class CorrelationResult(
    val direction: Correlation,
    val highCount: Int,
    val lowCount: Int,
    val totalDays: Int,
)

private const val MIN_PAIRS = 3
private const val DELTA_THRESHOLD = 0.15

/**
 * Builds the sentence shown under each domain chart.
 *
 * Mood correlations require manually logged moods. Correlating behaviour against an
 * inferred mood would only restate the inference engine's own scoring formula, since
 * that mood was derived from the same sleep, activity and screen figures.
 */
class DomainInsightNarrator
    @Inject
    constructor() {
        fun sleep(
            moodReady: Boolean,
            mood: Map<LocalDate, Float>,
            sleepMinutes: Map<LocalDate, Int>,
            lateNightMinutes: Map<LocalDate, Int>,
        ): String? {
            if (moodReady) {
                correlate(mood, sleepMinutes.asDriver())?.let { r ->
                    return when (r.direction) {
                        Correlation.POSITIVE ->
                            "Your ${r.highCount} longest sleep nights lined up with better mood this week."
                        Correlation.NEGATIVE ->
                            "Your mood ran lower after your ${r.highCount} longest nights this week."
                        Correlation.NONE ->
                            "Sleep and mood moved independently across your ${r.totalDays} tracked nights this week."
                    }
                }
            }
            correlate(sleepMinutes.asOutcome(), lateNightMinutes.asDriver())?.let { r ->
                return when (r.direction) {
                    Correlation.NEGATIVE ->
                        "Your shortest nights were the ones with the most late-night screen use."
                    Correlation.POSITIVE ->
                        "Late-night screen use didn't cut your sleep short this week."
                    Correlation.NONE ->
                        "Late-night screen use and how long you slept didn't track together this week."
                }
            }
            return sleepHighlight(sleepMinutes)
        }

        fun activity(
            moodReady: Boolean,
            mood: Map<LocalDate, Float>,
            steps: Map<LocalDate, Int>,
            sleepMinutes: Map<LocalDate, Int>,
        ): String? {
            if (moodReady) {
                correlate(mood, steps.asDriver())?.let { r ->
                    return when (r.direction) {
                        Correlation.POSITIVE ->
                            "On your ${r.highCount} most active days, your mood averaged higher than on quieter days."
                        Correlation.NEGATIVE ->
                            "Your mood dipped on ${r.highCount} of your busiest activity days — some recovery time may help."
                        Correlation.NONE ->
                            "Activity and mood moved independently across your ${r.totalDays} tracked days this week."
                    }
                }
            }
            correlate(steps.asOutcome(), sleepMinutes.asDriver())?.let { r ->
                return when (r.direction) {
                    Correlation.POSITIVE ->
                        "You moved more on the days after your longest nights."
                    Correlation.NEGATIVE ->
                        "You moved less on the days after your longest nights."
                    Correlation.NONE ->
                        "How long you slept and how much you moved didn't track together this week."
                }
            }
            return activityHighlight(steps)
        }

        fun screen(
            moodReady: Boolean,
            mood: Map<LocalDate, Float>,
            screenMinutes: Map<LocalDate, Int>,
            steps: Map<LocalDate, Int>,
        ): String? {
            if (moodReady) {
                correlate(mood, screenMinutes.asDriver())?.let { r ->
                    return when (r.direction) {
                        Correlation.NEGATIVE ->
                            "Your mood was lower on ${r.highCount} of your heaviest screen-use days."
                        Correlation.POSITIVE ->
                            "Your mood held up on ${r.highCount} of your heaviest screen-use days."
                        Correlation.NONE ->
                            "Screen use and mood didn't closely track across your ${r.totalDays} days this week."
                    }
                }
            }
            correlate(steps.asOutcome(), screenMinutes.asDriver())?.let { r ->
                return when (r.direction) {
                    Correlation.NEGATIVE ->
                        "You moved less on your heaviest screen-use days."
                    Correlation.POSITIVE ->
                        "You still moved plenty on your heaviest screen-use days."
                    Correlation.NONE ->
                        "Screen use and how much you moved didn't track together this week."
                }
            }
            return screenHighlight(screenMinutes)
        }

        // --- Descriptive fallbacks -------------------------------------------------

        private fun sleepHighlight(sleepMinutes: Map<LocalDate, Int>): String? {
            val tracked = sleepMinutes.filterValues { it > 0 }
            val best = tracked.maxByOrNull { it.value } ?: return null
            val worst = tracked.minByOrNull { it.value }
            if (worst == null || worst.key == best.key) {
                return "Your longest night this week was ${DateTimeUtils.formatMinutes(best.value)} on ${best.key.dayName()}."
            }
            return "Your longest night was ${DateTimeUtils.formatMinutes(best.value)} on ${best.key.dayName()}, " +
                "your shortest ${DateTimeUtils.formatMinutes(worst.value)} on ${worst.key.dayName()}."
        }

        private fun activityHighlight(steps: Map<LocalDate, Int>): String? {
            val best = steps.filterValues { it > 0 }.maxByOrNull { it.value } ?: return null
            return "Your most active day was ${best.key.dayName()} at ${"%,d".format(best.value)} steps."
        }

        private fun screenHighlight(screenMinutes: Map<LocalDate, Int>): String? {
            val heaviest = screenMinutes.filterValues { it > 0 }.maxByOrNull { it.value } ?: return null
            return "Your heaviest screen day was ${heaviest.key.dayName()} at ${DateTimeUtils.formatMinutes(heaviest.value)}."
        }
    }

// --- Correlation ---------------------------------------------------------------

/**
 * Splits the tracked days at the median of [driver] and compares the mean [outcome]
 * between the high and low halves. POSITIVE means [outcome] ran higher on high-[driver]
 * days. [outcome] must already be on a 0..1 scale so [DELTA_THRESHOLD] is meaningful.
 */
internal fun correlate(
    outcome: Map<LocalDate, Float>,
    driver: Map<LocalDate, Float>,
): CorrelationResult? {
    val pairs =
        outcome.keys
            .intersect(driver.keys)
            .filter { (driver[it] ?: 0f) > 0f }
            .map { driver.getValue(it) to outcome.getValue(it) }
    if (pairs.size < MIN_PAIRS) return null

    val sorted = pairs.map { it.first }.sorted()
    val median =
        if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        } else {
            sorted[sorted.size / 2]
        }

    val high = pairs.filter { it.first >= median }
    val low = pairs.filter { it.first < median }
    if (high.isEmpty() || low.isEmpty()) {
        return CorrelationResult(Correlation.NONE, high.size, low.size, pairs.size)
    }

    val delta = high.map { it.second }.average() - low.map { it.second }.average()
    val direction =
        when {
            delta > DELTA_THRESHOLD -> Correlation.POSITIVE
            delta < -DELTA_THRESHOLD -> Correlation.NEGATIVE
            else -> Correlation.NONE
        }
    return CorrelationResult(direction, high.size, low.size, pairs.size)
}

/** Only the median split matters for a driver, so the raw scale is kept. */
private fun Map<LocalDate, Int>.asDriver(): Map<LocalDate, Float> = mapValues { it.value.toFloat() }

/** Min-max scales an outcome into 0..1 so the delta threshold is unit-independent. */
private fun Map<LocalDate, Int>.asOutcome(): Map<LocalDate, Float> {
    val values = values.filter { it > 0 }
    val min = values.minOrNull() ?: return emptyMap()
    val max = values.maxOrNull() ?: return emptyMap()
    val span = (max - min).toFloat()
    return mapValues { (_, v) -> if (span == 0f) 0.5f else (v - min) / span }
}

private fun LocalDate.dayName(): String = format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
