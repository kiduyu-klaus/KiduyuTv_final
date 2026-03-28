package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val trendingTvShows: List<TvShow> = emptyList(),
    val trendingMovies: List<Movie> = emptyList(),
    val continueWatching: List<Movie> = emptyList(),
    val popularNetworks: List<NetworkItem> = emptyList(),
    val popularCompanies: List<NetworkItem> = emptyList(),
    val latestMovies: List<Movie> = emptyList(),
    val topTvShows: List<TvShow> = emptyList(),
    val oscarWinners2026: List<Movie> = emptyList(),
    val hallmarkMovies: List<Movie> = emptyList(),
    val trueStoryMovies: List<Movie> = emptyList(),
    val bestSitcoms: List<TvShow> = emptyList(),
    val bestClassics: List<Movie> = emptyList(),
    val spyMovies: List<Movie> = emptyList(),
    val stathamMovies: List<Movie> = emptyList(),
    val timeTravelMovies: List<Movie> = emptyList(),
    val selectedItem: Any? = null,
    val lastClickedItemId: Int? = null,
    val error: String? = null
)

/**
 * Represents a network or company item displayed in the home screen.
 */
data class NetworkItem(
    val id: Int,
    val name: String,
    val logoPath: String?,
    val type: String
)

/**
 * Represents an item in the user's personal list.
 */
data class MyListItem(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val type: String
)

/**
 * ViewModel for the home screen, responsible for fetching and managing home screen data.
 */
class HomeViewModel : ViewModel() {

    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeContent()
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val trendingTvDeferred = async { repository.getTrendingTvToday() }
                val trendingMoviesDeferred = async { repository.getTrendingMoviesToday() }
                val nowPlayingDeferred = async { repository.getNowPlayingMovies() }
                val topRatedMoviesDeferred = async { repository.getTopRatedMovies() }
                val topRatedTvDeferred = async { repository.getTopRatedTvShows() }

                val trendingTv = trendingTvDeferred.await().getOrNull() ?: emptyList()
                val trendingMovies = trendingMoviesDeferred.await().getOrNull() ?: emptyList()
                val nowPlaying = nowPlayingDeferred.await().getOrNull() ?: emptyList()
                val topRatedMovies = topRatedMoviesDeferred.await().getOrNull() ?: emptyList()
                val topRatedTv = topRatedTvDeferred.await().getOrNull() ?: emptyList()

                // Update initial state with primary content first to free up the UI thread
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trendingTvShows = trendingTv,
                    trendingMovies = trendingMovies,
                    continueWatching = nowPlaying.take(10),
                    latestMovies = topRatedMovies.take(10),
                    topTvShows = topRatedTv.take(10),
                    selectedItem = trendingTv.firstOrNull() ?: trendingMovies.firstOrNull()
                )

                // Load secondary content sequentially or in smaller batches to avoid OOM
                val oscarWinners2026 = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/oscar_winners_2026.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(oscarWinners2026 = oscarWinners2026)

                val hallmarkMovies = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/hallmark_movies.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(hallmarkMovies = hallmarkMovies)

                val trueStoryMovies = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klein/KiduyuTv_final/refs/heads/main/lists/true_story_movies.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(trueStoryMovies = trueStoryMovies)

                val bestSitcoms = repository.getGitHubTvShowList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/best_sitcoms.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(bestSitcoms = bestSitcoms)

                val bestClassics = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/best_classics.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(bestClassics = bestClassics)

                val spyMovies = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/cia_mossad_spies.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(spyMovies = spyMovies)

                val stathamMovies = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/jason_statham_movies.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(stathamMovies = stathamMovies)

                val timeTravelMovies = repository.getGitHubMovieList("https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/time_travel_movies.json").getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(timeTravelMovies = timeTravelMovies)

                // Load networks and companies last
                val networkIds = listOf(213, 2739, 1024, 49, 4330, 2552, 453, 3353, 174, 88, 67, 4, 6, 2, 16, 19, 71, 26, 214)
                val networks = networkIds.map { id -> repository.getNetworkDetails(id).getOrNull() }
                    .filterNotNull()
                    .filter { it.logoPath != null }
                    .map { NetworkItem(it.id, it.name, it.logoPath, "network") }
                _uiState.value = _uiState.value.copy(popularNetworks = networks)

                val companyIds = listOf(2, 420, 174, 33, 3, 1, 34, 5, 4, 521, 6704, 923, 25, 12, 9383)
                val companies = companyIds.map { id -> repository.getCompanyDetails(id).getOrNull() }
                    .filterNotNull()
                    .filter { it.logoPath != null }
                    .map { NetworkItem(it.id, it.name, it.logoPath, "company") }
                _uiState.value = _uiState.value.copy(popularCompanies = companies)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }

    fun onItemSelected(item: Any?) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    fun onItemClicked(itemId: Int) {
        _uiState.value = _uiState.value.copy(lastClickedItemId = itemId)
    }
}
