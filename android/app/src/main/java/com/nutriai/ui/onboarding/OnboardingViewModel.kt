package com.nutriai.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.GoalTimeline
import com.nutriai.data.remote.dto.ProfileDto
import com.nutriai.data.remote.dto.ProfileUpsertRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** Existing profile used to pre-fill the form when editing (null on first setup). */
    val prefill: ProfileDto? = null,
    val prefillLoaded: Boolean = false,
    /** Live safe-pace preview for the chosen target + timeframe (null until requested). */
    val timeline: GoalTimeline? = null,
    val timelineLoading: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** In-flight preview request; cancelled whenever a newer target/timeframe supersedes it. */
    private var timelineJob: Job? = null

    init {
        // Pre-fill from the saved profile so "Edit profile" isn't a full re-entry.
        viewModelScope.launch {
            val existing = repository.getProfile().getOrNull()
            _state.value = _state.value.copy(prefill = existing, prefillLoaded = true)
        }
    }

    /**
     * Fetch the safe-pace preview for [targetWeightKg] over [weeks]. Cancels any prior request so
     * the latest input wins. Callers debounce (see the screen's LaunchedEffect); a failed call just
     * clears the loading flag and leaves the last good preview untouched.
     */
    fun previewTimeline(targetWeightKg: Double, weeks: Int) {
        timelineJob?.cancel()
        _state.value = _state.value.copy(timelineLoading = true)
        timelineJob = viewModelScope.launch {
            val result = repository.goalTimeline(targetWeightKg, weeks)
            result.onSuccess { tl ->
                _state.value = _state.value.copy(timelineLoading = false, timeline = tl)
            }.onFailure {
                _state.value = _state.value.copy(timelineLoading = false)
            }
        }
    }

    /** Drop the preview when the inputs become incomplete (e.g. timeframe deselected). */
    fun clearTimeline() {
        timelineJob?.cancel()
        _state.value = _state.value.copy(timeline = null, timelineLoading = false)
    }

    fun save(request: ProfileUpsertRequest, onSuccess: () -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = repository.saveProfile(request)
            if (result.isSuccess) {
                _state.value = _state.value.copy(loading = false, error = null)
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    loading = false,
                    error = result.exceptionOrNull()?.message ?: "Could not save profile",
                )
            }
        }
    }
}
