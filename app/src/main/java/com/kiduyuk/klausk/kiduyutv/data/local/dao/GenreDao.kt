package com.kiduyuk.klausk.kiduyutv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiduyuk.klausk.kiduyutv.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for caching genre data from TMDB API.
 * Genres rarely change and are frequently accessed, making them
 * ideal candidates for persistent caching.
 */
@Dao
interface GenreDao {

    /**
     * Get all cached genres.
     */
    @Query("SELECT * FROM genres ORDER BY name ASC")
    fun getAllGenres(): Flow<List<GenreEntity>>

    /**
     * Get all movie genres.
     */
    @Query("SELECT * FROM genres WHERE mediaType = 'movie' ORDER BY name ASC")
    fun getMovieGenres(): Flow<List<GenreEntity>>

    /**
     * Get all TV show genres.
     */
    @Query("SELECT * FROM genres WHERE mediaType = 'tv' ORDER BY name ASC")
    fun getTvShowGenres(): Flow<List<GenreEntity>>

    /**
     * Get a specific genre by ID and media type.
     */
    @Query("SELECT * FROM genres WHERE id = :genreId AND mediaType = :mediaType LIMIT 1")
    suspend fun getGenre(genreId: Int, mediaType: String): GenreEntity?

    /**
     * Get genres by IDs for a specific media type.
     */
    @Query("SELECT * FROM genres WHERE id IN (:genreIds) AND mediaType = :mediaType")
    suspend fun getGenresByIds(genreIds: List<Int>, mediaType: String): List<GenreEntity>

    /**
     * Check if genres are cached for a specific media type.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM genres WHERE mediaType = :mediaType LIMIT 1)")
    suspend fun areGenresCached(mediaType: String): Boolean

    /**
     * Insert or replace genres.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<GenreEntity>)

    /**
     * Insert or replace a single genre.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenre(genre: GenreEntity)

    /**
     * Delete genres for a specific media type.
     */
    @Query("DELETE FROM genres WHERE mediaType = :mediaType")
    suspend fun deleteGenresByType(mediaType: String)

    /**
     * Delete all genres.
     */
    @Query("DELETE FROM genres")
    suspend fun deleteAllGenres()

    /**
     * Get the count of genres.
     */
    @Query("SELECT COUNT(*) FROM genres")
    suspend fun getGenreCount(): Int

    /**
     * Search genres by name.
     */
    @Query("SELECT * FROM genres WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchGenres(query: String): Flow<List<GenreEntity>>
}
