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
 * @param isLoading Indicates if data is currently being loaded.
 * @param trendingTvShows List of trending TV shows.
 * @param trendingMovies List of trending movies.
 * @param continueWatching List of movies the user can continue watching.
 * @param popularNetworks List of popular networks.
 * @param popularCompanies List of popular companies.
 * @param latestMovies List of latest movies.
 * @param topTvShows List of top-rated TV shows.
 * @param oscarMovies List of Oscar-nominated/winning movies.
 * @param myList List of items added to the user's personal list.
 * @param selectedItem The currently selected movie or TV show, used for the hero section.
 * @param error An error message if data loading fails.
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
    val myList: List<MyListItem> = emptyList(),
    val selectedItem: Any? = null,
    val lastClickedItemId: Int? = null,
    val error: String? = null
)

/**
 * Represents a network or company item displayed in the home screen.
 * @param id The unique identifier of the network or company.
 * @param name The name of the network or company.
 * @param logoPath The path to the logo image, if available.
 * @param type The type of item, e.g., "network" or "company".
 */
data class NetworkItem(
    val id: Int,
    val name: String,
    val logoPath: String?,
    val type: String
)

/**
 * Represents an item in the user's personal list.
 * @param id The unique identifier of the movie or TV show.
 * @param title The title of the movie or TV show.
 * @param posterPath The path to the poster image, if available.
 * @param type The type of item, e.g., "movie" or "tv".
 */
data class MyListItem(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val type: String
)

/**
 * ViewModel for the home screen, responsible for fetching and managing home screen data.
 * It exposes a [HomeUiState] to the UI layer.
 */
class HomeViewModel : ViewModel() {

    // Repository for fetching data from TMDB API.
    private val repository = TmdbRepository()

    // MutableStateFlow to hold and update the UI state.
    private val _uiState = MutableStateFlow(HomeUiState())
    // Publicly exposed StateFlow for UI observation.
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Load initial home content when the ViewModel is created.
        loadHomeContent()
    }

    /**
     * Loads all necessary content for the home screen concurrently.
     * Uses parallel execution with proper error handling for optimal performance.
     * Updates the [HomeUiState] with loading status, fetched data, or error messages.
     */
    fun loadHomeContent() {
        viewModelScope.launch {
            // Set loading state to true and clear any previous errors.
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Parallel fetch all independent API calls using async for maximum concurrency
                val trendingTvDeferred = async { repository.getTrendingTvToday() }
                val trendingMoviesDeferred = async { repository.getTrendingMoviesToday() }
                val nowPlayingDeferred = async { repository.getNowPlayingMovies() }
                val topRatedMoviesDeferred = async { repository.getTopRatedMovies() }
                val topRatedTvDeferred = async { repository.getTopRatedTvShows() }
                val popularMoviesDeferred = async { repository.getPopularMovies() }

                // Fetch networks and companies in parallel with main content
                val networkIds = listOf(213, 2739, 1024, 49, 4330, 2552, 453, 3353, 174, 88, 67, 4, 6, 2, 16, 19, 71, 26, 214)
                val companyIds = listOf(2, 420, 174, 33, 3, 1, 34, 5, 4, 521, 6704, 923, 25, 12, 9383)

                // Network and company details fetched in parallel
                val networkDetailsDeferred = networkIds.map { id ->
                    async { repository.getNetworkDetails(id).getOrNull() }
                }
                val companyDetailsDeferred = companyIds.map { id ->
                    async { repository.getCompanyDetails(id).getOrNull() }
                }

                // Fetch Oscar movies
                val oscarMoviesDeferred = async { repository.getOscarMovies() }

                // Fetch 2026 Oscar winners from Trakt
                val oscarWinners2026Deferred = async {
                    repository.getTraktListMovies(
                        userSlug = "visualcortex",
                        listSlug = "2026-oscar-winners",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

//                val hallmarkMoviesDeferred = async {
//                    repository.getTraktListMovies(
//                        userSlug = "trakt_kodi_321",
//                        listSlug = "hallmark-movies",
//                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
//                    )
//                }

                // Fetch Hallmark Movies from Trakt
                val hallmarkMoviesDeferred = async {
                    repository.getTraktListMovies(
                        userSlug = "trakt_kodi_321",
                        listSlug = "hallmark-movies",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                // Fetch Movies Based on True Stories from Trakt
                val trueStoryMoviesDeferred = async {
                    repository.getTraktListMovies(
                        userSlug = "benfranklin",
                        listSlug = "based-on-a-true-story",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                // Fetch Best Sitcoms from Trakt
                val bestSitcomsDeferred = async {
                    repository.getTraktListTvShows(
                        userSlug = "fidel-cb",
                        listSlug = "best-sitcoms",
                        clientId = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
                    )
                }

                // Await all results and provide empty lists as fallback
                val trendingTv = trendingTvDeferred.await().getOrNull() ?: emptyList()
                val trendingMovies = trendingMoviesDeferred.await().getOrNull() ?: emptyList()
                val nowPlaying = nowPlayingDeferred.await().getOrNull() ?: emptyList()
                val topRatedMovies = topRatedMoviesDeferred.await().getOrNull() ?: emptyList()
                val topRatedTv = topRatedTvDeferred.await().getOrNull() ?: emptyList()

                // Process network and company details
                val networks = networkDetailsDeferred.awaitAll()
                    .filterNotNull()
                    .filter { it.logoPath != null }
                    .map { NetworkItem(it.id, it.name, it.logoPath, "network") }

                val companies = companyDetailsDeferred.awaitAll()
                    .filterNotNull()
                    .filter { it.logoPath != null }
                    .map { NetworkItem(it.id, it.name, it.logoPath, "company") }

                // Update the UI state with the fetched data.
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
                    myList = emptyList(), // MyList is initially empty.
                    // Set the initial selected item for the hero section.
                    selectedItem = trendingTv.firstOrNull() ?: trendingMovies.firstOrNull()
                )
            } catch (e: Exception) {
                // Handle any errors during data fetching.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }

    /**
     * Sets the currently selected item for the hero section.
     * @param item The movie or TV show object to be selected.
     */
    fun selectItem(item: Any) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    /**
     * Sets the ID of the last clicked item to restore focus later.
     * @param id The ID of the clicked movie or TV show.
     */
    fun setLastClickedItemId(id: Int?) {
        _uiState.value = _uiState.value.copy(lastClickedItemId = id)
    }

    /**
     * Adds a movie or TV show to the user's personal list.
     * @param item The movie or TV show object to add.
     * @param type The type of item, e.g., "movie" or "tv".
     */
    fun addToMyList(item: Any, type: String) {
        val currentList = _uiState.value.myList.toMutableList()
        when (item) {
            is Movie -> currentList.add(MyListItem(item.id, item.title, item.posterPath, type))
            is TvShow -> currentList.add(MyListItem(item.id, item.name, item.posterPath, type))
        }
        _uiState.value = _uiState.value.copy(myList = currentList)
    }

    /**
     * Removes an item from the user's personal list.
     * @param itemId The ID of the item to remove.
     */
    fun removeFromMyList(itemId: Int) {
        val currentList = _uiState.value.myList.toMutableList()
        currentList.removeAll { it.id == itemId }
        _uiState.value = _uiState.value.copy(myList = currentList)
    }
}
