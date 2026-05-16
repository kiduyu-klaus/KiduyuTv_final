package com.kiduyuk.klausk.kiduyutv.ai

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.SearchResult
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository

/**
 * Service class for searching movies and TV shows via TMDB API.
 * Used by the AI assistant to provide real-time, accurate search results.
 *
 * This bridges the gap between AI understanding and actual TMDB data,
 * ensuring that users get correct IDs and information.
 */
class AiSearchService(
    private val tmdbRepository: TmdbRepository
) {
    private val TAG = "AiSearchService"

    /**
     * Result class for AI search operations.
     * Contains both the search results and formatted action data for UI.
     */
    data class SearchResultData(
        val type: MediaType,
        val results: List<SearchItem>,
        val formattedActions: List<String> // Pre-formatted action strings for AI response
    )

    /**
     * Individual search result item with all necessary data.
     */
    data class SearchItem(
        val id: Int,
        val title: String,
        val overview: String,
        val posterPath: String?,
        val voteAverage: Double,
        val releaseDate: String,
        val mediaType: MediaType
    )

    enum class MediaType {
        MOVIE,
        TV_SHOW,
        UNKNOWN
    }

    /**
     * Searches for movies matching the query.
     */
    suspend fun searchMovies(query: String): SearchResultData {
        return try {
            Log.d(TAG, "Searching movies for: $query")
            val result = tmdbRepository.searchMovies(query)
            
            result.fold(
                onSuccess = { movies ->
                    val items = movies.take(5).map { movie ->
                        SearchItem(
                            id = movie.id,
                            title = movie.title,
                            overview = movie.overview?.take(200) ?: "",
                            posterPath = movie.posterPath,
                            voteAverage = movie.voteAverage,
                            releaseDate = movie.releaseDate ?: "",
                            mediaType = MediaType.MOVIE
                        )
                    }
                    
                    val actions = items.map { item ->
                        "[ACTION:MOVIE|Watch Now|View ${item.title}|id=${item.id};title=${item.title};rating=${item.voteAverage}]"
                    }
                    
                    Log.d(TAG, "Found ${items.size} movie results")
                    SearchResultData(MediaType.MOVIE, items, actions)
                },
                onFailure = { error ->
                    Log.e(TAG, "Movie search failed: ${error.message}")
                    SearchResultData(MediaType.MOVIE, emptyList(), emptyList())
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in movie search: ${e.message}")
            SearchResultData(MediaType.MOVIE, emptyList(), emptyList())
        }
    }

    /**
     * Searches for TV shows matching the query.
     */
    suspend fun searchTvShows(query: String): SearchResultData {
        return try {
            Log.d(TAG, "Searching TV shows for: $query")
            val result = tmdbRepository.searchTvShows(query)
            
            result.fold(
                onSuccess = { tvShows ->
                    val items = tvShows.take(5).map { show ->
                        SearchItem(
                            id = show.id,
                            title = show.name,
                            overview = show.overview?.take(200) ?: "",
                            posterPath = show.posterPath,
                            voteAverage = show.voteAverage,
                            releaseDate = show.firstAirDate ?: "",
                            mediaType = MediaType.TV_SHOW
                        )
                    }
                    
                    val actions = items.map { item ->
                        "[ACTION:TV_SHOW|Watch Now|View ${item.title}|id=${item.id};title=${item.title};rating=${item.voteAverage}]"
                    }
                    
                    Log.d(TAG, "Found ${items.size} TV show results")
                    SearchResultData(MediaType.TV_SHOW, items, actions)
                },
                onFailure = { error ->
                    Log.e(TAG, "TV show search failed: ${error.message}")
                    SearchResultData(MediaType.TV_SHOW, emptyList(), emptyList())
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in TV show search: ${e.message}")
            SearchResultData(MediaType.TV_SHOW, emptyList(), emptyList())
        }
    }

    /**
     * Searches for both movies and TV shows.
     * Returns combined results with clear media type indicators.
     */
    suspend fun searchMulti(query: String): SearchResultData {
        return try {
            Log.d(TAG, "Searching multi for: $query")
            val result = tmdbRepository.searchMulti(query)
            
            result.fold(
                onSuccess = { searchResults ->
                    val movieItems = searchResults
                        .filter { it.mediaType == "movie" }
                        .take(3)
                        .map { searchResult ->
                            SearchItem(
                                id = searchResult.id,
                                title = searchResult.title ?: "",
                                overview = searchResult.overview?.take(200) ?: "",
                                posterPath = searchResult.posterPath,
                                voteAverage = searchResult.voteAverage,
                                releaseDate = searchResult.releaseDate ?: "",
                                mediaType = MediaType.MOVIE
                            )
                        }
                    
                    val tvItems = searchResults
                        .filter { it.mediaType == "tv" }
                        .take(3)
                        .map { searchResult ->
                            SearchItem(
                                id = searchResult.id,
                                title = searchResult.name ?: "",
                                overview = searchResult.overview?.take(200) ?: "",
                                posterPath = searchResult.posterPath,
                                voteAverage = searchResult.voteAverage,
                                releaseDate = searchResult.firstAirDate ?: "",
                                mediaType = MediaType.TV_SHOW
                            )
                        }
                    
                    val allItems = movieItems + tvItems
                    
                    val actions = allItems.map { item ->
                        val typeStr = if (item.mediaType == MediaType.MOVIE) "MOVIE" else "TV_SHOW"
                        "[ACTION:${typeStr}|Watch Now|View ${item.title}|id=${item.id};title=${item.title};rating=${item.voteAverage}]"
                    }
                    
                    Log.d(TAG, "Found ${allItems.size} multi results")
                    SearchResultData(MediaType.UNKNOWN, allItems, actions)
                },
                onFailure = { error ->
                    Log.e(TAG, "Multi search failed: ${error.message}")
                    SearchResultData(MediaType.UNKNOWN, emptyList(), emptyList())
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in multi search: ${e.message}")
            SearchResultData(MediaType.UNKNOWN, emptyList(), emptyList())
        }
    }

    /**
     * Generates a human-readable response with search results.
     * This is used by the AI to present search results to users.
     */
    fun formatSearchResponse(searchData: SearchResultData, originalQuery: String): String {
        if (searchData.results.isEmpty()) {
            return "I couldn't find any results for '$originalQuery'. Try a different search term or check your spelling."
        }

        val sb = StringBuilder()
        sb.append("I found some results for '$originalQuery':\n\n")

        searchData.results.forEachIndexed { index, item ->
            val rating = String.format("%.1f", item.voteAverage)
            sb.append("${index + 1}. **${item.title}** (${item.mediaType.name.replace("_", " ")})\n")
            sb.append("   Rating: ⭐ $rating | ${item.releaseDate.take(4)}\n")
            if (item.overview.isNotEmpty()) {
                sb.append("   ${item.overview.take(100)}...\n")
            }
            sb.append("   ${searchData.formattedActions[index]}\n\n")
        }

        sb.append("Tap any result above to view details!")
        return sb.toString()
    }

    /**
     * Detects if the user's message is asking for a movie or TV show search.
     * Returns the search query and intended media type.
     */
    fun detectSearchIntent(message: String): SearchIntent? {
        val lowerMessage = message.lowercase()
        
        // TV Show indicators
        val tvIndicators = listOf(
            "watch", "tv show", "series", "episode", "season",
            "netflix", "hulu", "prime", "hbo", "disney+", "show me",
            "i want to watch", "find me a show", "recommend a series"
        )
        
        // Movie indicators
        val movieIndicators = listOf(
            "movie", "film", "cinema", "watch a movie",
            "i want to see", "find me a movie", "recommend a film"
        )
        
        // Check if it's specifically a TV show request
        val isTvRequest = tvIndicators.any { lowerMessage.contains(it) }
        val isMovieRequest = movieIndicators.any { lowerMessage.contains(it) }
        
        // Extract potential search query
        val searchQuery = extractSearchQuery(message)
        
        if (searchQuery.isNullOrBlank()) {
            return null
        }
        
        return when {
            isTvRequest && !isMovieRequest -> SearchIntent(searchQuery, MediaType.TV_SHOW)
            isMovieRequest && !isTvRequest -> SearchIntent(searchQuery, MediaType.MOVIE)
            else -> SearchIntent(searchQuery, MediaType.UNKNOWN) // Let the API decide
        }
    }

    /**
     * Extracts a search query from the user's message.
     */
    private fun extractSearchQuery(message: String): String? {
        // Remove common filler words and questions
        val cleaned = message
            .replace(Regex("(can you|could you|please|tell me|show me|find me|search for|i want|i'm looking for)"), "")
            .replace(Regex("(movie|show|series|tv|tv show)"), "")
            .replace(Regex("(about|with|like|named|called)"), " ")
            .trim()
        
        // If the cleaned message is too short, return original
        return if (cleaned.length >= 2) cleaned else null
    }

    /**
     * Data class for search intent detection.
     */
    data class SearchIntent(
        val query: String,
        val mediaType: MediaType
    )
}