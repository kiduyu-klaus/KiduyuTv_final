package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv.StreamProvider
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

        private fun buildStreamProviders(
            tmdbId: Int,
            isTv: Boolean,
            season: Int?,
            episode: Int?
        ): List<StreamProvider> {
            val type = if (isTv) "tv" else "movie"
            return listOf(
                StreamProvider(
                    name = "Videasy",
                    urlTemplate = if (isTv) "https://player.videasy.net/tv/${tmdbId}/${season}/${episode}?nextEpisode=true&autoplayNextEpisode=true&episodeSelector=true&overlay=true&color=8B5CF6" else "https://player.videasy.net/movie/${tmdbId}?overlay=true",
                    type = type
                ),
                StreamProvider(
                    name = "VidLink",
                    urlTemplate = if (isTv) "https://vidlink.pro/tv/${tmdbId}/${season}/${episode}?autoPlay=true" else "https://vidlink.pro/movie/${tmdbId}?autoPlay=true",
                    type = type
                ),
                StreamProvider(
                    name = "VidFast",
                    urlTemplate = if (isTv) "https://vidfast.pro/tv/${tmdbId}/${season}/${episode}?autoPlay=true&nextButton=true&autoNext=true" else "https://vidfast.pro/movie/${tmdbId}?autoPlay=true",
                    type = type
                ),
                StreamProvider(
                    name = "VidKing",
                    urlTemplate = if (isTv) "https://www.vidking.net/embed/tv/${tmdbId}/${season}/${episode}?autoPlay=true&nextEpisode=true&episodeSelector=true" else "https://www.vidking.net/embed/movie/${tmdbId}?autoPlay=true",
                    type = type
                ),
                StreamProvider(
                    name = "VidNest",
                    urlTemplate = if (isTv) "https://vidnest.fun/tv/${tmdbId}/${season}/${episode}" else "https://vidnest.fun/movie/${tmdbId}",
                    type = type
                ),
                StreamProvider(
                    name = "Vidsync",
                    urlTemplate = if (isTv) "https://vidsync.xyz/embed/tv/${tmdbId}/${season}/${episode}?autoPlay=true&autoNext=true" else "https://vidsync.xyz/embed/movie/${tmdbId}?autoPlay=true",
                    type = type
                ),
                StreamProvider(
                    name = "Vidrock",
                    urlTemplate = if (isTv) "https://vidrock.net/tv/${tmdbId}/${season}/${episode}?autoplay=true&autonext=true" else "https://vidrock.net/movie/${tmdbId}?autoplay=true",
                    type = type
                ),
                StreamProvider(
                    name = "Flixer",
                    urlTemplate = if (isTv) "https://flixer.su/watch/tv/${tmdbId}/${season}/${episode}" else "https://flixer.su/watch/movie/${tmdbId}",
                    type = type
                ),
                StreamProvider(
                    name = "StreamingNow",
                    urlTemplate = if (isTv) "https://streamingnow.mov/tv/${tmdbId}/${season}/${episode}" else "https://streamingnow.mov/movie/${tmdbId}",
                    type = type
                ),
                StreamProvider(
                    name = "Autoembed",
                    urlTemplate = if (isTv) "https://autoembed.co/tv/tmdb/${tmdbId}-${season}-${episode}" else "https://autoembed.co/movie/tmdb/${tmdbId}",
                    type = type
                ),
                StreamProvider(
                    name = "VidSrc (WTF) v4",
                    urlTemplate = if (isTv)
                        "https://vidsrc.wtf/api/4/tv/?id=$tmdbId&s=$season&e=$episode"
                    else
                        "https://www.vidsrc.wtf/api/4/movie/?id=$tmdbId",
                    type = type
                ),
                StreamProvider(
                    name = "MoviesAPI",
                    urlTemplate = if (isTv)
                        "https://moviesapi.club/tv/$tmdbId-$season-$episode"
                    else
                        "https://moviesapi.club/movie/$tmdbId",
                    type = type
                ),
                StreamProvider(
                    name = "VidSrc (WTF) v1",
                    urlTemplate = if (isTv) "https://vidsrc.wtf/api/1/tv/?id=$tmdbId&s=$season&e=$episode" else "https://www.vidsrc.wtf/api/1/movie/?id=$tmdbId",
                    type = type
                ),
                StreamProvider(
                    name = "VidSrc (WTF) v3 - Multi Providers",
                    urlTemplate = if (isTv) "https://vidsrc.wtf/api/3/tv/?id=$tmdbId&s=$season&e=$episode" else "https://www.vidsrc.wtf/api/3/movie/?id=$tmdbId",
                    type = type
                ),
                StreamProvider(
                    name = "VidZee",
                    urlTemplate = if (isTv) "https://player.vidzee.wtf/embed/tv/${tmdbId}/${season}/${episode}" else "https://player.vidzee.wtf/embed/movie/${tmdbId}",
                    type = type
                ),
                StreamProvider(
                    name = "2Embed",
                    urlTemplate = if (isTv) "https://www.2embed.stream/embed/tv/${tmdbId}/${season}/${episode}" else "https://www.2embed.stream/embed/movie/${tmdbId}",
                    type = type
                )
            )
        }

        fun resolveProviderUrl(
            providerName: String,
            tmdbId: Int,
            isTv: Boolean,
            season: Int?,
            episode: Int?,
            timestamp: Long = 0L
        ): String? {
            val provider = buildStreamProviders(tmdbId, isTv, season, episode)
                .firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                ?: return null

            return if (timestamp > 0) {
                when (provider.name) {
                    "VidLink" -> "${provider.urlTemplate}&startAt=$timestamp"
                    "VidFast" -> "${provider.urlTemplate}&startAt=$timestamp"
                    "Videasy" -> "${provider.urlTemplate}&progress=$timestamp"
                    else -> provider.urlTemplate
                }
            } else {
                provider.urlTemplate
            }
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
            val initialProviders = buildStreamProviders(tmdbId, isTv, season, episode)

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

    /*private suspend fun checkUrlAvailability(client: OkHttpClient, urlString: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Checking URL availability: $urlString")

                val request = Request.Builder()
                    .url(urlString)
                    .head()
                    .build()

                val response = client.newCall(request).execute()
                val isAvailable = response.code in 200..399
                Log.i(TAG, "URL $urlTemplate availability: $isAvailable (code: ${response.code})")
                response.close()
                isAvailable
            } catch (e: Exception) {
                Log.i(TAG, "Failed to check URL availability for $urlString: ${e.message}")
                false
            }
        }
    }*/
}

data class StreamLinksUiState(
    val streamProviders: List<StreamProvider> = emptyList(),
    val isLoading: Boolean = false
)