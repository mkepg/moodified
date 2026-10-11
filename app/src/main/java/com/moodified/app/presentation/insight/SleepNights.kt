package com.moodified.app.presentation.insight

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sleep is stored by the date the user woke up, but people think of it by the night it began.
 * The chart, the log sheet and the insight text all name nights through these helpers.
 */
object SleepNights {
    private const val MORNING_HOUR = 6

    fun nightOf(wakeDate: LocalDate): LocalDate = wakeDate.minusDays(1)

    /** True while the night ending on [wakeDate] may not have finished yet. */
    fun isInProgress(
        wakeDate: LocalDate,
        now: LocalDateTime,
    ): Boolean = wakeDate == now.toLocalDate() && now.hour < MORNING_HOUR
}
