package com.kiduyuk.klausk.kiduyutv.data.model

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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

    private const val TAG = "StreamProviderManager"
    private const val PROVIDERS_CONFIG_PATH = "app_config/stream_providers_Configuration"

    private var firebaseListener: ValueEventListener? = null

    private val fallbackProviders = listOf(
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
        // 10. VidCore
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
        // 11. MoviesApi
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
        // 12. Peachify
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
        // 13. VidAPI (Phone only)
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
        // 14. VidPlus
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
        // 15. CineSrc (Phone only)
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
        // 16. Vidzen (Phone only)
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
        // 17. Cinemaos
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
        // 18. Amri
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
        // 19. Zxc
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
        // 20. Vlux
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
        // 21. VidSrc (WTF) v4 - Premium
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v4",
            movieUrlTemplate = "https://vidsrc.wtf/api/4/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/4/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 22. PrimeSrc
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "PrimeSrc",
            movieUrlTemplate = "https://primesrc.me/embed/movie?tmdb=%d",
            tvUrlTemplate = "https://primesrc.me/embed/tv?tmdb=%d&season=%d&episode=%d",
            movieParameters = { _, _ -> emptyMap() },
            tvParameters = { _, _, _, _ -> emptyMap() }
        ),

        // ═══════════════════════════════════════════════════════════════
        // 23. VidSrc (WTF) v3 - Multi Providers
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidSrc (WTF) v3 - Multi Providers",
            movieUrlTemplate = "https://vidsrc.wtf/api/3/movie/?id=%d",
            tvUrlTemplate = "https://vidsrc.wtf/api/3/tv/?id=%d&s=%d&e=%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 24. VidZee
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "VidZee",
            movieUrlTemplate = "https://player.vidzee.wtf/v2/embed/movie/%d",
            tvUrlTemplate = "https://player.vidzee.wtf/v2/embed/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 25. Lordflix
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Lordflix",
            movieUrlTemplate = "https://lordflix.org/watch/movie/%d",
            tvUrlTemplate = "https://lordflix.org/watch/tv/%d/%d/%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 26. Mapple
        // ═══════════════════════════════════════════════════════════════
        StreamProvider(
            name = "Mapple",
            movieUrlTemplate = "https://mapple.uk/watch/movie/%d",
            tvUrlTemplate = "https://mapple.uk/watch/tv/%d-%d-%d"
        ),

        // ═══════════════════════════════════════════════════════════════
        // 27. Smashystream (Phone only)
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
        // 28. Autoembed (Phone only)
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

    @Volatile
    var providers: List<StreamProvider> = fallbackProviders
        private set

    /**
     * Starts a realtime listener for app_config/stream_providers_Configuration.
     * The hardcoded provider list remains the fallback if Firebase is empty,
     * disabled, malformed, or temporarily unreachable.
     */
    fun startFirebaseSync() {
        if (firebaseListener != null) return

        val ref = FirebaseDatabase.getInstance().getReference(PROVIDERS_CONFIG_PATH)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteProviders = parseProviders(snapshot)
                if (remoteProviders.isNotEmpty()) {
                    providers = remoteProviders
                    Log.i(TAG, "Loaded ${remoteProviders.size} stream providers from Firebase")
                } else {
                    providers = fallbackProviders
                    Log.w(TAG, "Firebase stream provider config empty; using fallback providers")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                providers = fallbackProviders
                Log.w(TAG, "Failed to load stream providers from Firebase: ${error.message}")
            }
        }

        firebaseListener = listener
        ref.addValueEventListener(listener)
    }

    fun stopFirebaseSync() {
        val listener = firebaseListener ?: return
        FirebaseDatabase.getInstance()
            .getReference(PROVIDERS_CONFIG_PATH)
            .removeEventListener(listener)
        firebaseListener = null
    }

    private fun parseProviders(snapshot: DataSnapshot): List<StreamProvider> {
        val fallbackOrder = fallbackProviders
            .mapIndexed { index, provider -> provider.name.lowercase() to index }
            .toMap()

        return snapshot.children
            .mapIndexedNotNull { index, child ->
                parseProvider(child)
                    ?.let { ParsedProvider(it, fallbackOrder[it.name.lowercase()] ?: (fallbackProviders.size + index)) }
            }
            .sortedBy { it.order }
            .map { it.provider }
    }

    private fun parseProvider(snapshot: DataSnapshot): StreamProvider? {
        val enabled = snapshot.child("enabled").getValue(Boolean::class.java) ?: true
        if (!enabled) return null

        val name = snapshot.child("stream_provider_name").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.key
            ?: return null
        val movieUrlTemplate = snapshot.child("movie_url_template").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val tvUrlTemplate = snapshot.child("tv_url_template").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val matchingFallback = fallbackProviders.find { it.name.equals(name, ignoreCase = true) }
        val iframeAttributes = snapshot.child("iframe_attributes").toStringMap()
        val allowAttributes = snapshot.child("allow_attributes").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: matchingFallback?.allowAttributes
            ?: "autoplay; encrypted-media; picture-in-picture"
        val movieParameterMap = snapshot.child("movie_parameters").toStringMap()
        val tvParameterMap = snapshot.child("tv_parameters").toStringMap()
        val isPhoneOnly = snapshot.child("is_phone_only").getValue(Boolean::class.java)
            ?: snapshot.child("phone_only").getValue(Boolean::class.java)
            ?: matchingFallback?.isPhoneOnly
            ?: false

        return StreamProvider(
            name = name,
            movieUrlTemplate = movieUrlTemplate,
            tvUrlTemplate = tvUrlTemplate,
            iframeAttributes = iframeAttributes.ifEmpty { matchingFallback?.iframeAttributes ?: emptyMap() },
            allowAttributes = allowAttributes,
            movieParameters = { tmdbId, timestamp ->
                val fallbackParams = matchingFallback?.movieParameters?.invoke(tmdbId, timestamp).orEmpty()
                mergeParameterMaps(fallbackParams, movieParameterMap)
            },
            tvParameters = { tmdbId, season, episode, timestamp ->
                val fallbackParams = matchingFallback?.tvParameters?.invoke(tmdbId, season, episode, timestamp).orEmpty()
                mergeParameterMaps(fallbackParams, tvParameterMap)
            },
            isPhoneOnly = isPhoneOnly
        )
    }

    private fun DataSnapshot.toStringMap(): Map<String, String> {
        if (!exists()) return emptyMap()
        return children.mapNotNull { child ->
            val key = child.key ?: return@mapNotNull null
            val value = child.value?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    private fun mergeParameterMaps(
        fallbackParams: Map<String, String>,
        firebaseParams: Map<String, String>
    ): Map<String, String> {
        if (fallbackParams.isEmpty()) return firebaseParams
        if (firebaseParams.isEmpty()) return fallbackParams
        return fallbackParams.toMutableMap().apply { putAll(firebaseParams) }
    }

    private data class ParsedProvider(
        val provider: StreamProvider,
        val order: Int
    )

    /**
     * Static shell of the player wrapper HTML. The four placeholders are
     * replaced at runtime via [String.format] to avoid rebuilding the entire
     * document on every playback, which lets the JIT reparse the constant
     * parts and reduces GC pressure during fast scrolling through TV episodes.
     */
    private const val IFRAME_HTML_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            %1${'$'}s
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                iframe { width: 100%; height: 100%; border: none; overflow: hidden; position: absolute; top: 0; left: 0; }
            </style>
        </head>
        <body>
            <iframe id="player-frame" src="%2${'$'}s" %3${'$'}s></iframe>
            <script>window.__kiduyuPlayerConfig={contentId:%4${'$'}d,isTv:%5${'$'}s,season:%6${'$'}d,episode:%7${'$'}d};</script>
            <script src="file:///android_asset/kiduyu_tracker.js"></script>
        </body>
        </html>
    """

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

        // Ensure the iframe always advertises the required permissions for the embedded player.
        // We parse the existing "allow" value (if any) into individual features and make sure
        // "autoplay" and "encrypted-media" are present. Already-present features are left as-is
        // to preserve any extras the provider has configured (e.g. "picture-in-picture",
        // "fullscreen"). Works the same whether iframe_attributes is empty or already populated.
        val requiredAllowFeatures = listOf("autoplay", "encrypted-media")
        val existingAllowFeatures = attributes["allow"]
            ?.split(';', ' ', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableList()
            ?: mutableListOf()
        for (feature in requiredAllowFeatures) {
            if (feature !in existingAllowFeatures) {
                existingAllowFeatures.add(feature)
            }
        }
        attributes["allow"] = existingAllowFeatures.joinToString("; ")

        val attrString = attributes.map { "${it.key}=\"${it.value}\"" }.joinToString(" ")

        // Preconnect hint: pull DNS + TLS handshake for the provider's host out of
        // the critical path. The provider origin is parsed from the final URL; if
        // parsing fails we silently omit the hint rather than breaking the page.
        val preconnect = try {
            val providerUri = android.net.Uri.parse(finalUrl)
            val scheme = providerUri.scheme
            val host = providerUri.host
            if (scheme != null && host != null) {
                "<link rel=\"preconnect\" href=\"$scheme://$host\" crossorigin>" +
                    "<link rel=\"dns-prefetch\" href=\"//$host\">"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }

        return IFRAME_HTML_TEMPLATE.format(
            preconnect,             // %1$s
            finalUrl,               // %2$s
            attrString,             // %3$s
            tmdbId,                 // %4$d
            if (isTv) "true" else "false", // %5$s
            season ?: 1,            // %6$d
            episode ?: 1            // %7$d
        ).trimIndent()
    }

    /**
     * Returns the JS bootstrap snippet used to inject the per-playback player
     * configuration into a same-origin page. This is the counterpart to the
     * inline `<script>` in [IFRAME_HTML_TEMPLATE] and is invoked from
     * [PlayerActivity] when the player URL is loaded directly without the
     * wrapper document.
     */
    fun buildTrackerBootstrap(
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?
    ): String {
        val safeSeason = season ?: 1
        val safeEpisode = episode ?: 1
        return "(function(){window.__kiduyuPlayerConfig={contentId:$tmdbId," +
            "isTv:${if (isTv) "true" else "false"}," +
            "season:$safeSeason,episode:$safeEpisode};})();"
    }

    /**
     * Returns the inline `<script>` tag that loads the shared tracker asset.
     * Used both inside the wrapper template and when injecting the tracker
     * into a same-origin player page.
     */
    const val TRACKER_SCRIPT_TAG: String =
        "<script src=\"file:///android_asset/kiduyu_tracker.js\"></script>"

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