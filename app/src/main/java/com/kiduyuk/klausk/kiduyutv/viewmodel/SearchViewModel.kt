package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.SearchResult
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the search screen.
 * @param query The current search query entered by the user.
 * @param results The list of search results.
 * @param isLoading Whether a search is currently in progress.
 * @param error The error message if the search failed, null otherwise.
 * @param hasSearched Whether the user has performed a search at least once.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

/**
 * ViewModel for the search functionality.
 * Handles debounced search queries and manages search state.
 */
class SearchViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Updates the search query and triggers a debounced search.
     * The search is debounced by 500ms to avoid excessive API calls.
     * @param query The new search query.
     */
    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)

        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                isLoading = false,
                error = null,
                hasSearched = false
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce
            performSearch(query)
        }
    }

    /**
     * Performs the actual search API call.
     * @param query The search query to send to the API.
     */
    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        repository.searchMulti(query)
            .onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    results = results,
                    isLoading = false,
                    hasSearched = true
                )
            }
            .onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    error = exception.message ?: "An error occurred while searching",
                    isLoading = false,
                    hasSearched = true
                )
            }
    }

    /**
     * Clears the search query and results.
     */
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = SearchUiState()
    }
}
