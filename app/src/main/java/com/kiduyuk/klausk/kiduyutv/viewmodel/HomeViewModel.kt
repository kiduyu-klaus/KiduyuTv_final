package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val oscarMovies: List<Movie> = emptyList(),
    val oscarWinners2026: List<Movie> = emptyList(),
    val hallmarkMovies: List<Movie> = emptyList(),
    val trueStoryMovies: List<Movie> = emptyList(),
    val bestSitcoms: List<TvShow> = emptyList(),
    val bestClassics: List<Movie> = emptyList(),
    val myList: List<MyListItem> = emptyList(),
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

                val networkIds = listOf(213, 2739, 1024, 49, 4330, 2552, 453, 3353, 174, 88, 67, 4, 6, 2, 16, 19, 71, 26, 214)
                val companyIds = listOf(2, 420, 174, 33, 3, 1, 34, 5, 4, 521, 6704, 923, 25, 12, 9383)

                val networkDetailsDeferred = networkIds.map { id ->
                    async { repository.getNetworkDetails(id).getOrNull() }
                }
                val companyDetailsDeferred = companyIds.map { id ->
                    async { repository.getCompanyDetails(id).getOrNull() }
                }

                val oscarMoviesDeferred = async { repository.getOscarMovies() }

                val oscarWinners2026Deferred = async {
                    repository.getTraktListMovies("visualcortex", "2026-oscar-winners",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                val hallmarkMoviesDeferred = async {
                    repository.getTraktListMovies("trakt_kodi_321", "hallmark-movies",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                val trueStoryMoviesDeferred = async {
                    repository.getTraktListMovies("benfranklin", "based-on-a-true-story",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                val bestSitcomsDeferred = async {
                    repository.getTraktListTvShows("fidel-cb", "best-sitcoms",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                val bestClassicsDeferred = async {
                    repository.getTraktListMovies("captainnapalm", "1001-greatest-movies-of-all-time",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                val trendingTv = trendingTvDeferred.await().getOrNull() ?: emptyList()
                val trendingMovies = trendingMoviesDeferred.await().getOrNull() ?: emptyList()
                val nowPlaying = nowPlayingDeferred.await().getOrNull() ?: emptyList()
                val topRatedMovies = topRatedMoviesDeferred.await().getOrNull() ?: emptyList()
                val topRatedTv = topRatedTvDeferred.await().getOrNull() ?: emptyList()

                val networks = networkDetailsDeferred.awaitAll()
                    .filterNotNull()
                    .filter { it.logoPath != null }
                    .map { NetworkItem(it.id, it.name, it.logoPath, "network") }

                val companies = companyDetailsDeferred.awaitAll()
                    .filterNotNull()
                    .filter { it.logoPath != null }
                    .map { NetworkItem(it.id, it.name, it.logoPath, "company") }

                _uiState.value = HomeUiState(
                    isLoading = false,
                    trendingTvShows = trendingTv,
                    trendingMovies = trendingMovies,
                    continueWatching = nowPlaying.take(10),
                    popularNetworks = networks,
                    popularCompanies = companies,
                    latestMovies = topRatedMovies.take(10),
                    topTvShows = topRatedTv.take(10),
                    oscarMovies = oscarMoviesDeferred.await().getOrNull()?.mapNotNull { it.toMovie() } ?: emptyList(),
                    oscarWinners2026 = oscarWinners2026Deferred.await().getOrNull() ?: emptyList(),
                    hallmarkMovies = hallmarkMoviesDeferred.await().getOrNull() ?: emptyList(),
                    trueStoryMovies = trueStoryMoviesDeferred.await().getOrNull() ?: emptyList(),
                    bestSitcoms = bestSitcomsDeferred.await().getOrNull() ?: emptyList(),
                    bestClassics = bestClassicsDeferred.await().getOrNull() ?: emptyList(),
                    selectedItem = trendingTv.firstOrNull() ?: trendingMovies.firstOrNull()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }

    fun selectItem(item: Any) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    fun setLastClickedItemId(id: Int?) {
        _uiState.value = _uiState.value.copy(lastClickedItemId = id)
    }
}
