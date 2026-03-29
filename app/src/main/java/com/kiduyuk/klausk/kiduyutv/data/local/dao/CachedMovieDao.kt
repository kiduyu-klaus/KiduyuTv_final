package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for caching movie data from TMDB API.
 * Enables offline support and reduces redundant network requests by serving
 * cached content when available and still valid.
 */
@Dao
interface CachedMovieDao {

    /**
     * Get a specific cached movie by ID.
     */
    @Query("SELECT * FROM cached_movies WHERE id = :movieId")
    suspend fun getCachedMovie(movieId: Int): CachedMovieEntity?

    /**
     * Get a specific cached movie by ID as Flow for reactive updates.
     */
    @Query("SELECT * FROM cached_movies WHERE id = :movieId")
    fun getCachedMovieFlow(movieId: Int): Flow<CachedMovieEntity?>

    /**
     * Get all cached movies.
     */
    @Query("SELECT * FROM cached_movies ORDER BY fetchedTimestamp DESC")
    fun getAllCachedMovies(): Flow<List<CachedMovieEntity>>

    /**
     * Get cached movies by cache type (e.g., "trending", "popular", "top_rated").
     */
    @Query("SELECT * FROM cached_movies WHERE cacheType = :cacheType ORDER BY fetchedTimestamp DESC")
    fun getCachedMoviesByType(cacheType: String): Flow<List<CachedMovieEntity>>

    /**
     * Get cached movies that are still valid (not expired).
     */
    @Query("SELECT * FROM cached_movies WHERE expirationTimestamp > :currentTime ORDER BY fetchedTimestamp DESC")
    fun getValidCachedMovies(currentTime: Long = System.currentTimeMillis()): Flow<List<CachedMovieEntity>>

    /**
     * Get cached movies by cache type that are still valid.
     */
    @Query("""
        SELECT * FROM cached_movies 
        WHERE cacheType = :cacheType AND expirationTimestamp > :currentTime 
        ORDER BY fetchedTimestamp DESC
    """)
    fun getValidCachedMoviesByType(cacheType: String, currentTime: Long = System.currentTimeMillis()): Flow<List<CachedMovieEntity>>

    /**
     * Check if a movie is cached and still valid.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_movies WHERE id = :movieId AND expirationTimestamp > :currentTime)")
    suspend fun isMovieCached(movieId: Int, currentTime: Long = System.currentTimeMillis()): Boolean

    /**
     * Insert or replace a cached movie.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMovie(movie: CachedMovieEntity)

    /**
     * Insert or replace multiple cached movies.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCachedMovies(movies: List<CachedMovieEntity>)

    /**
     * Delete a specific cached movie.
     */
    @Query("DELETE FROM cached_movies WHERE id = :movieId")
    suspend fun deleteCachedMovie(movieId: Int)

    /**
     * Delete cached movies by cache type.
     */
    @Query("DELETE FROM cached_movies WHERE cacheType = :cacheType")
    suspend fun deleteCachedMoviesByType(cacheType: String)

    /**
     * Delete all expired cached movies.
     */
    @Query("DELETE FROM cached_movies WHERE expirationTimestamp <= :currentTime")
    suspend fun deleteExpiredCachedMovies(currentTime: Long = System.currentTimeMillis())

    /**
     * Delete all cached movies.
     */
    @Query("DELETE FROM cached_movies")
    suspend fun deleteAllCachedMovies()

    /**
     * Get the count of cached movies.
     */
    @Query("SELECT COUNT(*) FROM cached_movies")
    suspend fun getCachedMovieCount(): Int

    /**
     * Get the count of valid cached movies.
     */
    @Query("SELECT COUNT(*) FROM cached_movies WHERE expirationTimestamp > :currentTime")
    suspend fun getValidCachedMovieCount(currentTime: Long = System.currentTimeMillis()): Int

    /**
     * Search cached movies by title.
     */
    @Query("SELECT * FROM cached_movies WHERE title LIKE '%' || :query || '%' ORDER BY fetchedTimestamp DESC")
    fun searchCachedMovies(query: String): Flow<List<CachedMovieEntity>>

    /**
     * Get recently fetched cached movies.
     */
    @Query("SELECT * FROM cached_movies ORDER BY fetchedTimestamp DESC LIMIT :limit")
    fun getRecentlyFetchedMovies(limit: Int = 20): Flow<List<CachedMovieEntity>>
}
