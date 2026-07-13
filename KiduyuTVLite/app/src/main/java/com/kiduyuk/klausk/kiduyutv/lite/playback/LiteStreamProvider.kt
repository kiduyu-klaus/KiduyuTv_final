package com.kiduyuk.klausk.kiduyutv.lite.playback

import android.net.Uri

data class LiteStreamProvider(
    val name: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String,
    val iframeAttributes: Map<String, String> = emptyMap(),
    val movieParameters: (tmdbId: Int, timestamp: Long) -> Map<String, String> =
        { _, _ -> emptyMap() },
    val tvParameters: (
        tmdbId: Int,
        season: Int,
        episode: Int,
        timestamp: Long
    ) -> Map<String, String> = { _, _, _, _ -> emptyMap() }
)

object LiteStreamProviders {

    val all: List<LiteStreamProvider> = listOf(
        LiteStreamProvider(
            name = "Videasy",
            movieUrlTemplate = "https://player.videasy.net/movie/%d",
            tvUrlTemplate = "https://player.videasy.net/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0",
                "allow" to "encrypted-media"
            ),
            movieParameters = { _, timestamp ->
                buildMap {
                    put("overlay", "true")
                    put("color", "8B5CF6")
                    if (timestamp > 0) put("progress", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    put("nextEpisode", "true")
                    put("autoplayNextEpisode", "true")
                    put("episodeSelector", "true")
                    put("overlay", "true")
                    put("color", "8B5CF6")
                    if (timestamp > 0) put("progress", timestamp.toString())
                }
            }
        ),
        LiteStreamProvider(
            name = "Vidrock",
            movieUrlTemplate = "https://vidrock.net/movie/%d",
            tvUrlTemplate = "https://vidrock.net/tv/%d/%d/%d",
            movieParameters = { _, timestamp ->
                buildMap {
                    put("autoplay", "true")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    put("autoplay", "true")
                    put("autonext", "true")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            }
        ),
        LiteStreamProvider(
            name = "VidLink",
            movieUrlTemplate = "https://vidlink.pro/movie/%d",
            tvUrlTemplate = "https://vidlink.pro/tv/%d/%d/%d",
            iframeAttributes = mapOf("frameborder" to "0"),
            movieParameters = { _, timestamp ->
                buildMap {
                    put("autoPlay", "true")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    put("autoPlay", "true")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            }
        ),
        LiteStreamProvider(
            name = "VidFast",
            movieUrlTemplate = "https://vidfast.pro/movie/%d",
            tvUrlTemplate = "https://vidfast.pro/tv/%d/%d/%d",
            movieParameters = { _, timestamp ->
                buildMap {
                    put("autoPlay", "true")
                    put("theme", "9B59B6")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    put("autoPlay", "true")
                    put("nextButton", "true")
                    put("autoNext", "true")
                    put("theme", "9B59B6")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            }
        )
    )

    val default: LiteStreamProvider
        get() = all.first()

    fun resolve(name: String?): LiteStreamProvider =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default

    fun isAllowedPlaybackUri(uri: Uri): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val requestedHost = uri.host?.lowercase() ?: return false
        return allowedHosts.any { allowedHost ->
            requestedHost == allowedHost || requestedHost.endsWith(".$allowedHost")
        }
    }

    private val allowedHosts: Set<String> by lazy {
        all.flatMap { provider ->
            listOf(provider.movieUrlTemplate, provider.tvUrlTemplate)
        }.mapNotNull { template ->
            Uri.parse(template).host?.lowercase()
        }.toSet()
    }
}
