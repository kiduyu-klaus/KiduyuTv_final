package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowEntity
import com.kiduyuk.klausk.kiduyutv.data.model.*
import com.kiduyuk.klausk.kiduyutv.data.model.CompaniesNetworksResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository class that handles data operations, specifically fetching data from the TMDB API.
 * Now uses Room database for caching and watch history management.
 * 
 * DEBUG_MODE: Set to true to enable detailed logging for all API operations
 */
class TmdbRepository {

    private val api = ApiClient.tmdbApiService
    private val gson = Gson()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "TmdbRepository"
        
        // Debug mode flag - set to true to see detailed logs
        private const val DEBUG_MODE = true

        // Cache type constants for different content categories
        const val CACHE_TYPE_TRENDING = "trending"
        const val CACHE_TYPE_POPULAR = "popular"
        const val CACHE_TYPE_TOP_RATED = "top_rated"
        const val CACHE_TYPE_NOW_PLAYING = "now_playing"
        const val CACHE_TYPE_GITHUB_LIST = "github_list"
        const val CACHE_TYPE_TIME_TRAVEL = "time_travel"

        @Volatile
        private var githubHttpClient: OkHttpClient? = null

        fun getGitHubOkHttpClient(context: Context): OkHttpClient {
            return githubHttpClient ?: synchronized(this) {
                githubHttpClient ?: createGitHubOkHttpClient(context).also { githubHttpClient = it }
            }
        }

        private fun createGitHubOkHttpClient(context: Context): OkHttpClient {
            if (DEBUG_MODE) {
                Log.d(TAG, "=== Creating GitHub OkHttpClient ===")
                Log.d(TAG, "Cache dir: ${context.cacheDir}")
            }
            
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
        
        private fun logDebug(message: String) {
            if (DEBUG_MODE) {
                Log.d(TAG, message)
            }
        }
        
        private fun logError(message: String, throwable: Throwable? = null) {
            Log.e(TAG, message, throwable)
        }
    }
    
    // ========== Trending Content ==========

