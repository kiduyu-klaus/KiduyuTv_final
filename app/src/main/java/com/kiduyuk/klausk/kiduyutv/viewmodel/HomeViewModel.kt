package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val trendingTvShows: List<TvShow> = emptyList(),
    val trendingMovies: List<Movie> = emptyList(),
    val continueWatching: List<Movie> = emptyList(),
    val popularNetworks: List<NetworkItem> = emptyList(),
    val popularCompanies: List<NetworkItem> = emptyList(),
    val latestMovies: List<Movie> = emptyList(),
    val topTvShows: List<TvShow> = emptyList(),
    val myList: List<MyListItem> = emptyList(),
    val selectedItem: Any? = null,
    val error: String? = null
)

data class NetworkItem(
    val id: Int,
    val name: String,
    val logoPath: String?,
    val type: String
)

data class MyListItem(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val type: String
)

class HomeViewModel : ViewModel() {
    
    private val repository = TmdbRepository()
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeContent()
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Fetch all data concurrently
                val trendingTvDeferred = repository.getTrendingTvToday()
                val trendingMoviesDeferred = repository.getTrendingMoviesToday()
                val nowPlayingDeferred = repository.getNowPlayingMovies()
                val topRatedMoviesDeferred = repository.getTopRatedMovies()
                val topRatedTvDeferred = repository.getTopRatedTvShows()
                val popularMoviesDeferred = repository.getPopularMovies()

                val trendingTv = trendingTvDeferred.getOrNull() ?: emptyList()
                val trendingMovies = trendingMoviesDeferred.getOrNull() ?: emptyList()
                val nowPlaying = nowPlayingDeferred.getOrNull() ?: emptyList()
                val topRatedMovies = topRatedMoviesDeferred.getOrNull() ?: emptyList()
                val topRatedTv = topRatedTvDeferred.getOrNull() ?: emptyList()
                val popularMovies = popularMoviesDeferred.getOrNull() ?: emptyList()

                // Create popular networks list
                val networks = listOf(
                    NetworkItem(213, "Netflix", null, "network"),
                    NetworkItem(1024, "Apple TV+", null, "network"),
                    NetworkItem(1025, "Prime Video", null, "network"),
                    NetworkItem(158, "Disney+", null, "network"),
                    NetworkItem(2739, "Hulu", null, "network"),
                    NetworkItem(4451, "Peacock", null, "network"),
                    NetworkItem(3284, "Crunchyroll", null, "network"),
                    NetworkItem(283, "HBO Max", null, "network")
                )

                // Create popular companies list
                val companies = listOf(
                    NetworkItem(420, "Marvel Studios", null, "company"),
                    NetworkItem(109051, "Bad Robot", null, "company"),
                    NetworkItem(38109, "Legendary Pictures", null, "company"),
                    NetworkItem(174, "Warner Bros. Pictures", null, "company"),
                    NetworkItem(2, "Marvel Studios", null, "company"),
                    NetworkItem(429, "董完整", null, "company"),
                    NetworkItem(76043, "Revolution Studios", null, "company"),
                    NetworkItem(11461, "George Lucas", null, "company")
                )

                _uiState.value = HomeUiState(
                    isLoading = false,
                    trendingTvShows = trendingTv,
                    trendingMovies = trendingMovies,
                    continueWatching = nowPlaying.take(10),
                    popularNetworks = networks,
                    popularCompanies = companies,
                    latestMovies = topRatedMovies.take(10),
                    topTvShows = topRatedTv.take(10),
                    myList = emptyList(),
                    selectedItem = trendingTv.firstOrNull() ?: trendingMovies.firstOrNull()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An error occurred"
                )
            }
        }
    }

    fun selectItem(item: Any) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    fun addToMyList(item: Any, type: String) {
        val currentList = _uiState.value.myList.toMutableList()
        when (item) {
            is Movie -> currentList.add(MyListItem(item.id, item.title, item.posterPath, type))
            is TvShow -> currentList.add(MyListItem(item.id, item.name, item.posterPath, type))
        }
        _uiState.value = _uiState.value.copy(myList = currentList)
    }

    fun removeFromMyList(itemId: Int) {
        val currentList = _uiState.value.myList.toMutableList()
        currentList.removeAll { it.id == itemId }
        _uiState.value = _uiState.value.copy(myList = currentList)
    }
}
