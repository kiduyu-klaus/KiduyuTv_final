package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.local.entity.WatchHistoryEntity
import com.kiduyuk.klausk.kiduyutv.data.model.MovieDetail
import com.kiduyuk.klausk.kiduyutv.data.model.TvShowDetail
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Utility object for enriching WatchHistoryItem data with TMDB details.
 * 
 * This class provides functionality to fetch missing information from TMDB
 * and update the local database when watch history items have incomplete data.
 * 
 * It checks the following fields in WatchHistoryItem:
 * - title: String - Required, fetched as title (movies) or name (TV shows)
 * - overview: String? - Optional description/synopsis
 * - posterPath: String? - Poster image path
 * - backdropPath: String? - Backdrop image path
 * - voteAverage: Double - Rating score (0.0 means missing)
 * - releaseDate: String? - Release date (movies) or first air date (TV shows)
 * 
 * Usage:
 * ```
 * // Enrich all items with missing data
 * WatchHistoryEnricher.enrichAllMissingItems(context)
 * 
 * // Enrich a single item
 * WatchHistoryEnricher.enrichSingleItem(context, mediaId, mediaType)
 * 
 * // Check if an item needs enrichment
 * val needsEnrichment = WatchHistoryEnricher.itemNeedsEnrichment(item)
 * ```
 */
object WatchHistoryEnricher {

    private const val TAG = "WatchHistoryEnricher"
    private val api = ApiClient.tmdbApiService

    /**
     * Checks if a WatchHistoryItem needs enrichment.
     * An item needs enrichment when any of the critical fields are missing or empty.
     * 
     * @param item The WatchHistoryItem to check
     * @return true if the item needs TMDB detail enrichment, false otherwise
     */
    fun itemNeedsEnrichment(item: WatchHistoryItem): Boolean {
        return item.title.isBlank() ||
                item.posterPath.isNullOrBlank() ||
                item.backdropPath.isNullOrBlank() ||
                item.voteAverage == 0.0 ||
                item.releaseDate.isNullOrBlank()
    }

    /**
     * Checks if a WatchHistoryEntity needs enrichment.
     * An entity needs enrichment when any of the critical fields are missing or empty.
     * 
     * @param entity The WatchHistoryEntity to check
     * @return true if the entity needs TMDB detail enrichment, false otherwise
     */
    fun entityNeedsEnrichment(entity: WatchHistoryEntity): Boolean {
        return entity.title.isNullOrBlank() ||
                entity.posterPath.isNullOrBlank() ||
                entity.backdropPath.isNullOrBlank() ||
                entity.voteAverage == 0.0 ||
                entity.releaseDate.isNullOrBlank()
    }

    /**
     * Enriches all watch history items that have missing or incomplete data.
     * This method fetches TMDB details for items where any of the following are missing:
     * title, overview, posterPath, backdropPath, voteAverage, or releaseDate.
     * 
     * This is the primary method to use for ensuring the "Continue Watching" row
     * displays complete and accurate information for all items.
     * 
     * @param context Context required for database operations
     * @return Number of items that were successfully enriched
     */
    suspend fun enrichAllMissingItems(context: Context): Int {
        DatabaseManager.init(context)

        val itemsNeedingDetails = DatabaseManager.getWatchHistoryItemsNeedingDetails()

        if (itemsNeedingDetails.isEmpty()) {
            Log.i(TAG, "No watch history items need TMDB detail enrichment")
            return 0
        }

        Log.i(TAG, "Found ${itemsNeedingDetails.size} watch history items needing TMDB detail enrichment")

        var enrichedCount = 0

        for (entity in itemsNeedingDetails) {
            val success = enrichEntity(entity)
            if (success) {
                enrichedCount++
            }
        }

        Log.i(TAG, "Successfully enriched $enrichedCount out of ${itemsNeedingDetails.size} items")
        return enrichedCount
    }

