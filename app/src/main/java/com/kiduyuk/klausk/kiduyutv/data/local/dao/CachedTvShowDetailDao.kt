package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiduyuk.klausk.kiduyutv.data.local.entity.CachedTvShowDetailEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for caching detailed TV show information.
 * Stores extended TV show data including season counts, networks,
 * and episode information that requires additional API calls.
 */
@Dao
interface CachedTvShowDetailDao {

    /**
     * Get cached TV show detail by ID.
     */
    @Query("SELECT * FROM cached_tv_show_details WHERE id = :tvShowId")
    suspend fun getCachedTvShowDetail(tvShowId: Int): CachedTvShowDetailEntity?

    /**
     * Get cached TV show detail by ID as Flow.
     */
    @Query("SELECT * FROM cached_tv_show_details WHERE id = :tvShowId")
    fun getCachedTvShowDetailFlow(tvShowId: Int): Flow<CachedTvShowDetailEntity?>

    /**
     * Check if TV show detail is cached and still valid.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_tv_show_details WHERE id = :tvShowId AND expirationTimestamp > :currentTime)")
    suspend fun isTvShowDetailCached(tvShowId: Int, currentTime: Long = System.currentTimeMillis()): Boolean

    /**
     * Insert or replace cached TV show detail.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTvShowDetail(tvShowDetail: CachedTvShowDetailEntity)

    /**
     * Delete cached TV show detail.
     */
    @Query("DELETE FROM cached_tv_show_details WHERE id = :tvShowId")
    suspend fun deleteCachedTvShowDetail(tvShowId: Int)

    /**
     * Delete all expired cached TV show details.
     */
    @Query("DELETE FROM cached_tv_show_details WHERE expirationTimestamp <= :currentTime")
    suspend fun deleteExpiredCachedTvShowDetails(currentTime: Long = System.currentTimeMillis())

    /**
     * Delete all cached TV show details.
     */
    @Query("DELETE FROM cached_tv_show_details")
    suspend fun deleteAllCachedTvShowDetails()

    /**
     * Get the count of cached TV show details.
     */
    @Query("SELECT COUNT(*) FROM cached_tv_show_details")
    suspend fun getCachedTvShowDetailCount(): Int
}
