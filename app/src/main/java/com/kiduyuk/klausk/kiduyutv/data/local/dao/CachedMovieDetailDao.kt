package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedMovieDetailEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for caching detailed movie information.
 * Stores extended movie data including runtime, production companies,
 * and collection information that requires additional API calls.
 */
@Dao
interface CachedMovieDetailDao {

    /**
     * Get cached movie detail by ID.
     */
    @Query("SELECT * FROM cached_movie_details WHERE id = :movieId")
    suspend fun getCachedMovieDetail(movieId: Int): CachedMovieDetailEntity?

    /**
     * Get cached movie detail by ID as Flow.
     */
    @Query("SELECT * FROM cached_movie_details WHERE id = :movieId")
    fun getCachedMovieDetailFlow(movieId: Int): Flow<CachedMovieDetailEntity?>

    /**
     * Check if movie detail is cached and still valid.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_movie_details WHERE id = :movieId AND expirationTimestamp > :currentTime)")
    suspend fun isMovieDetailCached(movieId: Int, currentTime: Long = System.currentTimeMillis()): Boolean

    /**
     * Insert or replace cached movie detail.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMovieDetail(movieDetail: CachedMovieDetailEntity)

    /**
     * Delete cached movie detail.
     */
    @Query("DELETE FROM cached_movie_details WHERE id = :movieId")
    suspend fun deleteCachedMovieDetail(movieId: Int)

    /**
     * Delete all expired cached movie details.
     */
    @Query("DELETE FROM cached_movie_details WHERE expirationTimestamp <= :currentTime")
    suspend fun deleteExpiredCachedMovieDetails(currentTime: Long = System.currentTimeMillis())

    /**
     * Delete all cached movie details.
     */
    @Query("DELETE FROM cached_movie_details")
    suspend fun deleteAllCachedMovieDetails()

    /**
     * Get the count of cached movie details.
     */
    @Query("SELECT COUNT(*) FROM cached_movie_details")
    suspend fun getCachedMovieDetailCount(): Int

    /**
     * Get movies that belong to a collection.
     */
    @Query("SELECT * FROM cached_movie_details WHERE collectionId = :collectionId ORDER BY releaseDate ASC")
    fun getMoviesByCollection(collectionId: Int): Flow<List<CachedMovieDetailEntity>>
}
