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
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Repository class that handles data operations, specifically fetching data from the TMDB API.
 * Now uses Room database for caching and watch history management.
 *
 * This implementation provides:
 * - Automatic response caching with configurable expiration
 * - Offline support for previously fetched content
 * - Efficient watch history tracking
 * - Reduced network calls through intelligent cache management
 */
class TmdbRepository {

    private val api = ApiClient.tmdbApiService
    private val gson = Gson()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "TmdbRepository"

        // Cache type constants for different content categories
        const val CACHE_TYPE_TRENDING = "trending"
        const val CACHE_TYPE_POPULAR = "popular"
        const val CACHE_TYPE_TOP_RATED = "top_rated"
        const val CACHE_TYPE_NOW_PLAYING = "now_playing"
        const val CACHE_TYPE_GITHUB_LIST = "github_list"
    }

    // ========== Trending Content ==========

    /** Fetches trending TV shows for today with caching. */
    suspend fun getTrendingTvToday(): Result<List<TvShow>> = runCatching {
        // Try cache first
        val cached = tryGetCachedTvShows(CACHE_TYPE_TRENDING)
        // Check if cached items have missing images
        val hasMissingImages = cached.any { it.posterPath.isNullOrEmpty() && it.backdropPath.isNullOrEmpty() }

//        if (cached.isNotEmpty() && !hasMissingImages) {
//            Log.i(TAG, "Returning cached trending TV shows")
//            return@runCatching cached
//        }

        if (hasMissingImages) {
            Log.i(TAG, "Cached trending TV shows have missing images, refreshing from API")
        }

        // Fetch from API
        val result = api.getTrendingTvToday().results

        // Cache the results
        cacheTvShows(result, CACHE_TYPE_TRENDING)

        result
    }

    /** Fetches trending movies for today with caching. */
    suspend fun getTrendingMoviesToday(): Result<List<Movie>> = runCatching {
        val cached = tryGetCachedMovies(CACHE_TYPE_TRENDING)
        val hasMissingImages = cached.any { it.posterPath.isNullOrEmpty() && it.backdropPath.isNullOrEmpty() }

//        if (cached.isNotEmpty() && !hasMissingImages) {
//            Log.i(TAG, "Returning cached trending movies")
//            return@runCatching cached
//        }

        if (hasMissingImages) {
            Log.i(TAG, "Cached trending movies have missing images, refreshing from API")
        }

        val result = api.getTrendingMoviesToday().results
        cacheMovies(result, CACHE_TYPE_TRENDING)

        result
    }

    /** Fetches trending TV shows for the week. */
    suspend fun getTrendingTvThisWeek(): Result<List<TvShow>> = runCatching {
        api.getTrendingTvThisWeek().results
    }

    /** Fetches trending movies for the week. */
    suspend fun getTrendingMoviesThisWeek(): Result<List<Movie>> = runCatching {
        api.getTrendingMoviesThisWeek().results
    }

    // ========== General Content ==========

    /** Fetches movies currently playing in theaters with caching. */
    suspend fun getNowPlayingMovies(): Result<List<Movie>> = runCatching {
        val cached = tryGetCachedMovies(CACHE_TYPE_NOW_PLAYING)
        if (cached.isNotEmpty()) {
            Log.i(TAG, "Returning cached now playing movies")
            return@runCatching cached
        }

        val result = api.getNowPlayingMovies().results
        cacheMovies(result, CACHE_TYPE_NOW_PLAYING)

        result
    }

    /** Fetches top-rated movies with caching. */
    suspend fun getTopRatedMovies(): Result<List<Movie>> = runCatching {
        val cached = tryGetCachedMovies(CACHE_TYPE_TOP_RATED)
//        if (cached.isNotEmpty() && cached.size > 25) {
//            Log.i(TAG, "Returning cached top rated movies")
//            return@runCatching cached
//        }

        val result = api.getTopRatedMovies().results
        cacheMovies(result, CACHE_TYPE_TOP_RATED)

        result
    }

    /** Fetches top-rated TV shows. */
    suspend fun getTopRatedTvShows(): Result<List<TvShow>> = runCatching {
        val cached = tryGetCachedTvShows(CACHE_TYPE_TOP_RATED)
//        if (cached.isNotEmpty() && cached.size > 25) {
//            Log.i(TAG, "Returning cached top rated TV shows")
//            return@runCatching cached
//        }

        val result = api.getTopRatedTvShows().results
        cacheTvShows(result, CACHE_TYPE_TOP_RATED)

        result
    }

    /** Fetches popular movies with caching. */
    suspend fun getPopularMovies(): Result<List<Movie>> = runCatching {
        val cached = tryGetCachedMovies(CACHE_TYPE_POPULAR)
        if (cached.isNotEmpty()) {
            Log.i(TAG, "Returning cached popular movies")
            return@runCatching cached
        }

        val result = api.getPopularMovies().results
        cacheMovies(result, CACHE_TYPE_POPULAR)

        result
    }

    /** Fetches popular TV shows. */
    suspend fun getPopularTvShows(): Result<List<TvShow>> = runCatching {
        val cached = tryGetCachedTvShows(CACHE_TYPE_POPULAR)
        if (cached.isNotEmpty()) {
            Log.i(TAG, "Returning cached popular TV shows")
            return@runCatching cached
        }

        val result = api.getPopularTvShows().results
        cacheTvShows(result, CACHE_TYPE_POPULAR)

        result
    }

    // ========== Detail Endpoints ==========

    /** Fetches detailed information for a specific movie. */
    suspend fun getMovieDetail(movieId: Int): Result<MovieDetail> = runCatching {
        api.getMovieDetail(movieId)
    }

    /** Fetches detailed information for a specific TV show. */
    suspend fun getTvShowDetail(tvId: Int): Result<TvShowDetail> = runCatching {
        api.getTvShowDetail(tvId)
    }

    /** Fetches detailed information for a specific season of a TV show. */
    suspend fun getSeasonDetail(tvId: Int, seasonNumber: Int): Result<SeasonDetail> = runCatching {
        api.getSeasonDetail(tvId, seasonNumber)
    }

    // ========== Genre Endpoints ==========

    /** Fetches the list of available movie genres with caching. */
    suspend fun getMovieGenres(): Result<List<Genre>> = runCatching {
        try {
            // Try to get from cache first
            val cachedGenres = DatabaseManager.getCachedMovieGenres().first()
            if (cachedGenres.isNotEmpty()) {
                Log.i(TAG, "Returning cached movie genres")
                return@runCatching cachedGenres.map { DatabaseManager.entityToGenre(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get cached genres", e)
        }

        // Fetch from API
        val result = api.getMovieGenres().genres

        // Cache the genres
        DatabaseManager.cacheGenres(result, "movie")

        result
    }

    /** Fetches the list of available TV show genres with caching. */
    suspend fun getTvGenres(): Result<List<Genre>> = runCatching {
        try {
            val cachedGenres = DatabaseManager.getCachedTvGenres().first()
            if (cachedGenres.isNotEmpty()) {
                Log.i(TAG, "Returning cached TV genres")
                return@runCatching cachedGenres.map { DatabaseManager.entityToGenre(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get cached genres", e)
        }

        val result = api.getTvGenres().genres
        DatabaseManager.cacheGenres(result, "tv")

        result
    }

    // ========== Company/Network Endpoints ==========

    /** Fetches movies filtered by a specific production company. */
    suspend fun getMoviesByCompany(companyId: Int, page: Int = 1): Result<MovieResponse> =
        runCatching {
            api.getMoviesByCompany(companyId, page = page)
        }

    /** Fetches TV shows filtered by a specific network. */
    suspend fun getTvShowsByNetwork(networkId: Int, page: Int = 1): Result<TvShowResponse> =
        runCatching {
            api.getTvShowsByNetwork(networkId, page = page)
        }

    /** Fetches details for a specific network. */
    suspend fun getNetworkDetails(networkId: Int): Result<Network> = runCatching {
        api.getNetworkDetails(networkId)
    }

    /** Fetches details for a specific production company. */
    suspend fun getCompanyDetails(companyId: Int): Result<ProductionCompany> = runCatching {
        api.getCompanyDetails(companyId)
    }

    // ========== Video Endpoints ==========

    /** Fetches videos (trailers, etc.) for a specific movie. */
    suspend fun getMovieVideos(movieId: Int): Result<List<Video>> = runCatching {
        api.getMovieVideos(movieId).results
    }

    /** Fetches videos (trailers, etc.) for a specific TV show. */
    suspend fun getTvShowVideos(tvId: Int): Result<List<Video>> = runCatching {
        api.getTvShowVideos(tvId).results
    }

    // ========== Search Endpoints ==========

    /** Searches for movies matching a query string. */
    suspend fun searchMovies(query: String): Result<List<Movie>> = runCatching {
        api.searchMovies(query).results
    }

    /** Searches for TV shows matching a query string. */
    suspend fun searchTvShows(query: String): Result<List<TvShow>> = runCatching {
        api.searchTvShows(query).results
    }

    /** Searches for both movies and TV shows matching a query string. */
    suspend fun searchMulti(query: String): Result<List<SearchResult>> = runCatching {
        val response = api.searchMulti(query)
        response.results.mapNotNull { it.toSearchResult() }
    }

    // ========== Recommendations ==========

    /** Fetches recommended movies for a specific movie. */
    suspend fun getRecommendedMovies(movieId: Int): Result<List<Movie>> = runCatching {
        api.getRecommendedMovies(movieId).results
    }

    /** Fetches recommended TV shows for a specific TV show. */
    suspend fun getRecommendedTvShows(tvId: Int): Result<List<TvShow>> = runCatching {
        api.getRecommendedTvShows(tvId).results
    }

    /** Fetches detailed information for a movie collection. */
    suspend fun getCollectionDetails(collectionId: Int): Result<CollectionDetail> = runCatching {
        api.getCollectionDetails(collectionId)
    }

    // ========== Watch History (Now using Room) ==========

    /**
     * Saves a media item to the watch history using Room database.
     * Replaces the old SharedPreferences-based implementation.
     *
     * @param context Context for database operations
     * @param item The watch history item to save
     */
    fun saveToWatchHistory(context: Context, item: WatchHistoryItem) {
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
    }

    /**
     * Retrieves the watch history from Room database.
     *
     * @param context Context for database operations
     * @return List of watch history items
     */
    fun getWatchHistory(context: Context): List<WatchHistoryItem> {
        DatabaseManager.init(context)

        var items = emptyList<WatchHistoryItem>()
        applicationScope.launch {
            val entities = DatabaseManager.getRecentWatchHistory(20).first()
            items = entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
        }

        // For synchronous access, fall back to the database directly
        return try {
            val dao = DatabaseManager.watchHistoryDao()
            kotlinx.coroutines.runBlocking {
                dao.getRecentWatchHistory(20).first().map {
                    DatabaseManager.entityToWatchHistoryItem(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get watch history", e)
            items
        }
    }

    /**
     * Gets watch history as a Flow for reactive updates.
     *
     * @param limit Maximum number of items to return
     * @return Flow of watch history items
     */
    fun getWatchHistoryFlow(limit: Int = 20): Flow<List<WatchHistoryItem>> {
        return DatabaseManager.getRecentWatchHistory(limit).map { entities ->
            entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
        }
    }

    /**
     * Gets "Continue Watching" items as a Flow.
     *
     * @param limit Maximum number of items to return
     * @return Flow of continue watching items
     */
    fun getContinueWatchingFlow(limit: Int = 10): Flow<List<WatchHistoryItem>> {
        return DatabaseManager.getContinueWatching(limit).map { entities ->
            entities.map { DatabaseManager.entityToWatchHistoryItem(it) }
        }
    }

    /**
     * Updates playback position for a media item.
     *
     * @param mediaId The TMDB ID of the media
     * @param mediaType "movie" or "tv"
     * @param position The playback position in milliseconds
     */
    fun updatePlaybackPosition(mediaId: Int, mediaType: String, position: Long) {
        DatabaseManager.updatePlaybackPosition(mediaId, mediaType, position)
    }

    /**
     * Checks if a media item is in the watch history.
     */
    fun getWatchHistoryItem(context: Context, id: Int, isTv: Boolean): WatchHistoryItem? {
        DatabaseManager.init(context)

        return try {
            val dao = DatabaseManager.watchHistoryDao()
            kotlinx.coroutines.runBlocking {
                val entity = dao.getWatchHistoryItem(id, if (isTv) "tv" else "movie")
                entity?.let { DatabaseManager.entityToWatchHistoryItem(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get watch history item", e)
            null
        }
    }

    /**
     * Clears all watch history.
     */
    fun clearWatchHistory() {
        DatabaseManager.clearWatchHistory()
    }

    // ========== GitHub Lists with Caching ==========

    /**
     * Fetches movies from a GitHub JSON list with caching.
     */
    suspend fun getGitHubMovieList(urlString: String): Result<List<Movie>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw Exception("Failed to fetch GitHub list: HTTP $responseCode")
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val type = object : TypeToken<List<Movie>>() {}.type
                val movies: List<Movie> = gson.fromJson(response, type)

//                // Cache the movies
//                cacheMovies(
//                    movies,
//                    CACHE_TYPE_GITHUB_LIST,
//                    CachedMovieEntity.LONG_CACHE_DURATION_MS
//                )

                movies
            }
        }

    /**
     * Fetches TV shows from a GitHub JSON list with caching.
     */
    suspend fun getGitHubTvShowList(urlString: String): Result<List<TvShow>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw Exception("Failed to fetch GitHub list: HTTP $responseCode")
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val type = object : TypeToken<List<TvShow>>() {}.type
                val tvShows: List<TvShow> = gson.fromJson(response, type)

                // Cache the TV shows
//                cacheTvShows(
//                    tvShows,
//                    CACHE_TYPE_GITHUB_LIST,
//                    CachedTvShowEntity.LONG_CACHE_DURATION_MS
//                )

                tvShows
            }
        }

    /**
     * Fetches companies and networks from a GitHub JSON list.
     */
    suspend fun getGitHubCompaniesNetworks(urlString: String): Result<CompaniesNetworksResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw Exception("Failed to fetch GitHub list: HTTP $responseCode")
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val result: CompaniesNetworksResponse =
                    gson.fromJson(response, CompaniesNetworksResponse::class.java)
                result
            }
        }

    // ========== Private Helper Methods ==========

    /**
     * Attempts to get cached movies of a specific type.
     * Returns empty list if no valid cache exists.
     */
    private suspend fun tryGetCachedMovies(cacheType: String): List<Movie> {
        return try {
            DatabaseManager.getCachedMoviesByType(cacheType).first()
                .map { DatabaseManager.entityToMovie(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get cached movies for type: $cacheType", e)
            emptyList()
        }
    }

    /**
     * Attempts to get cached TV shows of a specific type.
     * Returns empty list if no valid cache exists.
     */
    private suspend fun tryGetCachedTvShows(cacheType: String): List<TvShow> {
        return try {
            DatabaseManager.getCachedTvShowsByType(cacheType).first()
                .map { DatabaseManager.entityToTvShow(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get cached TV shows for type: $cacheType", e)
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
        DatabaseManager.cacheTvShows(tvShows, cacheType, expirationMs)
    }

    /**
     * Cleans up expired cache entries.
     */
    fun cleanExpiredCache() {
        DatabaseManager.cleanExpiredCache()
    }
}
