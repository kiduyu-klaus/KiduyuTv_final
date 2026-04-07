package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kiduyuk.klausk.kiduyutv.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for watch history tracking.
 * Enables efficient tracking of user's viewing history with support
 * for resume playback functionality and "Continue Watching" features.
 */
@Dao
interface WatchHistoryDao {

    /**
     * Get all watch history items as a Flow for reactive updates.
     * Results are ordered by most recently watched first.
     */
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    /**
     * Get all watch history items as a List (suspend function).
     * Useful for batch operations like refreshing all images.
     */
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    suspend fun getAllWatchHistoryItems(): List<WatchHistoryEntity>

    /**
     * Get recent watch history limited by count.
     * Useful for displaying "Continue Watching" section.
     */
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    fun getRecentWatchHistory(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    /**
     * Get watch history for movies only.
     */
    @Query("SELECT * FROM watch_history WHERE mediaType = 'movie' ORDER BY lastWatchedTimestamp DESC")
    fun getMovieWatchHistory(): Flow<List<WatchHistoryEntity>>

    /**
     * Get watch history for TV shows only.
     */
    @Query("SELECT * FROM watch_history WHERE mediaType = 'tv' ORDER BY lastWatchedTimestamp DESC")
    fun getTvShowWatchHistory(): Flow<List<WatchHistoryEntity>>

    /**
     * Get items with incomplete playback (for Continue Watching feature).
     * Assumes playback is incomplete if position > 0 and < some threshold.
     */
    @Query("""
        SELECT * FROM watch_history 
        WHERE playbackPosition > 0 
        ORDER BY lastWatchedTimestamp DESC 
        LIMIT :limit
    """)
    fun getContinueWatching(limit: Int = 10): Flow<List<WatchHistoryEntity>>

    /**
     * Get a specific watch history item.
     */
    @Query("SELECT * FROM watch_history WHERE id = :mediaId AND mediaType = :mediaType LIMIT 1")
    suspend fun getWatchHistoryItem(mediaId: Int, mediaType: String): WatchHistoryEntity?

    /**
     * Check if a media item exists in watch history.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM watch_history WHERE id = :mediaId AND mediaType = :mediaType)")
    suspend fun isInWatchHistory(mediaId: Int, mediaType: String): Boolean

    /**
     * Insert or update a watch history item.
     * Uses REPLACE strategy to update existing entries or create new ones.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(watchHistory: WatchHistoryEntity)

    /**
     * Update playback position for a specific item.
     */
    @Query("""
        UPDATE watch_history 
        SET playbackPosition = :position, 
            lastWatchedTimestamp = :timestamp 
        WHERE id = :mediaId AND mediaType = :mediaType
    """)
    suspend fun updatePlaybackPosition(mediaId: Int, mediaType: String, position: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Update the episode info for a TV show.
     */
    @Query("""
        UPDATE watch_history 
        SET seasonNumber = :seasonNumber, 
            episodeNumber = :episodeNumber,
            lastWatchedTimestamp = :timestamp 
        WHERE id = :mediaId AND mediaType = :mediaType
    """)
    suspend fun updateEpisodeInfo(mediaId: Int, mediaType: String, seasonNumber: Int, episodeNumber: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Update the last watched timestamp.
     */
    @Query("""
        UPDATE watch_history 
        SET lastWatchedTimestamp = :timestamp 
        WHERE id = :mediaId AND mediaType = :mediaType
    """)
    suspend fun updateLastWatchedTimestamp(mediaId: Int, mediaType: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Delete a watch history item.
     */
    @Delete
    suspend fun deleteWatchHistory(watchHistory: WatchHistoryEntity)

    /**
     * Delete a watch history item by ID and type.
     */
    @Query("DELETE FROM watch_history WHERE id = :mediaId AND mediaType = :mediaType")
    suspend fun deleteWatchHistoryById(mediaId: Int, mediaType: String)

    /**
     * Delete all watch history.
     */
    @Query("DELETE FROM watch_history")
    suspend fun deleteAllWatchHistory()

    /**
     * Delete watch history older than specified timestamp.
     * Useful for cleaning up old entries automatically.
     */
    @Query("DELETE FROM watch_history WHERE lastWatchedTimestamp < :timestamp")
    suspend fun deleteOldWatchHistory(timestamp: Long)

    /**
     * Keep only the most recent N entries and delete the rest.
     */
    @Query("""
        DELETE FROM watch_history 
        WHERE databaseId NOT IN (
            SELECT databaseId FROM watch_history 
            ORDER BY lastWatchedTimestamp DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun trimWatchHistory(keepCount: Int)

    /**
     * Get the count of watch history items.
     */
    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun getWatchHistoryCount(): Int

    /**
     * Get watch history within a date range.
     */
    @Query("""
        SELECT * FROM watch_history 
        WHERE lastWatchedTimestamp BETWEEN :startTimestamp AND :endTimestamp 
        ORDER BY lastWatchedTimestamp DESC
    """)
    fun getWatchHistoryInRange(startTimestamp: Long, endTimestamp: Long): Flow<List<WatchHistoryEntity>>

    /**
     * Update watch history item with fetched TMDB details.
     * Used to fill in missing fields like overview, poster, backdrop, etc.
     */
    @Query("""
        UPDATE watch_history 
        SET title = :title,
            overview = :overview,
            posterPath = :posterPath,
            backdropPath = :backdropPath,
            voteAverage = :voteAverage,
            releaseDate = :releaseDate
        WHERE id = :mediaId AND mediaType = :mediaType
    """)
    suspend fun updateWatchHistoryDetails(
        mediaId: Int,
        mediaType: String,
        title: String?,
        overview: String?,
        posterPath: String?,
        backdropPath: String?,
        voteAverage: Double,
        releaseDate: String?
    )

    /**
     * Get all watch history items that need TMDB detail fetching.
     * Items where any critical field is missing or empty:
     * - posterPath is NULL or empty
     * - backdropPath is NULL or empty
     * - title is NULL or empty
     * - voteAverage is 0.0 (missing)
     * - overview is NULL or empty
     */
    @Query("""
        SELECT * FROM watch_history 
        WHERE posterPath IS NULL OR posterPath = '' 
           OR backdropPath IS NULL OR backdropPath = ''
           OR title IS NULL OR title = ''
           OR voteAverage = 0.0
           OR overview IS NULL OR overview = ''
        ORDER BY lastWatchedTimestamp DESC
    """)
    suspend fun getWatchHistoryItemsNeedingDetails(): List<WatchHistoryEntity>
}