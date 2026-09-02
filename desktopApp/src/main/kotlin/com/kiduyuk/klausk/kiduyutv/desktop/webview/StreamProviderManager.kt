package com.kiduyuk.klausk.kiduyutv.desktop.webview

import java.net.URI

data class StreamProvider(
    val name: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String,
    val iframeAttributes: Map<String, String> = emptyMap(),
    val movieParameters: (timestamp: Long) -> Map<String, String> = { emptyMap() },
    val tvParameters: (timestamp: Long) -> Map<String, String> = { emptyMap() }
)

/**
 * Desktop counterpart of the TV app's StreamProviderManager fallback catalog.
 *
 * The desktop browser path intentionally exposes the TV-enabled providers only. Phone-only
 * providers are omitted because their layouts and controls are not suitable for the desktop/TV UI.
 */
object StreamProviderManager {
    private fun resumeParameter(name: String, timestamp: Long): Map<String, String> =
        if (timestamp > 0L) mapOf(name to timestamp.toString()) else emptyMap()

    private fun autoplay(timestamp: Long, key: String = "startAt"): Map<String, String> =
        mapOf("autoplay" to "true") + resumeParameter(key, timestamp)

    val providers: List<StreamProvider> = listOf(
        StreamProvider(
            "Videasy",
            "https://player.videasy.net/movie/%d",
            "https://player.videasy.net/tv/%d/%d/%d",
            iframeAttributes = mapOf("frameborder" to "0", "allow" to "encrypted-media"),
            movieParameters = { timestamp ->
                mapOf("overlay" to "true", "color" to "8B5CF6") + resumeParameter("progress", timestamp)
            },
            tvParameters = { timestamp ->
                mapOf(
                    "nextEpisode" to "true",
                    "autoplayNextEpisode" to "true",
                    "episodeSelector" to "true",
                    "overlay" to "true",
                    "color" to "8B5CF6"
                ) + resumeParameter("progress", timestamp)
            }
        ),
        StreamProvider(
            "Vidrock",
            "https://vidrock.net/movie/%d",
            "https://vidrock.net/tv/%d/%d/%d",
            movieParameters = { autoplay(it) },
            tvParameters = { autoplay(it) + mapOf("autonext" to "true") }
        ),
        StreamProvider(
            "VidLink",
            "https://vidlink.pro/movie/%d",
            "https://vidlink.pro/tv/%d/%d/%d",
            iframeAttributes = mapOf("frameborder" to "0"),
            movieParameters = { mapOf("autoPlay" to "true") + resumeParameter("startAt", it) },
            tvParameters = { mapOf("autoPlay" to "true") + resumeParameter("startAt", it) }
        ),
        StreamProvider(
            "VidFast",
            "https://vidfast.pro/movie/%d",
            "https://vidfast.pro/tv/%d/%d/%d",
            movieParameters = {
                mapOf("autoPlay" to "true", "theme" to "9B59B6") + resumeParameter("startAt", it)
            },
            tvParameters = {
                mapOf(
                    "autoPlay" to "true",
                    "nextButton" to "true",
                    "autoNext" to "true",
                    "theme" to "9B59B6"
                ) + resumeParameter("startAt", it)
            }
        ),
        StreamProvider(
            "VidKing",
            "https://www.vidking.net/embed/movie/%d",
            "https://www.vidking.net/embed/tv/%d/%d/%d",
            movieParameters = { mapOf("autoPlay" to "true") },
            tvParameters = {
                mapOf("autoPlay" to "true", "nextEpisode" to "true", "episodeSelector" to "true")
            }
        ),
        StreamProvider(
            "VidNest",
            "https://vidnest.fun/movie/%d",
            "https://vidnest.fun/tv/%d/%d/%d",
            iframeAttributes = mapOf("scrolling" to "no", "frameBorder" to "0"),
            movieParameters = {
                mapOf("servericon" to "show", "bottomcaption" to "true", "timeslider" to "1") +
                    resumeParameter("startAt", it)
            },
            tvParameters = { resumeParameter("startAt", it) }
        ),
        StreamProvider(
            "VidUp",
            "https://vidup.to/movie/%d",
            "https://vidup.to/tv/%d/%d/%d",
            movieParameters = { mapOf("autoPlay" to "true") },
            tvParameters = { mapOf("autoPlay" to "true") }
        ),
        StreamProvider(
            "111Movies",
            "https://111movies.com/movie/%d",
            "https://111movies.com/tv/%d/%d/%d",
            iframeAttributes = mapOf("frameborder" to "0"),
            movieParameters = { resumeParameter("startAt", it) },
            tvParameters = { resumeParameter("startAt", it) }
        ),
        StreamProvider("Flixer", "https://flixer.su/watch/movie/%d", "https://flixer.su/watch/tv/%d/%d/%d"),
        StreamProvider(
            "VidCore",
            "https://vidcore.net/movie/%d",
            "https://vidcore.net/tv/%d/%d/%d",
            movieParameters = { mapOf("autoPlay" to "true", "sub" to "en") },
            tvParameters = { mapOf("autoPlay" to "true", "nextButton" to "true", "autoNext" to "true") }
        ),
        StreamProvider(
            "MoviesApi",
            "https://moviesapi.to/movie/%d",
            "https://moviesapi.to/tv/%d-%d-%d",
            iframeAttributes = mapOf("frameborder" to "0")
        ),
        StreamProvider(
            "Peachify",
            "https://peachify.top/embed/movie/%d",
            "https://peachify.top/embed/tv/%d/%d/%d",
            movieParameters = { mapOf("sub" to "English") },
            tvParameters = { mapOf("sub" to "English", "autoNext" to "30") }
        ),
        StreamProvider(
            "VidPlus",
            "https://player.vidplus.to/embed/movie/%d",
            "https://player.vidplus.to/embed/tv/%d/%d/%d",
            movieParameters = {
                mapOf(
                    "autoplay" to "true",
                    "autoNext" to "true",
                    "nextButton" to "true",
                    "poster" to "true",
                    "title" to "true",
                    "episodelist" to "true",
                    "servericon" to "true"
                )
            },
            tvParameters = {
                mapOf(
                    "autoplay" to "true",
                    "autoNext" to "true",
                    "poster" to "true",
                    "title" to "true",
                    "servericon" to "true"
                )
            }
        ),
        StreamProvider(
            "Cinemaos",
            "https://cinemaos.tech/player/%d",
            "https://cinemaos.tech/player/%d/%d/%d",
            movieParameters = { mapOf("autoplay" to "true") },
            tvParameters = { mapOf("autoplay" to "true") }
        ),
        StreamProvider(
            "Amri",
            "https://amri.gg/movie/%d",
            "https://amri.gg/tv/%d/%d/%d",
            movieParameters = { mapOf("autoplay" to "true") },
            tvParameters = { mapOf("autoplay" to "true") }
        ),
        StreamProvider(
            "Zxc",
            "https://zxcstream.xyz/embed/movie/%d",
            "https://zxcstream.xyz/embed/tv/%d/%d/%d",
            movieParameters = { mapOf("autoplay" to "true") },
            tvParameters = { mapOf("autoplay" to "true") }
        ),
        StreamProvider(
            "Vlux",
            "https://vidlux.xyz/embed/movie/%d",
            "https://vidlux.xyz/embed/tv/%d/%d/%d",
            movieParameters = { mapOf("autoplay" to "true") },
            tvParameters = { mapOf("autoplay" to "true") }
        ),
        StreamProvider(
            "VidSrc (WTF) v4",
            "https://vidsrc.wtf/api/4/movie/?id=%d",
            "https://vidsrc.wtf/api/4/tv/?id=%d&s=%d&e=%d"
        ),
        StreamProvider(
            "PrimeSrc",
            "https://primesrc.me/embed/movie?tmdb=%d",
            "https://primesrc.me/embed/tv?tmdb=%d&season=%d&episode=%d"
        ),
        StreamProvider(
            "VidSrc (WTF) v3 - Multi Providers",
            "https://vidsrc.wtf/api/3/movie/?id=%d",
            "https://vidsrc.wtf/api/3/tv/?id=%d&s=%d&e=%d"
        ),
        StreamProvider(
            "VidZee",
            "https://player.vidzee.wtf/v2/embed/movie/%d",
            "https://player.vidzee.wtf/v2/embed/tv/%d/%d/%d"
        ),
        StreamProvider(
            "Lordflix",
            "https://lordflix.org/watch/movie/%d",
            "https://lordflix.org/watch/tv/%d/%d/%d"
        ),
        StreamProvider(
            "Mapple",
            "https://mapple.uk/watch/movie/%d",
            "https://mapple.uk/watch/tv/%d-%d-%d"
        )
    )

