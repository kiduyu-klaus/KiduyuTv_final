package com.kiduyuk.klausk.kiduyutv.data.local.database

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedMovieDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedMovieDetailDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedTvShowDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.CachedTvShowDetailDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.GenreDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.SavedMediaDao
import com.kiduyuk.klausk.kiduyutv.data.local.dao.WatchHistoryDao
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieDetailEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowDetailEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.GenreEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.SavedMediaEntity
import com.kiduyuk.klausk.kiduyutv.data.local.entity.WatchHistoryEntity
import com.kiduyuk.klausk.kiduyutv.data.model.Genre
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton manager for accessing the Room database and providing
 * a clean API for data operations throughout the application.
 *
 * This class provides a centralized access point to all database operations
 * and handles the conversion between database entities and domain models.
 */
object DatabaseManager {

    private lateinit var database: AppDatabase
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private const val LEGACY_PREFS_NAME = "kiduyu_tv_prefs"
    private const val LEGACY_KEY_MY_LIST = "my_list"

    /**
     * Initialize the database manager.
     * Must be called before using any database operations.
     *
     * @param context Application context
     */
    fun init(context: Context) {
        if (!::database.isInitialized) {
            database = AppDatabase.getInstance(context)
            migrateLegacyDataIfNeeded(context)
        }
    }

    /**
     * Check if the database has been initialized.
     */
    fun isInitialized(): Boolean = ::database.isInitialized

    // ========== Saved Media (My List) Operations ==========

    /**
     * Get the DAO for saved media operations.
     */
    fun savedMediaDao(): SavedMediaDao = database.savedMediaDao()

    /**
     * Add an item to My List.
     */
    fun addToMyList(
        id: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        voteAverage: Double = 0.0,
        category: String? = null,
        character: String? = null,
        knownForDepartment: String? = null
    ) {
        applicationScope.launch {
            val entity = SavedMediaEntity(
                id = id,
                mediaType = mediaType,
                title = title,
                posterPath = posterPath,
                voteAverage = voteAverage,
                category = category,
                savedTimestamp = System.currentTimeMillis(),
                character = character,
                knownForDepartment = knownForDepartment
            )
            savedMediaDao().insertSavedMedia(entity)
        }
    }

    /**
     * Remove an item from My List.
     */
    fun removeFromMyList(mediaId: Int, mediaType: String) {
        applicationScope.launch {
            savedMediaDao().deleteSavedMediaById(mediaId, mediaType)
        }
    }

    /**
     * Check if a media item is in My List.
     */
    suspend fun isInMyList(mediaId: Int, mediaType: String): Boolean = withContext(Dispatchers.IO) {
        savedMediaDao().isMediaSaved(mediaId, mediaType)
    }

    /**
     * Get all items in My List as a Flow.
     */
    fun getMyList(): Flow<List<SavedMediaEntity>> {
        return savedMediaDao().getAllSavedMedia()
    }

    /**
     * Convert SavedMediaEntity to MyListItem domain model.
     */
    fun entityToMyListItem(entity: SavedMediaEntity): MyListItem {
        return MyListItem(
            id = entity.id,
            title = entity.title ?: "",
            posterPath = entity.posterPath,
            type = entity.mediaType,
            voteAverage = entity.voteAverage,
            character = entity.character,
            knownForDepartment = entity.knownForDepartment
        )
    }

    // ========== Watch History Operations ==========

    /**
     * Get the DAO for watch history operations.
     */
    fun watchHistoryDao(): WatchHistoryDao = database.watchHistoryDao()

    /**
     * Add or update a watch history item (suspend version for coroutines).
     */
    suspend fun addToWatchHistoryAsync(
        id: Int,
        mediaType: String,
        title: String,
        overview: String? = null,
        posterPath: String? = null,
        backdropPath: String? = null,
        voteAverage: Double = 0.0,
        releaseDate: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        playbackPosition: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val entity = WatchHistoryEntity(
            id = id,
            mediaType = mediaType,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            voteAverage = voteAverage,
            releaseDate = releaseDate,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            playbackPosition = playbackPosition,
            lastWatchedTimestamp = System.currentTimeMillis()
        )
        watchHistoryDao().insertWatchHistory(entity)
    }

    /**
     * Add or update a watch history item.
     */
    fun addToWatchHistory(
        id: Int,
        mediaType: String,
        title: String,
        overview: String? = null,
        posterPath: String? = null,
        backdropPath: String? = null,
        voteAverage: Double = 0.0,
        releaseDate: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        applicationScope.launch {
            val entity = WatchHistoryEntity(
                id = id,
                mediaType = mediaType,
                title = title,
                overview = overview,
                posterPath = posterPath,
                backdropPath = backdropPath,
                voteAverage = voteAverage,
                releaseDate = releaseDate,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                lastWatchedTimestamp = System.currentTimeMillis()
            )
            watchHistoryDao().insertWatchHistory(entity)
        }
    }

