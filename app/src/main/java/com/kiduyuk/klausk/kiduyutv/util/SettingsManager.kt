package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager.Companion.AUTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * A named, user-selectable direct-stream backend. [name] is what Settings shows;
 * [url] is the only value ever sent to the API client.
 */
data class DirectStreamEndpoint(val name: String, val url: String)

class SettingsManager(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext: Context = context.applicationContext

    fun saveDefaultProvider(provider: String) {
        preferences.edit().putString(KEY_DEFAULT_PROVIDER, provider).apply()
    }

    /** Returns the saved WebView default provider name, or [AUTO] if none is set. */
    fun getDefaultProvider(): String {
        return preferences.getString(KEY_DEFAULT_PROVIDER, AUTO) ?: AUTO
    }

    fun saveDefaultDirectStreamProvider(provider: String) {
        preferences.edit().putString(KEY_DEFAULT_DIRECT_STREAM_PROVIDER, provider).apply()
    }

    /** Returns the selected direct-stream provider, or [AUTO] to aggregate enabled providers. */
    fun getDefaultDirectStreamProvider(): String {
        return preferences.getString(KEY_DEFAULT_DIRECT_STREAM_PROVIDER, AUTO) ?: AUTO
    }

    /** Saves the playback engine selected for the native direct-stream player. */
    fun saveDirectStreamPlayer(player: String) {
        val safePlayer = player.takeIf { it == PLAYER_EXOPLAYER || it == PLAYER_LIBMPV }
            ?: PLAYER_EXOPLAYER
        preferences.edit().putString(KEY_DIRECT_STREAM_PLAYER, safePlayer).apply()
    }

    /** Returns the selected direct-stream playback engine. */
    fun getDirectStreamPlayer(): String =
        preferences.getString(KEY_DIRECT_STREAM_PLAYER, PLAYER_EXOPLAYER)
            ?.takeIf { it == PLAYER_EXOPLAYER || it == PLAYER_LIBMPV }
            ?: PLAYER_EXOPLAYER

    /** Reads the endpoint options this manager already has a context for. */
    private fun cachedEndpointOptions(): List<DirectStreamEndpoint> =
        getCachedDirectStreamEndpointOptions(appContext)

    /**
     * Stores the selected direct-stream backend. Only an endpoint that
     * discovery actually offered is persisted; anything else keeps the current
     * selection rather than widening where the provider API token may be sent.
     */
    fun saveDirectStreamEndpoint(endpoint: String) {
        val normalized = normalizeEndpointUrl(endpoint)
        val allowed = cachedEndpointOptions().map { it.url }
        val safeEndpoint = normalized.takeIf { it.isNotBlank() && it in allowed }
            ?: getDirectStreamEndpoint()
        if (safeEndpoint.isNotBlank()) {
            preferences.edit().putString(KEY_DIRECT_STREAM_ENDPOINT, safeEndpoint).apply()
        }
    }

    /**
     * Returns the backend used for direct-stream requests. A stored value that
     * discovery no longer allows, or that failed validation, falls back to the
     * compiled-in endpoint, so this never returns an empty string.
     */
    fun getDirectStreamEndpoint(): String {
        val stored = normalizeEndpointUrl(
            preferences.getString(KEY_DIRECT_STREAM_ENDPOINT, null).orEmpty()
        )
        return stored.takeIf { it.isNotBlank() && isAllowedEndpoint(it) }
            ?: FALLBACK_DIRECT_STREAM_ENDPOINT
    }

    fun setDirectStreamEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DIRECT_STREAM_ENABLED, enabled).apply()
    }

    fun isDirectStreamEnabled(): Boolean {
        return preferences.getBoolean(KEY_DIRECT_STREAM_ENABLED, false)
    }

    fun setWebSnifferEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WEB_SNIFFER_ENABLED, enabled).apply()
    }

    fun isWebSnifferEnabled(): Boolean {
        return preferences.getBoolean(KEY_WEB_SNIFFER_ENABLED, true)
    }

    fun setDaddyLiveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DADDYLIVE_ENABLED, enabled).apply()
    }

    fun isDaddyLiveEnabled(): Boolean {
        return preferences.getBoolean(KEY_DADDYLIVE_ENABLED, false)
    }

    /** Saves whether Live TV should hide channels whose title contains `18+`. */
    fun setHide18PlusChannels(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HIDE_18_PLUS_CHANNELS, enabled).apply()
    }

    /** Returns whether Live TV should hide channels whose title contains `18+`. */
    fun isHide18PlusChannelsEnabled(): Boolean {
        return preferences.getBoolean(KEY_HIDE_18_PLUS_CHANNELS, false)
    }

    /**
     * Saves the ads disabled preference.
     * @param disabled true to disable all ads, false to enable ads
     */
    fun setAdsDisabled(disabled: Boolean) {
        preferences.edit().putBoolean(KEY_ADS_DISABLED, disabled).apply()
    }

    /**
     * Returns true if ads are disabled, false otherwise.
     */
    fun isAdsDisabled(): Boolean {
        //return preferences.getBoolean(KEY_ADS_DISABLED, false)
        return false // Always return false to enable ads, regardless of user preference
    }

    /**
     * Returns a unique device ID for Firebase user identification.
     * Uses Android ID if available, otherwise generates and saves a UUID.
     */
    fun getDeviceId(): String {
        // Try to get existing saved device ID
        val savedId = preferences.getString(KEY_DEVICE_ID, null)
        if (savedId != null) {
            return savedId
        }

        // Try to get Android ID
        val androidId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        // Generate a new unique ID
        val newId = androidId ?: UUID.randomUUID().toString()
        
        // Save for future use
        preferences.edit().putString(KEY_DEVICE_ID, newId).apply()
        
        return newId
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_DEFAULT_DIRECT_STREAM_PROVIDER = "default_direct_stream_provider"
        private const val KEY_DIRECT_STREAM_PLAYER = "direct_stream_player"
        private const val KEY_DIRECT_STREAM_ENDPOINT = "direct_stream_endpoint"
        private const val KEY_DIRECT_STREAM_ENDPOINT_OPTIONS = "direct_stream_endpoint_options"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ADS_DISABLED = "ads_disabled"
        private const val KEY_DIRECT_STREAM_ENABLED = "direct_stream_enabled"
        private const val KEY_WEB_SNIFFER_ENABLED = "web_sniffer_enabled"
        private const val KEY_DADDYLIVE_ENABLED = "daddylive_enabled"
        private const val KEY_HIDE_18_PLUS_CHANNELS = "hide_18_plus_channels"
        private const val KEY_AUTO_SKIP_SEGMENTS = "auto_skip_segments"

        /** Sentinel value meaning "ask me each time" — no automatic selection. */
        const val AUTO = "Auto"
        const val PLAYER_EXOPLAYER = "exoplayer"
        const val PLAYER_LIBMPV = "libmpv"

        private const val ENDPOINT_DISCOVERY_URL = "https://sflatransport.com/api/getEndpoints"

        /**
         * Secondary discovery source. The primary route can occasionally
         * return a bot-protection response instead of the endpoint JSON, so
         * Firebase mirrors the same payload and is tried when the primary fails.
         */
        private const val ENDPOINT_FIREBASE_URL =
            "https://kiduyutvfinal-default-rtdb.firebaseio.com/app_config/endpoints.json"

        /** Tried in order; the first source to return a usable list wins. */
        private val ENDPOINT_DISCOVERY_SOURCES =
            listOf(ENDPOINT_DISCOVERY_URL, ENDPOINT_FIREBASE_URL)

        /**
         * Compiled-in fallback used whenever discovery has not produced a usable
         * list. Callers must never receive an empty base URL, or every provider
         * request would silently degrade to a relative path.
         */
        const val FALLBACK_DIRECT_STREAM_ENDPOINT =
            "https://sflatransport.com/kiduyuTv_providers"

        /**
         * Discovery is a remote hint, not a trust anchor. The provider API token
         * is sent to whichever endpoint is selected, so only these hosts are
         * accepted no matter what the discovery response claims.
         *
         * The ngrok host is an ephemeral tunnel subdomain, so treat it as
         * replaceable: remove it here if that tunnel is retired.
         */
        private val TRUSTED_ENDPOINT_HOSTS = setOf(
            "sflatransport.com",
            "wealthy-buzzard-divine.ngrok-free.app",
            "secondbanklabs.com",
            "cloudflare-stream-worker.muigakiduyu1995.workers.dev"
        )

        /** Bounds a server-supplied label before it reaches the picker UI. */
        private const val MAX_ENDPOINT_NAME_LENGTH = 40

        private val endpointClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
        }
        @Volatile
        private var endpointOptionsCache: List<DirectStreamEndpoint> = emptyList()

        /** User-selectable backends fetched from the endpoint discovery service. */
        val DIRECT_STREAM_ENDPOINT_OPTIONS: List<DirectStreamEndpoint>
            get() = endpointOptionsCache

        /** Only endpoints surfaced by discovery are accepted by the API client. */
        val DIRECT_STREAM_ENDPOINTS: List<String>
            get() = DIRECT_STREAM_ENDPOINT_OPTIONS.map { it.url }

        /** Friendly label for an endpoint URL, falling back to the raw URL. */
        fun endpointDisplayName(endpointUrl: String): String =
            DIRECT_STREAM_ENDPOINT_OPTIONS.firstOrNull { it.url == endpointUrl }?.name
                ?: endpointUrl

        fun getCachedDirectStreamEndpointOptions(context: Context? = null): List<DirectStreamEndpoint> {
            if (endpointOptionsCache.isNotEmpty()) return endpointOptionsCache
            val cached = context?.let { contextValue ->
                contextValue.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_DIRECT_STREAM_ENDPOINT_OPTIONS, null)
                    ?.let(::parseEndpointOptions)
            }.orEmpty()
            if (cached.isNotEmpty()) endpointOptionsCache = cached
            return endpointOptionsCache
        }

        /** Performs one discovery request and returns the validated endpoint list. */
        private fun fetchEndpointOptionsFrom(sourceUrl: String): List<DirectStreamEndpoint> {
            val request = Request.Builder()
                .url(sourceUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "KiduyuTVLite/1.0 (Android)")
                .get()
                .build()
            return endpointClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Endpoint discovery returned HTTP ${response.code}"
                }
                val options = parseEndpointOptions(response.body?.string().orEmpty())
                check(options.isNotEmpty()) {
                    "Endpoint discovery returned no valid endpoints"
                }
                options
            }
        }

        /** Fetches the authoritative named endpoint list and caches it locally. */
        @RequiresApi(Build.VERSION_CODES.GINGERBREAD)
        suspend fun fetchDirectStreamEndpointOptions(context: Context): List<DirectStreamEndpoint> =
            withContext(Dispatchers.IO) {
                val cached = getCachedDirectStreamEndpointOptions(context)
                val fetched = runCatching {
                    var lastError: Throwable? = null
                    var discovered: List<DirectStreamEndpoint>? = null
                    for (sourceUrl in ENDPOINT_DISCOVERY_SOURCES) {
                        if (discovered != null) break
                        try {
                            val options = fetchEndpointOptionsFrom(sourceUrl)
                            Log.i(TAG, "Loaded direct-stream endpoints from $sourceUrl")
                            discovered = options
                        } catch (error: Throwable) {
                            lastError = error
                            Log.w(TAG, "Endpoint discovery source failed: $sourceUrl", error)
                        }
                    }
                    discovered ?: throw (lastError
                        ?: IllegalStateException("Endpoint discovery failed"))
                }.onFailure { error ->
                    Log.w(TAG, "Could not load direct-stream endpoints", error)
                }.getOrElse { cached }
                    .also { options ->
                        if (options.isNotEmpty()) {
                            endpointOptionsCache = options
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putString(
                                    KEY_DIRECT_STREAM_ENDPOINT_OPTIONS,
                                    JSONObject().put(
                                        "endpoints",
                                        JSONArray().apply {
                                            options.forEach { option ->
                                                put(JSONObject().put("name", option.name).put("url", option.url))
                                            }
                                        }
                                    ).toString()
                                )
                                .apply()
                        }
                    }
                fetched
            }

        private fun parseEndpointOptions(raw: String): List<DirectStreamEndpoint> =
            runCatching {
                val json = JSONObject(raw)
                val namedEndpoints = json.optJSONArray("endpoints") ?: JSONArray()
                val array = if (namedEndpoints.length() > 0) {
                    namedEndpoints
                } else {
                    JSONArray().apply {
                        json.optJSONArray("urls")?.let { urls ->
                            for (index in 0 until urls.length()) {
                                put(
                                    JSONObject()
                                        .put("name", "Endpoint ${index + 1}")
                                        .put("url", urls.optString(index))
                                )
                            }
                        }
                    }
                }
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optString("name").trim()
                            .take(MAX_ENDPOINT_NAME_LENGTH)
                        val url = normalizeEndpointUrl(item.optString("url"))
                            .takeIf { isAllowedEndpoint(it) }
                            .orEmpty()
                        if (name.isNotBlank() && url.isNotBlank()) {
                            add(DirectStreamEndpoint(name, url))
                        }
                    }
                }.distinctBy { it.url }
            }.getOrDefault(emptyList())

        /** Trims and de-slashes a candidate URL. Makes no trust decision. */
        private fun normalizeEndpointUrl(value: String): String =
            value.trim().trimEnd('/')

        /**
         * True when the app is willing to send provider API calls to [value].
         * HTTPS on a trusted host is always allowed. A plain-HTTP LAN backend is
         * a debug-build-only convenience, because the provider API token would
         * otherwise travel in cleartext to a host named by a remote server.
         */
        private fun isAllowedEndpoint(value: String): Boolean {
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            if (host.isEmpty()) return false
            if (scheme == "https") return host in TRUSTED_ENDPOINT_HOSTS
            if (!BuildConfig.DEBUG) return false
            return scheme == "http" && isPrivateHost(host)
        }

        private fun isPrivateHost(host: String): Boolean =
            host == "localhost" ||
                host == "127.0.0.1" ||
                host.startsWith("192.168.") ||
                host.startsWith("10.")

        private const val TAG = "SettingsManager"

        /**
         * All active providers in display order.
         * Uses the first 10 providers from StreamProviderManager for consistency.
         */
        val PROVIDERS: List<String>
            get() = StreamProviderManager.providers.take(10).map { it.name }
    }

    fun setAutoSkipSegmentsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_SKIP_SEGMENTS, enabled).apply()
    }

    fun isAutoSkipSegmentsEnabled(): Boolean {
        return preferences.getBoolean(KEY_AUTO_SKIP_SEGMENTS, true)
    }
}
