package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for caching TV show data from TMDB API.
 * Enables offline support and reduces redundant network requests by serving
 * cached content when available and still valid.
 */
@Dao
interface CachedTvShowDao {

    /**
     * Get a specific cached TV show by ID.
     */
    @Query("SELECT * FROM cached_tv_shows WHERE id = :tvShowId")
    suspend fun getCachedTvShow(tvShowId: Int): CachedTvShowEntity?

    /**
     * Get a specific cached TV show by ID as Flow for reactive updates.
     */
    @Query("SELECT * FROM cached_tv_shows WHERE id = :tvShowId")
    fun getCachedTvShowFlow(tvShowId: Int): Flow<CachedTvShowEntity?>

    /**
     * Get all cached TV shows.
     */
    @Query("SELECT * FROM cached_tv_shows ORDER BY fetchedTimestamp DESC")
    fun getAllCachedTvShows(): Flow<List<CachedTvShowEntity>>

    /**
     * Get cached TV shows by cache type (e.g., "trending", "popular", "top_rated").
     */
    @Query("SELECT * FROM cached_tv_shows WHERE cacheType = :cacheType ORDER BY fetchedTimestamp DESC")
    fun getCachedTvShowsByType(cacheType: String): Flow<List<CachedTvShowEntity>>

    /**
     * Get cached TV shows that are still valid (not expired).
     */
    @Query("SELECT * FROM cached_tv_shows WHERE expirationTimestamp > :currentTime ORDER BY fetchedTimestamp DESC")
    fun getValidCachedTvShows(currentTime: Long = System.currentTimeMillis()): Flow<List<CachedTvShowEntity>>

    /**
     * Get cached TV shows by cache type that are still valid.
     */
    @Query("""
        SELECT * FROM cached_tv_shows 
        WHERE cacheType = :cacheType AND expirationTimestamp > :currentTime 
        ORDER BY fetchedTimestamp DESC
    """)
    fun getValidCachedTvShowsByType(cacheType: String, currentTime: Long = System.currentTimeMillis()): Flow<List<CachedTvShowEntity>>

    /**
     * Check if a TV show is cached and still valid.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_tv_shows WHERE id = :tvShowId AND expirationTimestamp > :currentTime)")
    suspend fun isTvShowCached(tvShowId: Int, currentTime: Long = System.currentTimeMillis()): Boolean

    /**
     * Insert or replace a cached TV show.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTvShow(tvShow: CachedTvShowEntity)

    /**
     * Insert or replace multiple cached TV shows.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCachedTvShows(tvShows: List<CachedTvShowEntity>)

    /**
     * Delete a specific cached TV show.
     */
    @Query("DELETE FROM cached_tv_shows WHERE id = :tvShowId")
    suspend fun deleteCachedTvShow(tvShowId: Int)

    /**
     * Delete cached TV shows by cache type.
     */
    @Query("DELETE FROM cached_tv_shows WHERE cacheType = :cacheType")
    suspend fun deleteCachedTvShowsByType(cacheType: String)

    /**
     * Delete all expired cached TV shows.
     */
    @Query("DELETE FROM cached_tv_shows WHERE expirationTimestamp <= :currentTime")
    suspend fun deleteExpiredCachedTvShows(currentTime: Long = System.currentTimeMillis())

    /**
     * Delete all cached TV shows.
     */
    @Query("DELETE FROM cached_tv_shows")
    suspend fun deleteAllCachedTvShows()

    /**
     * Get the count of cached TV shows.
     */
    @Query("SELECT COUNT(*) FROM cached_tv_shows")
    suspend fun getCachedTvShowCount(): Int

    /**
     * Get the count of valid cached TV shows.
     */
    @Query("SELECT COUNT(*) FROM cached_tv_shows WHERE expirationTimestamp > :currentTime")
    suspend fun getValidCachedTvShowCount(currentTime: Long = System.currentTimeMillis()): Int

    /**
     * Search cached TV shows by name.
     */
    @Query("SELECT * FROM cached_tv_shows WHERE name LIKE '%' || :query || '%' ORDER BY fetchedTimestamp DESC")
    fun searchCachedTvShows(query: String): Flow<List<CachedTvShowEntity>>

    /**
     * Get recently fetched cached TV shows.
     */
    @Query("SELECT * FROM cached_tv_shows ORDER BY fetchedTimestamp DESC LIMIT :limit")
    fun getRecentlyFetchedTvShows(limit: Int = 20): Flow<List<CachedTvShowEntity>>
}