    /**
     * Update playback position for resume functionality.
     */
    fun updatePlaybackPosition(mediaId: Int, mediaType: String, position: Long) {
        applicationScope.launch {
            watchHistoryDao().updatePlaybackPosition(mediaId, mediaType, position)
        }
    }

    /**
     * Update episode info for TV shows.
     */
    fun updateEpisodeInfo(mediaId: Int, mediaType: String, seasonNumber: Int, episodeNumber: Int) {
        applicationScope.launch {
            watchHistoryDao().updateEpisodeInfo(mediaId, mediaType, seasonNumber, episodeNumber)
        }
    }

    /**
     * Get recent watch history.
     */
    fun getRecentWatchHistory(limit: Int = 20): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao().getRecentWatchHistory(limit)
    }

    /**
     * Get all watch history as a Flow.
     */
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao().getAllWatchHistory()
    }

    /**
     * Get "Continue Watching" items.
     */
    fun getContinueWatching(limit: Int = 10): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao().getContinueWatching(limit)
    }

    /**
     * Convert WatchHistoryEntity to WatchHistoryItem domain model.
     */
    fun entityToWatchHistoryItem(entity: WatchHistoryEntity): WatchHistoryItem {
        return WatchHistoryItem(
            id = entity.id,
            title = entity.title,
            overview = entity.overview,
            posterPath = entity.posterPath,
            backdropPath = entity.backdropPath,
            voteAverage = entity.voteAverage,
            releaseDate = entity.releaseDate,
            isTv = entity.mediaType == "tv",
            seasonNumber = entity.seasonNumber,
            episodeNumber = entity.episodeNumber,
            lastWatched = entity.lastWatchedTimestamp,
            playbackPosition = entity.playbackPosition
        )
    }

    /**
     * Delete all watch history.
     */
    fun clearWatchHistory() {
        applicationScope.launch {
            watchHistoryDao().deleteAllWatchHistory()
        }
    }

    /**
     * Delete all items in My List.
     */
    fun clearMyList() {
        applicationScope.launch {
            savedMediaDao().deleteAllSavedMedia()
        }
    }

    /**
     * Delete items in My List by type.
     */
    fun clearMyListByType(mediaType: String) {
        applicationScope.launch {
            savedMediaDao().deleteSavedMediaByType(mediaType)
        }
    }

    /**
     * Delete watch history older than specified days.
     */
    fun deleteOldWatchHistory(daysOld: Int = 30) {
        val cutoffTimestamp = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        applicationScope.launch {
            watchHistoryDao().deleteOldWatchHistory(cutoffTimestamp)
        }
    }

    /**
     * Update watch history item with fetched TMDB details.
     * This is a suspend function that updates the database with fresh data from TMDB.
     *
     * @param mediaId The TMDB ID of the media
     * @param mediaType "movie" or "tv"
     * @param title The title/name from TMDB
     * @param overview The overview from TMDB
     * @param posterPath The poster path from TMDB
     * @param backdropPath The backdrop path from TMDB
     * @param voteAverage The vote average from TMDB
     * @param releaseDate The release/air date from TMDB
     */
    suspend fun updateWatchHistoryDetails(
        mediaId: Int,
        mediaType: String,
        title: String?,
        overview: String?,
        posterPath: String?,
        backdropPath: String?,
        voteAverage: Double,
        releaseDate: String?
    ) {
        withContext(Dispatchers.IO) {
            watchHistoryDao().updateWatchHistoryDetails(
                mediaId = mediaId,
                mediaType = mediaType,
                title = title,
                overview = overview,
                posterPath = posterPath,
                backdropPath = backdropPath,
                voteAverage = voteAverage,
                releaseDate = releaseDate
            )
        }
    }

    /**
     * Get watch history items that need TMDB detail fetching.
     * These are items with missing or empty poster/backdrop/title.
     *
     * @return List of entities needing details
     */
    suspend fun getWatchHistoryItemsNeedingDetails(): List<WatchHistoryEntity> {
        return withContext(Dispatchers.IO) {
            watchHistoryDao().getWatchHistoryItemsNeedingDetails()
        }
    }

    /**
     * Get all watch history items as a List.
     * Useful for batch operations like refreshing all images from TMDB.
     *
     * @return List of all watch history entities
     */
    suspend fun getAllWatchHistoryItems(): List<WatchHistoryEntity> {
        return withContext(Dispatchers.IO) {
            watchHistoryDao().getAllWatchHistoryItems()
        }
    }

    // ========== Movie Caching Operations ==========

    /**
     * Get the DAO for movie caching operations.
     */
    fun cachedMovieDao(): CachedMovieDao = database.cachedMovieDao()

    /**
     * Cache movies with a specific type.
     */
    fun cacheMovies(movies: List<Movie>, cacheType: String, expirationMs: Long = CachedMovieEntity.CACHE_DURATION_MS) {
        applicationScope.launch {
            val entities = movies.map { movie ->
                CachedMovieEntity(
                    id = movie.id,
                    title = movie.title,
                    overview = movie.overview,
                    posterPath = movie.posterPath,
                    backdropPath = movie.backdropPath,
                    voteAverage = movie.voteAverage,
                    releaseDate = movie.releaseDate,
                    genreIdsJson = movie.genreIds?.let { gson.toJson(it) },
                    popularity = movie.popularity,
                    cacheType = cacheType,
                    fetchedTimestamp = System.currentTimeMillis(),
                    expirationTimestamp = System.currentTimeMillis() + expirationMs
                )
            }
            cachedMovieDao().insertAllCachedMovies(entities)
        }
    }

    /**
     * Get cached movies by type.
     */
    fun getCachedMoviesByType(cacheType: String): Flow<List<CachedMovieEntity>> {
        return cachedMovieDao().getValidCachedMoviesByType(cacheType)
    }

    /**
     * Convert CachedMovieEntity to Movie domain model.
     */
    fun entityToMovie(entity: CachedMovieEntity): Movie {
        return Movie(
            id = entity.id,
            title = entity.title,
            overview = entity.overview ?: "",
            posterPath = entity.posterPath,
            backdropPath = entity.backdropPath,
            voteAverage = entity.voteAverage,
            releaseDate = entity.releaseDate ?: "",
            genreIds = entity.genreIdsJson?.let {
                val type = object : TypeToken<List<Int>>() {}.type
                gson.fromJson(it, type)
            },
            popularity = entity.popularity
        )
    }

    /**
     * Clean up expired cache entries.
     */
    fun cleanExpiredCache() {
        applicationScope.launch {
            cachedMovieDao().deleteExpiredCachedMovies()
            cachedTvShowDao().deleteExpiredCachedTvShows()
            cachedMovieDetailDao().deleteExpiredCachedMovieDetails()
            cachedTvShowDetailDao().deleteExpiredCachedTvShowDetails()
        }
    }

    // ========== TV Show Caching Operations ==========

    /**
     * Get the DAO for TV show caching operations.
     */
    fun cachedTvShowDao(): CachedTvShowDao = database.cachedTvShowDao()

    /**
     * Cache TV shows with a specific type.
     */
    fun cacheTvShows(tvShows: List<TvShow>, cacheType: String, expirationMs: Long = CachedTvShowEntity.CACHE_DURATION_MS) {
        applicationScope.launch {
            val entities = tvShows.map { tvShow ->
                CachedTvShowEntity(
                    id = tvShow.id,
                    name = tvShow.name,
                    overview = tvShow.overview,
                    posterPath = tvShow.posterPath,
                    backdropPath = tvShow.backdropPath,
                    voteAverage = tvShow.voteAverage,
                    firstAirDate = tvShow.firstAirDate,
                    genreIdsJson = tvShow.genreIds?.let { gson.toJson(it) },
                    popularity = tvShow.popularity,
                    cacheType = cacheType,
                    fetchedTimestamp = System.currentTimeMillis(),
                    expirationTimestamp = System.currentTimeMillis() + expirationMs
                )
            }
            cachedTvShowDao().insertAllCachedTvShows(entities)
        }
    }

    /**
     * Get cached TV shows by type.
     */
    fun getCachedTvShowsByType(cacheType: String): Flow<List<CachedTvShowEntity>> {
        return cachedTvShowDao().getValidCachedTvShowsByType(cacheType)
    }

    /**
     * Convert CachedTvShowEntity to TvShow domain model.
     */
    fun entityToTvShow(entity: CachedTvShowEntity): TvShow {
        return TvShow(
            id = entity.id,
            name = entity.name,
            overview = entity.overview ?: "",
            posterPath = entity.posterPath,
            backdropPath = entity.backdropPath,
            voteAverage = entity.voteAverage,
            firstAirDate = entity.firstAirDate ?: "",
            genreIds = entity.genreIdsJson?.let {
                val type = object : TypeToken<List<Int>>() {}.type
                gson.fromJson(it, type)
            },
            popularity = entity.popularity
        )
    }

    // ========== Genre Caching Operations ==========

    /**
     * Get the DAO for genre caching operations.
     */
    fun genreDao(): GenreDao = database.genreDao()

    /**
     * Cache genres for a specific media type.
     */
    fun cacheGenres(genres: List<Genre>, mediaType: String) {
        applicationScope.launch {
            val entities = genres.map { genre ->
                GenreEntity(
                    id = genre.id,
                    name = genre.name,
                    mediaType = mediaType,
                    fetchedTimestamp = System.currentTimeMillis()
                )
            }
            genreDao().insertGenres(entities)
        }
    }

    /**
     * Get cached genres for movies.
     */
    fun getCachedMovieGenres(): Flow<List<GenreEntity>> {
        return genreDao().getMovieGenres()
    }

    /**
     * Get cached genres for TV shows.
     */
    fun getCachedTvGenres(): Flow<List<GenreEntity>> {
        return genreDao().getTvShowGenres()
    }

    /**
     * Convert GenreEntity to Genre domain model.
     */
    fun entityToGenre(entity: GenreEntity): Genre {
        return Genre(
            id = entity.id,
            name = entity.name
        )
    }

    // ========== Movie Detail Caching Operations ==========

    /**
     * Get the DAO for movie detail caching operations.
     */
    fun cachedMovieDetailDao(): CachedMovieDetailDao = database.cachedMovieDetailDao()

    // ========== TV Show Detail Caching Operations ==========

    /**
     * Get the DAO for TV show detail caching operations.
     */
    fun cachedTvShowDetailDao(): CachedTvShowDetailDao = database.cachedTvShowDetailDao()

    // ========== Migration from SharedPreferences ==========

    /**
     * Migrate legacy SharedPreferences data to Room database.
     */
    private fun migrateLegacyDataIfNeeded(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val myListJson = prefs.getString(LEGACY_KEY_MY_LIST, null)

        if (myListJson != null) {
            applicationScope.launch {
                try {
                    val type = object : TypeToken<List<MyListItem>>() {}.type
                    val legacyList: List<MyListItem> = gson.fromJson(myListJson, type)

                    val entities = legacyList.map { item ->
                        SavedMediaEntity(
                            id = item.id,
                            mediaType = item.type,
                            title = item.title,
                            posterPath = item.posterPath,
                            savedTimestamp = System.currentTimeMillis()
                        )
                    }

                    savedMediaDao().insertAllSavedMedia(entities)

                    // Clear legacy SharedPreferences after successful migration
                    prefs.edit().remove(LEGACY_KEY_MY_LIST).apply()
                } catch (e: Exception) {
                    // Migration failed
                }
            }
        }
    }

    /**
     * Get database statistics for debugging/monitoring.
     */
    suspend fun getDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        DatabaseStats(
            savedMediaCount = savedMediaDao().getSavedMediaCount(),
            watchHistoryCount = watchHistoryDao().getWatchHistoryCount(),
            cachedMoviesCount = cachedMovieDao().getCachedMovieCount(),
            cachedTvShowsCount = cachedTvShowDao().getCachedTvShowCount(),
            cachedMovieDetailsCount = cachedMovieDetailDao().getCachedMovieDetailCount(),
            cachedTvShowDetailsCount = cachedTvShowDetailDao().getCachedTvShowDetailCount(),
            genresCount = genreDao().getGenreCount()
        )
    }

    /**
     * Clear all cached data from the database.
     */
    fun clearAllCache() {
        applicationScope.launch {
            cachedMovieDao().deleteAllCachedMovies()
            cachedTvShowDao().deleteAllCachedTvShows()
            cachedMovieDetailDao().deleteAllCachedMovieDetails()
            cachedTvShowDetailDao().deleteAllCachedTvShowDetails()
            genreDao().deleteAllGenres()
        }
    }
}

/**
 * Data class for database statistics.
 */
data class DatabaseStats(
    val savedMediaCount: Int,
    val watchHistoryCount: Int,
    val cachedMoviesCount: Int,
    val cachedTvShowsCount: Int,
    val cachedMovieDetailsCount: Int,
    val cachedTvShowDetailsCount: Int,
    val genresCount: Int
) {
    val totalEntries: Int
        get() = savedMediaCount + watchHistoryCount + cachedMoviesCount +
                cachedTvShowsCount + cachedMovieDetailsCount +
                cachedTvShowDetailsCount + genresCount
}