package com.moodified.app.presentation.insight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.data.local.datasource.CalibrationPreferencesDataSource
import com.moodified.app.domain.model.activity.ActivityBlock
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.inference.CalibrationWeights
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionSession
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepStatus
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.SleepRepository
import com.moodified.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.moodified.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.moodified.app.domain.usecase.inference.RuleBasedMoodInferenceEngine
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.moodified.app.domain.usecase.mood.GetMoodHistoryUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepSummariesUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class InsightViewModel
    @Inject
    constructor(
        private val getMoodHistory: GetMoodHistoryUseCase,
        private val getWeeklySleepSummaries: GetWeeklySleepSummariesUseCase,
        private val getWeeklyActivitySummaries: GetWeeklyActivitySummariesUseCase,
        private val getWeeklyInteractionSummaries: GetWeeklyInteractionSummariesUseCase,
        private val getWeeklySleepTrends: GetWeeklySleepTrendsUseCase,
        private val getWeeklyActivityTrends: GetWeeklyActivityTrendsUseCase,
        private val getWeeklyInteractionTrends: GetWeeklyInteractionTrendsUseCase,
        private val inferenceEngine: RuleBasedMoodInferenceEngine,
        private val insightGenerator: InsightGenerator,
        private val sleepRepository: SleepRepository,
        private val activityRepository: ActivityRepository,
        private val interactionRepository: InteractionRepository,
        private val calibrationSource: CalibrationPreferencesDataSource,
    ) : ViewModel() {
        val uiState: StateFlow<InsightUiState> =
            midnightTickerFlow()
                .flatMapLatest { today ->
                    val rawDataFlow =
                        combine(
                            getMoodHistory(),
                            getWeeklySleepSummaries(today),
                            getWeeklyActivitySummaries(today),
                            getWeeklyInteractionSummaries(today),
                        ) { moodHistory, sleepList, activityList, interactionList ->
                            RawWeeklyData(moodHistory, sleepList, activityList, interactionList)
                        }

                    val trendsFlow =
                        combine(
                            getWeeklySleepTrends(today),
                            getWeeklyActivityTrends(today),
                            getWeeklyInteractionTrends(today),
                        ) { sleepTrends, activityTrends, interactionTrends ->
                            WeeklyTrends(sleepTrends, activityTrends, interactionTrends)
                        }

                    val todayEventsFlow =
                        combine(
                            sleepRepository.getSegmentsForDate(today),
                            interactionRepository.getSessionsForDate(today),
                            activityRepository.getActivityBlocksForDate(today),
                        ) { sleepSegments, interactionSessions, activityBlocks ->
                            TodayDetailedEvents(sleepSegments, interactionSessions, activityBlocks)
                        }

                    combine(rawDataFlow, trendsFlow, todayEventsFlow, calibrationSource.flow()) { raw, trends, events, calibration ->
                        buildState(raw, trends, events, today, calibration)
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = InsightUiState(isLoading = true),
                )

        private fun buildState(
            raw: RawWeeklyData,
            trends: WeeklyTrends,
            todayEvents: TodayDetailedEvents,
            today: LocalDate,
            calibration: CalibrationWeights = CalibrationWeights(),
        ): InsightUiState {
            val last7Days = (0L until 7L).map { today.minusDays(it) }

            val sleepByDate: Map<LocalDate, DailySleepSummary> = raw.sleepList.associateBy { LocalDate.parse(it.date) }
            val activityByDate: Map<LocalDate, ActivityDailySummary> = raw.activityList.associateBy { LocalDate.parse(it.date) }
            val interactionByDate: Map<LocalDate, InteractionDailySummary> = raw.interactionList.associateBy { LocalDate.parse(it.date) }

            val bundles: List<DailyInsightBundle> =
                last7Days.map { date ->
                    val entries = raw.moodHistory[date] ?: emptyList()
                    val snapshot =
                        DailyBehaviorSnapshot(
                            targetDate = date,
                            sleepSummary = sleepByDate[date],
                            activitySummary = activityByDate[date],
                            interactionSummary = interactionByDate[date],
                            moodEntries = entries,
                            dataCompletenessScore =
                                computeCompleteness(
                                    sleepByDate[date],
                                    activityByDate[date],
                                    interactionByDate[date],
                                ),
                            sleepTrends = trends.sleep,
                            activityTrends = trends.activity,
                            interactionTrends = trends.interaction,
                        )

                    DailyInsightBundle(
                        date = date,
                        moodEntries = entries,
                        sleepSummary = sleepByDate[date],
                        activitySummary = activityByDate[date],
                        interactionSummary = interactionByDate[date],
                        inferredMood = runCatching { inferenceEngine(snapshot, calibration) }.getOrNull(),
                    )
                }

            val todayMood = bundles.firstOrNull()?.inferredMood

            val sleepDays = bundles.count { it.sleepSummary != null }
            val phoneDays = bundles.count { it.interactionSummary != null }
            val activityDays = bundles.count { it.activitySummary != null }
            val manualMoodDays = bundles.count { b -> b.moodEntries.any { it.isManual } }

            val domainReadiness =
                InsightDomainReadiness(
                    sleep = DomainReadiness(isReady = sleepDays >= 1, daysWithData = sleepDays, requiredDays = 0),
                    phone = DomainReadiness(isReady = phoneDays >= 1, daysWithData = phoneDays, requiredDays = 0),
                    activity =
                        DomainReadiness(
                            isReady = activityDays >= 1,
                            daysWithData = activityDays,
                            requiredDays = 0,
                        ),
                    mood =
                        DomainReadiness(
                            isReady = manualMoodDays >= MIN_MOOD_DAYS,
                            daysWithData = manualMoodDays,
                            requiredDays = MIN_MOOD_DAYS,
                        ),
                )

            val chartBundles = bundles.reversed()

            val moodPoints: List<MoodChartPoint> =
                if (domainReadiness.mood.isReady) {
                    chartBundles.flatMap { b ->
                        b.moodEntries.filter { it.isManual }.map { e ->
                            MoodChartPoint(date = b.date, valenceOrdinal = e.valence.ordinal.toFloat(), isManual = true)
                        }
                    }
                } else {
                    emptyList()
                }

            val sleepPoints: List<SleepBarPoint> =
                chartBundles.mapNotNull { b ->
                    b.sleepSummary?.let {
                            s ->
                        SleepBarPoint(date = b.date, totalSleepMinutes = s.totalSleepMinutes, isEstimated = s.isEstimated)
                    }
                }

            val activityPoints: List<ActivityBarPoint> =
                if (domainReadiness.activity.isReady) {
                    chartBundles.mapNotNull { b ->
                        b.activitySummary?.let { a ->
                            ActivityBarPoint(
                                date = b.date,
                                sedentaryMinutes = a.minutesPerIntensityBand[ActivityIntensity.SEDENTARY] ?: a.sedentaryMinutes,
                                lightMinutes = a.minutesPerIntensityBand[ActivityIntensity.LIGHT] ?: 0,
                                moderateMinutes = a.minutesPerIntensityBand[ActivityIntensity.MODERATE] ?: 0,
                                vigorousMinutes = a.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0,
                                totalSteps = a.totalSteps,
                            )
                        }
                    }
                } else {
                    emptyList()
                }

            val screenPoints: List<ScreenTimeBarPoint> =
                chartBundles.mapNotNull { b ->
                    b.interactionSummary?.let { i ->
                        ScreenTimeBarPoint(
                            date = b.date,
                            totalScreenMinutes = i.totalScreenTimeMinutes,
                            lateNightMinutes = i.lateNightUsageMinutes,
                        )
                    }
                }

            val daysWithDataCount =
                bundles.count {
                    it.sleepSummary != null || it.activitySummary != null ||
                        it.interactionSummary != null || it.moodEntries.isNotEmpty()
                }

            val stability = computeMoodStability(bundles)

            val timelineEvents =
                synthesizeTimeline(
                    todayEvents = todayEvents,
                    moodEntries = raw.moodHistory[today] ?: emptyList(),
                )

            val todayBundle = bundles.firstOrNull { it.date == today }

            // oldest-first for left-to-right chart alignment
            val weeklyMoodPoints: List<Float?> =
                chartBundles.map { bundle ->
                    val avgManualValence =
                        bundle.moodEntries
                            .filter { it.isManual }
                            .map { it.valence.ordinal.toFloat() }
                            .takeIf { it.isNotEmpty() }
                            ?.average()
                            ?.toFloat()
                    val fallbackValence = bundle.inferredMood?.valence?.ordinal?.toFloat()
                    val rawValue = avgManualValence ?: fallbackValence ?: return@map null
                    (rawValue / 2f).coerceIn(0f, 1f) // NEGATIVE=0f, NEUTRAL=0.5f, POSITIVE=1f
                }

            val dateToMood: Map<LocalDate, Float> =
                chartBundles
                    .zip(weeklyMoodPoints)
                    .mapNotNull { (bundle, moodPoint) -> moodPoint?.let { bundle.date to it } }
                    .toMap()

            val activityMoodInsight: String? =
                when (moodCorrelation(dateToMood, activityPoints.associate { it.date to it.totalSteps.toFloat() })) {
                    Correlation.POSITIVE -> "Your mood tended to be better on more active days."
                    Correlation.NEGATIVE -> "Your mood was lower on your busiest days — rest may help."
                    Correlation.NONE -> "Activity and mood moved independently this week."
                    null -> null
                }

            val sleepMoodInsight: String? =
                when (moodCorrelation(dateToMood, sleepPoints.associate { it.date to it.totalSleepMinutes.toFloat() })) {
                    Correlation.POSITIVE -> "More sleep lined up with better mood this week."
                    Correlation.NEGATIVE -> "Sleep duration and mood didn't track together this week."
                    Correlation.NONE -> "Sleep and mood moved independently this week."
                    null -> null
                }

            val screenMoodInsight: String? =
                when (moodCorrelation(dateToMood, screenPoints.associate { it.date to it.totalScreenMinutes.toFloat() }, invertedExpectation = true)) {
                    Correlation.POSITIVE -> "Your mood was lower on your heaviest screen-use days."
                    Correlation.NEGATIVE -> "Screen use and mood moved together this week."
                    Correlation.NONE -> "Screen use and mood didn't closely track this week."
                    null -> null
                }

            return InsightUiState(
                isLoading = false,
                domainReadiness = domainReadiness,
                weeklyBundles = bundles,
                todayInferredMood = todayMood,
                sleepTrends = trends.sleep,
                activityTrends = trends.activity,
                interactionTrends = trends.interaction,
                moodChartPoints = moodPoints,
                sleepBarPoints = sleepPoints,
                activityBarPoints = activityPoints,
                screenTimePoints = screenPoints,
                activityToday = todayBundle?.activitySummary,
                sleepLastNight = todayBundle?.sleepSummary,
                phoneToday = todayBundle?.interactionSummary,
                insightCards = insightGenerator.generate(bundles, domainReadiness),
                daysWithData = daysWithDataCount,
                moodStability = stability,
                todayTimeline = timelineEvents,
                weeklyMoodPoints = weeklyMoodPoints,
                activityMoodInsight = activityMoodInsight,
                sleepMoodInsight = sleepMoodInsight,
                screenMoodInsight = screenMoodInsight,
            )
        }

        private fun computeMoodStability(bundles: List<DailyInsightBundle>): MoodStability? {
            val manualEntries = bundles.flatMap { b -> b.moodEntries.filter { it.isManual } }
            if (manualEntries.size < 3) return null

            val mean = manualEntries.map { it.valence.ordinal.toFloat() }.average().toFloat()
            val variance = manualEntries.map { Math.pow((it.valence.ordinal.toFloat() - mean).toDouble(), 2.0) }.average().toFloat()

            val normalizedVariance = variance.coerceIn(0f, 1.2f)
            val score = (100f - (normalizedVariance / 1.2f * 100f)).toInt().coerceIn(0, 100)

            val label =
                when {
                    score >= 75 -> "Highly Stable"
                    score >= 40 -> "Moderate Fluctuations"
                    else -> "High Volatility"
                }

            return MoodStability(score, variance, label)
        }

        private fun synthesizeTimeline(
            todayEvents: TodayDetailedEvents,
            moodEntries: List<com.moodified.app.domain.model.mood.MoodEntry>,
        ): List<IntradayTimelineEvent> {
            val events = mutableListOf<IntradayTimelineEvent>()

            // 1. Sleep Segments
            todayEvents.sleepSegments.filter { it.status == SleepStatus.ASLEEP }.forEach { segment ->
                events.add(IntradayTimelineEvent.SleepOnset(timestamp = segment.startTime))
                events.add(IntradayTimelineEvent.SleepWakeUp(timestamp = segment.endTime, durationMinutes = segment.totalSleepMinutes))
            }

            // 2. Activity Blocks (>= 5 mins to filter out noise)
            todayEvents.activityBlocks.filter { it.durationMinutes >= 5 && it.intensity != ActivityIntensity.SEDENTARY }.forEach { block ->
                events.add(
                    IntradayTimelineEvent.ActivitySpike(
                        timestamp = block.startTime,
                        intensityName = block.intensity.name,
                        activeMinutes = block.durationMinutes,
                    ),
                )
            }

            // 3. Interaction Sessions (>= 5 mins to filter out noise)
            todayEvents.interactionSessions.filter { it.durationMinutes >= 5 }.forEach { session ->
                val hour = session.startTime.hour
                val isLateNight = hour < 5 || hour >= 24
                events.add(
                    IntradayTimelineEvent.ScreenTimeBlock(
                        timestamp = session.startTime,
                        durationMinutes = session.durationMinutes,
                        isLateNight = isLateNight,
                    ),
                )
            }

            // 4. Mood Logs
            moodEntries.forEach { entry ->
                events.add(
                    IntradayTimelineEvent.MoodLog(
                        timestamp = entry.timestamp,
                        valenceOrdinal = entry.valence.ordinal,
                        arousalOrdinal = entry.arousal.ordinal,
                        isManual = entry.isManual,
                    ),
                )
            }

            // Sort all events chronologically
            val sortedEvents = events.sortedBy { it.timestamp }

            // Post-process to merge consecutive events of the same type
            val mergedTimeline = mutableListOf<IntradayTimelineEvent>()

            for (event in sortedEvents) {
                if (mergedTimeline.isEmpty()) {
                    mergedTimeline.add(event)
                    continue
                }

                val last = mergedTimeline.last()

                // Merge consecutive Screen Time events
                if (last is IntradayTimelineEvent.ScreenTimeBlock && event is IntradayTimelineEvent.ScreenTimeBlock) {
                    // Only merge if they share the same classification (e.g. both are Late Night)
                    if (last.isLateNight == event.isLateNight) {
                        mergedTimeline[mergedTimeline.lastIndex] =
                            last.copy(
                                durationMinutes = last.durationMinutes + event.durationMinutes,
                            )
                        continue
                    }
                }

                // Optional bonus: Also merge consecutive Activity blocks if they share the exact same intensity
                if (last is IntradayTimelineEvent.ActivitySpike && event is IntradayTimelineEvent.ActivitySpike) {
                    if (last.intensityName == event.intensityName) {
                        mergedTimeline[mergedTimeline.lastIndex] =
                            last.copy(
                                activeMinutes = last.activeMinutes + event.activeMinutes,
                            )
                        continue
                    }
                }

                mergedTimeline.add(event)
            }

            return mergedTimeline
        }

        private fun computeCompleteness(
            sleep: DailySleepSummary?,
            activity: ActivityDailySummary?,
            interaction: InteractionDailySummary?,
        ): Int {
            var score = 0
            if (sleep != null) score += 40
            if (activity != null) score += 40
            if (interaction != null) score += 20
            return score
        }

        private data class RawWeeklyData(
            val moodHistory: Map<LocalDate, List<com.moodified.app.domain.model.mood.MoodEntry>>,
            val sleepList: List<DailySleepSummary>,
            val activityList: List<ActivityDailySummary>,
            val interactionList: List<InteractionDailySummary>,
        )

        private data class WeeklyTrends(
            val sleep: com.moodified.app.domain.model.sleep.SleepTrends?,
            val activity: com.moodified.app.domain.model.activity.ActivityTrends?,
            val interaction: com.moodified.app.domain.model.interaction.InteractionTrends?,
        )

        private data class TodayDetailedEvents(
            val sleepSegments: List<SleepSegment>,
            val interactionSessions: List<InteractionSession>,
            val activityBlocks: List<ActivityBlock>,
        )

        private enum class Correlation { POSITIVE, NEGATIVE, NONE }

        private fun moodCorrelation(
            dateToMood: Map<LocalDate, Float>,
            dateToValue: Map<LocalDate, Float>,
            invertedExpectation: Boolean = false,
        ): Correlation? {
            val pairs =
                dateToMood.keys
                    .intersect(dateToValue.keys)
                    .filter { (dateToValue[it] ?: 0f) > 0f }
                    .map { dateToValue[it]!! to dateToMood[it]!! }
            if (pairs.size < 3) return null

            val median = pairs.map { it.first }.sorted().let { s ->
                if (s.size % 2 == 0) (s[s.size / 2 - 1] + s[s.size / 2]) / 2f else s[s.size / 2]
            }
            val highMoodAvg = pairs.filter { it.first >= median }.map { it.second }.average()
            val lowMoodAvg = pairs.filter { it.first < median }.map { it.second }.average()
            val delta = if (invertedExpectation) lowMoodAvg - highMoodAvg else highMoodAvg - lowMoodAvg

            return when {
                delta > 0.15 -> Correlation.POSITIVE
                delta < -0.15 -> Correlation.NEGATIVE
                else -> Correlation.NONE
            }
        }

        private companion object {
            const val MIN_MOOD_DAYS = 3
        }
    }
