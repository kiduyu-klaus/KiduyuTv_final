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
            movieUrlTemplate = "https://player.videasy.to/movie/%d",
            tvUrlTemplate = "https://player.videasy.to/tv/%d/%d/%d",
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
            movieUrlTemplate = "https://vidrock.ru/movie/%d",
            tvUrlTemplate = "https://vidrock.ru/tv/%d/%d/%d",
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
            movieUrlTemplate = "https://vidfast.vc/movie/%d",
            tvUrlTemplate = "https://vidfast.vc/tv/%d/%d/%d",
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
        ),

        // ═══════════════════════════════════════════════════════════════
        // 5. VidKing
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidKing",
            movieUrlTemplate = "https://www.vidking.net/embed/movie/%d",
            tvUrlTemplate = "https://www.vidking.net/embed/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoPlay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "autoPlay" to "true",
                    "nextEpisode" to "true",
                    "episodeSelector" to "true"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 6. VidNest
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidNest",
            movieUrlTemplate = "https://vidnest.fun/movie/%d",
            tvUrlTemplate = "https://vidnest.fun/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "scrolling" to "no",
                "frameBorder" to "0"
            ),
            movieParameters = { _, timestamp ->
                buildMap {
                    put("servericon", "show")
                    put("bottomcaption", "true")
                    put("timeslider", "1")
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 7. VidUp
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidUp",
            movieUrlTemplate = "https://vidup.to/movie/%d",
            tvUrlTemplate = "https://vidup.to/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoPlay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoPlay" to "true")
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 8. 111Movies
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "111Movies",
            movieUrlTemplate = "https://111movies.com/movie/%d",
            tvUrlTemplate = "https://111movies.com/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0"
            ),
            movieParameters = { _, timestamp ->
                buildMap {
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 9. Flixer
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Flixer",
            movieUrlTemplate = "https://flixer.su/watch/movie/%d",
            tvUrlTemplate = "https://flixer.su/watch/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 10. VidCore
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidCore",
            movieUrlTemplate = "https://vidcore.net/movie/%d",
            tvUrlTemplate = "https://vidcore.net/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf(
                    "autoPlay" to "true",
                    "sub" to "en"
                )
            },
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "autoPlay" to "true",
                    "nextButton" to "true",
                    "autoNext" to "true"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 11. MoviesApi
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "MoviesApi",
            movieUrlTemplate = "https://moviesapi.to/movie/%d",
            tvUrlTemplate = "https://moviesapi.to/tv/%d-%d-%d",
            iframeAttributes = mapOf(
                "frameborder" to "0"
            ),
            movieParameters = { _, _ -> emptyMap() },
            tvParameters = { _, _, _, _ -> emptyMap() }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 12. Peachify
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Peachify",
            movieUrlTemplate = "https://peachify.top/embed/movie/%d",
            tvUrlTemplate = "https://peachify.top/embed/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("sub" to "English")
            },
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "sub" to "English",
                    "autoNext" to "30"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 13. VidAPI
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidAPI",
            movieUrlTemplate = "https://vaplayer.ru/embed/movie/%d",
            tvUrlTemplate = "https://vaplayer.ru/embed/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf(
                    "autoplay" to "1",
                    "overlay" to "true"
                )
            },
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "autoplay" to "1",
                    "overlay" to "true"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 14. VidPlus
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidPlus",
            movieUrlTemplate = "https://player.vidplus.to/embed/movie/%d",
            tvUrlTemplate = "https://player.vidplus.to/embed/tv/%d/%d/%d",
            movieParameters = { _, _ ->
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
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "autoplay" to "true",
                    "autoNext" to "true",
                    "poster" to "true",
                    "title" to "true",
                    "servericon" to "true"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 15. CineSrc
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "CineSrc",
            movieUrlTemplate = "https://cinesrc.st/embed/movie/%d",
            tvUrlTemplate = "https://cinesrc.st/embed/tv/%d?s=%d&e=%d",
            movieParameters = { _, _ ->
                mapOf(
                    "autoplay" to "true",
                    "quality" to "1080"
                )
            },
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "color" to "FF1493",
                    "autoplay" to "true",
                    "autonext" to "true"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 16. Vidzen
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Vidzen",
            movieUrlTemplate = "https://vidzen.fun/movie/%d",
            tvUrlTemplate = "https://vidzen.fun/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoplay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoplay" to "true")
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 17. Cinemaos
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Cinemaos",
            movieUrlTemplate = "https://cinemaos.tech/player/%d",
            tvUrlTemplate = "https://cinemaos.tech/player/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoplay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoplay" to "true")
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 18. Amri
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Amri",
            movieUrlTemplate = "https://amri.gg/movie/%d",
            tvUrlTemplate = "https://amri.gg/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoplay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoplay" to "true")
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 19. Zxc
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Zxc",
            movieUrlTemplate = "https://zxcstream.xyz/embed/movie/%d",
            tvUrlTemplate = "https://zxcstream.xyz/embed/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoplay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoplay" to "true")
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 20. Vlux
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Vlux",
            movieUrlTemplate = "https://vidlux.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidlux.xyz/embed/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoplay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoplay" to "true")
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 21. VidSrc (WTF) v4 - Premium
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidSrc (WTF) v4",
            movieUrlTemplate = "https://vidsrc.wtf/api/4/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/4/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 22. PrimeSrc
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "PrimeSrc",
            movieUrlTemplate = "https://primesrc.me/embed/movie?tmdb=%d",
            tvUrlTemplate = "https://primesrc.me/embed/tv?tmdb=%d&season=%d&episode=%d",
            movieParameters = { _, _ -> emptyMap() },
            tvParameters = { _, _, _, _ -> emptyMap() }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 23. VidSrc (WTF) v3 - Multi Providers
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidSrc (WTF) v3 - Multi Providers",
            movieUrlTemplate = "https://vidsrc.wtf/api/3/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/3/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 24. VidZee
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidZee",
            movieUrlTemplate = "https://player.vidzee.wtf/v2/embed/movie/%d",
            tvUrlTemplate = "https://player.vidzee.wtf/v2/embed/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 25. Lordflix
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Lordflix",
            movieUrlTemplate = "https://lordflix.org/watch/movie/%d",
            tvUrlTemplate = "https://lordflix.org/watch/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 26. Mapple
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Mapple",
            movieUrlTemplate = "https://mapple.uk/watch/movie/%d",
            tvUrlTemplate = "https://mapple.uk/watch/tv/%d-%d-%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 27. Smashystream
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Smashystream",
            movieUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d",
            tvUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d&season=%d&episode=%d",
            iframeAttributes = mapOf(
                "frameborder" to "0"
            ),
            movieParameters = { _, timestamp ->
                buildMap {
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            },
            tvParameters = { _, _, _, timestamp ->
                buildMap {
                    if (timestamp > 0) put("startAt", timestamp.toString())
                }
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 28. Autoembed
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Autoembed",
            movieUrlTemplate = "https://autoembed.co/movie/tmdb/%d",
            tvUrlTemplate = "https://autoembed.co/tv/tmdb/%d-%d-%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 29. EmbedMaster
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "EmbedMaster",
            movieUrlTemplate = "https://embedmaster.link/movie/%d",
            tvUrlTemplate = "https://embedmaster.link/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoPlay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf(
                    "autoPlay" to "true",
                    "nextButton" to "true",
                    "autoNext" to "true"
                )
            }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 30. Vidsync
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "Vidsync",
            movieUrlTemplate = "https://vidsync.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidsync.xyz/embed/tv/%d/%d/%d",
            movieParameters = { _, _ -> mapOf("autoPlay" to "true") },
            tvParameters = { _, _, _, _ -> mapOf("autoPlay" to "true", "autoNext" to "true") }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 31. VidSrc (WTF) v1 - Multi Server
        // ═══════════════════════════════════════════════════════════════
        LiteStreamProvider(
            name = "VidSrc (WTF) v1",
            movieUrlTemplate = "https://vidsrc.wtf/api/1/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/1/tv/?id=%d&s=%d&e=%d"
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