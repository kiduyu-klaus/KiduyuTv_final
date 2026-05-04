package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.kiduyuk.klausk.kiduyutv.data.model.CompaniesNetworksResponse
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
                coroutineScope {
                    // Launch all primary content fetches in parallel
                    val trendingTvDeferred = async { repository.getTrendingTvToday() }
                    val trendingMoviesDeferred = async { repository.getTrendingMoviesToday() }
                    val trendingMoviesThisWeekDeferred = async { repository.getTrendingMoviesThisWeek() }
                    val nowPlayingDeferred = async { repository.getNowPlayingMovies() }
                    val topRatedMoviesDeferred = async { repository.getTopRatedMovies() }
                    val topRatedTvDeferred = async { repository.getTopRatedTvShows() }
                    val timeTravelTvDeferred = async { repository.getTimeTravelTvShows() }

                    // Await all results
                    val trendingTv = trendingTvDeferred.await().getOrNull() ?: emptyList()
                    val trendingMovies = trendingMoviesDeferred.await().getOrNull() ?: emptyList()
                    val trendingMoviesThisWeek = trendingMoviesThisWeekDeferred.await().getOrNull() ?: emptyList()
                    val nowPlaying = nowPlayingDeferred.await().getOrNull() ?: emptyList()
                    val topRatedMovies = topRatedMoviesDeferred.await().getOrNull() ?: emptyList()
                    val topRatedTv = topRatedTvDeferred.await().getOrNull() ?: emptyList()
                    val timeTravelTv = timeTravelTvDeferred.await().getOrNull() ?: emptyList()

                    // Get watch history (runs in parallel with API calls)
                    val watchHistory = repository.getWatchHistory(context)

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
                    launch {
                        enrichWatchHistory(context, sortedWatchHistory)
                    }

                    // ========================================================================
                    // PHASE 3: PARALLEL SECONDARY CONTENT (GITHUB LISTS)
                    // ========================================================================
                    // Load secondary content in background using optimized batch requests
                    launch {
                        loadSecondaryContent(context)
                    }
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
                if (currentWatchHistory.any { watchItem -> watchItem.id == item.id && watchItem.isTv == (item.mediaType == "tv") }) {
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
    private suspend fun loadSecondaryContent(context: Context) {
        // Base URL for GitHub raw content
        val baseUrl = "https://raw.githubusercontent.com/kiduyu-klaus/KiduyuTv_final/refs/heads/main/lists/"

        // Launch all requests in parallel using async
        val oscarWinnersDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}oscar_winners_2026.json").getOrNull() ?: emptyList<Movie>() }
        val hallmarkMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}hallmark_movies.json").getOrNull() ?: emptyList<Movie>() }
        val trueStoryMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}true_story_movies.json").getOrNull() ?: emptyList<Movie>() }
        val bestSitcomsDeferred = async { repository.getGitHubTvShowList(context, "${baseUrl}best_sitcoms.json").getOrNull() ?: emptyList<TvShow>() }
        val bestClassicsDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}best_classics.json").getOrNull() ?: emptyList<Movie>() }
        val spyMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}cia_mossad_spies.json").getOrNull() ?: emptyList<Movie>() }
        val stathamMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}jason_statham_movies.json").getOrNull() ?: emptyList<Movie>() }
        val timeTravelMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}time_travel_movies.json").getOrNull() ?: emptyList<Movie>() }
        val christianMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}christian_movies.json").getOrNull() ?: emptyList<Movie>() }
        val bibleMoviesDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}movies_from_the_bible.json").getOrNull() ?: emptyList<Movie>() }
        val christianTvShowsDeferred = async { repository.getGitHubTvShowList(context, "${baseUrl}christian_tv_shows.json").getOrNull() ?: emptyList<TvShow>() }
        val doctorWhoSpecialsDeferred = async { repository.getGitHubMovieList(context, "${baseUrl}doctor_who_specials.json").getOrNull() ?: emptyList<Movie>() }
        val companiesNetworksDeferred = async { repository.getGitHubCompaniesNetworks(context, "${baseUrl}companies_networks.json").getOrNull() }

        // Await all results simultaneously
        val oscarWinners2026 = oscarWinnersDeferred.await()
        val hallmarkMovies = hallmarkMoviesDeferred.await()
        val trueStoryMovies = trueStoryMoviesDeferred.await()
        val bestSitcoms = bestSitcomsDeferred.await()
        val bestClassics = bestClassicsDeferred.await()
        val spyMovies = spyMoviesDeferred.await()
        val stathamMovies = stathamMoviesDeferred.await()
        val timeTravelMovies = timeTravelMoviesDeferred.await()
        val christianMovies = christianMoviesDeferred.await()
        val bibleMovies = bibleMoviesDeferred.await()
        val christianTvShows = christianTvShowsDeferred.await()
        val doctorWhoSpecials = doctorWhoSpecialsDeferred.await()
        val companiesNetworks = companiesNetworksDeferred.await()

        // Sort all movie lists by vote average
        val sortedOscarWinners = oscarWinners2026.sortedByDescending { it.voteAverage }
        val sortedHallmark = hallmarkMovies.sortedByDescending { it.voteAverage }
        val sortedTrueStory = trueStoryMovies.sortedByDescending { it.voteAverage }
        val sortedClassics = bestClassics.sortedByDescending { it.voteAverage }
        val sortedSpyMovies = spyMovies.sortedByDescending { it.voteAverage }
        val sortedStathamMovies = stathamMovies.sortedByDescending { it.voteAverage }
        val sortedTimeTravel = timeTravelMovies.sortedByDescending { it.voteAverage }
        val sortedChristianMovies = christianMovies.sortedByDescending { it.voteAverage }
        val sortedBibleMovies = bibleMovies.sortedByDescending { it.voteAverage }
        val sortedDoctorWhoSpecials = doctorWhoSpecials.sortedByDescending { it.voteAverage }

        val sortedSitcoms = bestSitcoms.sortedByDescending { it.voteAverage }
        val sortedChristianTvShows = christianTvShows.sortedByDescending { it.voteAverage }

        // Process companies and networks
        val networksList: List<NetworkItem> = if (companiesNetworks != null) {
            companiesNetworks.networks
                .filter { network: com.kiduyuk.klausk.kiduyutv.data.model.GitHubNetwork -> network.logoPath != null }
                .map { network: com.kiduyuk.klausk.kiduyutv.data.model.GitHubNetwork -> 
                    NetworkItem(network.id, network.name, network.logoPath, "network") 
                }
        } else {
            emptyList()
        }

        val companiesList: List<NetworkItem> = if (companiesNetworks != null) {
            companiesNetworks.companies
                .filter { company: com.kiduyuk.klausk.kiduyutv.data.model.GitHubCompany -> company.logoPath != null }
                .map { company: com.kiduyuk.klausk.kiduyutv.data.model.GitHubCompany -> 
                    NetworkItem(company.id, company.name, company.logoPath, "company") 
                }
        } else {
            emptyList()
        }

        // Update UI with all secondary content at once
        _uiState.update {
            it.copy(
                oscarWinners2026 = sortedOscarWinners,
                hallmarkMovies = sortedHallmark,
                trueStoryMovies = sortedTrueStory,
                bestSitcoms = sortedSitcoms,
                bestClassics = sortedClassics,
                spyMovies = sortedSpyMovies,
                stathamMovies = sortedStathamMovies,
                timeTravelMovies = sortedTimeTravel,
                christianMovies = sortedChristianMovies,
                bibleMovies = sortedBibleMovies,
                christianTvShows = sortedChristianTvShows,
                doctorWhoSpecials = sortedDoctorWhoSpecials,
                popularNetworks = networksList,
                popularCompanies = companiesList
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
        movies: List<Movie>,
        tvShows: List<TvShow>
    ) {
        val allMedia = mutableListOf<Pair<Any, String>>()
        allMedia.addAll(movies.map { movie -> movie to "movie" })
        allMedia.addAll(tvShows.map { tv -> tv to "tv" })

        if (allMedia.isNotEmpty()) {
            val randomItem = allMedia.random()
            val media = randomItem.first
            val type = randomItem.second

            if (type == "movie") {
                val movie = media as Movie
                NotificationHelper.postMediaNotification(
                    context,
                    movie.id,
                    movie.title ?: "Unknown Movie",
                    "movie",
                    movie.overview ?: "Check out this movie on Kiduyu TV!"
                )
            } else {
                val tvShow = media as TvShow
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