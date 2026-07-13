package com.kiduyuk.klausk.kiduyutv.lite.playback

import android.net.Uri
import com.kiduyuk.klausk.kiduyutv.lite.BuildConfig

object LitePlaybackUrlBuilder {

    fun movie(tmdbId: Int): String? =
        baseBuilder(tmdbId)?.build()?.toString()

    fun episode(tmdbId: Int, season: Int, episode: Int): String? =
        baseBuilder(tmdbId)
            ?.appendQueryParameter("s", season.toString())
            ?.appendQueryParameter("e", episode.toString())
            ?.build()
            ?.toString()

    private fun baseBuilder(tmdbId: Int): Uri.Builder? {
        if (tmdbId <= 0) return null
        val baseUrl = BuildConfig.PLAYER_BASE_URL.trim()
        val uri = Uri.parse(baseUrl)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        return uri.buildUpon().appendQueryParameter("id", tmdbId.toString())
    }
}
