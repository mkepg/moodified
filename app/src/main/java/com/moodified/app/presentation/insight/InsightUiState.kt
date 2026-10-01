package com.moodified.app.presentation.insight

import androidx.compose.ui.graphics.vector.ImageVector
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivityTrends
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionTrends
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepTrends
import java.time.LocalDate
import java.time.LocalDateTime

data class DomainReadiness(
    val isReady: Boolean,
    val daysWithData: Int,
    val requiredDays: Int,
) {
    val progressFraction: Float
        get() =
            if (requiredDays == 0) {
                1f
            } else {
                (daysWithData.toFloat() / requiredDays).coerceIn(0f, 1f)
            }
}

data class InsightDomainReadiness(
    val sleep: DomainReadiness,
    val phone: DomainReadiness,
    val activity: DomainReadiness,
    val mood: DomainReadiness,
) {
    val anyReady: Boolean
        get() = sleep.isReady || phone.isReady || activity.isReady || mood.isReady
}

data class DailyInsightBundle(
    val date: LocalDate,
    val moodEntries: List<MoodEntry> = emptyList(),
    val sleepSummary: DailySleepSummary? = null,
    val activitySummary: ActivityDailySummary? = null,
    val interactionSummary: InteractionDailySummary? = null,
    val inferredMood: InferredMoodState? = null,
)

enum class InsightPriority { HIGH, MEDIUM, LOW }

enum class InsightCategory { SLEEP, ACTIVITY, PHONE, MOOD, CORRELATION }

data class InsightCard(
    val id: String,
    val category: InsightCategory,
    val priority: InsightPriority,
    val headline: String,
    val body: String,
    val icon: ImageVector,
)

data class MoodChartPoint(
    val date: LocalDate,
    val valenceOrdinal: Float,
    val isManual: Boolean,
)

data class SleepBarPoint(
    val date: LocalDate,
    val totalSleepMinutes: Int,
    val isEstimated: Boolean,
)

data class ActivityBarPoint(
    val date: LocalDate,
    val sedentaryMinutes: Int,
    val lightMinutes: Int,
    val moderateMinutes: Int,
    val vigorousMinutes: Int,
    val totalSteps: Int,
) {
    val activeMinutes: Int get() = lightMinutes + moderateMinutes + vigorousMinutes
}

data class ScreenTimeBarPoint(
    val date: LocalDate,
    val totalScreenMinutes: Int,
    val lateNightMinutes: Int,
)

data class MoodStability(
    val score: Int,
    val variance: Float,
    val stateLabel: String,
)

sealed interface IntradayTimelineEvent {
    val timestamp: LocalDateTime

    data class SleepOnset(
        override val timestamp: LocalDateTime,
    ) : IntradayTimelineEvent

    data class SleepWakeUp(
        override val timestamp: LocalDateTime,
        val durationMinutes: Int,
    ) : IntradayTimelineEvent

    data class ActivitySpike(
        override val timestamp: LocalDateTime,
        val intensityName: String,
        val activeMinutes: Int,
    ) : IntradayTimelineEvent

    data class ScreenTimeBlock(
        override val timestamp: LocalDateTime,
        val durationMinutes: Int,
        val isLateNight: Boolean,
    ) : IntradayTimelineEvent

    data class MoodLog(
        override val timestamp: LocalDateTime,
        val valenceOrdinal: Int,
        val arousalOrdinal: Int,
        val isManual: Boolean,
    ) : IntradayTimelineEvent
}

data class InsightUiState(
    val isLoading: Boolean = true,
    val domainReadiness: InsightDomainReadiness =
        InsightDomainReadiness(
            sleep = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 0),
            phone = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 0),
            activity = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 0),
            mood = DomainReadiness(isReady = false, daysWithData = 0, requiredDays = 3),
        ),
    val weeklyBundles: List<DailyInsightBundle> = emptyList(),
    val todayInferredMood: InferredMoodState? = null,
    val sleepTrends: SleepTrends? = null,
    val activityTrends: ActivityTrends? = null,
    val interactionTrends: InteractionTrends? = null,
    val moodChartPoints: List<MoodChartPoint> = emptyList(),
    val sleepBarPoints: List<SleepBarPoint> = emptyList(),
    val activityBarPoints: List<ActivityBarPoint> = emptyList(),
    val screenTimePoints: List<ScreenTimeBarPoint> = emptyList(),
    val activityToday: ActivityDailySummary? = null,
    val sleepLastNight: DailySleepSummary? = null,
    val phoneToday: InteractionDailySummary? = null,
    val insightCards: List<InsightCard> = emptyList(),
    val daysWithData: Int = 0,
    val moodStability: MoodStability? = null,
    val todayTimeline: List<IntradayTimelineEvent> = emptyList(),
    val weeklyMoodPoints: List<Float?> = emptyList(),
    val activityMoodInsight: String? = null,
    val sleepMoodInsight: String? = null,
    val screenMoodInsight: String? = null,
) {
    val hasEnoughData: Boolean get() = domainReadiness.anyReady
}
