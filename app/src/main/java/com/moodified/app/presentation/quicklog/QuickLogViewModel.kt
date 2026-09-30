package com.moodified.app.presentation.quicklog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.usecase.inference.FeedbackCalibrationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class QuickLogStep { VALENCE, AROUSAL, SUCCESS }

data class QuickLogUiState(
    val step: QuickLogStep = QuickLogStep.VALENCE,
    val editingEntryId: Long? = null,
    val selectedValence: Valence? = null,
    val selectedArousal: Arousal? = null,
    val note: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isTimestampCustomized: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val isEditMode: Boolean get() = editingEntryId != null
}

sealed interface QuickLogEvent {
    data class SaveError(val message: String) : QuickLogEvent

    data object EntryNotFound : QuickLogEvent

    data object Deleted : QuickLogEvent
}

@HiltViewModel
class QuickLogViewModel
    @Inject
    constructor(
        private val repository: MoodRepository,
        private val feedbackCalibration: FeedbackCalibrationUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(QuickLogUiState())
        val uiState: StateFlow<QuickLogUiState> = _uiState.asStateFlow()

        private val _events = Channel<QuickLogEvent>(Channel.CONFLATED)
        val events = _events.receiveAsFlow()

        // Called by QuickLogSheet on appear when opened in add mode.
        fun startAdd() {
            _uiState.value = QuickLogUiState()
        }

        // Called by QuickLogSheet on appear when opened in edit mode. Loads the target
        // entry and populates state. If the id no longer resolves (e.g., deleted from
        // another surface), emits EntryNotFound so the sheet can close gracefully.
        fun startEdit(id: Long) {
            _uiState.value = QuickLogUiState(editingEntryId = id, isSaving = true)
            viewModelScope.launch {
                runCatching { repository.getEntryById(id) }
                    .onSuccess { entry ->
                        if (entry == null) {
                            _events.send(QuickLogEvent.EntryNotFound)
                            return@onSuccess
                        }
                        _uiState.value =
                            QuickLogUiState(
                                step = QuickLogStep.VALENCE,
                                editingEntryId = entry.id,
                                selectedValence = entry.valence,
                                selectedArousal = entry.arousal,
                                note = entry.note.orEmpty(),
                                timestamp = entry.timestamp,
                                isTimestampCustomized = true,
                                isSaving = false,
                            )
                    }
                    .onFailure {
                        _uiState.update { it.copy(isSaving = false) }
                        _events.send(QuickLogEvent.SaveError(it.message ?: "Couldn't load entry"))
                    }
            }
        }

        fun selectValence(valence: Valence) {
            _uiState.update { it.copy(selectedValence = valence) }
        }

        fun goToArousal() {
            if (_uiState.value.selectedValence == null) return
            _uiState.update { it.copy(step = QuickLogStep.AROUSAL) }
        }

        fun goBackToValence() {
            _uiState.update { it.copy(step = QuickLogStep.VALENCE) }
        }

        fun selectArousal(arousal: Arousal) {
            _uiState.update { it.copy(selectedArousal = arousal) }
        }

        fun updateNote(text: String) {
            _uiState.update { it.copy(note = text) }
        }

        // Custom timestamp picked by the user via the When chip. Locks
        // isTimestampCustomized so save() doesn't overwrite it with now().
        fun updateTimestamp(timestamp: LocalDateTime) {
            _uiState.update { it.copy(timestamp = timestamp, isTimestampCustomized = true) }
        }

        fun resetTimestampToNow() {
            _uiState.update { it.copy(timestamp = LocalDateTime.now(), isTimestampCustomized = false) }
        }

        fun save() {
            var alreadySaving = false
            _uiState.update { current ->
                if (current.isSaving) {
                    alreadySaving = true
                    current
                } else {
                    current.copy(isSaving = true)
                }
            }
            if (alreadySaving) return

            val state = _uiState.value
            val valence =
                state.selectedValence ?: run {
                    _uiState.update { it.copy(isSaving = false) }
                    return
                }
            val arousal =
                state.selectedArousal ?: run {
                    _uiState.update { it.copy(isSaving = false) }
                    return
                }

            // If the user never customized the time, use now() at save-time (not the
            // instant the sheet opened) so a moment's delay doesn't record a stale time.
            val effectiveTimestamp =
                if (state.isTimestampCustomized) state.timestamp else LocalDateTime.now()

            if (effectiveTimestamp.isAfter(LocalDateTime.now())) {
                _uiState.update { it.copy(isSaving = false) }
                viewModelScope.launch {
                    _events.send(QuickLogEvent.SaveError("Can't log a mood in the future"))
                }
                return
            }

            val trimmedNote = state.note.trim().takeIf { it.isNotEmpty() }
            // Capture entry for calibration before the coroutine scope
            val entryForCalibration = MoodEntry(
                valence = valence,
                arousal = arousal,
                note = trimmedNote,
                timestamp = effectiveTimestamp,
                isManual = true,
            )

            viewModelScope.launch {
                runCatching {
                    if (state.editingEntryId != null) {
                        repository.updateEntry(entryForCalibration.copy(id = state.editingEntryId))
                    } else {
                        repository.insertEntry(entryForCalibration)
                    }
                }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, step = QuickLogStep.SUCCESS) }
                    // Fire-and-forget: calibration failure must not surface to the user
                    viewModelScope.launch {
                        runCatching { feedbackCalibration(entryForCalibration) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(QuickLogEvent.SaveError(e.message ?: "Failed to save entry"))
                }
            }
        }

        fun deleteCurrent() {
            val id = _uiState.value.editingEntryId ?: return
            var alreadyDeleting = false
            _uiState.update { current ->
                if (current.isDeleting) {
                    alreadyDeleting = true
                    current
                } else {
                    current.copy(isDeleting = true)
                }
            }
            if (alreadyDeleting) return

            viewModelScope.launch {
                runCatching { repository.deleteEntry(id) }
                    .onSuccess {
                        _uiState.update { it.copy(isDeleting = false) }
                        _events.send(QuickLogEvent.Deleted)
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isDeleting = false) }
                        _events.send(QuickLogEvent.SaveError(e.message ?: "Couldn't delete entry"))
                    }
            }
        }

        fun reset() {
            _uiState.value = QuickLogUiState()
        }
    }
