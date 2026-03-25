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
 * @param isLoadingMore Indicates if more data is being fetched (pagination).
 * @param title The title to display on the screen (e.g., Company Name).
 * @param movies List of movies, if any.
 * @param tvShows List of TV shows, if any.
 * @param currentPage The current page number.
 * @param totalPages The total number of pages available.
 * @param error Error message if fetching fails.
 */
data class MediaListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val title: String = "",
    val movies: List<Movie> = emptyList(),
    val tvShows: List<TvShow> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val error: String? = null
)

/**
 * ViewModel for displaying a list of movies or TV shows filtered by company or network.
 */
class MediaListViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(MediaListUiState())
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    private var currentCompanyId: Int = 0
    private var currentNetworkId: Int = 0

    /**
     * Loads movies for a specific production company.
     * @param companyId The TMDB company ID.
     * @param companyName The name of the company to display as title.
     */
    fun loadMoviesByCompany(companyId: Int, companyName: String) {
        currentCompanyId = companyId
        viewModelScope.launch {
            _uiState.value = MediaListUiState(isLoading = true, title = companyName)
            repository.getMoviesByCompany(companyId)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movies = response.results,
                        currentPage = response.page,
                        totalPages = response.totalPages
                    )
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
        currentNetworkId = networkId
        viewModelScope.launch {
            _uiState.value = MediaListUiState(isLoading = true, title = networkName)
            repository.getTvShowsByNetwork(networkId)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        tvShows = response.results,
                        currentPage = response.page,
                        totalPages = response.totalPages
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                }
        }
    }

    /**
     * Loads the next page of movies for the current company.
     */
    fun loadMoreMovies() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || currentState.currentPage >= currentState.totalPages) {
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoadingMore = true)
            val nextPage = currentState.currentPage + 1
            repository.getMoviesByCompany(currentCompanyId, nextPage)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        movies = currentState.movies + response.results,
                        currentPage = response.page,
                        totalPages = response.totalPages
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    /**
     * Loads the next page of TV shows for the current network.
     */
    fun loadMoreTvShows() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || currentState.currentPage >= currentState.totalPages) {
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoadingMore = true)
            val nextPage = currentState.currentPage + 1
            repository.getTvShowsByNetwork(currentNetworkId, nextPage)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        tvShows = currentState.tvShows + response.results,
                        currentPage = response.page,
                        totalPages = response.totalPages
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }
}
