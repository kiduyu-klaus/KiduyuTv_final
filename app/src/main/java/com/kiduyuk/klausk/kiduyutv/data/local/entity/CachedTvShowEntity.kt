package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class for caching TV show data from the TMDB API.
 * This enables offline support and reduces network calls by serving
 * cached content when appropriate.
 *
 * @param id The unique TMDB identifier for the TV show
 * @param name The name of the TV show
 * @param overview A brief summary of the TV show's plot
 * @param posterPath The path to the TV show's poster image
 * @param backdropPath The path to the TV show's backdrop image
 * @param voteAverage The average vote score for the TV show
 * @param firstAirDate The first air date of the TV show
 * @param genreIdsJson JSON string of genre IDs for quick lookup
 * @param popularity The popularity score of the TV show
 * @param fetchedTimestamp When this data was fetched from the API
 * @param expirationTimestamp When this cached data expires
 * @param cacheType The category of cache (e.g., "trending", "popular", "top_rated")
 */
@Entity(
    tableName = "cached_tv_shows",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["fetchedTimestamp"]),
        Index(value = ["cacheType"])
    ]
)
data class CachedTvShowEntity(
    @PrimaryKey
    val id: Int,
    val name: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val firstAirDate: String?,
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
