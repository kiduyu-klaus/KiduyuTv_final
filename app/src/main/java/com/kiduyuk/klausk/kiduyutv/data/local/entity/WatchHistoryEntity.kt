package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing a watch history item.
 * This replaces the SharedPreferences-based watch history in TmdbRepository.
 *
 * @param id The unique TMDB identifier for the media item
 * @param mediaType The type of media ("movie" or "tv")
 * @param title The display title of the media item
 * @param posterPath The path to the poster image
 * @param backdropPath The path to the backdrop image
 * @param seasonNumber The season number for TV shows (null for movies)
 * @param episodeNumber The episode number for TV shows (null for movies)
 * @param lastWatchedTimestamp The timestamp when the item was last watched
 * @param playbackPosition The playback position in milliseconds (for resume functionality)
 */
@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["id", "mediaType"], unique = true),
        Index(value = ["lastWatchedTimestamp"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val databaseId: Long = 0,
    val id: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val playbackPosition: Long = 0L
)
