package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersApi
import java.util.Locale

/**
 * Display name + server-side key for a provider reported by the
 * kiduyuTv_providers backend.
 *
 * The empty key is the special "aggregate" mode. The resolver uses the live
 * backend catalog to query every provider compatible with the current title.
 * A non-empty key scopes the request to that compatible provider.
 */
data class StreamProviderChoice(
    val displayName: String,
    val key: String,
    /** Category supplied by the backend, such as MoviesTv or Anime. */
    val categories: List<String> = emptyList()
) {
    /** Primary category retained for compatibility with existing callers. */
    val category: String?
        get() = categories.firstOrNull()
}

object StreamCatalog {

    private val aggregate = StreamProviderChoice("All Providers", "")

    val default: StreamProviderChoice
        get() = aggregate

    /**
     * Reads the live provider configuration from the backend. This performs
     * network I/O and must be called on Dispatchers.IO.
     */
    fun enabled(): List<StreamProviderChoice> =
        listOf(aggregate) + ProvidersApi.enabledProviders().map { provider ->
            StreamProviderChoice(
                displayName = formatDisplayName(provider.name),
                key = provider.name,
                categories = provider.categories
            )
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
