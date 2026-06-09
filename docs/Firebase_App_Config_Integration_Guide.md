# Firebase App Configuration Integration Guide

This document provides a comprehensive guide for migrating from hardcoded constants to Firebase Realtime Database for centralized app configuration management.

---

## Table of Contents

1. [Overview](#overview)
2. [Current State: Hardcoded Constants](#current-state-hardcoded-constants)
3. [Firebase Configuration Structure](#firebase-configuration-structure)
4. [Implementation Strategy](#implementation-strategy)
5. [Data Models](#data-models)
6. [Repository Implementation](#repository-implementation)
7. [Usage Examples](#usage-examples)
8. [Error Handling and Fallbacks](#error-handling-and-fallbacks)
9. [Migration Checklist](#migration-checklist)

---

## Overview

The application currently relies on hardcoded constants spread across multiple files. By migrating to Firebase Realtime Database, we gain several benefits:

- **Centralized Configuration**: All settings managed from a single Firebase console
- **Real-time Updates**: Configuration changes propagate immediately without app updates
- **Environment-specific Settings**: Different configurations for development, staging, and production
- **A/B Testing**: Easily test different configurations across user segments
- **Reduced Build Times**: No need to rebuild for configuration changes

---

## Current State: Hardcoded Constants

The following files currently contain hardcoded configuration values that should be migrated:

### Primary Files with Hardcoded Constants

| File | Configuration Type | Values |
|------|-------------------|--------|
| `IptvRepository.kt` | Playlist URLs | M3U playlist, EPG XML URLs |
| `TraktAuthManager.kt` | API Credentials | Client ID, Client Secret |
| `StreamProvider.kt` | Stream Providers | 30+ provider URLs and parameters |
| `ScheduleApiService.kt` | Network Settings | Base URL, timeout |
| `ApiClient.kt` | Cache Settings | Cache size, max age, stale duration |
| `AdvancedAdBlocker.kt` | Filter URLs | Easylist, Easyprivacy URLs |
| `AdUnitIds.kt` | Ad Unit IDs | Banner, Interstitial, Rewarded IDs |

### Example of Current Hardcoded Implementation

```kotlin
// Current implementation in IptvRepository.kt
object IptvRepository {
    // Hardcoded constants
    const val PLAYLIST_URL = "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u"
    const val PLAYLIST_EPG_URL = "https://raw.githubusercontent.com/JulioCesarXY/EPG-LG-Channels/refs/heads/main/lg_epg_us.xml"
    private const val CACHE_VALIDITY_MS = 6 * 60 * 60 * 1000L // 6 hours
    
    suspend fun fetchPlaylist(): Result<Playlist> {
        // Implementation uses hardcoded PLAYLIST_URL
    }
}
```

---

## Firebase Configuration Structure

The `app_config` node in Firebase Realtime Database contains the following structure:

### Complete Firebase Data Structure

```json
{
  "api_Configuration": {
    "tmdb_bearer_token": "",
    "trakt_client_id": "",
    "trakt_client_secret": ""
  },
  "feature_flags_Configuration": {
    "cursor_hide_delay_ms": 5000,
    "cursor_speed": 50,
    "disable_ads_globally": false
  },
  "filter_lists_Configuration": {
    "custom_filters_url": "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/native.oppo-realme.txt",
    "easylist_url": "https://easylist.to/easylist/easylist.txt",
    "easyprivacy_url": "https://easylist.to/easylist/easyprivacy.txt",
    "enable_custom_filters": false,
    "filter_fallback_easylist": "https://easylist-downloads.adblockplus.org/easylist.txt",
    "filter_fallback_easyprivacy": "https://easylist-downloads.adblockplus.org/easyprivacy.txt",
    "filter_timeout_ms": 30000,
    "update_interval_hours": 24
  },
  "google_ads_Configuration": {
    "enable_test_ads": false,
    "phone_banner_ad_unit_id": "",
    "phone_interstitial_ad_unit_id": "",
    "phone_rewarded_ad_unit_id": "",
    "tv_banner_ad_unit_id": "",
    "tv_interstitial_ad_unit_id": "",
    "use_test_ads": false
  },
  "network_settings_Configuration": {
    "api_cache_size_mb": 10,
    "api_timeout_seconds": 30,
    "cache_max_age_minutes": 5,
    "cache_max_stale_days": 7,
    "max_retries": 3,
    "retry_delay_ms": 3000
  },
  "playlist_url": {
    "playlist_cache_duration": 6,
    "playlist_epg": "https://raw.githubusercontent.com/JulioCesarXY/EPG-LG-Channels/refs/heads/main/lg_epg_us.xml",
    "playlist_url": "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u",
    "schedule_api": "https://dlhd.pk"
  },
  "stream_providers_Configuration": {
    "VidLink": {
      "allow_attributes": "",
      "createdAt": "2026-06-04T23:23:02.226Z",
      "enabled": true,
      "iframe_attributes": {
        "frameborder": "0"
      },
      "movie_parameters": {
        "autoPlay": "true"
      },
      "movie_url_template": "https://vidlink.pro/movie/%d",
      "stream_provider_name": "VidLink",
      "tv_parameters": {
        "autoPlay": "true"
      },
      "tv_url_template": "https://vidlink.pro/tv/%d/%d/%d",
      "url": "https://vidlink.pro"
    }
  }
}
```

---

## Implementation Strategy

### Step 1: Create Data Models

Create model classes that mirror the Firebase structure:

```kotlin
// AppConfig.kt - Main configuration container
package com.kiduyuk.klausk.kiduyutv.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class AppConfig(
    val apiConfiguration: ApiConfiguration? = null,
    val featureFlagsConfiguration: FeatureFlagsConfiguration? = null,
    val filterListsConfiguration: FilterListsConfiguration? = null,
    val googleAdsConfiguration: GoogleAdsConfiguration? = null,
    val networkSettingsConfiguration: NetworkSettingsConfiguration? = null,
    val playlistUrl: PlaylistUrlConfig? = null,
    val streamProvidersConfiguration: Map<String, StreamProviderConfig>? = null
)

@IgnoreExtraProperties
data class ApiConfiguration(
    val tmdb_bearer_token: String = "",
    val trakt_client_id: String = "",
    val trakt_client_secret: String = ""
)

@IgnoreExtraProperties
data class FeatureFlagsConfiguration(
    val cursor_hide_delay_ms: Int = 5000,
    val cursor_speed: Int = 50,
    val disable_ads_globally: Boolean = false
)

@IgnoreExtraProperties
data class FilterListsConfiguration(
    val custom_filters_url: String = "",
    val easylist_url: String = "https://easylist.to/easylist/easylist.txt",
    val easyprivacy_url: String = "https://easylist.to/easylist/easyprivacy.txt",
    val enable_custom_filters: Boolean = false,
    val filter_fallback_easylist: String = "https://easylist-downloads.adblockplus.org/easylist.txt",
    val filter_fallback_easyprivacy: String = "https://easylist-downloads.adblockplus.org/easyprivacy.txt",
    val filter_timeout_ms: Int = 30000,
    val update_interval_hours: Int = 24
)

@IgnoreExtraProperties
data class GoogleAdsConfiguration(
    val enable_test_ads: Boolean = false,
    val phone_banner_ad_unit_id: String = "",
    val phone_interstitial_ad_unit_id: String = "",
    val phone_rewarded_ad_unit_id: String = "",
    val tv_banner_ad_unit_id: String = "",
    val tv_interstitial_ad_unit_id: String = "",
    val use_test_ads: Boolean = false
)

@IgnoreExtraProperties
data class NetworkSettingsConfiguration(
    val api_cache_size_mb: Int = 10,
    val api_timeout_seconds: Int = 30,
    val cache_max_age_minutes: Int = 5,
    val cache_max_stale_days: Int = 7,
    val max_retries: Int = 3,
    val retry_delay_ms: Int = 3000
)

@IgnoreExtraProperties
data class PlaylistUrlConfig(
    val playlist_cache_duration: Int = 6,
    val playlist_epg: String = "",
    val playlist_url: String = "",
    val schedule_api: String = "https://dlhd.pk"
)

@IgnoreExtraProperties
data class StreamProviderConfig(
    val allow_attributes: String = "",
    val createdAt: String = "",
    val enabled: Boolean = true,
    val iframe_attributes: Map<String, String>? = null,
    val movie_parameters: Map<String, String>? = null,
    val movie_url_template: String = "",
    val stream_provider_name: String = "",
    val tv_parameters: Map<String, String>? = null,
    val tv_url_template: String = "",
    val url: String = ""
)
```

---

## Repository Implementation

### AppConfigRepository.kt

Create a centralized repository for fetching and managing app configuration:

```kotlin
// AppConfigRepository.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kiduyuk.klausk.kiduyutv.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching and managing app configuration from Firebase Realtime Database.
 * 
 * This repository provides:
 * - Real-time configuration updates via Firebase listeners
 * - Caching of configuration locally for offline access
 * - Default values as fallback when Firebase is unavailable
 */
object AppConfigRepository {

    private const val TAG = "AppConfigRepository"
    private const val APP_CONFIG_PATH = "app_config"
    private const val CACHE_PREFS_NAME = "app_config_cache"
    private const val KEY_CONFIG_CACHE = "cached_config_json"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    private const val CACHE_VALIDITY_MS = 15 * 60 * 1000L // 15 minutes

    private val database = FirebaseDatabase.getInstance()
    private val configReference = database.getReference(APP_CONFIG_PATH)

    // State flows for reactive configuration updates
    private val _appConfig = MutableStateFlow<AppConfig?>(null)
    val appConfig: StateFlow<AppConfig?> = _appConfig

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Singleton instances for each configuration section
    private val _apiConfig = MutableStateFlow(ApiConfiguration())
    val apiConfig: StateFlow<ApiConfiguration> = _apiConfig

    private val _featureFlags = MutableStateFlow(FeatureFlagsConfiguration())
    val featureFlags: StateFlow<FeatureFlagsConfiguration> = _featureFlags

    private val _filterLists = MutableStateFlow(FilterListsConfiguration())
    val filterLists: StateFlow<FilterListsConfiguration> = _filterLists

    private val _googleAds = MutableStateFlow(GoogleAdsConfiguration())
    val googleAds: StateFlow<GoogleAdsConfiguration> = _googleAds

    private val _networkSettings = MutableStateFlow(NetworkSettingsConfiguration())
    val networkSettings: StateFlow<NetworkSettingsConfiguration> = _networkSettings

    private val _playlistConfig = MutableStateFlow(PlaylistUrlConfig())
    val playlistConfig: StateFlow<PlaylistUrlConfig> = _playlistConfig

    private val _streamProviders = MutableStateFlow<Map<String, StreamProviderConfig>>(emptyMap())
    val streamProviders: StateFlow<Map<String, StreamProviderConfig>> = _streamProviders

    /**
     * Initialize the repository and start listening for configuration changes.
     * Should be called once during app startup (e.g., in SplashActivity).
     */
    fun initialize() {
        loadCachedConfig()
        fetchConfiguration()
    }

    /**
     * Load configuration from Firebase Realtime Database.
     * Sets up a real-time listener that updates configuration when it changes in Firebase.
     */
    fun fetchConfiguration() {
        _isLoading.value = true
        _error.value = null

        configReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val config = snapshot.getValue(AppConfig::class.java)
                    if (config != null) {
                        _appConfig.value = config
                        updateAllConfigurations(config)
                        cacheConfig(config)
                        Log.d(TAG, "Configuration loaded successfully")
                    } else {
                        Log.w(TAG, "Firebase returned null configuration")
                        loadDefaultConfig()
                    }
                    _isLoading.value = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Firebase configuration", e)
                    _error.value = "Failed to parse configuration: ${e.message}"
                    _isLoading.value = false
                    loadCachedConfig()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error: ${error.message}", error.toException())
                _error.value = "Firebase error: ${error.message}"
                _isLoading.value = false
                loadCachedConfig()
            }
        })
    }

    /**
     * Asynchronously fetch configuration once (non-listening).
     * Use this when you only need the current configuration without real-time updates.
     */
    suspend fun fetchConfigOnce(): Result<AppConfig> {
        return try {
            _isLoading.value = true
            val snapshot = configReference.get().await()
            val config = snapshot.getValue(AppConfig::class.java)
            if (config != null) {
                _appConfig.value = config
                updateAllConfigurations(config)
                cacheConfig(config)
                Result.success(config)
            } else {
                Result.failure(Exception("No configuration found in Firebase"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching configuration", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Get a specific configuration section using a Flow.
     */
    inline fun <reified T> getConfigSection(crossinline extractor: (AppConfig) -> T?): Flow<T?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val config = snapshot.getValue(AppConfig::class.java)
                val section = config?.let { extractor(it) }
                trySend(section)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        configReference.addValueEventListener(listener)
        
        awaitClose {
            configReference.removeEventListener(listener)
        }
    }

    /**
     * Update a specific configuration section.
     */
    suspend fun updateApiConfiguration(config: ApiConfiguration): Result<Unit> {
        return try {
            configReference.child("apiConfiguration").setValue(config).await()
            _apiConfig.value = config
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating API configuration", e)
            Result.failure(e)
        }
    }

    /**
     * Update feature flags.
     */
    suspend fun updateFeatureFlags(flags: FeatureFlagsConfiguration): Result<Unit> {
        return try {
            configReference.child("featureFlagsConfiguration").setValue(flags).await()
            _featureFlags.value = flags
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating feature flags", e)
            Result.failure(e)
        }
    }

    /**
     * Get enabled stream providers only.
     */
    fun getEnabledStreamProviders(): List<StreamProviderConfig> {
        return _streamProviders.value.values.filter { it.enabled }
    }

    /**
     * Get a specific stream provider by name.
     */
    fun getStreamProvider(name: String): StreamProviderConfig? {
        return _streamProviders.value[name]
    }

    private fun updateAllConfigurations(config: AppConfig) {
        config.apiConfiguration?.let { _apiConfig.value = it }
        config.featureFlagsConfiguration?.let { _featureFlags.value = it }
        config.filterListsConfiguration?.let { _filterLists.value = it }
        config.googleAdsConfiguration?.let { _googleAds.value = it }
        config.networkSettingsConfiguration?.let { _networkSettings.value = it }
        config.playlistUrl?.let { _playlistConfig.value = it }
        config.streamProvidersConfiguration?.let { _streamProviders.value = it }
    }

    private fun loadCachedConfig() {
        try {
            val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(
                com.kiduyuk.klausk.kiduyutv.KiduyuTvApp.instance
            )
            val cacheTimestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime - cacheTimestamp < CACHE_VALIDITY_MS) {
                val cachedJson = prefs.getString(KEY_CONFIG_CACHE, null)
                if (cachedJson != null) {
                    val config = com.google.gson.Gson().fromJson(cachedJson, AppConfig::class.java)
                    if (config != null) {
                        _appConfig.value = config
                        updateAllConfigurations(config)
                        Log.d(TAG, "Loaded configuration from cache")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached configuration", e)
        }
    }

    private fun cacheConfig(config: AppConfig) {
        try {
            val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(
                com.kiduyuk.klausk.kiduyutv.KiduyuTvApp.instance
            )
            val editor = prefs.edit()
            editor.putString(KEY_CONFIG_CACHE, com.google.gson.Gson().toJson(config))
            editor.putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error caching configuration", e)
        }
    }

    private fun loadDefaultConfig() {
        // Use hardcoded defaults as fallback
        val defaultConfig = AppConfig(
            apiConfiguration = ApiConfiguration(
                tmdb_bearer_token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w",
                trakt_client_id = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0",
                trakt_client_secret = "12c597436f61997d8fcb31d246af7400359533d0411374f456af6df2bf7313d9"
            ),
            featureFlagsConfiguration = FeatureFlagsConfiguration(),
            filterListsConfiguration = FilterListsConfiguration(),
            googleAdsConfiguration = GoogleAdsConfiguration(),
            networkSettingsConfiguration = NetworkSettingsConfiguration(
                api_cache_size_mb = 10,
                api_timeout_seconds = 30,
                cache_max_age_minutes = 5,
                cache_max_stale_days = 7,
                max_retries = 3,
                retry_delay_ms = 3000
            ),
            playlistUrl = PlaylistUrlConfig(
                playlist_cache_duration = 6,
                playlist_epg = "https://raw.githubusercontent.com/JulioCesarXY/EPG-LG-Channels/refs/heads/main/lg_epg_us.xml",
                playlist_url = "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u",
                schedule_api = "https://dlhd.pk"
            )
        )
        
        updateAllConfigurations(defaultConfig)
        Log.d(TAG, "Loaded default configuration")
    }
}
```

---

## Usage Examples

### Example 1: Using Playlist URLs

**Before (Hardcoded):**

```kotlin
// IptvRepository.kt
object IptvRepository {
    const val PLAYLIST_URL = "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u"
    const val PLAYLIST_EPG_URL = "https://raw.githubusercontent.com/JulioCesarXY/EPG-LG-Channels/refs/heads/main/lg_epg_us.xml"
    
    suspend fun fetchPlaylist(): Result<Playlist> {
        return api.fetch(PLAYLIST_URL) // Uses hardcoded URL
    }
}
```

**After (Firebase-Based):**

```kotlin
// IptvRepository.kt
object IptvRepository {
    
    private val configRepository = AppConfigRepository
    
    suspend fun fetchPlaylist(): Result<Playlist> {
        val playlistUrl = configRepository.playlistConfig.value.playlist_url
        
        return if (playlistUrl.isNotEmpty()) {
            api.fetch(playlistUrl)
        } else {
            Result.failure(Exception("Playlist URL not configured"))
        }
    }
    
    suspend fun fetchEpg(): Result<String> {
        val epgUrl = configRepository.playlistConfig.value.playlist_epg
        
        return if (epgUrl.isNotEmpty()) {
            api.fetch(epgUrl)
        } else {
            Result.failure(Exception("EPG URL not configured"))
        }
    }
}
```

### Example 2: Using Stream Providers

**Before (Hardcoded):**

```kotlin
// StreamProvider.kt
data class StreamProvider(
    val name: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String
) {
    companion object {
        val ALL_PROVIDERS = listOf(
            StreamProvider("VidLink", "https://vidlink.pro/movie/%d", "https://vidlink.pro/tv/%d/%d/%d"),
            StreamProvider("VidSrc", "https://vidsrc.wtf/api/1/movie/?id=%d", "https://vidsrc.wtf/api/1/tv/?id=%d&s=%d&e=%d"),
            // ... 30+ more hardcoded providers
        )
    }
}
```

**After (Firebase-Based):**

```kotlin
// StreamProviderManager.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderConfig
import com.kiduyuk.klausk.kiduyutv.util.StreamProvider

/**
 * Manager for stream providers that fetches configuration from Firebase.
 */
object StreamProviderManager {

    private val configRepository = AppConfigRepository
    
    /**
     * Get all enabled stream providers.
     */
    fun getEnabledProviders(): List<StreamProvider> {
        return configRepository.getEnabledStreamProviders().map { config ->
            StreamProvider(
                id = config.stream_provider_name,
                name = config.stream_provider_name,
                movieUrlTemplate = config.movie_url_template,
                tvUrlTemplate = config.tv_url_template,
                parameters = config.movie_parameters ?: emptyMap(),
                iframeAttributes = config.iframe_attributes ?: emptyMap()
            )
        }
    }
    
    /**
     * Get a specific provider by name.
     */
    fun getProvider(name: String): StreamProvider? {
        return configRepository.getStreamProvider(name)?.let { config ->
            StreamProvider(
                id = config.stream_provider_name,
                name = config.stream_provider_name,
                movieUrlTemplate = config.movie_url_template,
                tvUrlTemplate = config.tv_url_template,
                parameters = config.movie_parameters ?: emptyMap(),
                iframeAttributes = config.iframe_attributes ?: emptyMap()
            )
        }
    }
    
    /**
     * Get the default provider for auto-selection.
     */
    fun getDefaultProvider(): StreamProvider? {
        return getEnabledProviders().firstOrNull()
    }
}

// Extend StreamProvider data class if needed
data class StreamProvider(
    val id: String,
    val name: String,
    val movieUrlTemplate: String,
    val tvUrlTemplate: String,
    val parameters: Map<String, String> = emptyMap(),
    val iframeAttributes: Map<String, String> = emptyMap()
) {
    
    fun buildMovieUrl(tmdbId: Int): String {
        var url = movieUrlTemplate.format(tmdbId)
        if (parameters.isNotEmpty()) {
            val query = parameters.map { "${it.key}=${it.value}" }.joinToString("&")
            url = if (url.contains("?")) "$url&$query" else "$url?$query"
        }
        return url
    }
    
    fun buildTvUrl(tmdbId: Int, season: Int, episode: Int): String {
        var url = tvUrlTemplate.format(tmdbId, season, episode)
        if (parameters.isNotEmpty()) {
            val query = parameters.map { "${it.key}=${it.value}" }.joinToString("&")
            url = if (url.contains("?")) "$url&$query" else "$url?$query"
        }
        return url
    }
}
```

### Example 3: Using Network Settings

**Before (Hardcoded):**

```kotlin
// ApiClient.kt
object ApiClient {
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB
    private const val CACHE_MAX_AGE = 5 // 5 minutes
    private const val CACHE_MAX_STALE = 7 // 7 days
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 3000L
    
    private const val TIMEOUT_SECONDS = 30L
}
```

**After (Firebase-Based):**

```kotlin
// NetworkClient.kt
package com.kiduyuk.klausk.kiduyutv.data.network

import com.kiduyuk.klausk.kiduyutv.data.repository.AppConfigRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val networkSettings = AppConfigRepository.networkSettings
    
    fun createOkHttpClient(): OkHttpClient {
        val settings = networkSettings.value
        
        return OkHttpClient.Builder()
            .cache(createCache())
            .connectTimeout(settings.api_timeout_seconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(settings.api_timeout_seconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(settings.api_timeout_seconds.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    private fun createCache(): Cache {
        val cacheSize = networkSettings.value.api_cache_size_mb * 1024 * 1024L
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, cacheSize)
    }
    
    /**
     * Execute a network request with retry logic from Firebase configuration.
     */
    suspend fun <T> executeWithRetry(request: suspend () -> T): T {
        val settings = networkSettings.value
        var lastException: Exception? = null
        
        repeat(settings.max_retries) { attempt ->
            try {
                return request()
            } catch (e: Exception) {
                lastException = e
                if (attempt < settings.max_retries - 1) {
                    delay(settings.retry_delay_ms.toLong())
                }
            }
        }
        
        throw lastException ?: Exception("Unknown error during network request")
    }
}
```

### Example 4: Using Filter List Configuration

**Before (Hardcoded):**

```kotlin
// AdvancedAdBlocker.kt
object AdvancedAdBlocker {
    private val filterFiles = listOf(
        "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
        "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt"
    )
}
```

**After (Firebase-Based):**

```kotlin
// AdBlockerManager.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.FilterListsConfiguration

object AdBlockerManager {

    private val configRepository = AppConfigRepository
    private val filterConfig = configRepository.filterLists
    
    /**
     * Get all filter URLs with fallback options.
     */
    fun getFilterUrls(): FilterUrls {
        val config = filterConfig.value
        return FilterUrls(
            easylist = config.easylist_url,
            easylistFallback = config.filter_fallback_easylist,
            easyprivacy = config.easyprivacy_url,
            easyprivacyFallback = config.filter_fallback_easyprivacy,
            customFilters = if (config.enable_custom_filters) config.custom_filters_url else null
        )
    }
    
    /**
     * Get the filter update interval in milliseconds.
     */
    fun getUpdateIntervalMs(): Long {
        return filterConfig.value.update_interval_hours * 60 * 60 * 1000L
    }
    
    /**
     * Get the filter download timeout.
     */
    fun getFilterTimeoutMs(): Long {
        return filterConfig.value.filter_timeout_ms.toLong()
    }
    
    data class FilterUrls(
        val easylist: String,
        val easylistFallback: String,
        val easyprivacy: String,
        val easyprivacyFallback: String,
        val customFilters: String?
    )
}
```

### Example 5: Using Feature Flags

```kotlin
// FeatureFlags.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

object FeatureFlags {
    
    private val configRepository = AppConfigRepository
    private val featureFlags = configRepository.featureFlags
    
    /**
     * Check if ads are disabled globally.
     */
    fun isAdsDisabled(): Boolean {
        return featureFlags.value.disable_ads_globally
    }
    
    /**
     * Get the cursor hide delay in milliseconds.
     */
    fun getCursorHideDelayMs(): Long {
        return featureFlags.value.cursor_hide_delay_ms.toLong()
    }
    
    /**
     * Get the cursor movement speed.
     */
    fun getCursorSpeed(): Int {
        return featureFlags.value.cursor_speed
    }
}
```

### Example 6: Using API Configuration

```kotlin
// ApiCredentials.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

object ApiCredentials {
    
    private val configRepository = AppConfigRepository
    private val apiConfig = configRepository.apiConfig
    
    /**
     * Get TMDB Bearer Token.
     */
    fun getTmdbBearerToken(): String {
        val token = apiConfig.value.tmdb_bearer_token
        return if (token.isNotEmpty()) token else DEFAULT_TMDB_TOKEN
    }
    
    /**
     * Get Trakt Client ID.
     */
    fun getTraktClientId(): String {
        val clientId = apiConfig.value.trakt_client_id
        return if (clientId.isNotEmpty()) clientId else DEFAULT_TRAKT_CLIENT_ID
    }
    
    /**
     * Get Trakt Client Secret.
     */
    fun getTraktClientSecret(): String {
        val clientSecret = apiConfig.value.trakt_client_secret
        return if (clientSecret.isNotEmpty()) clientSecret else DEFAULT_TRAKT_CLIENT_SECRET
    }
    
    companion object {
        private const val DEFAULT_TMDB_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0MTAzZmMzMDY1YzEyMmViNWRiNmJkY2ZmNzQ5ZmRlNyIsIm5iZiI6MTY2ODA2NDAzNC4yNDk5OTk4LCJzdWIiOiI2MzZjYTMyMjA0OTlmMjAwN2ZlYjA4MWEiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.tjvtYPTPfLOyMdOouQ14GGgOzmfnZRW4RgvOzfoq19w"
        private const val DEFAULT_TRAKT_CLIENT_ID = "98f8c9590ae29a666942f81c5f86628f0dbe2767d28b88cdedbb7bbbd316e1a0"
        private const val DEFAULT_TRAKT_CLIENT_SECRET = "12c597436f61997d8fcb31d246af7400359533d0411374f456af6df2bf7313d9"
    }
}
```

---

## Error Handling and Fallbacks

### Global Error Handler

```kotlin
// ConfigurationErrorHandler.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

import android.util.Log

object ConfigurationErrorHandler {

    private const val TAG = "ConfigErrorHandler"
    
    /**
     * Handle configuration-related errors with appropriate fallback strategies.
     */
    fun <T> withConfigFallback(
        configLoader: () -> T?,
        defaultProvider: () -> T,
        configName: String
    ): T {
        return try {
            configLoader() ?: run {
                Log.w(TAG, "Configuration '$configName' is null, using default")
                defaultProvider()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading configuration '$configName', using default", e)
            defaultProvider()
        }
    }
    
    /**
     * Handle configuration loading with logging.
     */
    fun <T> safeLoadConfig(
        configLoader: () -> T?,
        defaultValue: T,
        configName: String
    ): T {
        return try {
            configLoader() ?: defaultValue.also {
                Log.d(TAG, "Using default value for '$configName'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading '$configName', using default", e)
            defaultValue
        }
    }
}
```

### Usage in ViewModel

```kotlin
// ExampleViewModel.kt
class ExampleViewModel : ViewModel() {

    private val configRepository = AppConfigRepository
    
    val uiState = MutableStateFlow<UiState>(UiState.Loading)
    
    init {
        observeConfiguration()
    }
    
    private fun observeConfiguration() {
        viewModelScope.launch {
            configRepository.networkSettings.collect { settings ->
                // Use settings with error handling
                val timeout = ConfigurationErrorHandler.safeLoadConfig(
                    configLoader = { settings.api_timeout_seconds },
                    defaultValue = 30,
                    configName = "api_timeout_seconds"
                )
                
                uiState.value = UiState.Success(settings)
            }
        }
    }
    
    sealed class UiState {
        data class Success(val config: NetworkSettingsConfiguration) : UiState()
        data class Error(val message: String) : UiState()
        object Loading : UiState()
    }
}
```

---

## Migration Checklist

### Phase 1: Data Models

- [ ] Create `AppConfig` data class
- [ ] Create configuration data classes for each section
- [ ] Add `@IgnoreExtraProperties` annotation
- [ ] Create default/fallback values for all fields

### Phase 2: Repository

- [ ] Create `AppConfigRepository` singleton
- [ ] Implement Firebase database listener
- [ ] Add local caching mechanism
- [ ] Implement error handling and fallbacks
- [ ] Add logging for debugging

### Phase 3: Integration

- [ ] Update `IptvRepository` to use Firebase config
- [ ] Update `StreamProvider` to load from Firebase
- [ ] Update `NetworkClient` with configurable settings
- [ ] Update `AdvancedAdBlocker` with configurable URLs
- [ ] Update `ApiCredentials` to use Firebase values

### Phase 4: Testing

- [ ] Test with valid Firebase configuration
- [ ] Test fallback behavior when Firebase is unavailable
- [ ] Test offline mode with cached configuration
- [ ] Test real-time updates when Firebase data changes
- [ ] Verify all feature flags work correctly

### Phase 5: Deployment

- [ ] Create Firebase configuration in Firebase Console
- [ ] Set up proper security rules
- [ ] Document configuration structure for future updates
- [ ] Create migration guide for developers

---

## Firebase Console Setup

### Creating the app_config Node

1. Navigate to Firebase Console
2. Select your project
3. Go to Realtime Database
4. Click "Start in test mode" (for development)
5. Add the `app_config` node with the JSON structure provided

### Security Rules

```json
{
  "rules": {
    "app_config": {
      ".read": true,
      ".write": false
    }
  }
}
```

For production, consider adding authentication requirements:

```json
{
  "rules": {
    "app_config": {
      ".read": "auth != null && auth.token.admin == true",
      ".write": false
    }
  }
}
```

---

## Best Practices

### 1. Always Provide Fallbacks

```kotlin
val playlistUrl = config.playlistUrl ?: DEFAULT_PLAYLIST_URL
```

### 2. Cache Configuration Locally

```kotlin
private fun cacheConfig(config: AppConfig) {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_CONFIG, Gson().toJson(config)).apply()
}
```

### 3. Handle Network Errors Gracefully

```kotlin
configReference.addValueEventListener(object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) {
        // Handle success
    }
    
    override fun onCancelled(error: DatabaseError) {
        Log.e(TAG, "Firebase error: ${error.message}")
        loadCachedConfig()
    }
})
```

### 4. Use Type-Safe Accessors

```kotlin
val cursorHideDelay = featureFlags.value.cursor_hide_delay_ms
val isAdsDisabled = featureFlags.value.disable_ads_globally
```

### 5. Log Configuration Changes for Debugging

```kotlin
Log.d(TAG, "Configuration updated: cursorHideDelay=$cursorHideDelay")
```

---

## Summary

This guide provides a complete framework for migrating from hardcoded constants to Firebase-based configuration management. The implementation includes:

- Type-safe data models for all configuration sections
- A centralized repository with real-time updates
- Automatic caching for offline access
- Comprehensive error handling with fallbacks
- Ready-to-use examples for each configuration type

By following this guide, you can centralize your app's configuration management and gain the flexibility to update settings without deploying new app versions.