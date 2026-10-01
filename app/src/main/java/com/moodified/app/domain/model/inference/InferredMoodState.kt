package com.moodified.app.domain.model.inference

import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence

/**
 * Represents the final output of the Mood Inference Engine.
 */
data class InferredMoodState(
    val valence: Valence,
    val arousal: Arousal,
    // Converted intuitive label (e.g., "Stressed", "Calm") [cite: 7]
    val interpretationLabel: String,
    // 0-100 score reflecting data completeness and consistency [cite: 8]
    val confidenceScore: Int,
    // A short natural language string justifying the inference [cite: 9]
    val explainabilityString: String,
    // Behavioral events that contributed to this inference; populated by passive inference, empty for fallback/manual
    val scoringEvents: List<ScoringEvent> = emptyList(),
    // Identifies if this inference was strictly derived from data or fell back to defaults
    val isFallback: Boolean = false,
)
