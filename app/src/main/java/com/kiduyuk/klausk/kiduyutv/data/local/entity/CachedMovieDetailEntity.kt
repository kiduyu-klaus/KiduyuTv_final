package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class for caching detailed movie information from the TMDB API.
 * This includes extended information like runtime, production companies,
 * and collection data that requires additional API calls to fetch.
 *
 * @param id The unique TMDB identifier for the movie
 * @param title The title of the movie
 * @param overview A brief summary of the movie's plot
 * @param posterPath The path to the movie's poster image
 * @param backdropPath The path to the movie's backdrop image
 * @param voteAverage The average vote score for the movie
 * @param releaseDate The release date of the movie
 * @param runtime The runtime of the movie in minutes
 * @param genresJson JSON string containing genre information
 * @param productionCompaniesJson JSON string containing production company information
 * @param collectionId The ID of the collection this movie belongs to (if any)
 * @param collectionName The name of the collection this movie belongs to (if any)
 * @param fetchedTimestamp When this data was fetched from the API
 * @param expirationTimestamp When this cached data expires
 */
@Entity(
    tableName = "cached_movie_details",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["fetchedTimestamp"])
    ]
)
data class CachedMovieDetailEntity(
    @PrimaryKey
    val id: Int,
    val title: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val runtime: Int?,
    val genresJson: String?,
    val productionCompaniesJson: String?,
    val collectionId: Int?,
    val collectionName: String?,
    val fetchedTimestamp: Long = System.currentTimeMillis(),
    val expirationTimestamp: Long = System.currentTimeMillis() + CACHE_DURATION_MS
) {
    companion object {
        const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour for detail pages
    }
}
