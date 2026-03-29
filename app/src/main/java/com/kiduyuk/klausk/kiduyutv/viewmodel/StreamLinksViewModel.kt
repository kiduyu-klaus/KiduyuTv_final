package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.StreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class StreamLinksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StreamLinksUiState())
    val uiState: StateFlow<StreamLinksUiState> = _uiState

    fun loadStreamProviders(tmdbId: Int, isTv: Boolean, season: Int?, episode: Int?) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val providers = listOf(
                StreamProvider(
                    name = "VidLink",
                    urlTemplate = if (isTv) "https://vidlink.pro/tv/${tmdbId}/${season}/${episode}?autoPlay=true" else "https://vidlink.pro/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                ),
                StreamProvider(
                    name = "VidKing",
                    urlTemplate = if (isTv) "https://www.vidking.net/embed/tv/${tmdbId}/${season}/${episode}?autoPlay=true&nextEpisode=true" else "https://www.vidking.net/embed/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                ) ,
                StreamProvider(
                    name = "Videasy",
                    urlTemplate = if (isTv) "https://player.videasy.net/tv/${tmdbId}/${season}/${episode}?autoPlay=true" else "https://player.videasy.net/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                ),
                StreamProvider(
                    name = "Mapple",
                    urlTemplate = if (isTv) "https://mapple.uk/watch/tv/${tmdbId}-${season}-${episode}" else "https://mapple.uk/watch/movie/${tmdbId}",
                    type = if (isTv) "tv" else "movie"
                ),
                StreamProvider(
                    name = "Flixer",
                    urlTemplate = if (isTv) "https://flixer.su/watch/tv/${tmdbId}/${season}/${episode}" else "https://flixer.su/watch/movie/${tmdbId}",
                    type = if (isTv) "tv" else "movie"
                ),
                StreamProvider(
                    name = "VidFast",
                    urlTemplate = if (isTv) "https://vidfast.pro/tv/${tmdbId}/${season}/${episode}?autoPlay=true" else "https://vidfast.pro/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                )
            )

            val checkedProviders = providers.map { provider ->
                val isAvailable = checkUrlAvailability(provider.urlTemplate)
                provider.copy(isAvailable = isAvailable)
            }

            _uiState.value = _uiState.value.copy(
                streamProviders = checkedProviders,
                isLoading = false
            )
        }
    }

    private suspend fun checkUrlAvailability(urlString: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000 // 5 seconds timeout
                val responseCode = connection.responseCode
                // Consider 2xx and 3xx responses as available
                responseCode in 200..399
            } catch (e: Exception) {
                false
            }
        }
    }
}

data class StreamLinksUiState(
    val streamProviders: List<StreamProvider> = emptyList(),
    val isLoading: Boolean = false
)
