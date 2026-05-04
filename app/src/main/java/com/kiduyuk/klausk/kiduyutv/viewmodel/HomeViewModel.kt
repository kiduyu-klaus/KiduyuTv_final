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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val christianMovies: List<Movie> = emptyList(),
    val bibleMovies: List<Movie> = emptyList(),
    val bestSitcoms: List<TvShow> = emptyList(),
    val bestClassics: List<Movie> = emptyList(),
    val spyMovies: List<Movie> = emptyList(),
    val stathamMovies: List<Movie> = emptyList(),
    val timeTravelMovies: List<Movie> = emptyList(),
    val timeTravelTvShows: List<TvShow> = emptyList(),
    val christianTvShows: List<TvShow> = emptyList(),
    val doctorWhoSpecials: List<Movie> = emptyList(),
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
 * Optimized with parallel request batching for faster content loading.
 */
class HomeViewModel : ViewModel() {

    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // loadHomeContent will be called from the UI with context
    }

    /**
     * Loads all home screen content using optimized parallel request batching.
     * Uses async/awaitAll pattern for maximum concurrency and minimal loading time.
     */
    fun loadHomeContent(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // ========================================================================
                // PHASE 1: PARALLEL CORE API REQUESTS
                // ========================================================================
                // Launch all core API calls in parallel using coroutineScope
                // This ensures all requests run concurrently, reducing total load time
                val (primaryResults, watchHistory) = coroutineScope {
                    // Launch all primary content fetches in parallel
                    val deferreds = listOf(
                        async { "trendingTv" to repository.getTrendingTvToday() },
                        async { "trendingMovies" to repository.getTrendingMoviesToday() },
                        async { "trendingMoviesThisWeek" to repository.getTrendingMoviesThisWeek() },
                        async { "nowPlaying" to repository.getNowPlayingMovies() },
                        async { "topRatedMovies" to repository.getTopRatedMovies() },
                        async { "topRatedTv" to repository.getTopRatedTvShows() },
                        async { "timeTravelTv" to repository.getTimeTravelTvShows() }
                    )

                    // Await all results with awaitAll() for maximum efficiency
                    val results = deferreds.awaitAll()

                    // Get watch history (runs in parallel with API calls)
                    val history = repository.getWatchHistory(context)

                    // Process results into a map for easy access
                    val resultMap = results.associate { it.first to it.second.getOrNull() ?: emptyList<Any>() }
                    resultMap to history
                }

                // Extract results with proper typing
                val trendingTv: List<TvShow> = (primaryResults["trendingTv"] as? List<*>)?.filterIsInstance<TvShow>() ?: emptyList()
                val trendingMovies: List<Movie> = (primaryResults["trendingMovies"] as? List<*>)?.filterIsInstance<Movie>() ?: emptyList()
                val trendingMoviesThisWeek: List<Movie> = (primaryResults["trendingMoviesThisWeek"] as? List<*>)?.filterIsInstance<Movie>() ?: emptyList()
                val nowPlaying: List<Movie> = (primaryResults["nowPlaying"] as? List<*>)?.filterIsInstance<Movie>() ?: emptyList()
                val topRatedMovies: List<Movie> = (primaryResults["topRatedMovies"] as? List<*>)?.filterIsInstance<Movie>() ?: emptyList()
                val topRatedTv: List<TvShow> = (primaryResults["topRatedTv"] as? List<*>)?.filterIsInstance<TvShow>() ?: emptyList()
                val timeTravelTv: List<TvShow> = (primaryResults["timeTravelTv"] as? List<*>)?.filterIsInstance<TvShow>() ?: emptyList()

                // Sort all content rows by vote average (highest first)
                val sortedTrendingTv = trendingTv.sortedByDescending { it.voteAverage }
                val sortedTrendingMovies = trendingMovies.sortedByDescending { it.voteAverage }
                val sortedTrendingMoviesThisWeek = trendingMoviesThisWeek.sortedByDescending { it.voteAverage }
                val sortedWatchHistory = watchHistory.sortedByDescending { it.lastWatched }
                val sortedTopRatedMovies = topRatedMovies.take(30).sortedByDescending { it.voteAverage }
                val sortedTopRatedTv = topRatedTv.take(30).sortedByDescending { it.voteAverage }

                // Update initial state with primary content first to free up the UI thread
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        trendingTvShows = sortedTrendingTv,
                        trendingMovies = sortedTrendingMovies,
                        trendingMoviesThisWeek = sortedTrendingMoviesThisWeek,
                        nowPlayingMovies = nowPlaying,
                        continueWatching = sortedWatchHistory,
                        latestMovies = sortedTopRatedMovies,
                        topTvShows = sortedTopRatedTv,
                        timeTravelTvShows = timeTravelTv,
                        selectedItem = nowPlaying.firstOrNull() ?: sortedTrendingTv.firstOrNull() ?: sortedTrendingMovies.firstOrNull()
                    )
                }

                // Trigger a random recommendation notification if we have content
                triggerRandomRecommendation(context, sortedTrendingMovies, sortedTrendingTv)

                // ========================================================================
                // PHASE 2: BACKGROUND WATCH HISTORY ENRICHMENT
                // ========================================================================
                // Refresh watch history images and enrich items with TMDB details in background
                // This ensures "Continue Watching" row displays complete and accurate information
                viewModelScope.launch {
                    enrichWatchHistory(context, sortedWatchHistory)
                }

                // ========================================================================
                // PHASE 3: PARALLEL SECONDARY CONTENT (GITHUB LISTS)
                // ========================================================================
                // Load secondary content in background using optimized batch requests
                // Uses awaitAll() for maximum parallelization of GitHub content fetches
                viewModelScope.launch {
                    loadSecondaryContent(context)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An error occurred loading home content"
                    )
                }
            }
        }
    }

    /**
     * Enriches watch history items with fresh TMDB data in the background.
     * Runs independently to not block the primary content display.
     */
    private suspend fun enrichWatchHistory(context: Context, currentWatchHistory: List<WatchHistoryItem>) {
        try {
            // First, refresh all images from TMDB to ensure fresh images
            WatchHistoryEnricher.refreshAllWatchHistoryImages(context)

            // Then, enrich items with missing TMDB details
            WatchHistoryEnricher.enrichAllMissingItems(context)

            // Get enriched watch history
            val enrichedWatchHistory = WatchHistoryEnricher.getEnrichedWatchHistory(context)
            _uiState.update { it.copy(continueWatching = enrichedWatchHistory) }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Error enriching watch history: ${e.message}")
        }

        try {
            // Also enrich items with missing voteAverage or overview
            val itemsWithMissingDetails = WatchHistoryEnricher.getItemsWithMissingDetails(context)
            for (item in itemsWithMissingDetails) {
                if (currentWatchHistory.any { it.id == item.id && it.isTv == (item.mediaType == "tv") }) {
                    WatchHistoryEnricher.enrichSingleItem(context, item.id, item.mediaType)
                }
            }

            // Refresh after individual enrichment
            val enrichedWatchHistory = WatchHistoryEnricher.getEnrichedWatchHistory(context)
            _uiState.update { it.copy(continueWatching = enrichedWatchHistory) }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Error enriching continue watching items: ${e.message}")
        }
    }

    /**
     * Loads all secondary content (GitHub lists) in parallel using awaitAll().
     * Optimized batch loading for maximum concurrency.
     */
    private suspend fun loadSecondaryContent(context: Context) = coroutineScope {
        // Base URL for GitHub raw content
        val baseUrl = "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/"

        // Define all GitHub list requests with their repository methods
        val githubRequests = listOf(
            "oscarWinners2026" to { repository.getGitHubMovieList(context, "${baseUrl}oscar_winners_2026.json") },
            "hallmarkMovies" to { repository.getGitHubMovieList(context, "${baseUrl}hallmark_movies.json") },
            "trueStoryMovies" to { repository.getGitHubMovieList(context, "${baseUrl}true_story_movies.json") },
            "bestSitcoms" to { repository.getGitHubTvShowList(context, "${baseUrl}best_sitcoms.json") },
            "bestClassics" to { repository.getGitHubMovieList(context, "${baseUrl}best_classics.json") },
            "spyMovies" to { repository.getGitHubMovieList(context, "${baseUrl}cia_mossad_spies.json") },
            "stathamMovies" to { repository.getGitHubMovieList(context, "${baseUrl}jason_statham_movies.json") },
            "timeTravelMovies" to { repository.getGitHubMovieList(context, "${baseUrl}time_travel_movies.json") },
            "christianMovies" to { repository.getGitHubMovieList(context, "${baseUrl}christian_movies.json") },
            "bibleMovies" to { repository.getGitHubMovieList(context, "${baseUrl}movies_from_the_bible.json") },
            "christianTvShows" to { repository.getGitHubTvShowList(context, "${baseUrl}christian_tv_shows.json") },
            "doctorWhoSpecials" to { repository.getGitHubMovieList(context, "${baseUrl}doctor_who_specials.json") },
            "companiesNetworks" to { repository.getGitHubCompaniesNetworks(context, "${baseUrl}companies_networks.json") }
        )

        // Launch all requests in parallel
        val deferredResults = githubRequests.map { (name, request) ->
            async { name to request().getOrNull() }
        }

        // Await all results simultaneously
        val allResults = deferredResults.awaitAll().toMap()

        // Process and sort all movie lists
        val processMovies: (String) -> List<Movie> = { key ->
            (allResults[key] as? List<*>)?.filterIsInstance<Movie>() ?: emptyList()
        }

        val processTvShows: (String) -> List<TvShow> = { key ->
            (allResults[key] as? List<*>)?.filterIsInstance<TvShow>() ?: emptyList()
        }

        val oscarWinners2026 = processMovies("oscarWinners2026").sortedByDescending { it.voteAverage }
        val hallmarkMovies = processMovies("hallmarkMovies").sortedByDescending { it.voteAverage }
        val trueStoryMovies = processMovies("trueStoryMovies").sortedByDescending { it.voteAverage }
        val bestClassics = processMovies("bestClassics").sortedByDescending { it.voteAverage }
        val spyMovies = processMovies("spyMovies").sortedByDescending { it.voteAverage }
        val stathamMovies = processMovies("stathamMovies").sortedByDescending { it.voteAverage }
        val timeTravelMovies = processMovies("timeTravelMovies").sortedByDescending { it.voteAverage }
        val christianMovies = processMovies("christianMovies").sortedByDescending { it.voteAverage }
        val bibleMovies = processMovies("bibleMovies").sortedByDescending { it.voteAverage }
        val doctorWhoSpecials = processMovies("doctorWhoSpecials").sortedByDescending { it.voteAverage }

        val bestSitcoms = processTvShows("bestSitcoms").sortedByDescending { it.voteAverage }
        val christianTvShows = processTvShows("christianTvShows").sortedByDescending { it.voteAverage }

        // Process companies and networks
        val companiesNetworks = allResults["companiesNetworks"]
        val networks = (companiesNetworks as? com.kiduyuk.klausk.kiduyutv.data.model.CompaniesNetworksResponse)
            ?.networks
            ?.filter { it.logoPath != null }
            ?.map { NetworkItem(it.id, it.name, it.logoPath, "network") }
            ?: emptyList()

        val companies = (companiesNetworks as? com.kiduyuk.klausk.kiduyutv.data.model.CompaniesNetworksResponse)
            ?.companies
            ?.filter { it.logoPath != null }
            ?.map { NetworkItem(it.id, it.name, it.logoPath, "company") }
            ?: emptyList()

        // Update UI with all secondary content at once
        _uiState.update {
            it.copy(
                oscarWinners2026 = oscarWinners2026,
                hallmarkMovies = hallmarkMovies,
                trueStoryMovies = trueStoryMovies,
                bestSitcoms = bestSitcoms,
                bestClassics = bestClassics,
                spyMovies = spyMovies,
                stathamMovies = stathamMovies,
                timeTravelMovies = timeTravelMovies,
                christianMovies = christianMovies,
                bibleMovies = bibleMovies,
                christianTvShows = christianTvShows,
                doctorWhoSpecials = doctorWhoSpecials,
                popularNetworks = networks,
                popularCompanies = companies
            )
        }
    }

    fun onItemSelected(item: Any?) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun onItemClicked(itemId: Int) {
        _uiState.update { it.copy(lastClickedItemId = itemId) }
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