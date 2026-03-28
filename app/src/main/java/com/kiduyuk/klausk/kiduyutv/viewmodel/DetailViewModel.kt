package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.*
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the detail screens (Movie and TV Show).
 * @param isLoading Indicates if data is currently being loaded.
 * @param movieDetail The detailed information of a movie, if applicable.
 * @param tvShowDetail The detailed information of a TV show, if applicable.
 * @param seasons List of seasons for a TV show.
 * @param episodes List of episodes for a specific season.
 * @param similarMovies List of similar movies.
 * @param similarTvShows List of similar TV shows.
 * @param isInMyList Indicates if the current item is in the user's personal list.
 * @param trailerKey The YouTube key for the trailer video, if available.
 * @param error An error message if data loading fails.
 */
data class DetailUiState(
    val isLoading: Boolean = true,
    val movieDetail: MovieDetail? = null,
    val tvShowDetail: TvShowDetail? = null,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val similarMovies: List<Movie> = emptyList(),
    val similarTvShows: List<TvShow> = emptyList(),
    val collectionDetail: CollectionDetail? = null,
    val isInMyList: Boolean = false,
    val trailerKey: String? = null,
    val error: String? = null
)

/**
 * ViewModel for the detail screens, responsible for fetching and managing detailed data
 * for movies and TV shows, including seasons and episodes.
 */
class DetailViewModel : ViewModel() {

    // Repository for fetching data from TMDB API.
    private val repository = TmdbRepository()

