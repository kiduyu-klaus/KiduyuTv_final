package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApi
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApiHttpException
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches streams from the kiduyuTv_providers server and surfaces them as a
 * single list. When the aggregate provider is selected, the resolver first
 * reads the live provider configuration and then queries every enabled
 * provider individually so one slow or failing scraper does not hide the
 * streams returned by the others.
 */
class StreamResolver {

    private val tag = "KiduyuLiteProvider"

    /**
     * Returns the combined stream list in provider response order. For series
     * titles, both [season] and [episode] are required and must be > 0.
     * [onProviderProgress] is invoked before each provider request, and
     * [onProviderRetry] is invoked before retrying a failed or empty request.
     */
    suspend fun load(
        type: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        provider: StreamProviderChoice = StreamCatalog.default,
        onProviderProgress: suspend (index: Int, total: Int, providerName: String) -> Unit = { _, _, _ -> },
        onProviderRetry: suspend (index: Int, total: Int, providerName: String) -> Unit = { _, _, _ -> }
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val providerNames = if (provider.key.isBlank()) {
            ProvidersApi.enabledProviderNames()
        } else {
            listOf(provider.key)
        }

        if (providerNames.isEmpty()) {
            Log.w(tag, "No enabled providers returned by the providers API")
            return@withContext emptyList()
        }

        val streams = mutableListOf<StreamItem>()
        providerNames.forEachIndexed { index, providerName ->
            onProviderProgress(index + 1, providerNames.size, providerName)
            Log.i(
                tag,
                "Resolver.load provider=$providerName type=$type tmdbId=$tmdbId " +
                    "season=${season ?: "-"} episode=${episode ?: "-"}"
            )

            var response: StreamResponse? = null
            var failure: Throwable? = null
            repeat(2) { attempt ->
                if (response?.streams?.isNotEmpty() == true) return@repeat

                runCatching {
                    ProvidersApi.streams(
                        type = type,
                        tmdbId = tmdbId,
                        season = season,
                        episode = episode,
                        provider = providerName
                    )
                }.onSuccess { result ->
                    response = result
                    if (result.streams.isEmpty() && attempt == 0) {
                        Log.w(tag, "Provider $providerName returned no streams; retrying stream fetch")
                        onProviderRetry(index + 1, providerNames.size, providerName)
                    }
                }.onFailure { error ->
                    failure = error
                    val shouldRetry = error is ProvidersApiHttpException &&
                        error.statusCode == 500 && attempt == 0
                    if (shouldRetry) {
                        Log.w(tag, "Provider $providerName returned HTTP 500; retrying stream fetch")
                        onProviderRetry(index + 1, providerNames.size, providerName)
                    }
                }
            }

            response?.let { result ->
                Log.i(tag, "Provider $providerName returned ${result.streams.size} streams")
                streams += result.streams
            } ?: Log.w(tag, "Provider $providerName failed: ${failure?.message}")
        }

        // A provider can occasionally return the same stream more than once.
        // Keep the first occurrence while retaining the server/provider order.
        streams.distinctBy { "${it.provider.lowercase()}|${it.url}" }.also {
            Log.i(tag, "Resolver.load returned ${it.size} combined streams")
        }
    }
}
