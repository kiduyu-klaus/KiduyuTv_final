package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.SearchResult
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.SearchHistoryManager
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
 * @param recentSearches The list of recent search queries.
 * @param isLoading Whether a search is currently in progress.
 * @param error The error message if the search failed, null otherwise.
 * @param hasSearched Whether the user has performed a search at least once.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

/**
 * Factory for creating SearchViewModel with application context.
 */
class SearchViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * ViewModel for the search functionality.
 * Handles debounced search queries, manages search state, and tracks recent searches.
 */
class SearchViewModel(private val application: Application) : ViewModel() {

    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Initialize SearchHistoryManager with application context
        SearchHistoryManager.init(application.applicationContext)

        // Load recent searches on initialization
        loadRecentSearches()
    }

    /**
     * Loads recent searches from storage.
     */
    private fun loadRecentSearches() {
        val recentSearches = SearchHistoryManager.getRecentSearches()
        _uiState.value = _uiState.value.copy(recentSearches = recentSearches)
    }

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
     * Also saves the query to recent searches on successful search.
     * @param query The search query to send to the API.
     */
    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        repository.searchMulti(query)
            .onSuccess { results ->
                // Save to recent searches on successful search
                SearchHistoryManager.addRecentSearch(query)

                // Reload recent searches to reflect the update
                val recentSearches = SearchHistoryManager.getRecentSearches()

                _uiState.value = _uiState.value.copy(
                    results = results,
                    recentSearches = recentSearches,
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
        _uiState.value = SearchUiState(recentSearches = SearchHistoryManager.getRecentSearches())
    }

    /**
     * Clears all recent searches.
     */
    fun clearRecentSearches() {
        SearchHistoryManager.clearRecentSearches()
        _uiState.value = _uiState.value.copy(recentSearches = emptyList())
    }

    /**
     * Removes a specific recent search.
     * @param query The search query to remove.
     */
    fun removeRecentSearch(query: String) {
        SearchHistoryManager.removeRecentSearch(query)
        _uiState.value = _uiState.value.copy(recentSearches = SearchHistoryManager.getRecentSearches())
    }

    /**
     * Sets the search query from a recent search item.
     * @param query The search query to set.
     */
    fun setSearchQuery(query: String) {
        onQueryChange(query)
    }
}