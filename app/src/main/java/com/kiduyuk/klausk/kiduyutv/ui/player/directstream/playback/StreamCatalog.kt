package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApi
import java.util.Locale

/**
 * Display name + server-side key for one of the providers the local
 * kiduyuTv_providers (TMDB-Embed-API) server knows about. The keys here must
 * match the file names in the server's `providers/` directory and the names
 * registered in `providers/registry.js`.
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

    private val known: List<StreamProviderChoice> = listOf(
        StreamProviderChoice("Showbox",      "showbox"),
        StreamProviderChoice("4KHDHub",      "4khdhub"),
        StreamProviderChoice("VixSrc",       "vixsrc"),
        StreamProviderChoice("Videasy",      "videasy"),
        StreamProviderChoice("Vidlink",      "vidlink"),
        StreamProviderChoice("LordFlix",     "lordflix"),
        StreamProviderChoice("NoTorrent",    "notorrent"),
        StreamProviderChoice("DahmerMovies", "dahmermovies"),
        StreamProviderChoice("Hexa",         "hexa"),
        StreamProviderChoice("Peachify",     "peachify"),
        StreamProviderChoice("VidUp",        "vidup"),
        StreamProviderChoice("VidFast",      "vidfast"),
        StreamProviderChoice("VidCore",      "vidcore")
    )

    val default: StreamProviderChoice
        get() = aggregate

    /**
     * Reads the live provider configuration from the backend. This performs
     * network I/O and must be called on Dispatchers.IO.
     */
    fun enabled(): List<StreamProviderChoice> =
        listOf(aggregate) + ProvidersApi.enabledProviderNames().map { key ->
            known.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?: StreamProviderChoice(formatDisplayName(key), key)
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

        return known.firstOrNull {
            it.displayName.equals(value, ignoreCase = true) ||
                it.key.equals(value, ignoreCase = true)
        } ?: StreamProviderChoice(
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
