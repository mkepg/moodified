package com.moodified.app.domain.usecase.inference

internal object InferenceConstants {
    const val BASE_SCORE = 50

    // Valence & Arousal Boundaries
    const val VALENCE_NEGATIVE_THRESHOLD = 38
    const val VALENCE_POSITIVE_THRESHOLD = 62
    const val AROUSAL_LOW_THRESHOLD = 40
    const val AROUSAL_HIGH_THRESHOLD = 60

    // Dynamic Trend Multipliers & EMA Tuning
    const val DYNAMIC_POOR_SLEEP_MULTIPLIER = 0.80f
    const val DYNAMIC_GOOD_SLEEP_MIN_MULTIPLIER = 0.95f
    const val DYNAMIC_HIGH_ACTIVITY_MULTIPLIER = 1.25f
    const val DYNAMIC_HIGH_STEPS_MULTIPLIER = 1.20f

    // Asymmetric EMA Parameters
    const val EMA_ALPHA_UP = 0.30 // Adapt to positive habits quickly
    const val EMA_ALPHA_DOWN = 0.10 // Resist sudden negative drops (e.g., all-nighters)
    const val EMA_CLAMP_RATIO = 0.40 // Maximum daily deviation allowed in baseline calc

    // Logistic Curve (S-Curve) Parameters for Sleep Debt
    const val LOGISTIC_MAX_PENALTY = 25f
    const val LOGISTIC_STEEPNESS = 0.03f
    const val LOGISTIC_MIDPOINT = 150f // Inflection point at 2.5 hours of sleep debt

    // Floor Decay Parameters
    const val TRANSIENT_DECAY_RATE = 0.15f
    const val TRANSIENT_FLOOR_RATIO = 0.30f // Event retains at least 30% of its impact

    // Quality & Health Constants
    const val AWAKENING_THRESHOLD = 3

    // Sleep onset is stored as minutes-since-6PM (see SleepTimeUtils.minutesSince6PM).
    // 420 min past 6PM = 1AM — bedtime this late correlates with worse mood the next day.
    const val LATE_BEDTIME_MINUTES = 420
    const val SEDENTARY_MINUTES_THRESHOLD = 480
    const val VIGOROUS_MINUTES_THRESHOLD = 15
    const val LONG_COMMUTE_MINUTES = 60
    const val LATE_NIGHT_MINUTES_THRESHOLD = 30
    const val HIGH_SESSION_COUNT = 30
    const val SHORT_SESSION_DURATION_MINUTES = 3
    const val HIGH_SCREEN_TIME_MINUTES = 240

    // Absolute Fallbacks
    const val GOOD_SLEEP_MINUTES_MIN = 420
    const val HIGH_ACTIVITY_MINUTES = 45
    const val HIGH_STEPS_THRESHOLD = 8_000
    const val LOW_ACTIVITY_CONSISTENCY_THRESHOLD = 40

    // Penalties & Bonuses
    const val ESTIMATED_SLEEP_PENALTY = 10
    const val PARTIAL_DAY_ACTIVITY_PENALTY = 8
    const val MANUAL_ENTRY_BONUS_PER_ENTRY = 8
    const val MANUAL_ENTRY_BONUS_MAX_ENTRIES = 2
    const val MIN_COMPLETENESS_FOR_INFERENCE = 30

    // Consistency bonus thresholds
    const val CONSISTENCY_BONUS_THRESHOLD = 80
    const val SLEEP_CONSISTENCY_BONUS = 3
    const val ACTIVITY_CONSISTENCY_BONUS = 3

    // Chronotype-aware late bedtime offset (minutes past personal baseline)
    const val LATE_BEDTIME_OFFSET_MINUTES = 60

    // Dynamic screen-time multipliers (relative to personal average)
    const val DYNAMIC_HIGH_SCREEN_TIME_MULTIPLIER = 1.25f
    const val DYNAMIC_HIGH_LATE_NIGHT_MULTIPLIER = 1.50f

    // Calibration constants (used in Task 7)
    const val CALIBRATION_BASE_NUDGE = 0.04f
    const val CALIBRATION_DECAY_RATE = 0.05f
    const val CALIBRATION_MIN_CONFIDENCE = 30
    const val CALIBRATION_MIN_COMPLETENESS = 30
}
