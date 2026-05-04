package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import com.kiduyuk.klausk.kiduyutv.util.SingletonDnsResolver
import java.util.concurrent.TimeUnit

/**
 * UI-friendly Stream Provider data class for display purposes.
 * Contains both provider info and pre-generated URL for quick access.
 */
data class StreamProviderUi(
    val name: String,
    val urlTemplate: String,
    var isAvailable: Boolean = false,
    val type: String
)

/**
 * ViewModel for managing stream provider links and availability checking.
 * Uses StreamProviderManager from data/model/StreamProvider.kt for centralized
 * stream provider configuration and URL generation.
 */
class StreamLinksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StreamLinksUiState())
    val uiState: StateFlow<StreamLinksUiState> = _uiState

    companion object {
        private const val TAG = "StreamLinksViewModel"
        private const val CACHE_SIZE = 5L * 1024 * 1024 // 5 MB cache for stream checks

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

        /**
         * Build a list of StreamProviderUi objects for the UI.
         * Uses StreamProviderManager to generate URLs.
         */
        private fun buildStreamProviders(
            tmdbId: Int,
            isTv: Boolean,
            season: Int?,
            episode: Int?
        ): List<StreamProviderUi> {
            val type = if (isTv) "tv" else "movie"

            // Get all providers from StreamProviderManager and create UI-friendly versions
            return StreamProviderManager.providers.map { provider ->
                val url = StreamProviderManager.generateUrl(
                    providerName = provider.name,
                    tmdbId = tmdbId,
                    isTv = isTv,
                    season = season,
                    episode = episode
                )
                StreamProviderUi(
                    name = provider.name,
                    urlTemplate = url,
                    type = type,
                    isAvailable = false // Will be updated after availability check
                )
            }
        }

        /**
         * Resolve provider URL with optional timestamp for resume playback.
         * Uses StreamProviderManager.generateUrl() for consistent URL generation.
         */
        fun resolveProviderUrl(
            providerName: String,
            tmdbId: Int,
            isTv: Boolean,
            season: Int?,
            episode: Int?,
            timestamp: Long = 0L
        ): String? {
            // Use StreamProviderManager to generate the URL
            return try {
                StreamProviderManager.generateUrl(
                    providerName = providerName,
                    tmdbId = tmdbId,
                    isTv = isTv,
                    season = season,
                    episode = episode,
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving provider URL: ${e.message}")
                null
            }
        }
    }

    /**
     * Load stream providers and check their availability.
     * Uses buildStreamProviders() which delegates to StreamProviderManager.
     */
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
            val finalProviders = mutableListOf<StreamProviderUi>()

            for (provider in initialProviders) {
                // Availability check is commented out - all providers shown as available
                // val isAvailable = checkUrlAvailability(client, provider.urlTemplate)
                val isAvailable = true
                finalProviders.add(provider.copy(isAvailable = isAvailable))
            }

            _uiState.value = _uiState.value.copy(
                streamProviders = finalProviders,
                isLoading = false
            )
        }
    }

    /* Availability check method (currently unused)
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

/**
 * UI State for Stream Links screen.
 * Uses StreamProviderUi for UI-friendly provider data.
 */
data class StreamLinksUiState(
    val streamProviders: List<StreamProviderUi> = emptyList(),
    val isLoading: Boolean = false
)