package com.moodified.app.domain.model.inference

import kotlinx.serialization.Serializable

@Serializable
data class CalibrationWeights(
    val sleepMultiplier: Float = 1.0f,
    val activityMultiplier: Float = 1.0f,
    val screenMultiplier: Float = 1.0f,
    val feedbackCount: Int = 0,
    val lastUpdatedIso: String? = null,
) {
    val lastUpdated: java.time.LocalDate?
        get() = lastUpdatedIso?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

    fun withLastUpdated(date: java.time.LocalDate) = copy(lastUpdatedIso = date.toString())
}
