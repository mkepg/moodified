package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepTrends
import com.moodified.app.domain.repository.SleepRepository
import com.moodified.app.domain.usecase.inference.InferenceConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GetWeeklySleepTrendsUseCase
    @Inject
    constructor(
        private val repository: SleepRepository,
    ) {
        companion object {
            private const val DEFAULT_BASELINE_MINUTES = 480
            private const val MAX_BACKFILL_SLEEP_MINUTES = 600
            private const val DURATION_NORMALIZER_MINUTES = 120.0
            private const val ONSET_NORMALIZER_MINUTES = 120.0
            private const val SLEEP_DEBT_RECOVERY_RATE = 0.5
            private const val MAX_RUNNING_DEBT_MINUTES = 600
        }

        operator fun invoke(endDate: LocalDate): Flow<SleepTrends?> {
            return repository.getWeeklySummaries(endDate).map { summaries ->
                if (summaries.isEmpty()) return@map null

                val cappedSummaries =
                    summaries.map { s ->
                        if (s.isEstimated) {
                            s.copy(totalSleepMinutes = s.totalSleepMinutes.coerceAtMost(MAX_BACKFILL_SLEEP_MINUTES))
                        } else {
                            s
                        }
                    }.sortedBy { it.date }

                var runningDebt = 0
                cappedSummaries.forEach { summary ->
                    val delta = summary.totalSleepMinutes - DEFAULT_BASELINE_MINUTES
                    if (delta < 0) {
                        runningDebt += (-delta)
                    } else {
                        val recovery = (delta * SLEEP_DEBT_RECOVERY_RATE).toInt()
                        runningDebt = (runningDebt - recovery).coerceAtLeast(0)
                    }
                    runningDebt = runningDebt.coerceAtMost(MAX_RUNNING_DEBT_MINUTES)
                }

                val sleepDurations = cappedSummaries.map { it.totalSleepMinutes }
                val dynamicBaseline = calculateAsymmetricEma(sleepDurations, DEFAULT_BASELINE_MINUTES)

                val durationVariance =
                    cappedSummaries.sumOf {
                        (it.totalSleepMinutes - dynamicBaseline).toDouble().pow(2.0)
                    } / cappedSummaries.size
                val durationStdDev = sqrt(durationVariance)
                val durationScore = (100.0 - (durationStdDev / DURATION_NORMALIZER_MINUTES * 100.0)).coerceIn(0.0, 100.0)

                val onsetMinutes = cappedSummaries.mapNotNull { it.sleepOnsetMinutes }
                val (onsetScore, onsetWeight) =
                    if (onsetMinutes.size >= 2) {
                        val avgOnset = onsetMinutes.average()
                        val onsetVariance = onsetMinutes.sumOf { (it.toDouble() - avgOnset).pow(2.0) } / onsetMinutes.size
                        val score = (100.0 - (sqrt(onsetVariance) / ONSET_NORMALIZER_MINUTES * 100.0)).coerceIn(0.0, 100.0)
                        score to 0.5
                    } else {
                        0.0 to 0.0
                    }

                val durationWeight = 1.0 - onsetWeight
                val consistencyScore = ((durationScore * durationWeight) + (onsetScore * onsetWeight)).roundToInt()

                SleepTrends(
                    daysAnalyzed = cappedSummaries.size,
                    averageSleepMinutes = dynamicBaseline,
                    totalSleepDebtMinutes = runningDebt,
                    consistencyScore = consistencyScore,
                    sleepGoalMinutes = DEFAULT_BASELINE_MINUTES,
                    inferredSleepGoalMinutes = computeInferredSleepGoal(cappedSummaries),
                    baselineSleepOnsetMinutes = computeBaselineOnset(cappedSummaries),
                )
            }
        }

        internal fun computeInferredSleepGoal(summaries: List<DailySleepSummary>): Int {
            val durations = summaries.map { it.totalSleepMinutes }.filter { it > 0 }
            if (durations.size < 5) return DEFAULT_BASELINE_MINUTES
            val sorted = durations.sorted()
            val trimmed = sorted.drop(1).dropLast(1)
            return trimmed.average().roundToInt()
        }

        internal fun computeBaselineOnset(summaries: List<DailySleepSummary>): Int? {
            val onsets = summaries.mapNotNull { it.sleepOnsetMinutes }.filter { it >= 0 }
            if (onsets.isEmpty()) return null
            var ema = onsets.first().toDouble()
            onsets.drop(1).forEach { onset ->
                val alpha = if (onset > ema) InferenceConstants.EMA_ALPHA_UP else InferenceConstants.EMA_ALPHA_DOWN
                ema = alpha * onset + (1.0 - alpha) * ema
            }
            return ema.roundToInt()
        }

        private fun calculateAsymmetricEma(
            values: List<Int>,
            defaultFallback: Int,
        ): Int {
            if (values.isEmpty()) return defaultFallback

            val firstDay = values.first()
            var ema =
                if (kotlin.math.abs(firstDay - defaultFallback) > (defaultFallback * 0.5)) {
                    ((firstDay + defaultFallback) / 2.0)
                } else {
                    firstDay.toDouble()
                }

            for (i in 1 until values.size) {
                val current = values[i].toDouble()
                val clampedCurrent =
                    if (i >= 3) {
                        val floor = ema * (1.0 - InferenceConstants.EMA_CLAMP_RATIO)
                        val ceiling = ema * (1.0 + InferenceConstants.EMA_CLAMP_RATIO)
                        current.coerceIn(floor, ceiling)
                    } else {
                        current
                    }

                val alpha = if (clampedCurrent > ema) InferenceConstants.EMA_ALPHA_UP else InferenceConstants.EMA_ALPHA_DOWN
                ema = (clampedCurrent * alpha) + (ema * (1.0 - alpha))
            }
            return ema.toInt()
        }
    }
