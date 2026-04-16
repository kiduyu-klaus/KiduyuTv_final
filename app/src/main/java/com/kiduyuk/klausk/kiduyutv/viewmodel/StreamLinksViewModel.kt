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
                // ── 2Embed CC ─────────────────────────────────────────────────────────
                StreamProvider(
                    name = "2Embed CC",
                    urlTemplate = if (isTv)
                        "https://www.2embed.cc/embedtv/$tmdbId&s=$season&e=$episode"
                    else
                        "https://www.2embed.cc/embed/$tmdbId",
                    type = type
                ),
                // ── 2Embed Stream ─────────────────────────────────────────────────────
                StreamProvider(
                    name = "2Embed Stream",
                    urlTemplate = if (isTv)
                        "https://www.2embed.stream/embed/tv/$tmdbId/$season/$episode"
                    else
                        "https://www.2embed.stream/embed/movie/$tmdbId",
                    type = type
                ),
                // ── BidSRC (PRO) ──────────────────────────────────────────────────────
                StreamProvider(
                    name = "BidSRC (PRO)",
                    urlTemplate = if (isTv)
                        "https://bidsrc.pro/tv/$tmdbId/$season/$episode?color=770000&autoplay=1"
                    else
                        "https://bidsrc.pro/movie/$tmdbId/?color=770000&autoplay=1",
                    type = type
                ),
                // ── MoviesAPI ─────────────────────────────────────────────────────────
                StreamProvider(
                    name = "MoviesAPI",
                    urlTemplate = if (isTv)
                        "https://moviesapi.club/tv/$tmdbId-$season-$episode"
                    else
                        "https://moviesapi.club/movie/$tmdbId",
                    type = type
                ),
                // ── VidRock ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidRock",
                    urlTemplate = if (isTv)
                        "https://vidrock.net/tv/$tmdbId/$season/$episode"
                    else
                        "https://vidrock.net/movie/$tmdbId",
                    type = type
                ),
                // ── VidOra ────────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidOra",
                    urlTemplate = if (isTv)
                        "https://vidora.su/tv/$tmdbId/$season/$episode?autoplay=true&colour=6966ff"
                    else
                        "https://vidora.su/movie/$tmdbId?autoplay=true&colour=6966ff",
                    type = type
                ),
                // ── VidSync ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidSync",
                    urlTemplate = if (isTv)
                        "https://vidsync.xyz/embed/tv/$tmdbId/$season/$episode?autoPlay=true&autoNext=true"
                    else
                        "https://vidsync.xyz/embed/movie/$tmdbId?autoPlay=true&autoNext=true",
                    type = type
                ),
                // ── VidLink ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidLink",
                    urlTemplate = if (isTv)
                        "https://vidlink.pro/tv/$tmdbId/$season/$episode?primaryColor=ff0000&secondaryColor=ff0000&iconColor=ffffff&icons=default&player=default&title=true&poster=true&autoplay=true&nextbutton=true"
                    else
                        "https://vidlink.pro/movie/$tmdbId?primaryColor=ff0000&secondaryColor=ff0000&iconColor=ffffff&icons=default&player=default&title=true&poster=true&autoplay=true&nextbutton=true",
                    type = type
                ),
                // ── VidSrc (CO) ───────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidSrc (CO)",
                    urlTemplate = if (isTv)
                        "https://player.vidsrc.co/embed/tv/$tmdbId/$season/$episode?autoplay=true&autonext=true&nextbutton=true&poster=true&primarycolor=FF0000&secondarycolor=FF0000&iconcolor=FFFFFF&fontcolor=FFFFFF&fontsize=16px&opacity=0.5&font=Poppins"
                    else
                        "https://player.vidsrc.co/embed/movie/$tmdbId?autoplay=true&autonext=true&nextbutton=true&poster=true&primarycolor=FF0000&secondarycolor=FF0000&iconcolor=FFFFFF&fontcolor=FFFFFF&fontsize=16px&opacity=0.5&font=Poppins",
                    type = type
                ),
                // ── SuperEmbed (VIP) ──────────────────────────────────────────────────
                StreamProvider(
                    name = "SuperEmbed (VIP)",
                    urlTemplate = if (isTv)
                        "https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1&s=$season&e=$episode"
                    else
                        "https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1",
                    type = type
                ),
                // ── VidSrc (WTF) v1 ──────────────────────────────────────────────────
                StreamProvider(
                    name = "VidSrc (WTF) v1",
                    urlTemplate = if (isTv)
                        "https://vidsrc.wtf/api/1/tv/?id=$tmdbId&s=$season&e=$episode"
                    else
                        "https://www.vidsrc.wtf/api/1/movie/?id=$tmdbId",
                    type = type
                ),
                // ── VidSrc (WTF) v3 ──────────────────────────────────────────────────
                StreamProvider(
                    name = "VidSrc (WTF) v3 - Multi Providers",
                    urlTemplate = if (isTv)
                        "https://vidsrc.wtf/api/3/tv/?id=$tmdbId&s=$season&e=$episode"
                    else
                        "https://www.vidsrc.wtf/api/3/movie/?id=$tmdbId",
                    type = type
                ),
                // ── VidSrc (WTF) v4 ──────────────────────────────────────────────────
                StreamProvider(
                    name = "VidSrc (WTF) v4 - Premium Providers",
                    urlTemplate = if (isTv)
                        "https://vidsrc.wtf/api/4/tv/?id=$tmdbId&s=$season&e=$episode"
                    else
                        "https://www.vidsrc.wtf/api/4/movie/?id=$tmdbId",
                    type = type
                ),
                // ── VidNest ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidNest",
                    urlTemplate = if (isTv)
                        "https://vidnest.fun/tv/$tmdbId/$season/$episode"
                    else
                        "https://vidnest.fun/movie/$tmdbId",
                    type = type
                ),
                // ── Vidify ────────────────────────────────────────────────────────────
                StreamProvider(
                    name = "Vidify",
                    urlTemplate = if (isTv)
                        "https://player.vidify.top/embed/tv/$tmdbId/$season/$episode?autoplay=false&poster=true&chromecast=true&servericon=true&setting=true&pip=true&logourl=https%3A%2F%2Fi.ibb.co%2F67wTJd9R%2Fpngimg-com-netflix-PNG11.png&font=Roboto&fontcolor=6f63ff&fontsize=20&opacity=0.5&primarycolor=ffffff&secondarycolor=1f2937&iconcolor=ffffff"
                    else
                        "https://player.vidify.top/embed/movie/$tmdbId?autoplay=false&poster=true&chromecast=true&servericon=true&setting=true&pip=true&logourl=https%3A%2F%2Fi.ibb.co%2F67wTJd9R%2Fpngimg-com-netflix-PNG11.png&font=Roboto&fontcolor=6f63ff&fontsize=20&opacity=0.5&primarycolor=ffffff&secondarycolor=1f2937&iconcolor=ffffff",
                    type = type
                ),
                // ── VidEasy ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidEasy",
                    urlTemplate = if (isTv)
                        "https://player.videasy.net/tv/$tmdbId/$season/$episode"
                    else
                        "https://player.videasy.net/movie/$tmdbId",
                    type = type
                ),
                // ── VidFast ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidFast",
                    urlTemplate = if (isTv)
                        "https://vidfast.pro/tv/$tmdbId/$season/$episode"
                    else
                        "https://vidfast.pro/movie/$tmdbId",
                    type = type
                ),
                // ── VidAPI ────────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidAPI",
                    urlTemplate = if (isTv)
                        "https://vidapi.xyz/embed/tv/$tmdbId&s=$season&e=$episode"
                    else
                        "https://vidapi.xyz/embed/movie/$tmdbId",
                    type = type
                ),
                // ── Cinetaro ──────────────────────────────────────────────────────────
                StreamProvider(
                    name = "Cinetaro",
                    urlTemplate = if (isTv)
                        "https://api.cinetaro.buzz/tv/$tmdbId/$season/$episode/english"
                    else
                        "https://api.cinetaro.buzz/movie/$tmdbId/english",
                    type = type
                ),
                // ── VidZee ────────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidZee",
                    urlTemplate = if (isTv)
                        "https://player.vidzee.wtf/v2/embed/tv/$tmdbId/$season/$episode/"
                    else
                        "https://player.vidzee.wtf/v2/embed/movie/$tmdbId/",
                    type = type
                ),
                // ── VidKing ───────────────────────────────────────────────────────────
                StreamProvider(
                    name = "VidKing",
                    urlTemplate = if (isTv)
                        "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?color=e50914&autoPlay=true"
                    else
                        "https://www.vidking.net/embed/movie/$tmdbId?color=e50914&autoPlay=true",
                    type = type
                ),
                // ── Flixer ────────────────────────────────────────────────────────────
                StreamProvider(
                    name = "Flixer",
                    urlTemplate = if (isTv)
                        "https://flixer.su/watch/tv/$tmdbId/$season/$episode"
                    else
                        "https://flixer.su/watch/movie/$tmdbId",
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
                    "VidLink"  -> "${provider.urlTemplate}&startAt=$timestamp"
                    "VidKing"  -> "${provider.urlTemplate}&progress=$timestamp"
                    "VidEasy"  -> "${provider.urlTemplate}&progress=$timestamp"
                    "VidFast"  -> "${provider.urlTemplate}&startAt=$timestamp"
                    "VidSrc (CO)" -> "${provider.urlTemplate}&startAt=$timestamp"
                    else       -> provider.urlTemplate
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

            val finalProviders = mutableListOf<StreamProvider>()

            for (provider in initialProviders) {
                finalProviders.add(provider.copy(isAvailable = true))
            }

            _uiState.value = _uiState.value.copy(
                streamProviders = finalProviders,
                isLoading = false
            )
        }
    }

    // checkUrlAvailability is disabled — all providers are assumed available (isAvailable = true).
    // Re-enable to restore HEAD-request validation via OkHttpClient.
    //
    // private suspend fun checkUrlAvailability(client: OkHttpClient, urlString: String): Boolean {
    //     return withContext(Dispatchers.IO) {
    //         try {
    //             Log.i(TAG, "Checking URL availability: $urlString")
    //
    //             val request = Request.Builder()
    //                 .url(urlString)
    //                 .head()
    //                 .build()
    //
    //             val response = client.newCall(request).execute()
    //             val isAvailable = response.code in 200..399
    //             Log.i(TAG, "URL $urlString availability: $isAvailable (code: ${response.code})")
    //             response.close()
    //             isAvailable
    //         } catch (e: Exception) {
    //             Log.i(TAG, "Failed to check URL availability for $urlString: ${e.message}")
    //             false
    //         }
    //     }
    // }
}

data class StreamLinksUiState(
    val streamProviders: List<StreamProvider> = emptyList(),
    val isLoading: Boolean = false
)
