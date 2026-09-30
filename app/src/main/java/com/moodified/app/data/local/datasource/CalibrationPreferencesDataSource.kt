package com.moodified.app.data.local.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moodified.app.domain.model.inference.CalibrationWeights
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calibrationDataStore by preferencesDataStore(name = "calibration_prefs")
private val KEY_WEIGHTS = stringPreferencesKey("calibration_weights")

@Singleton
class CalibrationPreferencesDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun flow(): Flow<CalibrationWeights> =
            context.calibrationDataStore.data.map { prefs ->
                prefs[KEY_WEIGHTS]
                    ?.let { runCatching { Json.decodeFromString<CalibrationWeights>(it) }.getOrNull() }
                    ?: CalibrationWeights()
            }

        suspend fun update(weights: CalibrationWeights) {
            context.calibrationDataStore.edit { prefs ->
                prefs[KEY_WEIGHTS] = Json.encodeToString(weights)
            }
        }
    }
