package com.kiduyuk.klausk.kiduyutv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class for caching genre data from the TMDB API.
 * Genres rarely change and are frequently accessed, making them
 * ideal candidates for caching.
 *
 * @param id The unique TMDB identifier for the genre
 * @param name The name of the genre
 * @param mediaType The type of media this genre applies to ("movie" or "tv")
 * @param fetchedTimestamp When this data was fetched from the API
 */
@Entity(
    tableName = "genres",
    indices = [
        Index(value = ["id", "mediaType"], unique = true)
    ]
)
data class GenreEntity(
    @PrimaryKey(autoGenerate = true)
    val databaseId: Long = 0,
    val id: Int,
    val name: String,
    val mediaType: String,
    val fetchedTimestamp: Long = System.currentTimeMillis()
)
