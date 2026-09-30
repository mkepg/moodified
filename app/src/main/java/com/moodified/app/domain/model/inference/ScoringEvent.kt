package com.moodified.app.domain.model.inference

enum class InferenceDomain { SLEEP, ACTIVITY, SCREEN, OTHER }

/**
 * Records a single rule that fired during mood inference, along with its numeric contribution.
 * Passed from [com.moodified.app.domain.usecase.inference.RuleBasedMoodInferenceEngine] to
 * [com.moodified.app.domain.usecase.inference.MoodExplainabilityGenerator] so that explanations
 * are derived from the same events that produced the score — not independently re-evaluated.
 */
data class ScoringEvent(
    val description: String,
    val valenceDelta: Int = 0,
    val arousalDelta: Int = 0,
    val domain: InferenceDomain = InferenceDomain.OTHER,
)
