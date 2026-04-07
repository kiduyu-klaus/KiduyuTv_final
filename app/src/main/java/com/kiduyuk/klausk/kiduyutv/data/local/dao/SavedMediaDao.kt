package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kiduyuk.klausk.kiduyutv.data.local.entity.SavedMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved media items (My List feature).
 * Provides type-safe database operations for managing user's saved movies and TV shows.
 */
@Dao
interface SavedMediaDao {

    /**
     * Get all saved media items as a Flow for reactive updates.
     * Results are ordered by most recently saved first.
     */
    @Query("SELECT * FROM saved_media ORDER BY savedTimestamp DESC")
    fun getAllSavedMedia(): Flow<List<SavedMediaEntity>>

    /**
     * Get all saved movies only.
     */
    @Query("SELECT * FROM saved_media WHERE mediaType = 'movie' ORDER BY savedTimestamp DESC")
    fun getSavedMovies(): Flow<List<SavedMediaEntity>>

    /**
     * Get all saved TV shows only.
     */
    @Query("SELECT * FROM saved_media WHERE mediaType = 'tv' ORDER BY savedTimestamp DESC")
    fun getSavedTvShows(): Flow<List<SavedMediaEntity>>

    /**
     * Get saved items by category.
     */
    @Query("SELECT * FROM saved_media WHERE category = :category ORDER BY savedTimestamp DESC")
    fun getSavedMediaByCategory(category: String): Flow<List<SavedMediaEntity>>

    /**
     * Check if a specific media item is saved.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_media WHERE id = :mediaId AND mediaType = :mediaType)")
    suspend fun isMediaSaved(mediaId: Int, mediaType: String): Boolean

    /**
     * Get a specific saved media item.
     */
    @Query("SELECT * FROM saved_media WHERE id = :mediaId AND mediaType = :mediaType LIMIT 1")
    suspend fun getSavedMedia(mediaId: Int, mediaType: String): SavedMediaEntity?

    /**
     * Insert a new saved media item.
     * Uses REPLACE strategy to handle duplicates gracefully.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedMedia(savedMedia: SavedMediaEntity)

    /**
     * Insert multiple saved media items.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSavedMedia(savedMediaList: List<SavedMediaEntity>)

    /**
     * Update an existing saved media item.
     */
    @Update
    suspend fun updateSavedMedia(savedMedia: SavedMediaEntity)

    /**
     * Delete a saved media item.
     */
    @Delete
    suspend fun deleteSavedMedia(savedMedia: SavedMediaEntity)

    /**
     * Delete a saved media item by ID and type.
     */
    @Query("DELETE FROM saved_media WHERE id = :mediaId AND mediaType = :mediaType")
    suspend fun deleteSavedMediaById(mediaId: Int, mediaType: String)

    /**
     * Delete all saved media items.
     */
    @Query("DELETE FROM saved_media")
    suspend fun deleteAllSavedMedia()

    /**
     * Delete saved media items by type.
     */
    @Query("DELETE FROM saved_media WHERE mediaType = :mediaType")
    suspend fun deleteSavedMediaByType(mediaType: String)

    /**
     * Get the count of saved items.
     */
    @Query("SELECT COUNT(*) FROM saved_media")
    suspend fun getSavedMediaCount(): Int

    /**
     * Get the count of saved movies.
     */
    @Query("SELECT COUNT(*) FROM saved_media WHERE mediaType = 'movie'")
    suspend fun getSavedMoviesCount(): Int

    /**
     * Get the count of saved TV shows.
     */
    @Query("SELECT COUNT(*) FROM saved_media WHERE mediaType = 'tv'")
    suspend fun getSavedTvShowsCount(): Int

    /**
     * Search saved media by title.
     */
    @Query("SELECT * FROM saved_media WHERE title LIKE '%' || :query || '%' ORDER BY savedTimestamp DESC")
    fun searchSavedMedia(query: String): Flow<List<SavedMediaEntity>>
}
