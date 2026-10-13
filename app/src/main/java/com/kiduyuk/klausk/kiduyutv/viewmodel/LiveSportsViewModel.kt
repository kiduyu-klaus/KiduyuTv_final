package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsEvent
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsSource
import com.kiduyuk.klausk.kiduyutv.data.repository.LiveSportsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveSportsUiState(
    val isLoading: Boolean = false,
    val events: List<LiveSportsEvent> = emptyList(),
    val error: String? = null
)

/** Keeps PPV and TIMST requests independent so changing tabs never blocks the other feed. */
class LiveSportsViewModel : ViewModel() {
    private val _ppvUiState = MutableStateFlow(LiveSportsUiState())
    val ppvUiState: StateFlow<LiveSportsUiState> = _ppvUiState.asStateFlow()

    private val _timstUiState = MutableStateFlow(LiveSportsUiState())
    val timstUiState: StateFlow<LiveSportsUiState> = _timstUiState.asStateFlow()

    fun load(source: LiveSportsSource, forceRefresh: Boolean = false) {
        val target = if (source == LiveSportsSource.PPV) _ppvUiState else _timstUiState
        val current = target.value
        if (current.isLoading || (!forceRefresh && current.events.isNotEmpty())) return
        target.value = current.copy(isLoading = true, error = null)
        viewModelScope.launch {
            LiveSportsRepository.fetch(source).fold(
                onSuccess = { events ->
                    target.value = LiveSportsUiState(events = events)
                },
                onFailure = { error ->
                    target.value = current.copy(isLoading = false, error = error.message ?: "Unable to load live sports")
                }
            )
        }
    }
}
