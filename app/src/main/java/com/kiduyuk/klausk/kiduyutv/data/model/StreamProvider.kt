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
    val isPhoneOnly: Boolean = false,
    val isPhoneEnabled: Boolean = true,
    val isTvEnabled: Boolean = !isPhoneOnly
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
                if (snapshot.exists() && snapshot.hasChildren()) {
                    providers = remoteProviders
                    Log.i(TAG, "Loaded ${remoteProviders.size} enabled stream providers from Firebase")
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
        val enabled = snapshot.child("enabled").booleanValue() ?: true
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
        val isPhoneOnly = snapshot.child("is_phone_only").booleanValue()
            ?: snapshot.child("phone_only").booleanValue()
            ?: matchingFallback?.isPhoneOnly
            ?: false
        val isPhoneEnabled = snapshot.child("isphone_enabled").booleanValue()
            ?: snapshot.child("is_phone_enabled").booleanValue()
            ?: snapshot.child("phone_enabled").booleanValue()
            ?: matchingFallback?.isPhoneEnabled
            ?: true
        val isTvEnabled = snapshot.child("istv_enabled").booleanValue()
            ?: snapshot.child("is_tv_enabled").booleanValue()
            ?: snapshot.child("tv_enabled").booleanValue()
            ?: matchingFallback?.isTvEnabled
            ?: !isPhoneOnly

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
            isPhoneOnly = isPhoneOnly,
            isPhoneEnabled = isPhoneEnabled,
            isTvEnabled = isTvEnabled
        )
    }

    private fun DataSnapshot.booleanValue(): Boolean? {
        return when (val rawValue = value) {
            is Boolean -> rawValue
            is String -> rawValue.equals("true", ignoreCase = true)
            is Number -> rawValue.toInt() != 0
            else -> null
        }
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
     * Generate iframe HTML for a given provider
     */
    fun generateIframeHtml(
        providerName: String,
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        timestamp: Long = 0L,
        isTvDevice: Boolean? = null
    ): String {
        val deviceProviders = isTvDevice?.let { getProvidersForDevice(it) } ?: providers
        val provider = deviceProviders.find { it.name.equals(providerName, ignoreCase = true) }
            ?: deviceProviders.firstOrNull()
            ?: return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;background:#000;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;">
                    <p>No stream providers are enabled for this device.</p>
                </body>
                </html>
            """.trimIndent()

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

        // Ensure every provider's iframe gets frameborder="0" and allowfullscreen, even if the
        // provider config doesn't set them. Check case-insensitively since some fallback
        // providers use "frameBorder" — avoid adding a duplicate under a different casing.
        val existingKeysLower = attributes.keys.map { it.lowercase() }
        if ("frameborder" !in existingKeysLower) {
            attributes["frameborder"] = "0"
        }
        if ("allowfullscreen" !in existingKeysLower) {
            attributes["allowfullscreen"] = ""
        }

        // Ensure the iframe always advertises the required permissions for the embedded player.
        // We parse the existing "allow" value (if any) into individual features and make sure
        // "autoplay" and "encrypted-media" are present. Already-present features are left as-is
        // to preserve any extras the provider has configured (e.g. "picture-in-picture",
        // "fullscreen"). Works the same whether iframe_attributes is empty or already populated.
        val requiredAllowFeatures = listOf("autoplay", "encrypted-media", "fullscreen")
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

        val attrString = attributes.entries.joinToString(" ") { (key, value) ->
            // allowfullscreen is a boolean HTML attribute — its presence means true, so it's
            // written bare rather than as allowfullscreen="...".
            if (key.equals("allowfullscreen", ignoreCase = true)) key else "$key=\"$value\""
        }

        // =============== UPDATED TRACKING SCRIPT ============================
        // Only supports: Videasy, Vidrock, Vidfast, Vidking, Vidnest
        // Uses postMessage to receive events from the iframe.
        // Extracts progress (seconds), season, episode and sends to Android via MavisInterface.
        val trackingScript = """
            <script>
            (function() {
                const currentContentId = $tmdbId;
                const currentIsTv = $isTv;
                const currentSeason = ${season ?: 1};
                const currentEpisode = ${episode ?: 1};

                function sendToAndroid(currentTime, duration, seasonNum, episodeNum, provider) {
                    if (typeof MavisInterface !== 'undefined' && MavisInterface.onPlayerEvent) {
                        const payload = {
                            currentTime: parseFloat(currentTime),
                            duration:    parseFloat(duration),
                            season: seasonNum ? parseInt(seasonNum, 10) : null,
                            episode: episodeNum ? parseInt(episodeNum, 10) : null,
                            provider: provider || ""
                        };
                        MavisInterface.onPlayerEvent(JSON.stringify(payload));
                    }
                }

                // Provider origin → name mapping
                const providerMap = {
                    'https://player.videasy.net': 'Videasy',
                    'https://vidrock.ru': 'Vidrock',
                    'https://vidfast.pro': 'Vidfast',
                    'https://vidfast.in': 'Vidfast',
                    'https://vidfast.io': 'Vidfast',
                    'https://vidfast.me': 'Vidfast',
                    'https://vidfast.net': 'Vidfast',
                    'https://vidfast.pm': 'Vidfast',
                    'https://vidfast.xyz': 'Vidfast',
                    'https://www.vidking.net': 'Vidking',
                    'https://vidnest.fun': 'Vidnest'
                };

                window.addEventListener('message', function(event) {
                    const origin = event.origin || '';
                    const provider = providerMap[origin];
                    if (!provider) return;  // ignore other origins

                    const data = event.data;
                    if (!data) return;

                    try {
                        // ---- 1. Videasy / Vidking (JSON with timestamp/season/episode) ----
                        if (provider === 'Videasy' || provider === 'Vidking') {
                            if (typeof data === 'string') {
                                const parsed = JSON.parse(data);
                                if (parsed.timestamp !== undefined) {
                                    const seasonVal = parsed.season !== undefined ? parsed.season : (currentIsTv ? currentSeason : null);
                                    const episodeVal = parsed.episode !== undefined ? parsed.episode : (currentIsTv ? currentEpisode : null);
                                    sendToAndroid(parsed.timestamp, parsed.duration || 0, seasonVal, episodeVal, provider);
                                }
                            }
                            return;
                        }

                        // ---- 2. Vidrock / Vidfast / Vidnest (MEDIA_DATA) ----
                        if (data.type === 'MEDIA_DATA' && data.data) {
                            const mediaData = data.data;
                            let item = null;

                            // Extract the correct item based on provider's storage key pattern
                            if (provider === 'Vidrock') {
                                // Vidrock: array of objects with .id
                                if (Array.isArray(mediaData)) {
                                    item = mediaData.find(function(x) { return x && x.id == currentContentId; });
                                }
                            } else if (provider === 'Vidfast') {
                                // Vidfast: keys "t{id}" for TV, "m{id}" for movies
                                const key = (currentIsTv ? 't' : 'm') + currentContentId;
                                item = mediaData[key] || null;
                            } else if (provider === 'Vidnest') {
                                // Vidnest: keys are simply the ID as string
                                item = mediaData[String(currentContentId)] || null;
                            }

                            if (!item) return;

                            // Determine progress, season, episode
                            let watched = 0;
                            let seasonNum = null;
                            let episodeNum = null;

                            if (currentIsTv) {
                                // Use last_season_watched / last_episode_watched if available, else fallback to current
                                const rawSeason = item.last_season_watched !== undefined ? parseInt(item.last_season_watched, 10) : currentSeason;
                                const rawEpisode = item.last_episode_watched !== undefined ? parseInt(item.last_episode_watched, 10) : currentEpisode;
                                seasonNum = rawSeason;
                                episodeNum = rawEpisode;

                                // Look for episode-specific progress
                                const epKey = 's' + seasonNum + 'e' + episodeNum;
                                if (item.show_progress && item.show_progress[epKey]) {
                                    const epProgress = item.show_progress[epKey];
                                    if (epProgress.progress && epProgress.progress.watched !== undefined) {
                                        watched = epProgress.progress.watched;
                                    } else if (epProgress.watched !== undefined) {
                                        watched = epProgress.watched;
                                    }
                                } else if (item.progress && item.progress.watched !== undefined) {
                                    watched = item.progress.watched;
                                }
                            } else {
                                // Movie
                                if (item.progress && item.progress.watched !== undefined) {
                                    watched = item.progress.watched;
                                }
                            }

                            // Duration: try to get from progress.duration or fallback to 0
                            let duration = 0;
                            if (item.progress && item.progress.duration !== undefined) {
                                duration = item.progress.duration;
                            }

                            sendToAndroid(watched, duration, seasonNum, episodeNum, provider);
                        }
                    } catch (e) {
                        // ignore parsing errors
                    }
                });
            })();
            </script>
        """.trimIndent()
        // =============== END OF UPDATED TRACKING SCRIPT ====================

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
                $trackingScript
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Extract base URL from a URL template (scheme + host)
     */
    fun getBaseUrl(providerName: String): String {
        val provider = providers.find { it.name.equals(providerName, ignoreCase = true) }
            ?: providers.firstOrNull()
            ?: return ""
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
            providers.filter { it.isTvEnabled }
        } else {
            providers.filter { it.isPhoneEnabled }
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
        timestamp: Long = 0L,
        isTvDevice: Boolean? = null
    ): String {
        val deviceProviders = isTvDevice?.let { getProvidersForDevice(it) } ?: providers
        val provider = deviceProviders.find { it.name.equals(providerName, ignoreCase = true) }
            ?: deviceProviders.firstOrNull()
            ?: return "about:blank"

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
