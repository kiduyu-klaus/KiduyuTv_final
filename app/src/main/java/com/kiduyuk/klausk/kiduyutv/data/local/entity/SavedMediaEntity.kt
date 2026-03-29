package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing a saved media item in the user's "My List".
 * This replaces the SharedPreferences-based MyListManager for better performance
 * and type-safe queries.
 *
 * @param id The unique TMDB identifier for the media item
 * @param mediaType The type of media ("movie" or "tv")
 * @param title The display title of the media item
 * @param posterPath The path to the poster image
 * @param savedTimestamp The timestamp when the item was saved
 * @param category Optional category for organization (e.g., "favorites", "watchlist")
 */
@Entity(
    tableName = "saved_media",
    indices = [
        Index(value = ["id", "mediaType"], unique = true),
        Index(value = ["savedTimestamp"])
    ]
)
data class SavedMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val databaseId: Long = 0,
    val id: Int,
    val mediaType: String,
    val title: String?,
    val posterPath: String?,
    val savedTimestamp: Long = System.currentTimeMillis(),
    val category: String? = null
)