    // MutableStateFlow to hold and update the UI state.
    private val _uiState = MutableStateFlow(DetailUiState())
    // Publicly exposed StateFlow for UI observation.
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "DetailViewModel"
    }

    /**
     * Loads detailed information for a specific movie.
     * @param movieId The ID of the movie to load.
     */
    fun loadMovieDetail(movieId: Int) {
        viewModelScope.launch {
            // Set loading state to true.
            _uiState.value = DetailUiState(isLoading = true)

            try {
                // Fetch movie details and recommended movies.
                val movieDetailResult = repository.getMovieDetail(movieId)
                val recommendedMoviesResult = repository.getRecommendedMovies(movieId)
                val videosResult = repository.getMovieVideos(movieId)

                val movieDetail = movieDetailResult.getOrNull()
                val similarMovies = recommendedMoviesResult.getOrNull()?.take(10) ?: repository.getTrendingMoviesToday().getOrNull()?.take(10) ?: emptyList()
                val videos = videosResult.getOrNull() ?: emptyList()

                // Fetch collection details if the movie belongs to one.
                val collectionDetail = movieDetail?.belongsToCollection?.id?.let { collectionId ->
                    repository.getCollectionDetails(collectionId).getOrNull()
                }

                // Find the first YouTube trailer.
                val trailerKey = videos.firstOrNull {
                    it.site.equals("YouTube", ignoreCase = true) &&
                            it.type.equals("Trailer", ignoreCase = true)
                }?.key ?: videos.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key

                // Check if in My List
                val isInMyList = MyListManager.isInList(movieId, "movie")

                // Update UI state with fetched data.
                _uiState.value = DetailUiState(
                    isLoading = false,
                    movieDetail = movieDetail,
                    similarMovies = similarMovies,
                    collectionDetail = collectionDetail,
                    trailerKey = trailerKey,
                    isInMyList = isInMyList
                )
            } catch (e: Exception) {
                // Handle errors.
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load movie details"
                )
            }
        }
    }

    /**
     * Loads detailed information for a specific TV show.
     * @param tvId The ID of the TV show to load.
     */
    fun loadTvShowDetail(tvId: Int) {
        viewModelScope.launch {
            // Set loading state to true.
            _uiState.value = DetailUiState(isLoading = true)

            try {
                // Fetch TV show details and recommended TV shows.
                val tvShowDetail = repository.getTvShowDetail(tvId).getOrElse { throw it }
                val recommendedTvShowsResult = repository.getRecommendedTvShows(tvId)
                val videosResult = repository.getTvShowVideos(tvId)

                val similarTvShows = recommendedTvShowsResult.getOrNull()?.take(10) ?: repository.getTrendingTvToday().getOrNull()?.take(10) ?: emptyList()
                val videos = videosResult.getOrNull() ?: emptyList()

                // Find the first YouTube trailer.
                val trailerKey = videos.firstOrNull {
                    it.site.equals("YouTube", ignoreCase = true) &&
                            it.type.equals("Trailer", ignoreCase = true)
                }?.key ?: videos.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key

                // Filter out season 0 (usually specials) for the main season list.
                val seasonList = tvShowDetail.seasons
                    ?.filter { it.seasonNumber > 0 }
                    ?: emptyList<Season>()

                // Check if in My List
                val isInMyList = MyListManager.isInList(tvId, "tv")

                // Update UI state with fetched data.
                _uiState.value = DetailUiState(
                    isLoading = false,
                    tvShowDetail = tvShowDetail,
                    seasons = seasonList,
                    similarTvShows = similarTvShows,
                    trailerKey = trailerKey,
                    isInMyList = isInMyList
                )
            } catch (e: Exception) {
                // Handle errors.
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load TV show details"
                )
            }
        }
    }

    /**
     * Loads the list of seasons for a TV show.
     * @param tvId The ID of the TV show.
     * @param totalSeasons The total number of seasons, used as a fallback if API fails.
     */
    fun loadSeasons(tvId: Int, totalSeasons: Int) {
        viewModelScope.launch {
            Log.i(TAG, "loadSeasons: tvId=$tvId, totalSeasons=$totalSeasons")
            try {
                // Fetch TV show details to get the season list.
                val tvShowDetail = repository.getTvShowDetail(tvId).getOrElse { throw it }
                val seasonList = tvShowDetail.seasons
                    ?.filter { it.seasonNumber > 0 }
                    ?: emptyList<Season>()

                if (seasonList.isNotEmpty()) {
                    Log.i(TAG, "loadSeasons: loaded ${seasonList.size} seasons for tvId=$tvId")
                    _uiState.value = _uiState.value.copy(seasons = seasonList)
                } else {
                    // Fallback: generate seasons from totalSeasons count if API returns empty.
                    Log.i(TAG, "loadSeasons: API returned no seasons, generating $totalSeasons from totalSeasons")
                    val fallbackList = (1..totalSeasons).map { n ->
                        Season(id = n, name = "Season $n", seasonNumber = n, posterPath = null, episodeCount = null)
                    }
                    _uiState.value = _uiState.value.copy(seasons = fallbackList)
                }
            } catch (e: Exception) {
                // Fallback: generate seasons from totalSeasons count if API call fails.
                Log.i(TAG, "loadSeasons: error for tvId=$tvId - ${e.message}, falling back to $totalSeasons seasons", e)
                val fallbackList = (1..totalSeasons).map { n ->
                    Season(id = n, name = "Season $n", seasonNumber = n, posterPath = null, episodeCount = null)
                }
                _uiState.value = _uiState.value.copy(seasons = fallbackList)
            }
        }
    }

    /**
     * Loads the episodes for a specific season of a TV show.
     * @param tvId The ID of the TV show.
     * @param seasonNumber The season number to load episodes for.
     */
    fun loadSeasonEpisodes(tvId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            Log.i(TAG, "loadSeasonEpisodes: tvId=$tvId, seasonNumber=$seasonNumber")
            // Set loading state to true and clear previous errors.
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Fetch season details to get the episodes.
                val seasonDetail = repository.getSeasonDetail(tvId, seasonNumber)
                val episodes = seasonDetail.getOrNull()?.episodes ?: emptyList()
                Log.i(TAG, "loadSeasonEpisodes: loaded ${episodes.size} episodes for season $seasonNumber")

                // Update UI state with fetched episodes.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    episodes = episodes
                )
            } catch (e: Exception) {
                // Handle errors.
                Log.i(TAG, "loadSeasonEpisodes: error loading season $seasonNumber - ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load episodes for season $seasonNumber"
                )
            }
        }
    }

    /**
     * Toggles the "in my list" status for the current item.
     */
    fun toggleMyList(context: Context) {
        val currentState = _uiState.value
        val newState = !currentState.isInMyList

        if (newState) {
            // Add to list
            val item = if (currentState.movieDetail != null) {
                MyListItem(
                    id = currentState.movieDetail.id,
                    title = currentState.movieDetail.title,
                    posterPath = currentState.movieDetail.posterPath,
                    type = "movie"
                )
            } else if (currentState.tvShowDetail != null) {
                MyListItem(
                    id = currentState.tvShowDetail.id,
                    title = currentState.tvShowDetail.name,
                    posterPath = currentState.tvShowDetail.posterPath,
                    type = "tv"
                )
            } else null

            item?.let { MyListManager.addItem(it, context) }
        } else {
            // Remove from list
            val id = currentState.movieDetail?.id ?: currentState.tvShowDetail?.id
            val type = if (currentState.movieDetail != null) "movie" else "tv"
            id?.let { MyListManager.removeItem(it, type, context) }
        }

        _uiState.value = _uiState.value.copy(isInMyList = newState)
    }
}
