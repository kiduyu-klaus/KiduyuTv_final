package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class for caching detailed TV show information from the TMDB API.
 * This includes extended information like season counts, networks, and
 * episode data that requires additional API calls to fetch.
 *
 * @param id The unique TMDB identifier for the TV show
 * @param name The name of the TV show
 * @param overview A brief summary of the TV show's plot
 * @param posterPath The path to the TV show's poster image
 * @param backdropPath The path to the TV show's backdrop image
 * @param voteAverage The average vote score for the TV show
 * @param firstAirDate The first air date of the TV show
 * @param numberOfSeasons The total number of seasons
 * @param numberOfEpisodes The total number of episodes
 * @param genresJson JSON string containing genre information
 * @param networksJson JSON string containing network information
 * @param fetchedTimestamp When this data was fetched from the API
 * @param expirationTimestamp When this cached data expires
 */
@Entity(
    tableName = "cached_tv_show_details",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["fetchedTimestamp"])
    ]
)
data class CachedTvShowDetailEntity(
    @PrimaryKey
    val id: Int,
    val name: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val firstAirDate: String?,
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val genresJson: String?,
    val networksJson: String?,
    val fetchedTimestamp: Long = System.currentTimeMillis(),
    val expirationTimestamp: Long = System.currentTimeMillis() + CACHE_DURATION_MS
) {
    companion object {
        const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour for detail pages
    }
}
