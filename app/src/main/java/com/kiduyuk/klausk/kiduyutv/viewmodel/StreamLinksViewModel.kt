package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.StreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import com.kiduyuk.klausk.kiduyutv.util.SingletonDnsResolver
import java.util.concurrent.TimeUnit

class StreamLinksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StreamLinksUiState())
    val uiState: StateFlow<StreamLinksUiState> = _uiState

    companion object {
        private const val TAG = "StreamLinksViewModel"
        private const val CACHE_SIZE = 5L * 1024 * 1024 // 5 MB cache for stream checks (limited)

        @Volatile
        private var httpClient: OkHttpClient? = null

        fun getOkHttpClient(context: Context): OkHttpClient {
            return httpClient ?: synchronized(this) {
                httpClient ?: createOkHttpClient(context).also { httpClient = it }
            }
        }

        private fun createOkHttpClient(context: Context): OkHttpClient {
            val cacheDir = File(context.cacheDir, "stream_check_cache")
            val cache = Cache(cacheDir, CACHE_SIZE)

            return OkHttpClient.Builder()
                .cache(cache)
                .dns(SingletonDnsResolver.getDns()) // Cloudflare DNS over HTTPS
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    fun loadStreamProviders(
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        context: Context
    ) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val initialProviders = listOf(
                StreamProvider(
                    name = "VidLink",
                    urlTemplate = if (isTv) "https://vidlink.pro/tv/${tmdbId}/${season}/${episode}?autoPlay=true" else "https://vidlink.pro/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                ),
                StreamProvider(
                    name = "Videasy",
                    urlTemplate = if (isTv) "https://player.videasy.net/tv/${tmdbId}/${season}/${episode}?nextEpisode=true&autoplayNextEpisode=true&episodeSelector=true&overlay=true&color=8B5CF6" else "https://player.videasy.net/movie/${tmdbId}",
                    type = if (isTv) "tv" else "movie"
                ),
//                StreamProvider(
//                    name = "Hexa",
//                    urlTemplate = if (isTv) "https://hexa.su/watch/tv/${tmdbId}/${season}/${episode}" else "https://hexa.su/watch/movie/${tmdbId}",
//                    type = if (isTv) "tv" else "movie"
//                ),
                StreamProvider(
                    name = "VidFast",
                    urlTemplate = if (isTv) "https://vidfast.pro/tv/${tmdbId}/${season}/${episode}?autoPlay=true&nextButton=true&autoNext=true" else "https://vidfast.pro/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                ),
//                StreamProvider(
//                    name = "VidSrc",
//                    urlTemplate = if (isTv) "https://vidsrc.icu/embed/tv/${tmdbId}/${season}/${episode}" else "https://vidsrc.icu/embed/movie/${tmdbId}",
//                    type = if (isTv) "tv" else "movie"
//                ),
                StreamProvider(
                    name = "VidKing",
                    urlTemplate = if (isTv) "https://www.vidking.net/embed/tv/${tmdbId}/${season}/${episode}?autoPlay=true&nextEpisode=true&episodeSelector=true" else "https://www.vidking.net/embed/movie/${tmdbId}?autoPlay=true",
                    type = if (isTv) "tv" else "movie"
                ),
//                StreamProvider(
//                    name = "Mapple",
//                    urlTemplate = if (isTv) "https://mapple.uk/watch/tv/${tmdbId}-${season}-${episode}" else "https://mapple.uk/watch/movie/${tmdbId}",
//                    type = if (isTv) "tv" else "movie"
//                ),
                StreamProvider(
                    name = "Flixer",
                    urlTemplate = if (isTv) "https://flixer.su/watch/tv/${tmdbId}/${season}/${episode}" else "https://flixer.su/watch/movie/${tmdbId}",
                    type = if (isTv) "tv" else "movie"
                )
            )

            val client = getOkHttpClient(context)
            val finalProviders = mutableListOf<StreamProvider>()

            for (provider in initialProviders) {
                //val isAvailable = checkUrlAvailability(client, provider.urlTemplate)
                val isAvailable = true
                finalProviders.add(provider.copy(isAvailable = isAvailable))
            }

            _uiState.value = _uiState.value.copy(
                streamProviders = finalProviders,
                isLoading = false
            )
        }
    }

    private suspend fun checkUrlAvailability(client: OkHttpClient, urlString: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Checking URL availability: $urlString")

                val request = Request.Builder()
                    .url(urlString)
                    .head()
                    .build()

                val response = client.newCall(request).execute()
                val isAvailable = response.code in 200..399
                Log.i(TAG, "URL $urlString availability: $isAvailable (code: ${response.code})")
                response.close()
                isAvailable
            } catch (e: Exception) {
                Log.i(TAG, "Failed to check URL availability for $urlString: ${e.message}")
                false
            }
        }
    }
}

data class StreamLinksUiState(
    val streamProviders: List<StreamProvider> = emptyList(),
    val isLoading: Boolean = false
)
