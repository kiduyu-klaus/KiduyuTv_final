package com.kiduyuk.klausk.kiduyutv.data.model

/**
 * Stream provider configuration
 */
data class StreamProvider(
    val name: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String,
    val iframeAttributes: Map<String, String> = emptyMap(),
    val allowAttributes: String = "autoplay; fullscreen; encrypted-media; picture-in-picture",
    val movieParameters: (tmdbId: Int, timestamp: Long) -> Map<String, String> = { _, _ -> emptyMap() },
    val tvParameters: (tmdbId: Int, season: Int, episode: Int, timestamp: Long) -> Map<String, String> = { _, _, _, _ -> emptyMap() }
)

/**
 * StreamProviderManager - Manages all stream providers with iframe HTML generation
 */
object StreamProviderManager {

    val providers = listOf(
        // ═══════════════════════════════════════════════════════════════
        // 1. Videasy - with frameborder and allowfullscreen
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Videasy",
            movieUrlTemplate = "https://player.videasy.net/movie/%d",
            tvUrlTemplate = "https://player.videasy.net/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0",
                "allowfullscreen" to "",
                "allow" to "encrypted-media"
            ),
            movieParameters = { _, timestamp ->
                val params = mutableMapOf("overlay" to "true", "color" to "8B5CF6")
                if (timestamp > 0) params["progress"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf(
                    "nextEpisode" to "true",
                    "autoplayNextEpisode" to "true",
                    "episodeSelector" to "true",
                    "overlay" to "true",
                    "color" to "8B5CF6"
                )
                if (timestamp > 0) params["progress"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 2. VidLink - with frameborder and allowfullscreen
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidLink",
            movieUrlTemplate = "https://vidlink.pro/movie/%d",
            tvUrlTemplate = "https://vidlink.pro/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0",
                "allowfullscreen" to ""
            ),
            movieParameters = { _, timestamp ->
                val params = mutableMapOf("autoPlay" to "true")
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf("autoPlay" to "true")
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 3. VidFast - with theme=9B59B6
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidFast",
            movieUrlTemplate = "https://vidfast.pro/movie/%d",
            tvUrlTemplate = "https://vidfast.pro/tv/%d/%d/%d",
            movieParameters = { _, timestamp ->
                val params = mutableMapOf("autoPlay" to "true", "theme" to "9B59B6")
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf(
                    "autoPlay" to "true",
                    "nextButton" to "true",
                    "autoNext" to "true",
                    "theme" to "9B59B6"
                )
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 4. VidKing
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidKing",
            movieUrlTemplate = "https://www.vidking.net/embed/movie/%d",
            tvUrlTemplate = "https://www.vidking.net/embed/tv/%d/%d/%d",
            movieParameters = { _, timestamp ->
                val params = mutableMapOf("autoPlay" to "true")
                if (timestamp > 0) params["progress"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf(
                    "autoPlay" to "true",
                    "nextEpisode" to "true",
                    "episodeSelector" to "true"
                )
                if (timestamp > 0) params["progress"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 5. VidNest - with frameborder, allowfullscreen, and custom movieParameters
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidNest",
            movieUrlTemplate = "https://vidnest.fun/movie/%d",
            tvUrlTemplate = "https://vidnest.fun/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "scrolling" to "no",
                "frameBorder" to "0",
                "allowfullscreen" to ""
            ),
            movieParameters = { _, timestamp ->
                val params = mutableMapOf(
                    "servericon" to "show",
                    "bottomcaption" to "true",
                    "timeslider" to "1"
                )
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf<String, String>()
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 6. Vidsync
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Vidsync",
            movieUrlTemplate = "https://vidsync.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidsync.xyz/embed/tv/%d/%d/%d",
            movieParameters = { _, _ -> mapOf("autoPlay" to "true") },
            tvParameters = { _, _, _, _ -> mapOf("autoPlay" to "true", "autoNext" to "true") }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 7. Vidrock
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Vidrock",
            movieUrlTemplate = "https://vidrock.net/movie/%d",
            tvUrlTemplate = "https://vidrock.net/tv/%d/%d/%d",
            movieParameters = { _, timestamp ->
                val params = mutableMapOf("autoplay" to "true")
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf("autoplay" to "true", "autonext" to "true")
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 8. Flixer
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Flixer",
            movieUrlTemplate = "https://flixer.su/watch/movie/%d",
            tvUrlTemplate = "https://flixer.su/watch/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 9. StreamingNow
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "StreamingNow",
            movieUrlTemplate = "https://streamingnow.mov/movie/%d",
            tvUrlTemplate = "https://streamingnow.mov/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 10. Autoembed
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Autoembed",
            movieUrlTemplate = "https://autoembed.co/movie/tmdb/%d",
            tvUrlTemplate = "https://autoembed.co/tv/tmdb/%d-%d-%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 11. VidSrc (WTF) v4 - Premium
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v4",
            movieUrlTemplate = "https://vidsrc.wtf/api/4/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/4/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 12. MoviesAPI
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "MoviesAPI",
            movieUrlTemplate = "https://moviesapi.club/movie/%d",
            tvUrlTemplate = "https://moviesapi.club/tv/%d-%d-%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 13. VidSrc (WTF) v1 - Multi Server
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v1",
            movieUrlTemplate = "https://vidsrc.wtf/api/1/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/1/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 14. VidSrc (WTF) v3 - Multi Providers
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v3 - Multi Providers",
            movieUrlTemplate = "https://vidsrc.wtf/api/3/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/3/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 15. VidZee
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidZee",
            movieUrlTemplate = "https://player.vidzee.wtf/embed/movie/%d",
            tvUrlTemplate = "https://player.vidzee.wtf/embed/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 16. 2Embed
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "2Embed",
            movieUrlTemplate = "https://www.2embed.stream/embed/movie/%d",
            tvUrlTemplate = "https://www.2embed.stream/embed/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 17. Smashystream
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Smashystream",
            movieUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d",
            tvUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d&season=%d&episode=%d",
            iframeAttributes = mapOf(
                "frameborder" to "0",
                "allowfullscreen" to ""
            ),
            movieParameters = { _, timestamp ->
                val params = mutableMapOf<String, String>()
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf<String, String>()
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 18. 111Movies
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "111Movies",
            movieUrlTemplate = "https://111movies.com/movie/%d",
            tvUrlTemplate = "https://111movies.com/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0",
                "allowfullscreen" to ""
            ),
            movieParameters = { _, timestamp ->
                val params = mutableMapOf<String, String>()
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            },
            tvParameters = { _, _, _, timestamp ->
                val params = mutableMapOf<String, String>()
                if (timestamp > 0) params["startAt"] = timestamp.toString()
                params
            }
        )
    )

    /**
     * Generate iframe HTML for a given provider
     */
    fun generateIframeHtml(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L
    ): String {
        val provider = providers.find { it.name.equals(providerName, ignoreCase = true) } ?: providers[0]

        val baseUrl: String
        val params: Map<String, String>

        if (isTv) {
            val s = season ?: 1
            val e = episode ?: 1
            baseUrl = String.format(provider.tvUrlTemplate, tmdbId, s, e)
            params = provider.tvParameters(tmdbId, s, e, timestamp)
        } else {
            baseUrl = String.format(provider.movieUrlTemplate, tmdbId)
            params = provider.movieParameters(tmdbId, timestamp)
        }

        val finalUrl = if (params.isNotEmpty()) {
            val query = params.map { "${it.key}=${it.value}" }.joinToString("&")
            if (baseUrl.contains("?")) "$baseUrl&$query" else "$baseUrl?$query"
        } else {
            baseUrl
        }

        val attributes = provider.iframeAttributes.toMutableMap()
        if (!attributes.containsKey("allow")) attributes["allow"] = provider.allowAttributes

        val attrString = attributes.map { "${it.key}=\"${it.value}\"" }.joinToString(" ")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                    iframe { width: 100%; height: 100%; border: none; position: absolute; top: 0; left: 0; }
                </style>
            </head>
            <body>
                <iframe 
                    id="player-frame"
                    src="$finalUrl" 
                    allowfullscreen 
                    $attrString>
                </iframe>
                <script>
                    window.addEventListener('message', function(event) {
                        if (window.VideasyInterface) {
                            var data = typeof event.data === 'string' ? event.data : JSON.stringify(event.data);
                            window.VideasyInterface.postMessage(data);
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Extract base URL from a URL template (scheme + host)
     */
    fun getBaseUrl(providerName: String): String {
        val provider = providers.find { it.name.equals(providerName, ignoreCase = true) } ?: providers[0]
        val url = provider.movieUrlTemplate

        return try {
            val protocolEnd = url.indexOf("://")
            val pathStart = url.indexOf("/", protocolEnd + 3)
            if (pathStart != -1) {
                url.substring(0, pathStart)
            } else {
                url
            }
        } catch (e: Exception) {
            "https://vidlink.pro"
        }
    }

    /**
     * Get provider by name
     */
    fun getProvider(providerName: String): StreamProvider? {
        return providers.find { it.name.equals(providerName, ignoreCase = true) }
    }

    /**
     * Get all provider names
     */
    fun getAllProviderNames(): List<String> {
        return providers.map { it.name }
    }

    /**
     * Generate URL (without iframe HTML) for a provider
     */
    fun generateUrl(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L
    ): String {
        val provider = providers.find { it.name.equals(providerName, ignoreCase = true) } ?: providers[0]

        val baseUrl: String
        val params: Map<String, String>

        if (isTv) {
            val s = season ?: 1
            val e = episode ?: 1
            baseUrl = String.format(provider.tvUrlTemplate, tmdbId, s, e)
            params = provider.tvParameters(tmdbId, s, e, timestamp)
        } else {
            baseUrl = String.format(provider.movieUrlTemplate, tmdbId)
            params = provider.movieParameters(tmdbId, timestamp)
        }

        return if (params.isNotEmpty()) {
            val query = params.map { "${it.key}=${it.value}" }.joinToString("&")
            if (baseUrl.contains("?")) "$baseUrl&$query" else "$baseUrl?$query"
        } else {
            baseUrl
        }
    }
}