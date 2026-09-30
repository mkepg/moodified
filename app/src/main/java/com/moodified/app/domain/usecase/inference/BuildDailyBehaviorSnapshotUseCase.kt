package com.moodified.app.domain.usecase.inference

import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.moodified.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.moodified.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.moodified.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import javax.inject.Inject

class BuildDailyBehaviorSnapshotUseCase
    @Inject
    constructor(
        private val getDailySleepSummary: GetDailySleepSummaryUseCase,
        private val getDailyActivitySummary: GetDailyActivitySummaryUseCase,
        private val getDailyInteractionSummary: GetDailyInteractionSummaryUseCase,
        private val getWeeklySleepTrends: GetWeeklySleepTrendsUseCase,
        private val getWeeklyActivityTrends: GetWeeklyActivityTrendsUseCase,
        private val getWeeklyInteractionTrends: GetWeeklyInteractionTrendsUseCase,
        private val moodRepository: MoodRepository,
    ) {
        operator fun invoke(date: LocalDate): Flow<DailyBehaviorSnapshot> {
            // Grouping into Triples safely bypasses Kotlin's 5-Flow combine limit
            return combine(
                combine(
                    getDailySleepSummary(date).catch { emit(null) },
                    getDailyActivitySummary(date).catch { emit(null) },
                    getDailyInteractionSummary(date).catch { emit(null) },
                    ::Triple,
                ),
                combine(
                    moodRepository.getEntriesForDate(date).catch { emit(emptyList()) },
                    getWeeklySleepTrends(date).catch { emit(null) },
                    getWeeklyActivityTrends(date).catch { emit(null) },
                    ::Triple,
                ),
                getWeeklyInteractionTrends(date).catch { emit(null) },
            ) { (sleepSummary, activitySummary, interactionSummary), (moodEntries, sleepTrends, activityTrends), interactionTrends ->
                DailyBehaviorSnapshot(
                    targetDate = date,
                    sleepSummary = sleepSummary,
                    activitySummary = activitySummary,
                    interactionSummary = interactionSummary,
                    moodEntries = moodEntries,
                    dataCompletenessScore =
                        calculateCompleteness(
                            hasSleep = sleepSummary != null,
                            hasActivity = activitySummary != null,
                            hasInteraction = interactionSummary != null,
                        ),
                    sleepTrends = sleepTrends,
                    activityTrends = activityTrends,
                    interactionTrends = interactionTrends,
                )
            }.flowOn(Dispatchers.IO)
        }

        private fun calculateCompleteness(
            hasSleep: Boolean,
            hasActivity: Boolean,
            hasInteraction: Boolean,
        ): Int {
            var score = 0
            if (hasSleep) score += 40
            if (hasActivity) score += 40
            if (hasInteraction) score += 20
            return score
        }
    }
