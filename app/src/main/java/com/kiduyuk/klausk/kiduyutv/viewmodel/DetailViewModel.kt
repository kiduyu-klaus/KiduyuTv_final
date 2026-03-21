package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.*
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val movieDetail: MovieDetail? = null,
    val tvShowDetail: TvShowDetail? = null,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val similarMovies: List<Movie> = emptyList(),
    val similarTvShows: List<TvShow> = emptyList(),
    val isInMyList: Boolean = false,
    val error: String? = null
)

class DetailViewModel : ViewModel() {

    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "DetailViewModel"
    }

    fun loadMovieDetail(movieId: Int) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)

            try {
                val movieDetail = repository.getMovieDetail(movieId)
                val similarMovies = repository.getTrendingMoviesToday()

                _uiState.value = DetailUiState(
                    isLoading = false,
                    movieDetail = movieDetail.getOrNull(),
                    similarMovies = similarMovies.getOrNull()?.take(10) ?: emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load movie details"
                )
            }
        }
    }

    fun loadTvShowDetail(tvId: Int) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)

            try {
                val tvShowDetail = repository.getTvShowDetail(tvId).getOrElse { throw it }
                val similarTvShows = repository.getTrendingTvToday()
                val seasonList = tvShowDetail.seasons
                    ?.filter { it.seasonNumber > 0 }
                    ?: emptyList<Season>()

                _uiState.value = DetailUiState(
                    isLoading = false,
                    tvShowDetail = tvShowDetail,
                    seasons = seasonList,
                    similarTvShows = similarTvShows.getOrNull()?.take(10) ?: emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load TV show details"
                )
            }
        }
    }

    fun loadSeasons(tvId: Int, totalSeasons: Int) {
        viewModelScope.launch {
            Log.i(TAG, "loadSeasons: tvId=$tvId, totalSeasons=$totalSeasons")
            try {
                val tvShowDetail = repository.getTvShowDetail(tvId).getOrElse { throw it }
                val seasonList = tvShowDetail.seasons
                    ?.filter { it.seasonNumber > 0 }
                    ?: emptyList<Season>()

                if (seasonList.isNotEmpty()) {
                    Log.i(TAG, "loadSeasons: loaded ${seasonList.size} seasons for tvId=$tvId")
                    _uiState.value = _uiState.value.copy(seasons = seasonList)
                } else {
                    // Fallback: generate seasons from totalSeasons count
                    Log.i(TAG, "loadSeasons: API returned no seasons, generating $totalSeasons from totalSeasons")
                    val fallbackList = (1..totalSeasons).map { n ->
                        Season(id = n, name = "Season $n", seasonNumber = n, posterPath = null, episodeCount = null)
                    }
                    _uiState.value = _uiState.value.copy(seasons = fallbackList)
                }
            } catch (e: Exception) {
                Log.i(TAG, "loadSeasons: error for tvId=$tvId - ${e.message}, falling back to $totalSeasons seasons", e)
                val fallbackList = (1..totalSeasons).map { n ->
                    Season(id = n, name = "Season $n", seasonNumber = n, posterPath = null, episodeCount = null)
                }
                _uiState.value = _uiState.value.copy(seasons = fallbackList)
            }
        }
    }

    fun loadSeasonEpisodes(tvId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            Log.i(TAG, "loadSeasonEpisodes: tvId=$tvId, seasonNumber=$seasonNumber")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val seasonDetail = repository.getSeasonDetail(tvId, seasonNumber)
                val episodes = seasonDetail.getOrNull()?.episodes ?: emptyList()
                Log.i(TAG, "loadSeasonEpisodes: loaded ${episodes.size} episodes for season $seasonNumber")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    episodes = episodes
                )
            } catch (e: Exception) {
                Log.i(TAG, "loadSeasonEpisodes: error loading season $seasonNumber - ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load episodes for season $seasonNumber"
                )
            }
        }
    }

    fun toggleMyList() {
        _uiState.value = _uiState.value.copy(isInMyList = !_uiState.value.isInMyList)
    }
}