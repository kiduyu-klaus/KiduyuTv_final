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
 * Represents the UI state for the home screen.
 * @param isLoading Indicates if data is currently being loaded.
 * @param trendingTvShows List of trending TV shows.
 * @param trendingMovies List of trending movies.
 * @param continueWatching List of movies the user can continue watching.
 * @param popularNetworks List of popular networks.
 * @param popularCompanies List of popular companies.
 * @param latestMovies List of latest movies.
 * @param topTvShows List of top-rated TV shows.
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
    val myList: List<MyListItem> = emptyList(),
    val selectedItem: Any? = null,
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
     * Updates the [HomeUiState] with loading status, fetched data, or error messages.
     */
    fun loadHomeContent() {
        viewModelScope.launch {
            // Set loading state to true and clear any previous errors.
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Fetch all data concurrently using the repository.
                val trendingTvDeferred = repository.getTrendingTvToday()
                val trendingMoviesDeferred = repository.getTrendingMoviesToday()
                val nowPlayingDeferred = repository.getNowPlayingMovies()
                val topRatedMoviesDeferred = repository.getTopRatedMovies()
                val topRatedTvDeferred = repository.getTopRatedTvShows()
                val popularMoviesDeferred = repository.getPopularMovies()

                // Await results and provide empty lists as fallback.
                val trendingTv = trendingTvDeferred.getOrNull() ?: emptyList()
                val trendingMovies = trendingMoviesDeferred.getOrNull() ?: emptyList()
                val nowPlaying = nowPlayingDeferred.getOrNull() ?: emptyList()
                val topRatedMovies = topRatedMoviesDeferred.getOrNull() ?: emptyList()
                val topRatedTv = topRatedTvDeferred.getOrNull() ?: emptyList()
                val popularMovies = popularMoviesDeferred.getOrNull() ?: emptyList()

                // Manually create a list of popular networks.
                val networks = listOf(
                    NetworkItem(213, "Netflix", null, "network"),
                    NetworkItem(1024, "Apple TV+", null, "network"),
                    NetworkItem(1025, "Prime Video", null, "network"),
                    NetworkItem(158, "Disney+", null, "network"),
                    NetworkItem(2739, "Hulu", null, "network"),
                    NetworkItem(4451, "Peacock", null, "network"),
                    NetworkItem(3284, "Crunchyroll", null, "network"),
                    NetworkItem(283, "HBO Max", null, "network")
                )

                // Manually create a list of popular companies.
                val companies = listOf(
                    NetworkItem(420, "Marvel Studios", null, "company"),
                    NetworkItem(109051, "Bad Robot", null, "company"),
                    NetworkItem(38109, "Legendary Pictures", null, "company"),
                    NetworkItem(174, "Warner Bros. Pictures", null, "company"),
                    NetworkItem(2, "Marvel Studios", null, "company"),
                    NetworkItem(429, "董完整", null, "company"),
                    NetworkItem(76043, "Revolution Studios", null, "company"),
                    NetworkItem(11461, "George Lucas", null, "company")
                )

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