    /**
     * Enriches a single watch history item with TMDB details.
     * Use this when you want to update a specific item rather than all items.
     * 
     * @param context Context required for database operations
     * @param mediaId The TMDB ID of the media
     * @param mediaType "movie" or "tv"
     * @return true if enrichment was successful, false otherwise
     */
    suspend fun enrichSingleItem(
        context: Context,
        mediaId: Int,
        mediaType: String
    ): Boolean {
        DatabaseManager.init(context)

        return try {
            val dao = DatabaseManager.watchHistoryDao()
            val entity = withContext(Dispatchers.IO) {
                dao.getWatchHistoryItem(mediaId, mediaType)
            }

            entity?.let { enrichEntity(it) } ?: run {
                Log.w(TAG, "Watch history item not found: $mediaId ($mediaType)")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching item $mediaId: ${e.message}")
            false
        }
    }

    /**
     * Enriches a WatchHistoryEntity with TMDB details.
     * 
     * @param entity The entity to enrich
     * @return true if enrichment was successful, false otherwise
     */
    private suspend fun enrichEntity(entity: WatchHistoryEntity): Boolean {
        return try {
            val details = fetchTmdbDetails(entity.id, entity.mediaType)

            if (details != null) {
                val (title, overview, posterPath, backdropPath, voteAverage, releaseDate) = extractDetails(details)

                // Update only missing fields
                val updatedTitle = coalesce(entity.title, title)
                val updatedOverview = coalesce(entity.overview, overview)
                val updatedPosterPath = coalesce(entity.posterPath, posterPath)
                val updatedBackdropPath = coalesce(entity.backdropPath, backdropPath)
                val updatedVoteAverage = if (entity.voteAverage == 0.0 && voteAverage > 0) voteAverage else entity.voteAverage
                val updatedReleaseDate = coalesce(entity.releaseDate, releaseDate)

                DatabaseManager.updateWatchHistoryDetails(
                    mediaId = entity.id,
                    mediaType = entity.mediaType,
                    title = updatedTitle,
                    overview = updatedOverview,
                    posterPath = updatedPosterPath,
                    backdropPath = updatedBackdropPath,
                    voteAverage = updatedVoteAverage,
                    releaseDate = updatedReleaseDate
                )

                Log.i(TAG, "Enriched watch history item ${entity.id} (${entity.mediaType})")
                true
            } else {
                Log.w(TAG, "Failed to fetch TMDB details for ${entity.id} (${entity.mediaType})")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching entity ${entity.id}: ${e.message}")
            false
        }
    }

    /**
     * Fetches TMDB details for a media item.
     * 
     * @param mediaId The TMDB ID
     * @param mediaType "movie" or "tv"
     * @return MovieDetail, TvShowDetail, or null if fetch failed
     */
    private suspend fun fetchTmdbDetails(mediaId: Int, mediaType: String): Any? {
        return try {
            when (mediaType) {
                "movie" -> api.getMovieDetail(mediaId)
                "tv" -> api.getTvShowDetail(mediaId)
                else -> {
                    Log.w(TAG, "Unknown media type: $mediaType")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB details for $mediaId: ${e.message}")
            null
        }
    }

    /**
     * Extracts relevant details from TMDB response.
     * 
     * @param details The TMDB details (MovieDetail or TvShowDetail)
     * @return Tuple of (title, overview, posterPath, backdropPath, voteAverage, releaseDate)
     */
    private fun extractDetails(details: Any): EnrichmentDetails {
        return when (details) {
            is MovieDetail -> EnrichmentDetails(
                title = details.title,
                overview = details.overview,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                voteAverage = details.voteAverage,
                releaseDate = details.releaseDate
            )
            is TvShowDetail -> EnrichmentDetails(
                title = details.name,
                overview = details.overview,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                voteAverage = details.voteAverage,
                releaseDate = details.firstAirDate
            )
            else -> EnrichmentDetails(
                title = null,
                overview = null,
                posterPath = null,
                backdropPath = null,
                voteAverage = 0.0,
                releaseDate = null
            )
        }
    }

    /**
     * Returns the first non-blank value.
     */
    private fun coalesce(existing: String?, new: String?): String? {
        return if (!existing.isNullOrBlank()) existing else new
    }

    /**
     * Data class holding extracted enrichment details.
     */
    private data class EnrichmentDetails(
        val title: String?,
        val overview: String?,
        val posterPath: String?,
        val backdropPath: String?,
        val voteAverage: Double,
        val releaseDate: String?
    )

    /**
     * Refreshes the watch history list by re-fetching from the database.
     * Call this after enrichment to get updated items.
     * 
     * @param context Context required for database operations
     * @return List of enriched WatchHistoryItem objects
     */
    suspend fun getEnrichedWatchHistory(context: Context): List<WatchHistoryItem> {
        DatabaseManager.init(context)

        return try {
            val dao = DatabaseManager.watchHistoryDao()
            withContext(Dispatchers.IO) {
                dao.getRecentWatchHistory(20).first().map {
                    DatabaseManager.entityToWatchHistoryItem(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enriched watch history: ${e.message}")
            emptyList()
        }
    }
}
