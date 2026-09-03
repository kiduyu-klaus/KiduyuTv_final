package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApi
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApiHttpException
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private companion object {
        const val MAX_CONCURRENT_PROVIDER_REQUESTS = 3
    }

    /**
     * Returns the combined stream list in provider response order. For series
     * titles, both [season] and [episode] are required and must be > 0.
     * At most three provider requests are active at once. [onProviderProgress]
     * is invoked after each provider completes, so its [index] is the number
     * of completed providers. [onProviderRetry] is invoked before retrying a
     * failed or empty request.
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

        val totalProviders = providerNames.size
        val completedProviders = AtomicInteger(0)
        val requestLimiter = Semaphore(MAX_CONCURRENT_PROVIDER_REQUESTS)

        val providerResults = coroutineScope {
            providerNames.mapIndexed { index, providerName ->
                async {
                    requestLimiter.withPermit {
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
                                    Log.w(
                                        tag,
                                        "Provider $providerName returned no streams; retrying stream fetch"
                                    )
                                    onProviderRetry(index + 1, totalProviders, providerName)
                                }
                            }.onFailure { error ->
                                failure = error
                                val shouldRetry = error is ProvidersApiHttpException &&
                                    error.statusCode == 500 && attempt == 0
                                if (shouldRetry) {
                                    Log.w(
                                        tag,
                                        "Provider $providerName returned HTTP 500; retrying stream fetch"
                                    )
                                    onProviderRetry(index + 1, totalProviders, providerName)
                                }
                            }
                        }

                        val streams = response?.streams.orEmpty()
                        if (response != null) {
                            Log.i(tag, "Provider $providerName returned ${streams.size} streams")
                        } else {
                            Log.w(tag, "Provider $providerName failed: ${failure?.message}")
                        }

                        val completed = completedProviders.incrementAndGet()
                        onProviderProgress(completed, totalProviders, providerName)
                        streams
                    }
                }
            }.awaitAll()
        }

        // A provider can occasionally return the same stream more than once.
        // Keep configured provider order; completion order is used only for
        // progress reporting.
        providerResults.flatten()
            .distinctBy { "${it.provider.lowercase()}|${it.url}" }
            .also {
                Log.i(tag, "Resolver.load returned ${it.size} combined streams")
            }
    }
}