    /** Fetches trending TV shows for today. */
    suspend fun getTrendingTvToday(): Result<List<TvShow>> = runCatching {
        logDebug(">>> getTrendingTvToday() - Fetching trending TV shows for today")
        try {
            val response = api.getTrendingTvToday()
            logDebug("<<< getTrendingTvToday() - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! getTrendingTvToday() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches trending movies for today. */
    suspend fun getTrendingMoviesToday(): Result<List<Movie>> = runCatching {
        logDebug(">>> getTrendingMoviesToday() - Fetching trending movies for today")
        try {
            val response = api.getTrendingMoviesToday()
            logDebug("<<< getTrendingMoviesToday() - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! getTrendingMoviesToday() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches trending TV shows for the week. */
    suspend fun getTrendingTvThisWeek(): Result<List<TvShow>> = runCatching {
        logDebug(">>> getTrendingTvThisWeek() - Fetching trending TV shows for this week")
        try {
            val response = api.getTrendingTvThisWeek()
            logDebug("<<< getTrendingTvThisWeek() - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! getTrendingTvThisWeek() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches trending movies for the week. */
    suspend fun getTrendingMoviesThisWeek(): Result<List<Movie>> = runCatching {
        logDebug(">>> getTrendingMoviesThisWeek() - Fetching trending movies for this week")
        try {
            val response = api.getTrendingMoviesThisWeek()
            logDebug("<<< getTrendingMoviesThisWeek() - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! getTrendingMoviesThisWeek() - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== General Content ==========

    /** Fetches movies currently playing in theaters. */
    suspend fun getNowPlayingMovies(): Result<List<Movie>> = runCatching {
        logDebug(">>> getNowPlayingMovies() - Fetching movies now playing")
        try {
            val response = api.getNowPlayingMovies()
            logDebug("<<< getNowPlayingMovies() - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! getNowPlayingMovies() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches top-rated movies. */
    suspend fun getTopRatedMovies(): Result<List<Movie>> = runCatching {
        logDebug(">>> getTopRatedMovies() - Fetching top rated movies")
        try {
            val response = api.getTopRatedMovies()
            logDebug("<<< getTopRatedMovies() - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! getTopRatedMovies() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches top-rated TV shows. */
    suspend fun getTopRatedTvShows(): Result<List<TvShow>> = runCatching {
        logDebug(">>> getTopRatedTvShows() - Fetching top rated TV shows")
        try {
            val response = api.getTopRatedTvShows()
            logDebug("<<< getTopRatedTvShows() - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! getTopRatedTvShows() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches popular movies. */
    suspend fun getPopularMovies(): Result<List<Movie>> = runCatching {
        logDebug(">>> getPopularMovies() - Fetching popular movies")
        try {
            val response = api.getPopularMovies()
            logDebug("<<< getPopularMovies() - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! getPopularMovies() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches popular TV shows. */
    suspend fun getPopularTvShows(): Result<List<TvShow>> = runCatching {
        logDebug(">>> getPopularTvShows() - Fetching popular TV shows")
        try {
            val response = api.getPopularTvShows()
            logDebug("<<< getPopularTvShows() - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! getPopularTvShows() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches TV shows with the "time travel" keyword. */
    suspend fun getTimeTravelTvShows(): Result<List<TvShow>> = runCatching {
        logDebug(">>> getTimeTravelTvShows() - Fetching time travel TV shows")
        try {
            val response = api.getTimeTravelTvShows()
            logDebug("<<< getTimeTravelTvShows() - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! getTimeTravelTvShows() - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Detail Endpoints ==========

    /** Fetches detailed information for a specific movie. */
    suspend fun getMovieDetail(movieId: Int): Result<MovieDetail> = runCatching {
        logDebug(">>> getMovieDetail($movieId) - Fetching movie details")
        try {
            val response = api.getMovieDetail(movieId)
            logDebug("<<< getMovieDetail($movieId) - Success: ${response.title}")
            response
        } catch (e: Exception) {
            logError("!!! getMovieDetail($movieId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches detailed information for a specific TV show. */
    suspend fun getTvShowDetail(tvId: Int): Result<TvShowDetail> = runCatching {
        logDebug(">>> getTvShowDetail($tvId) - Fetching TV show details")
        try {
            val response = api.getTvShowDetail(tvId)
            logDebug("<<< getTvShowDetail($tvId) - Success: ${response.name}")
            response
        } catch (e: Exception) {
            logError("!!! getTvShowDetail($tvId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches detailed information for a specific season of a TV show. */
    suspend fun getSeasonDetail(tvId: Int, seasonNumber: Int): Result<SeasonDetail> = runCatching {
        logDebug(">>> getSeasonDetail($tvId, $seasonNumber) - Fetching season details")
        try {
            val response = api.getSeasonDetail(tvId, seasonNumber)
            logDebug("<<< getSeasonDetail($tvId, $seasonNumber) - Success: ${response.name}")
            response
        } catch (e: Exception) {
            logError("!!! getSeasonDetail($tvId, $seasonNumber) - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Genre Endpoints ==========

    /** Fetches the list of available movie genres. */
    suspend fun getMovieGenres(): Result<List<Genre>> = runCatching {
        logDebug(">>> getMovieGenres() - Fetching movie genres")
        try {
            val response = api.getMovieGenres()
            logDebug("<<< getMovieGenres() - Success: ${response.genres.size} genres")
            response.genres
        } catch (e: Exception) {
            logError("!!! getMovieGenres() - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches the list of available TV show genres. */
    suspend fun getTvGenres(): Result<List<Genre>> = runCatching {
        logDebug(">>> getTvGenres() - Fetching TV genres")
        try {
            val response = api.getTvGenres()
            logDebug("<<< getTvGenres() - Success: ${response.genres.size} genres")
            response.genres
        } catch (e: Exception) {
            logError("!!! getTvGenres() - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Company/Network Endpoints ==========

    /** Fetches movies filtered by a specific production company. */
    suspend fun getMoviesByCompany(companyId: Int, page: Int = 1): Result<MovieResponse> = runCatching {
        logDebug(">>> getMoviesByCompany($companyId, page=$page) - Fetching movies by company")
        try {
            val response = api.getMoviesByCompany(companyId, page = page)
            logDebug("<<< getMoviesByCompany($companyId) - Success: ${response.results.size} movies")
            response
        } catch (e: Exception) {
            logError("!!! getMoviesByCompany($companyId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches TV shows filtered by a specific network. */
    suspend fun getTvShowsByNetwork(networkId: Int, page: Int = 1): Result<TvShowResponse> = runCatching {
        logDebug(">>> getTvShowsByNetwork($networkId, page=$page) - Fetching TV shows by network")
        try {
            val response = api.getTvShowsByNetwork(networkId, page = page)
            logDebug("<<< getTvShowsByNetwork($networkId) - Success: ${response.results.size} TV shows")
            response
        } catch (e: Exception) {
            logError("!!! getTvShowsByNetwork($networkId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches movies filtered by a specific genre. */
    suspend fun getMoviesByGenre(genreId: Int, page: Int = 1): Result<MovieResponse> = runCatching {
        logDebug(">>> getMoviesByGenre($genreId, page=$page) - Fetching movies by genre")
        try {
            val response = api.getMoviesByGenre(genreId, page = page)
            logDebug("<<< getMoviesByGenre($genreId) - Success: ${response.results.size} movies")
            response
        } catch (e: Exception) {
            logError("!!! getMoviesByGenre($genreId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches TV shows filtered by a specific genre. */
    suspend fun getTvShowsByGenre(genreId: Int, page: Int = 1): Result<TvShowResponse> = runCatching {
        logDebug(">>> getTvShowsByGenre($genreId, page=$page) - Fetching TV shows by genre")
        try {
            val response = api.getTvShowsByGenre(genreId, page = page)
            logDebug("<<< getTvShowsByGenre($genreId) - Success: ${response.results.size} TV shows")
            response
        } catch (e: Exception) {
            logError("!!! getTvShowsByGenre($genreId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches details for a specific network. */
    suspend fun getNetworkDetails(networkId: Int): Result<Network> = runCatching {
        logDebug(">>> getNetworkDetails($networkId) - Fetching network details")
        try {
            val response = api.getNetworkDetails(networkId)
            logDebug("<<< getNetworkDetails($networkId) - Success: ${response.name}")
            response
        } catch (e: Exception) {
            logError("!!! getNetworkDetails($networkId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches details for a specific production company. */
    suspend fun getCompanyDetails(companyId: Int): Result<ProductionCompany> = runCatching {
        logDebug(">>> getCompanyDetails($companyId) - Fetching company details")
        try {
            val response = api.getCompanyDetails(companyId)
            logDebug("<<< getCompanyDetails($companyId) - Success: ${response.name}")
            response
        } catch (e: Exception) {
            logError("!!! getCompanyDetails($companyId) - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Video Endpoints ==========

    /** Fetches videos (trailers, etc.) for a specific movie. */
    suspend fun getMovieVideos(movieId: Int): Result<List<Video>> = runCatching {
        logDebug(">>> getMovieVideos($movieId) - Fetching movie videos")
        try {
            val response = api.getMovieVideos(movieId)
            logDebug("<<< getMovieVideos($movieId) - Success: ${response.results.size} videos")
            response.results
        } catch (e: Exception) {
            logError("!!! getMovieVideos($movieId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches videos (trailers, etc.) for a specific TV show. */
    suspend fun getTvShowVideos(tvId: Int): Result<List<Video>> = runCatching {
        logDebug(">>> getTvShowVideos($tvId) - Fetching TV show videos")
        try {
            val response = api.getTvShowVideos(tvId)
            logDebug("<<< getTvShowVideos($tvId) - Success: ${response.results.size} videos")
            response.results
        } catch (e: Exception) {
            logError("!!! getTvShowVideos($tvId) - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Search Endpoints ==========

    /** Searches for movies matching a query string. */
    suspend fun searchMovies(query: String): Result<List<Movie>> = runCatching {
        logDebug(">>> searchMovies('$query') - Searching movies")
        try {
            val response = api.searchMovies(query)
            logDebug("<<< searchMovies('$query') - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! searchMovies('$query') - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Searches for TV shows matching a query string. */
    suspend fun searchTvShows(query: String): Result<List<TvShow>> = runCatching {
        logDebug(">>> searchTvShows('$query') - Searching TV shows")
        try {
            val response = api.searchTvShows(query)
            logDebug("<<< searchTvShows('$query') - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! searchTvShows('$query') - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Searches for both movies and TV shows matching a query string. */
    suspend fun searchMulti(query: String): Result<List<SearchResult>> = runCatching {
        logDebug(">>> searchMulti('$query') - Multi-search")
        try {
            val response = api.searchMulti(query)
            val results = response.results.mapNotNull { it.toSearchResult() }
            logDebug("<<< searchMulti('$query') - Success: ${results.size} results")
            results
        } catch (e: Exception) {
            logError("!!! searchMulti('$query') - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Recommendations ==========

    /** Fetches recommended movies for a specific movie. */
    suspend fun getRecommendedMovies(movieId: Int): Result<List<Movie>> = runCatching {
        logDebug(">>> getRecommendedMovies($movieId) - Fetching movie recommendations")
        try {
            val response = api.getRecommendedMovies(movieId)
            logDebug("<<< getRecommendedMovies($movieId) - Success: ${response.results.size} movies")
            response.results
        } catch (e: Exception) {
            logError("!!! getRecommendedMovies($movieId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches recommended TV shows for a specific TV show. */
    suspend fun getRecommendedTvShows(tvId: Int): Result<List<TvShow>> = runCatching {
        logDebug(">>> getRecommendedTvShows($tvId) - Fetching TV show recommendations")
        try {
            val response = api.getRecommendedTvShows(tvId)
            logDebug("<<< getRecommendedTvShows($tvId) - Success: ${response.results.size} TV shows")
            response.results
        } catch (e: Exception) {
            logError("!!! getRecommendedTvShows($tvId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches detailed information for a movie collection. */
    suspend fun getCollectionDetails(collectionId: Int): Result<CollectionDetail> = runCatching {
        logDebug(">>> getCollectionDetails($collectionId) - Fetching collection details")
        try {
            val response = api.getCollectionDetails(collectionId)
            logDebug("<<< getCollectionDetails($collectionId) - Success: ${response.name}")
            response
        } catch (e: Exception) {
            logError("!!! getCollectionDetails($collectionId) - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Credits (Cast & Crew) ==========

    /** Fetches movie credits (cast and crew) for a specific movie. */
    suspend fun getMovieCredits(movieId: Int): Result<MovieCreditsResponse> = runCatching {
        logDebug(">>> getMovieCredits($movieId) - Fetching movie credits")
        try {
            val response = api.getMovieCredits(movieId)
            logDebug("<<< getMovieCredits($movieId) - Success: ${response.cast.size} cast members")
            response
        } catch (e: Exception) {
            logError("!!! getMovieCredits($movieId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches TV show credits (cast and crew) for a specific TV show. */
    suspend fun getTvShowCredits(tvId: Int): Result<TvShowCreditsResponse> = runCatching {
        logDebug(">>> getTvShowCredits($tvId) - Fetching TV show credits")
        try {
            val response = api.getTvShowCredits(tvId)
            logDebug("<<< getTvShowCredits($tvId) - Success: ${response.cast.size} cast members")
            response
        } catch (e: Exception) {
            logError("!!! getTvShowCredits($tvId) - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Person Credits ==========

    /** Fetches movie credits for a specific person. */
    suspend fun getPersonMovieCredits(personId: Int): Result<PersonMovieCreditsResponse> = runCatching {
        logDebug(">>> getPersonMovieCredits($personId) - Fetching person movie credits")
        try {
            val response = api.getPersonMovieCredits(personId)
            logDebug("<<< getPersonMovieCredits($personId) - Success: ${response.cast.size} movies")
            response
        } catch (e: Exception) {
            logError("!!! getPersonMovieCredits($personId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches TV show credits for a specific person. */
    suspend fun getPersonTvCredits(personId: Int): Result<PersonTvCreditsResponse> = runCatching {
        logDebug(">>> getPersonTvCredits($personId) - Fetching person TV credits")
        try {
            val response = api.getPersonTvCredits(personId)
            logDebug("<<< getPersonTvCredits($personId) - Success: ${response.cast.size} TV shows")
            response
        } catch (e: Exception) {
            logError("!!! getPersonTvCredits($personId) - Failed: ${e.message}", e)
            throw e
        }
    }

    /** Fetches detailed information for a specific person. */
    suspend fun getPersonDetails(personId: Int): Result<PersonDetail> = runCatching {
        logDebug(">>> getPersonDetails($personId) - Fetching person details")
        try {
            val response = api.getPersonDetails(personId)
            logDebug("<<< getPersonDetails($personId) - Success: ${response.name}")
            response
        } catch (e: Exception) {
            logError("!!! getPersonDetails($personId) - Failed: ${e.message}", e)
            throw e
        }
    }

    // ========== Watch History (Now using Room) ==========

    /**
     * Saves a media item to the watch history using Room database.
     * Replaces the old SharedPreferences-based implementation.
     */
    fun saveToWatchHistory(context: Context, item: WatchHistoryItem) {
        logDebug(">>> saveToWatchHistory() - ID: ${item.id}, Title: ${item.title}")
        DatabaseManager.init(context)

        DatabaseManager.addToWatchHistory(
            id = item.id,
            mediaType = if (item.isTv) "tv" else "movie",
            title = item.title,
            overview = item.overview,
            posterPath = item.posterPath,
            backdropPath = item.backdropPath,
            voteAverage = item.voteAverage,
            releaseDate = item.releaseDate,
            seasonNumber = item.seasonNumber,
            episodeNumber = item.episodeNumber
        )
        logDebug("<<< saveToWatchHistory() - Saved successfully")
    }

    /**
     * Retrieves the watch history from Room database.
     */
    fun getWatchHistory(context: Context): List<WatchHistoryItem> {
        logDebug(">>> getWatchHistory() - Fetching watch history")
        DatabaseManager.init(context)

        var items = emptyList<WatchHistoryItem>()
        applicationScope.launch {
            val entities = DatabaseManager.getAllWatchHistory().first()
            items = entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
        }

        return try {
            val dao = DatabaseManager.watchHistoryDao()
            val result = kotlinx.coroutines.runBlocking {
                dao.getAllWatchHistoryItems().map {
                    DatabaseManager.entityToWatchHistoryItem(it)
                }
            }
            logDebug("<<< getWatchHistory() - Success: ${result.size} items")
            result
        } catch (e: Exception) {
            logError("!!! getWatchHistory() - Failed: ${e.message}", e)
            items
        }
    }

    /**
     * Gets watch history as a Flow for reactive updates.
     */
    fun getWatchHistoryFlow(limit: Int = 0): Flow<List<WatchHistoryItem>> {
        logDebug(">>> getWatchHistoryFlow(limit=$limit)")
        return if (limit > 0) {
            DatabaseManager.getRecentWatchHistory(limit).map { entities ->
                entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
            }
        } else {
            DatabaseManager.getAllWatchHistory().map { entities ->
                entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
            }
        }
    }

    /**
     * Gets "Continue Watching" items as a Flow.
     */
    fun getContinueWatchingFlow(limit: Int = 10): Flow<List<WatchHistoryItem>> {
        logDebug(">>> getContinueWatchingFlow(limit=$limit)")
        return DatabaseManager.getContinueWatching(limit).map { entities ->
            entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
        }
    }

    /**
     * Updates playback position for a media item.
     */
    fun updatePlaybackPosition(mediaId: Int, mediaType: String, position: Long) {
        logDebug(">>> updatePlaybackPosition() - ID: $mediaId, Type: $mediaType, Position: $position")
        DatabaseManager.updatePlaybackPosition(mediaId, mediaType, position)
    }

    /**
     * Updates episode info for a TV show.
     */
    fun updateEpisodeInfo(mediaId: Int, mediaType: String, seasonNumber: Int, episodeNumber: Int) {
        logDebug(">>> updateEpisodeInfo() - ID: $mediaId, S$seasonNumber E$episodeNumber")
        DatabaseManager.updateEpisodeInfo(mediaId, mediaType, seasonNumber, episodeNumber)
    }

    /**
     * Checks if a media item is in the watch history.
     */
    fun getWatchHistoryItem(context: Context, id: Int, isTv: Boolean): WatchHistoryItem? {
        logDebug(">>> getWatchHistoryItem() - ID: $id, isTv: $isTv")
        DatabaseManager.init(context)

        return try {
            val dao = DatabaseManager.watchHistoryDao()
            kotlinx.coroutines.runBlocking {
                val entity = dao.getWatchHistoryItem(id, if (isTv) "tv" else "movie")
                entity?.let { DatabaseManager.entityToWatchHistoryItem(it) }
            }
        } catch (e: Exception) {
            logError("!!! getWatchHistoryItem() - Failed: ${e.message}", e)
            null
        }
    }

    /**
     * Checks if a media item exists in the watch history.
     */
    fun isInWatchHistory(context: Context, id: Int, isTv: Boolean): Boolean {
        logDebug(">>> isInWatchHistory() - ID: $id, isTv: $isTv")
        DatabaseManager.init(context)

        return try {
            val dao = DatabaseManager.watchHistoryDao()
            kotlinx.coroutines.runBlocking {
                dao.isInWatchHistory(id, if (isTv) "tv" else "movie")
            }
        } catch (e: Exception) {
            logError("!!! isInWatchHistory() - Failed: ${e.message}", e)
            false
        }
    }

    /**
     * Clears all watch history.
     */
    fun clearWatchHistory() {
        logDebug(">>> clearWatchHistory() - Clearing all watch history")
        DatabaseManager.clearWatchHistory()
    }

    // ========== GitHub Lists ==========

    /**
     * Fetches movies from a GitHub JSON list.
     */
    suspend fun getGitHubMovieList(context: Context, urlString: String): Result<List<Movie>> =
        withContext(Dispatchers.IO) {
            runCatching {
                logDebug(">>> getGitHubMovieList() - URL: $urlString")

                val client = getGitHubOkHttpClient(context)
                val request = Request.Builder()
                    .url(urlString)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    logError("!!! getGitHubMovieList() - HTTP Error: ${response.code}")
                    response.close()
                    throw Exception("Failed to fetch GitHub list: HTTP ${response.code}")
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    logError("!!! getGitHubMovieList() - Empty response")
                    response.close()
                    throw Exception("Empty response from GitHub")
                }

                response.close()

                val type = object : TypeToken<List<Movie>>() {}.type
                val movies: List<Movie> = gson.fromJson(responseBody, type)

                logDebug("<<< getGitHubMovieList() - Success: ${movies.size} movies")
                movies
            }
        }

    /**
     * Fetches TV shows from a GitHub JSON list.
     */
    suspend fun getGitHubTvShowList(context: Context, urlString: String): Result<List<TvShow>> =
        withContext(Dispatchers.IO) {
            runCatching {
                logDebug(">>> getGitHubTvShowList() - URL: $urlString")

                val client = getGitHubOkHttpClient(context)
                val request = Request.Builder()
                    .url(urlString)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    logError("!!! getGitHubTvShowList() - HTTP Error: ${response.code}")
                    response.close()
                    throw Exception("Failed to fetch GitHub list: HTTP ${response.code}")
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    logError("!!! getGitHubTvShowList() - Empty response")
                    response.close()
                    throw Exception("Empty response from GitHub")
                }

                response.close()

                val type = object : TypeToken<List<TvShow>>() {}.type
                val tvShows: List<TvShow> = gson.fromJson(responseBody, type)

                logDebug("<<< getGitHubTvShowList() - Success: ${tvShows.size} TV shows")
                tvShows
            }
        }

    /**
     * Fetches companies and networks from a GitHub JSON list.
     */
    suspend fun getGitHubCompaniesNetworks(context: Context, urlString: String): Result<CompaniesNetworksResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                logDebug(">>> getGitHubCompaniesNetworks() - URL: $urlString")

                val client = getGitHubOkHttpClient(context)
                val request = Request.Builder()
                    .url(urlString)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    logError("!!! getGitHubCompaniesNetworks() - HTTP Error: ${response.code}")
                    response.close()
                    throw Exception("Failed to fetch GitHub list: HTTP ${response.code}")
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    logError("!!! getGitHubCompaniesNetworks() - Empty response")
                    response.close()
                    throw Exception("Empty response from GitHub")
                }

                response.close()

                val result: CompaniesNetworksResponse = gson.fromJson(responseBody, CompaniesNetworksResponse::class.java)

                logDebug("<<< getGitHubCompaniesNetworks() - Success")
                result
            }
        }

    // ========== Private Helper Methods ==========

    /**
     * Attempts to get cached movies of a specific type.
     */
    private suspend fun tryGetCachedMovies(cacheType: String): List<Movie> {
        logDebug(">>> tryGetCachedMovies($cacheType)")
        return try {
            val cached = DatabaseManager.getCachedMoviesByType(cacheType).first()
                .map { DatabaseManager.entityToMovie(it) }
            logDebug("<<< tryGetCachedMovies() - Found ${cached.size} cached movies")
            cached
        } catch (e: Exception) {
            logError("!!! tryGetCachedMovies() - Failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Attempts to get cached TV shows of a specific type.
     */
    private suspend fun tryGetCachedTvShows(cacheType: String): List<TvShow> {
        logDebug(">>> tryGetCachedTvShows($cacheType)")
        return try {
            val cached = DatabaseManager.getCachedTvShowsByType(cacheType).first()
                .map { DatabaseManager.entityToTvShow(it) }
            logDebug("<<< tryGetCachedTvShows() - Found ${cached.size} cached TV shows")
            cached
        } catch (e: Exception) {
            logError("!!! tryGetCachedTvShows() - Failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Caches movies with the specified type and expiration.
     */
    private fun cacheMovies(
        movies: List<Movie>,
        cacheType: String,
        expirationMs: Long = CachedMovieEntity.CACHE_DURATION_MS
    ) {
        logDebug(">>> cacheMovies() - Type: $cacheType, Count: ${movies.size}")
        DatabaseManager.cacheMovies(movies, cacheType, expirationMs)
    }

    /**
     * Caches TV shows with the specified type and expiration.
     */
    private fun cacheTvShows(
        tvShows: List<TvShow>,
        cacheType: String,
        expirationMs: Long = CachedTvShowEntity.CACHE_DURATION_MS
    ) {
        logDebug(">>> cacheTvShows() - Type: $cacheType, Count: ${tvShows.size}")
        DatabaseManager.cacheTvShows(tvShows, cacheType, expirationMs)
    }

    /**
     * Cleans up expired cache entries.
     */
    fun cleanExpiredCache() {
        logDebug(">>> cleanExpiredCache() - Cleaning expired cache")
        DatabaseManager.cleanExpiredCache()
    }
}