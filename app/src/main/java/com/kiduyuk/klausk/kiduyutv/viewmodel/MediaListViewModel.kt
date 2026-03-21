package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the MediaListScreen.
 * @param isLoading Indicates if data is being fetched.
 * @param title The title to display on the screen (e.g., Company Name).
 * @param movies List of movies, if any.
 * @param tvShows List of TV shows, if any.
 * @param error Error message if fetching fails.
 */
data class MediaListUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val movies: List<Movie> = emptyList(),
    val tvShows: List<TvShow> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for displaying a list of movies or TV shows filtered by company or network.
 */
class MediaListViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(MediaListUiState())
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    /**
     * Loads movies for a specific production company.
     * @param companyId The TMDB company ID.
     * @param companyName The name of the company to display as title.
     */
    fun loadMoviesByCompany(companyId: Int, companyName: String) {
        viewModelScope.launch {
            _uiState.value = MediaListUiState(isLoading = true, title = companyName)
            repository.getMoviesByCompany(companyId)
                .onSuccess { movies ->
                    _uiState.value = _uiState.value.copy(isLoading = false, movies = movies)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                }
        }
    }

    /**
     * Loads TV shows for a specific network.
     * @param networkId The TMDB network ID.
     * @param networkName The name of the network to display as title.
     */
    fun loadTvShowsByNetwork(networkId: Int, networkName: String) {
        viewModelScope.launch {
            _uiState.value = MediaListUiState(isLoading = true, title = networkName)
            repository.getTvShowsByNetwork(networkId)
                .onSuccess { tvShows ->
                    _uiState.value = _uiState.value.copy(isLoading = false, tvShows = tvShows)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                }
        }
    }
}