    fun getProvider(providerName: String): StreamProvider? =
        providers.firstOrNull { it.name.equals(providerName, ignoreCase = true) }

    fun generateUrl(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L
    ): String {
        val provider = getProvider(providerName) ?: providers.first()
        val baseUrl = if (isTv) {
            provider.tvUrlTemplate.format(tmdbId, season ?: 1, episode ?: 1)
        } else {
            provider.movieUrlTemplate.format(tmdbId)
        }
        val params = if (isTv) provider.tvParameters(timestamp) else provider.movieParameters(timestamp)
        if (params.isEmpty()) return baseUrl
        val query = params.entries.joinToString("&") { (key, value) -> "$key=$value" }
        return if ('?' in baseUrl) "$baseUrl&$query" else "$baseUrl?$query"
    }

    fun generateIframeHtml(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L
    ): String {
        val provider = getProvider(providerName) ?: providers.first()
        val finalUrl = generateUrl(provider.name, tmdbId, isTv, season, episode, timestamp)
        val attributes = linkedMapOf<String, String>().apply { putAll(provider.iframeAttributes) }
        if (attributes.keys.none { it.equals("frameborder", ignoreCase = true) }) attributes["frameborder"] = "0"
        if (attributes.keys.none { it.equals("allowfullscreen", ignoreCase = true) }) attributes["allowfullscreen"] = ""

        val allowFeatures = attributes.entries
            .firstOrNull { it.key.equals("allow", ignoreCase = true) }
            ?.value
            ?.split(';', ' ', ',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toMutableList()
            ?: mutableListOf()
        listOf("autoplay", "encrypted-media", "fullscreen", "picture-in-picture").forEach { feature ->
            if (allowFeatures.none { it.equals(feature, ignoreCase = true) }) allowFeatures += feature
        }
        attributes.entries.removeAll { it.key.equals("allow", ignoreCase = true) }
        attributes["allow"] = allowFeatures.joinToString("; ")

        val attributeHtml = attributes.entries.joinToString(" ") { (key, value) ->
            if (key.equals("allowfullscreen", ignoreCase = true)) "allowfullscreen"
            else "${escapeHtml(key)}=\"${escapeHtml(value)}\""
        }
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                <style>
                    html,body { margin:0; width:100%; height:100%; overflow:hidden; background:#000; }
                    iframe { position:absolute; inset:0; width:100%; height:100%; border:0; background:#000; }
                </style>
            </head>
            <body>
                <iframe id="player-frame" src="${escapeHtml(finalUrl)}" $attributeHtml></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    fun getBaseUrl(providerName: String): String {
        val url = getProvider(providerName)?.movieUrlTemplate ?: return "https://localhost/"
        return runCatching {
            val uri = URI(url)
            "${uri.scheme}://${uri.authority}/"
        }.getOrDefault("https://localhost/")
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character.toString()
                }
            )
        }
    }
}
