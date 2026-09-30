package com.moodified.app.domain.model.inference

import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivityTrends
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionTrends
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepTrends
import java.time.LocalDate

data class DailyBehaviorSnapshot(
    val targetDate: LocalDate,
    val sleepSummary: DailySleepSummary?,
    val activitySummary: ActivityDailySummary?,
    val interactionSummary: InteractionDailySummary?,
    val moodEntries: List<MoodEntry>,
    val dataCompletenessScore: Int,
    val sleepTrends: SleepTrends? = null,
    val activityTrends: ActivityTrends? = null,
    val interactionTrends: InteractionTrends? = null,
)
