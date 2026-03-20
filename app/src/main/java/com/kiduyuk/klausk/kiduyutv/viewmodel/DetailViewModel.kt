package com.kiduyuk.klausk.kiduyutv.viewmodel

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
                val tvShowDetail = repository.getTvShowDetail(tvId)
                val seasons = repository.getTvShowSeasons(tvId)
                val similarTvShows = repository.getTrendingTvToday()
                
                _uiState.value = DetailUiState(
                    isLoading = false,
                    tvShowDetail = tvShowDetail.getOrNull(),
                    seasons = seasons.getOrNull()?.seasons ?: emptyList(),
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

    fun loadSeasonEpisodes(tvId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                val seasonDetail = repository.getSeasonDetail(tvId, seasonNumber)
                _uiState.value = _uiState.value.copy(
                    episodes = seasonDetail.getOrNull()?.episodes ?: emptyList()
                )
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun toggleMyList() {
        _uiState.value = _uiState.value.copy(isInMyList = !_uiState.value.isInMyList)
    }
}
