package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
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

    /** Stores the trusted backend used for direct-stream provider requests. */
    fun saveDirectStreamEndpoint(endpoint: String) {
        val normalized = normalizeEndpointUrl(endpoint)
        val discovered = getCachedDirectStreamEndpointOptions()
        val safeEndpoint = normalized.takeIf {
            it.isNotBlank() && (discovered.isEmpty() || it in discovered.map { option -> option.url })
        } ?: getDirectStreamEndpoint()
        if (safeEndpoint.isNotBlank()) {
            preferences.edit().putString(KEY_DIRECT_STREAM_ENDPOINT, safeEndpoint).apply()
        }
    }

    /**
     * Returns a supported direct-stream backend. Invalid or legacy values are
     * deliberately reset to the primary endpoint rather than being used as an
     * arbitrary network URL.
     */
    fun getDirectStreamEndpoint(): String {
        return normalizeEndpointUrl(preferences.getString(KEY_DIRECT_STREAM_ENDPOINT, null).orEmpty())
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

        private const val ENDPOINT_DISCOVERY_URL = "https://sflatransport.com/api/getEndpoints"
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

        /** Fetches the authoritative named endpoint list and caches it locally. */
        suspend fun fetchDirectStreamEndpointOptions(context: Context): List<DirectStreamEndpoint> =
            withContext(Dispatchers.IO) {
                val cached = getCachedDirectStreamEndpointOptions(context)
                val fetched = runCatching {
                    var lastError: Throwable? = null
                    var discovered: List<DirectStreamEndpoint>? = null
                    repeat(2) { attempt ->
                        if (discovered != null) return@repeat
                        try {
                            val request = Request.Builder()
                                .url(ENDPOINT_DISCOVERY_URL)
                                .header("Accept", "application/json")
                                .header("User-Agent", "KiduyuTVLite/1.0 (Android)")
                                .get()
                                .build()
                            endpointClient.newCall(request).execute().use { response ->
                                check(response.isSuccessful) {
                                    "Endpoint discovery returned HTTP ${response.code}"
                                }
                                val options = parseEndpointOptions(response.body?.string().orEmpty())
                                check(options.isNotEmpty()) {
                                    "Endpoint discovery returned no valid endpoints"
                                }
                                discovered = options
                            }
                        } catch (error: Throwable) {
                            lastError = error
                            if (attempt == 0) delay(500)
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
                        val url = normalizeEndpointUrl(item.optString("url"))
                        if (name.isNotBlank() && url.isNotBlank()) {
                            add(DirectStreamEndpoint(name, url))
                        }
                    }
                }.distinctBy { it.url }
            }.getOrDefault(emptyList())

        private fun normalizeEndpointUrl(value: String): String =
            value.trim().trimEnd('/').takeIf {
                it.startsWith("https://") || it.startsWith("http://")
            }.orEmpty()

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
