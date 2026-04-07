package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.NotificationHelper
import com.kiduyuk.klausk.kiduyutv.util.WatchHistoryEnricher
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
    val trendingMoviesThisWeek: List<Movie> = emptyList(),
    val nowPlayingMovies: List<Movie> = emptyList(),
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
    val type: String,
    val voteAverage: Double = 0.0,
    val character: String? = null,
    val knownForDepartment: String? = null
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
                val trendingMoviesThisWeekDeferred = async { repository.getTrendingMoviesThisWeek() }
                val nowPlayingDeferred = async { repository.getNowPlayingMovies() }
                val topRatedMoviesDeferred = async { repository.getTopRatedMovies() }
                val topRatedTvDeferred = async { repository.getTopRatedTvShows() }

                val trendingTv = trendingTvDeferred.await().getOrNull() ?: emptyList()
                val trendingMovies = trendingMoviesDeferred.await().getOrNull() ?: emptyList()
                val trendingMoviesThisWeek = trendingMoviesThisWeekDeferred.await().getOrNull() ?: emptyList()
                val nowPlaying = nowPlayingDeferred.await().getOrNull() ?: emptyList()
                val topRatedMovies = topRatedMoviesDeferred.await().getOrNull() ?: emptyList()
                val topRatedTv = topRatedTvDeferred.await().getOrNull() ?: emptyList()

                // Get watch history
                val watchHistory = repository.getWatchHistory(context)

                // Sort all content rows by vote average (highest first)
                val sortedTrendingTv = trendingTv.sortedByDescending { it.voteAverage }
                val sortedTrendingMovies = trendingMovies.sortedByDescending { it.voteAverage }
                val sortedTrendingMoviesThisWeek = trendingMoviesThisWeek.sortedByDescending { it.voteAverage }
                val sortedNowPlaying = nowPlaying // No sorting for now playing movies
                val sortedWatchHistory = watchHistory.sortedByDescending { it.voteAverage }
                val sortedTopRatedMovies = topRatedMovies.take(30).sortedByDescending { it.voteAverage }
                val sortedTopRatedTv = topRatedTv.take(30).sortedByDescending { it.voteAverage }

                // Update initial state with primary content first to free up the UI thread
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trendingTvShows = sortedTrendingTv,
                    trendingMovies = sortedTrendingMovies,
                    trendingMoviesThisWeek = sortedTrendingMoviesThisWeek,
                    nowPlayingMovies = sortedNowPlaying,
                    continueWatching = sortedWatchHistory,
                    latestMovies = sortedTopRatedMovies,
                    topTvShows = sortedTopRatedTv,
                    selectedItem = sortedNowPlaying.firstOrNull() ?: sortedTrendingTv.firstOrNull() ?: sortedTrendingMovies.firstOrNull()
                )

                // Trigger a random recommendation notification if we have content
                triggerRandomRecommendation(context, sortedTrendingMovies, sortedTrendingTv)

                // Refresh watch history images and enrich items with TMDB details in background
                // This ensures "Continue Watching" row displays complete and accurate information
                // even for items that were saved with incomplete data.
                //
                // The WatchHistoryEnricher handles two types of updates:
                // 1. refreshAllWatchHistoryImages - Always fetches and overwrites poster/backdrop
                //    images from TMDB to ensure users see the most current images
                // 2. enrichAllMissingItems - Fills in other missing fields (title, overview, etc.)
                //
                // Items with null/empty vote average or overview will be refreshed when displayed
                viewModelScope.launch {
                    try {
                        // First, refresh all images from TMDB to ensure fresh images
                        WatchHistoryEnricher.refreshAllWatchHistoryImages(context)

                        // Then, enrich items with missing TMDB details (including voteAverage and overview)
                        WatchHistoryEnricher.enrichAllMissingItems(context)

                        // Refresh the watch history after enrichment to get updated items
                        val enrichedWatchHistory = WatchHistoryEnricher.getEnrichedWatchHistory(context)
                        _uiState.value = _uiState.value.copy(continueWatching = enrichedWatchHistory)
                    } catch (e: Exception) {
                        // Log error but don't fail the entire home screen load
                        android.util.Log.e("HomeViewModel", "Error enriching watch history: ${e.message}")
                    }
                }

                // Also enrich items in Continue Watching row that have null/empty vote average or overview
                // This ensures that items displayed in Continue Watching on any screen (Home, Movies, TV Shows)
                // will have their details fetched and updated
                viewModelScope.launch {
                    try {
                        // Get items that specifically need voteAverage or overview enrichment
                        val itemsWithMissingDetails = WatchHistoryEnricher.getItemsWithMissingDetails(context)
                        for (item in itemsWithMissingDetails) {
                            // Only enrich items that are in the continue watching list
                            if (sortedWatchHistory.any { it.id == item.id && it.isTv == (item.mediaType == "tv") }) {
                                WatchHistoryEnricher.enrichSingleItem(context, item.id, item.mediaType)
                                android.util.Log.i("HomeViewModel", "Enriched continue watching item: ${item.id} (${item.mediaType})")
                            }
                        }
                        // Refresh the watch history after individual enrichment
                        val enrichedWatchHistory = WatchHistoryEnricher.getEnrichedWatchHistory(context)
                        _uiState.value = _uiState.value.copy(continueWatching = enrichedWatchHistory)
                    } catch (e: Exception) {
                        // Log error but don't fail the entire home screen load
                        android.util.Log.e("HomeViewModel", "Error enriching continue watching items: ${e.message}")
                    }
                }

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

                    // Sort secondary content by vote average (highest first)
                    val sortedOscarWinners = oscarWinners2026.sortedByDescending { it.voteAverage }
                    val sortedHallmark = hallmarkMovies.sortedByDescending { it.voteAverage }
                    val sortedTrueStory = trueStoryMovies.sortedByDescending { it.voteAverage }
                    val sortedSitcoms = bestSitcoms.sortedByDescending { it.voteAverage }
                    val sortedClassics = bestClassics.sortedByDescending { it.voteAverage }
                    val sortedSpyMovies = spyMovies.sortedByDescending { it.voteAverage }
                    val sortedStathamMovies = stathamMovies.sortedByDescending { it.voteAverage }
                    val sortedTimeTravel = timeTravelMovies.sortedByDescending { it.voteAverage }

                    // Update UI with all secondary content at once
                    _uiState.value = _uiState.value.copy(
                        oscarWinners2026 = sortedOscarWinners,
                        hallmarkMovies = sortedHallmark,
                        trueStoryMovies = sortedTrueStory,
                        bestSitcoms = sortedSitcoms,
                        bestClassics = sortedClassics,
                        spyMovies = sortedSpyMovies,
                        stathamMovies = sortedStathamMovies,
                        timeTravelMovies = sortedTimeTravel,
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

    /**
     * Selects a random movie or TV show from the provided lists and posts a notification.
     */
    private fun triggerRandomRecommendation(
        context: Context,
        movies: List<com.kiduyuk.klausk.kiduyutv.data.model.Movie>,
        tvShows: List<com.kiduyuk.klausk.kiduyutv.data.model.TvShow>
    ) {
        val allMedia = mutableListOf<Pair<Any, String>>()
        allMedia.addAll(movies.map { it to "movie" })
        allMedia.addAll(tvShows.map { it to "tv" })

        if (allMedia.isNotEmpty()) {
            val randomItem = allMedia.random()
            val media = randomItem.first
            val type = randomItem.second

            if (type == "movie") {
                val movie = media as com.kiduyuk.klausk.kiduyutv.data.model.Movie
                NotificationHelper.postMediaNotification(
                    context,
                    movie.id,
                    movie.title ?: "Unknown Movie",
                    "movie",
                    movie.overview ?: "Check out this movie on Kiduyu TV!"
                )
            } else {
                val tvShow = media as com.kiduyuk.klausk.kiduyutv.data.model.TvShow
                NotificationHelper.postMediaNotification(
                    context,
                    tvShow.id,
                    tvShow.name ?: "Unknown TV Show",
                    "tv",
                    tvShow.overview ?: "Check out this TV show on Kiduyu TV!"
                )
            }
        }
    }
}