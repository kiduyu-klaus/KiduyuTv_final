package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class for caching movie data from the TMDB API.
 * This enables offline support and reduces network calls by serving
 * cached content when appropriate.
 *
 * @param id The unique TMDB identifier for the movie
 * @param title The title of the movie
 * @param overview A brief summary of the movie's plot
 * @param posterPath The path to the movie's poster image
 * @param backdropPath The path to the movie's backdrop image
 * @param voteAverage The average vote score for the movie
 * @param releaseDate The release date of the movie
 * @param genreIdsJson JSON string of genre IDs for quick lookup
 * @param popularity The popularity score of the movie
 * @param fetchedTimestamp When this data was fetched from the API
 * @param expirationTimestamp When this cached data expires
 * @param cacheType The category of cache (e.g., "trending", "popular", "top_rated")
 */
@Entity(
    tableName = "cached_movies",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["fetchedTimestamp"]),
        Index(value = ["cacheType"])
    ]
)
data class CachedMovieEntity(
    @PrimaryKey
    val id: Int,
    val title: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val genreIdsJson: String?,
    val popularity: Double?,
    val fetchedTimestamp: Long = System.currentTimeMillis(),
    val expirationTimestamp: Long = System.currentTimeMillis() + CACHE_DURATION_MS,
    val cacheType: String = "default"
) {
    companion object {
        const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes default cache duration
        const val LONG_CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 hours for less frequently changing content
    }
}
