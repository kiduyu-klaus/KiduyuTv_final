package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApi
import java.util.Locale

/**
 * Display name + server-side key for a provider reported by the
 * kiduyuTv_providers backend.
 *
 * The empty key is the special "aggregate" mode: when chosen, the app calls
 * `/api/streams/{type}/{tmdbId}` and the server merges results from every
 * enabled provider. Otherwise the app calls
 * `/api/streams/{key}/{type}/{tmdbId}` to scope the request to one provider.
 */
data class StreamProviderChoice(
    val displayName: String,
    val key: String
)

object StreamCatalog {

    private val aggregate = StreamProviderChoice("All Providers", "")

    val default: StreamProviderChoice
        get() = aggregate

    /**
     * Reads the live provider configuration from the backend. This performs
     * network I/O and must be called on Dispatchers.IO.
     */
    fun enabled(): List<StreamProviderChoice> =
        listOf(aggregate) + ProvidersApi.enabledProviderNames().map { key ->
            StreamProviderChoice(formatDisplayName(key), key)
        }

    fun resolve(name: String?): StreamProviderChoice {
        val value = name?.trim().orEmpty()
        if (
            value.isBlank() ||
            value.equals("Auto", ignoreCase = true) ||
            value.equals(aggregate.displayName, ignoreCase = true)
        ) {
            return default
        }

        return StreamProviderChoice(
            displayName = formatDisplayName(value),
            key = value.lowercase(Locale.ROOT)
        )
    }

    private fun formatDisplayName(key: String): String =
        key.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char -> char.uppercase() }
            }
}
