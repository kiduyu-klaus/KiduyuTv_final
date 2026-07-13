package com.kiduyuk.klausk.kiduyutv.lite.playback

import android.net.Uri
import java.util.Locale

object LitePlaybackUrlBuilder {

    fun movie(
        tmdbId: Int,
        providerName: String? = null,
        timestamp: Long = 0L
    ): String? {
        if (tmdbId <= 0) return null
        val provider = LiteStreamProviders.resolve(providerName)
        val baseUrl = String.format(Locale.US, provider.movieUrlTemplate, tmdbId)
        return appendParameters(baseUrl, provider.movieParameters(tmdbId, timestamp))
    }

    fun episode(
        tmdbId: Int,
        season: Int,
        episode: Int,
        providerName: String? = null,
        timestamp: Long = 0L
    ): String? {
        if (tmdbId <= 0 || season <= 0 || episode <= 0) return null
        val provider = LiteStreamProviders.resolve(providerName)
        val baseUrl = String.format(
            Locale.US,
            provider.tvUrlTemplate,
            tmdbId,
            season,
            episode
        )
        val parameters = provider.tvParameters(tmdbId, season, episode, timestamp)
        return appendParameters(baseUrl, parameters)
    }

    private fun appendParameters(
        baseUrl: String,
        parameters: Map<String, String>
    ): String? {
        val uri = Uri.parse(baseUrl)
        if (!LiteStreamProviders.isAllowedPlaybackUri(uri)) return null
        return uri.buildUpon().apply {
            parameters.forEach { (name, value) -> appendQueryParameter(name, value) }
        }.build().toString()
    }
}
