package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
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
    val continueWatching: List<WatchHistoryItem> = emptyList(),
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
        // loadHomeContent will be called from the UI with context
    }

    fun loadHomeContent(context: Context) {
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

                // Get watch history
                val watchHistory = repository.getWatchHistory(context)

                // Update initial state with primary content first to free up the UI thread
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trendingTvShows = trendingTv,
                    trendingMovies = trendingMovies,
                    continueWatching = watchHistory,
                    latestMovies = topRatedMovies.take(30),
                    topTvShows = topRatedTv.take(30),
                    selectedItem = trendingTv.firstOrNull() ?: trendingMovies.firstOrNull()
                )

                // Load secondary content in background to avoid blocking the UI
                // Use parallel async calls to load all content simultaneously for faster display
                viewModelScope.launch {
                    val oscarWinnersDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/oscar_winners_2026.json").getOrNull() ?: emptyList()
                    }
                    // Networks and companies loaded in parallel with other content
                    val companiesNetworksDeferred = async {
                        repository.getGitHubCompaniesNetworks(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/companies_networks.json").getOrNull()
                    }
                    val hallmarkMoviesDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/hallmark_movies.json").getOrNull() ?: emptyList()
                    }
                    val trueStoryMoviesDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/true_story_movies.json").getOrNull() ?: emptyList()
                    }
                    val bestSitcomsDeferred = async {
                        repository.getGitHubTvShowList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/best_sitcoms.json").getOrNull() ?: emptyList()
                    }
                    val bestClassicsDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/best_classics.json").getOrNull() ?: emptyList()
                    }
                    val spyMoviesDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/cia_mossad_spies.json").getOrNull() ?: emptyList()
                    }
                    val stathamMoviesDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/jason_statham_movies.json").getOrNull() ?: emptyList()
                    }
                    val timeTravelMoviesDeferred = async {
                        repository.getGitHubMovieList(context, "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/time_travel_movies.json").getOrNull() ?: emptyList()
                    }


                    // Await all results in parallel
                    val oscarWinners2026 = oscarWinnersDeferred.await()
                    val hallmarkMovies = hallmarkMoviesDeferred.await()
                    val trueStoryMovies = trueStoryMoviesDeferred.await()
                    val bestSitcoms = bestSitcomsDeferred.await()
                    val bestClassics = bestClassicsDeferred.await()
                    val spyMovies = spyMoviesDeferred.await()
                    val stathamMovies = stathamMoviesDeferred.await()
                    val timeTravelMovies = timeTravelMoviesDeferred.await()
                    val companiesNetworks = companiesNetworksDeferred.await()

                    // Process networks and companies
                    val networks = companiesNetworks?.networks
                        ?.filter { it.logoPath != null }
                        ?.map { NetworkItem(it.id, it.name, it.logoPath, "network") }
                        ?: emptyList()

                    val companies = companiesNetworks?.companies
                        ?.filter { it.logoPath != null }
                        ?.map { NetworkItem(it.id, it.name, it.logoPath, "company") }
                        ?: emptyList()

                    // Update UI with all secondary content at once
                    _uiState.value = _uiState.value.copy(
                        oscarWinners2026 = oscarWinners2026,
                        hallmarkMovies = hallmarkMovies,
                        trueStoryMovies = trueStoryMovies,
                        bestSitcoms = bestSitcoms,
                        bestClassics = bestClassics,
                        spyMovies = spyMovies,
                        stathamMovies = stathamMovies,
                        timeTravelMovies = timeTravelMovies,
                        popularNetworks = networks,
                        popularCompanies = companies
                    )
                }
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
