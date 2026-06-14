package com.kiduyuk.klausk.kiduyutv.data.model

/**
 * Stream provider configuration
 */
data class StreamProvider(
    val name: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String,
    val iframeAttributes: Map<String, String> = emptyMap(),
    val allowAttributes: String = "autoplay; encrypted-media; picture-in-picture",
    val movieParameters: (tmdbId: Int, timestamp: Long) -> Map<String, String> = { _, _ -> emptyMap() },
    val tvParameters: (tmdbId: Int, season: Int, episode: Int, timestamp: Long) -> Map<String, String> = { _, _, _, _ -> emptyMap() },
    val isPhoneOnly: Boolean = false
)

/**
 * StreamProviderManager - Manages all stream providers with iframe HTML generation
 */
object StreamProviderManager {

    val providers = listOf(
        // ═══════════════════════════════════════════════════════════════
        // 1. Videasy - with frameborder
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Videasy",
            movieUrlTemplate = "https://player.videasy.net/movie/%d",
            tvUrlTemplate = "https://player.videasy.net/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0",
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
        // 2. Vidrock
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
        // 3. VidLink - with frameborder
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidLink",
            movieUrlTemplate = "https://vidlink.pro/movie/%d",
            tvUrlTemplate = "https://vidlink.pro/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0"
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
        // 4. VidFast - with theme=9B59B6
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
        // 5. VidKing
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 6. VidNest - with frameborder
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidNest",
            movieUrlTemplate = "https://vidnest.fun/movie/%d",
            tvUrlTemplate = "https://vidnest.fun/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "scrolling" to "no",
                "frameBorder" to "0"
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
        // 7. VidUp
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        StreamProvider(
            name = "111Movies",
            movieUrlTemplate = "https://111movies.com/movie/%d",
            tvUrlTemplate = "https://111movies.com/tv/%d/%d/%d",
            iframeAttributes = mapOf(
                "frameborder" to "0"
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
        // 9. Flixer
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Flixer",
            movieUrlTemplate = "https://flixer.su/watch/movie/%d",
            tvUrlTemplate = "https://flixer.su/watch/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 9. VidCore
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 10. MoviesApi
        // Movies:   https://moviesapi.to/movie/$id
        // TV Shows: https://moviesapi.to/tv/$id-$season-$episode
        // ($id is a TMDB id)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 11. Peachify
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 12. VidAPI (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
            },
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 13. VidPlus
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 14. CineSrc (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
            },
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 15. Vidzen (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Vidzen",
            movieUrlTemplate = "https://vidzen.fun/movie/%d",
            tvUrlTemplate = "https://vidzen.fun/tv/%d/%d/%d",
            movieParameters = { _, _ ->
                mapOf("autoplay" to "true")
            },
            tvParameters = { _, _, _, _ ->
                mapOf("autoplay" to "true")
            },
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 16. Cinemaos
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 17. Amri
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 18. Zxc
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 19. Vlux
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
        // 20. VidSrc (WTF) v4 - Premium
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v4",
            movieUrlTemplate = "https://vidsrc.wtf/api/4/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/4/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 21. PrimeSrc
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "PrimeSrc",
            movieUrlTemplate = "https://primesrc.me/embed/movie?tmdb=%d",
            tvUrlTemplate = "https://primesrc.me/embed/tv?tmdb=%d&season=%d&episode=%d",
            movieParameters = { _, _ -> emptyMap() },
            tvParameters = { _, _, _, _ -> emptyMap() }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 22. VidSrc (WTF) v3 - Multi Providers
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v3 - Multi Providers",
            movieUrlTemplate = "https://vidsrc.wtf/api/3/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/3/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 23. VidZee
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidZee",
            movieUrlTemplate = "https://player.vidzee.wtf/v2/embed/movie/%d",
            tvUrlTemplate = "https://player.vidzee.wtf/v2/embed/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 24. Lordflix
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Lordflix",
            movieUrlTemplate = "https://lordflix.org/watch/movie/%d",
            tvUrlTemplate = "https://lordflix.org/watch/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 25. Mapple
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Mapple",
            movieUrlTemplate = "https://mapple.uk/watch/movie/%d",
            tvUrlTemplate = "https://mapple.uk/watch/tv/%d-%d-%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 26. Smashystream (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Smashystream",
            movieUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d",
            tvUrlTemplate = "https://embed.smashystream.com/playere.php?tmdb=%d&season=%d&episode=%d",
            iframeAttributes = mapOf(
                "frameborder" to "0"
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
            },
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 27. Autoembed (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Autoembed",
            movieUrlTemplate = "https://autoembed.co/movie/tmdb/%d",
            tvUrlTemplate = "https://autoembed.co/tv/tmdb/%d-%d-%d",
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 29. EmbedMaster (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
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
            },
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 30. Vidsync (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Vidsync",
            movieUrlTemplate = "https://vidsync.xyz/embed/movie/%d",
            tvUrlTemplate = "https://vidsync.xyz/embed/tv/%d/%d/%d",
            movieParameters = { _, _ -> mapOf("autoPlay" to "true") },
            tvParameters = { _, _, _, _ -> mapOf("autoPlay" to "true", "autoNext" to "true") },
            isPhoneOnly = true
        ),

        // ═══════════════════════════════════════════════════════════════
        // 31. VidSrc (WTF) v1 - Multi Server (Phone only)
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v1",
            movieUrlTemplate = "https://vidsrc.wtf/api/1/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/1/tv/?id=%d&s=%d&e=%d",
            isPhoneOnly = true
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

        // Enable postMessage communication from iframes by setting document.domain
        // This allows the tracking script to receive position updates from the embedded player
        val domainScript = """
            <script>
            try { document.domain = document.domain; } catch(e) {}
            </script>
        """.trimIndent()

        // Unified tracking script for watch progress
        val trackingScript = """
            <script>
            (function () {
              'use strict';
              var PROVIDER = '${provider.name.replace("'", "\\'")}';

              var VIDFAST_ORIGINS = {
                'https://vidfast.pro': 1, 'https://vidfast.in': 1, 'https://vidfast.io': 1,
                'https://vidfast.me':  1, 'https://vidfast.net': 1, 'https://vidfast.pm': 1,
                'https://vidfast.xyz': 1
              };

              function originOK(origin) {
                switch (PROVIDER) {
                  case 'Vidrock':  return origin === 'https://vidrock.ru';
                  case 'VidLink':  return origin === 'https://vidlink.pro';
                  case 'VidFast':  return !!VIDFAST_ORIGINS[origin];
                  case 'VidNest':  return origin === 'https://vidnest.fun';
                  case 'VidUp':    return origin === 'https://vidup.to';
                  case 'VidCore':  return origin === 'https://vidcore.net';
                  case 'Peachify': return origin === 'https://peachify.top';
                  default:         return true;
                }
              }

              function emit(position, season, episode) {
                if (!window.MavisInterface || !window.MavisInterface.onPlayerEvent) return;
                window.MavisInterface.onPlayerEvent(JSON.stringify({
                  provider:    PROVIDER,
                  currentTime: position,
                  season:      season,
                  episode:     episode
                }));
              }

              function fromStringPayload(str) {
                if (PROVIDER !== 'Videasy' && PROVIDER !== 'VidKing') return false;
                var msg;
                try { msg = JSON.parse(str); } catch (e) { return false; }
                if (!msg || typeof msg !== 'object') return false;
                emit(
                  typeof msg.timestamp === 'number' ? msg.timestamp : null,
                  typeof msg.season   === 'number' ? msg.season   : null,
                  typeof msg.episode  === 'number' ? msg.episode  : null
                );
                return true;
              }

              function fromMediaData(payload) {
                if (!payload || payload.type !== 'MEDIA_DATA' || !payload.data) return false;
                for (var key in payload.data) {
                  if (!Object.prototype.hasOwnProperty.call(payload.data, key)) continue;
                  var item = payload.data[key];
                  if (!item) continue;

                  var season  = item.last_season_watched  != null ? item.last_season_watched  : null;
                  var episode = item.last_episode_watched != null ? item.last_episode_watched : null;

                  var ep = null;
                  if (season != null && episode != null && item.show_progress) {
                    ep = item.show_progress['s' + season + 'e' + episode] || null;
                  }

                  var currentTime = null;
                  if (ep && ep.progress && typeof ep.progress.watched === 'number') {
                    currentTime = ep.progress.watched;
                    if (typeof ep.season  === 'number') season  = ep.season;
                    if (typeof ep.episode === 'number') episode = ep.episode;
                  } else if (item.progress && typeof item.progress.watched === 'number') {
                    currentTime = item.progress.watched;
                  }

                  if (typeof season  === 'string') season  = parseInt(season,  10);
                  if (typeof episode === 'string') episode = parseInt(episode, 10);

                  emit(currentTime, season, episode);
                }
                return true;
              }

              function fromTimeUpdate(payload) {
                if (!payload || payload.type !== 'timeupdate' || !payload.data) return false;
                emit(
                  typeof payload.data.currentTime === 'number' ? payload.data.currentTime : null,
                  null,
                  null
                );
                return true;
              }

              window.addEventListener('message', function (event) {
                if (!originOK(event.origin)) return;
                var raw = event.data;

                if (typeof raw === 'string') {
                  fromStringPayload(raw);
                  return;
                }
                if (!raw || typeof raw !== 'object') return;

                if (raw.type === 'timeupdate') {
                  fromTimeUpdate(raw);
                  return;
                }

                if (raw.type === 'MEDIA_DATA') {
                  fromMediaData(raw);
                  return;
                }
              });
            })();
            </script>
        """.trimIndent()

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                    iframe { width: 100%; height: 100%; border: none; overflow: hidden; position: absolute; top: 0; left: 0; }
                </style>
            </head>
            <body>
                <iframe 
                    id="player-frame"
                    src="$finalUrl" 
                    $attrString>
                </iframe>
                $domainScript
                $trackingScript
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
     * Get providers filtered by device type
     * @param isTvDevice true for TV devices, false for phone/tablet
     */
    fun getProvidersForDevice(isTvDevice: Boolean): List<StreamProvider> {
        return if (isTvDevice) {
            providers.filter { !it.isPhoneOnly }
        } else {
            providers
        }
    }

    /**
     * Get provider names filtered by device type
     * @param isTvDevice true for TV devices, false for phone/tablet
     */
    fun getProviderNamesForDevice(isTvDevice: Boolean): List<String> {
        return getProvidersForDevice(isTvDevice).map { it.name }
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