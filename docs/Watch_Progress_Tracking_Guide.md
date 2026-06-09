# Watch Progress Tracking Implementation Guide

This guide explains how to implement watch progress tracking in the KiduyuTv app. The feature saves media to a watch history, tracks playback progress from WebView JavaScript postMessages, syncs data to Firebase, and updates both local storage and Firebase every 15 seconds.

## Overview

The watch progress tracking system consists of four main components:

1. **WatchHistoryManager** - Handles saving and updating watch progress locally
2. **FirebaseManager** - Handles syncing watch progress to Firebase for cross-device support
3. **WebMessageListener** - Receives progress updates from the WebView JavaScript
4. **Unified JavaScript Event Listener** - Injected into the WebView to capture provider-specific messages

## Key Features

- **Duplicate Prevention**: Check if media is already in watch history before adding
- **Firebase Sync**: Sync watch history to Firebase on initial add and every 15 seconds
- **Cross-Device Support**: Watch progress syncs across all devices signed in with the same account
- **Autoplay Detection**: Detect season/episode changes during playback

## 1. WatchHistoryManager with Firebase Sync

Create a new manager class to handle watch history operations with Firebase synchronization:

```kotlin
// File: app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/local/WatchHistoryManager.kt

package com.kiduyuk.klausk.kiduyutv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager

/**
 * WatchHistoryManager - Manages watch history and progress tracking
 * 
 * This manager handles:
 * - Checking if media is already in watch history before adding
 * - Saving media to watch history on playback start
 * - Syncing watch history to Firebase
 * - Updating progress for movies and TV episodes (both local and Firebase)
 * - Tracking season/episode changes for autoplay scenarios
 * - Persisting data using SharedPreferences
 */
class WatchHistoryManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "WatchHistoryManager"
        private const val PREFS_NAME = "watch_history_prefs"
        private const val KEY_WATCH_HISTORY = "watch_history"
        private const val KEY_LAST_PROGRESS_UPDATE = "last_progress_update"
        private const val UPDATE_INTERVAL_MS = 15000L // 15 seconds
    }

    /**
     * Data class representing a watch history item
     */
    data class WatchHistoryItem(
        val tmdbId: Int,
        val type: String, // "movie" or "tv"
        val title: String,
        val posterPath: String?,
        val backdropPath: String?,
        val currentTime: Long, // Current playback position in seconds
        val duration: Long, // Total duration in seconds
        val season: Int?, // For TV shows
        val episode: Int?, // For TV shows
        val progressPercent: Float, // Progress as percentage (0-100)
        val lastUpdated: Long // Timestamp of last update
    )

    /**
     * Check if media is already in watch history
     * Returns true if the item exists, false otherwise
     */
    fun isInWatchHistory(tmdbId: Int, isTv: Boolean, season: Int? = null, episode: Int? = null): Boolean {
        val history = getWatchHistory()
        return history.any { 
            it.tmdbId == tmdbId && 
            (if (isTv) it.type == "tv" else it.type == "movie") &&
            (season == null || it.season == season) &&
            (episode == null || it.episode == episode)
        }
    }

    /**
     * Add or update media in watch history
     * Called when PlayerActivity is created to add media if not already present
     * Also syncs the data to Firebase
     * 
     * @return Boolean indicating if this was a new addition (true) or update (false)
     */
    suspend fun addToWatchHistory(
        tmdbId: Int,
        isTv: Boolean,
        title: String,
        posterPath: String? = null,
        backdropPath: String? = null,
        season: Int? = null,
        episode: Int? = null,
        currentTime: Long = 0L,
        duration: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        val history = getWatchHistory().toMutableList()
        
        // Check if item already exists
        val existingIndex = history.indexOfFirst { 
            it.tmdbId == tmdbId && 
            (if (isTv) it.type == "tv" else it.type == "movie") &&
            (season == null || it.season == season) &&
            (episode == null || it.episode == episode)
        }

        val isNewAddition = existingIndex == -1

        if (existingIndex != -1) {
            // Update existing item
            val existing = history[existingIndex]
            history[existingIndex] = existing.copy(
                currentTime = currentTime,
                duration = duration,
                progressPercent = if (duration > 0) (currentTime.toFloat() / duration * 100) else 0f,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            // Add new item
            val newItem = WatchHistoryItem(
                tmdbId = tmdbId,
                type = if (isTv) "tv" else "movie",
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                currentTime = currentTime,
                duration = duration,
                season = season,
                episode = episode,
                progressPercent = if (duration > 0) (currentTime.toFloat() / duration * 100) else 0f,
                lastUpdated = System.currentTimeMillis()
            )
            history.add(0, newItem) // Add to beginning of list
            Log.i(TAG, "[WatchHistory] Added new item: $title (tmdbId: $tmdbId, isTv: $isTv)")
        }

        saveWatchHistory(history)
        
        // Sync to Firebase after adding/updating
        syncToFirebase(tmdbId, isTv, season, episode, currentTime, duration, title, posterPath, backdropPath)
        
        isNewAddition
    }

    /**
     * Update watch progress for a specific media item
     * Called every 15 seconds during playback
     * Updates both local storage and Firebase
     */
    suspend fun updateProgress(
        tmdbId: Int,
        isTv: Boolean,
        currentTime: Long,
        duration: Long,
        season: Int? = null,
        episode: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        // Rate limit: only update every 15 seconds
        val lastUpdate = prefs.getLong(KEY_LAST_PROGRESS_UPDATE, 0)
        val currentTimeMs = System.currentTimeMillis()
        
        if (currentTimeMs - lastUpdate < UPDATE_INTERVAL_MS) {
            return@withContext false
        }

        prefs.edit().putLong(KEY_LAST_PROGRESS_UPDATE, currentTimeMs).apply()

        val history = getWatchHistory().toMutableList()
        
        val existingIndex = history.indexOfFirst { 
            it.tmdbId == tmdbId && 
            (if (isTv) it.type == "tv" else it.type == "movie") &&
            (season == null || it.season == season) &&
            (episode == null || it.episode == episode)
        }

        if (existingIndex != -1) {
            val existing = history[existingIndex]
            val progressPercent = if (duration > 0) (currentTime.toFloat() / duration * 100) else 0f
            
            // Move to top of history (most recently watched)
            val updatedItem = existing.copy(
                currentTime = currentTime,
                duration = duration,
                season = season ?: existing.season,
                episode = episode ?: existing.episode,
                progressPercent = progressPercent,
                lastUpdated = currentTimeMs
            )
            
            history.removeAt(existingIndex)
            history.add(0, updatedItem)
            saveWatchHistory(history)
            
            // Sync to Firebase after updating progress
            syncToFirebase(tmdbId, isTv, season, episode, currentTime, duration, existing.title, existing.posterPath, existing.backdropPath)
            
            Log.d(TAG, "[Progress] Updated: S${season ?: 0}E${episode ?: 0} - ${currentTime}s / ${duration}s")
            true
        } else {
            false
        }
    }

    /**
     * Sync watch history item to Firebase
     * Uses the existing FirebaseManager.syncWatchHistory() method
     */
    private suspend fun syncToFirebase(
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        currentTime: Long,
        duration: Long,
        title: String?,
        posterPath: String?,
        backdropPath: String?
    ) {
        try {
            FirebaseManager.syncWatchHistory(
                tmdbId = tmdbId,
                isTv = isTv,
                seasonNumber = season,
                episodeNumber = episode,
                playbackPosition = currentTime,
                duration = duration,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath
            )
            Log.i(TAG, "[Firebase] Synced watch progress: tmdbId=$tmdbId, isTv=$isTv, position=${currentTime}s")
        } catch (e: Exception) {
            Log.e(TAG, "[Firebase] Error syncing watch progress", e)
        }
    }

    /**
     * Get the last watch progress for a media item (for resuming playback)
     */
    fun getLastProgress(tmdbId: Int, isTv: Boolean, season: Int? = null, episode: Int? = null): Long {
        val history = getWatchHistory()
        val item = history.find { 
            it.tmdbId == tmdbId && 
            (if (isTv) it.type == "tv" else it.type == "movie") &&
            (season == null || it.season == season) &&
            (episode == null || it.episode == episode)
        }
        return item?.currentTime ?: 0L
    }

    /**
     * Get all watch history items
     */
    fun getWatchHistory(): List<WatchHistoryItem> {
        val json = prefs.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                WatchHistoryItem(
                    tmdbId = obj.getInt("tmdbId"),
                    type = obj.getString("type"),
                    title = obj.getString("title"),
                    posterPath = obj.optString("posterPath").takeIf { it.isNotEmpty() },
                    backdropPath = obj.optString("backdropPath").takeIf { it.isNotEmpty() },
                    currentTime = obj.getLong("currentTime"),
                    duration = obj.getLong("duration"),
                    season = obj.optInt("season").takeIf { it > 0 },
                    episode = obj.optInt("episode").takeIf { it > 0 },
                    progressPercent = obj.getDouble("progressPercent").toFloat(),
                    lastUpdated = obj.getLong("lastUpdated")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get continue watching items (incomplete media with >5% progress)
     * Note: This is already implemented in the homescreen, so no additional UI is needed here
     */
    fun getContinueWatching(): List<WatchHistoryItem> {
        return getWatchHistory().filter { 
            it.progressPercent in 5f..95f 
        }
    }

    /**
     * Clear watch history (both local and Firebase)
     */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_WATCH_HISTORY).apply()
        // Note: Clearing Firebase watch history would need a separate method in FirebaseManager
    }

    /**
     * Remove a specific item from watch history
     */
    suspend fun removeFromHistory(tmdbId: Int, isTv: Boolean, season: Int? = null, episode: Int? = null) = withContext(Dispatchers.IO) {
        val history = getWatchHistory().toMutableList()
        history.removeAll { 
            it.tmdbId == tmdbId && 
            (if (isTv) it.type == "tv" else it.type == "movie") &&
            (season == null || it.season == season) &&
            (episode == null || it.episode == episode)
        }
        saveWatchHistory(history)
    }

    private fun saveWatchHistory(history: List<WatchHistoryItem>) {
        val array = JSONArray()
        history.forEach { item ->
            val obj = JSONObject().apply {
                put("tmdbId", item.tmdbId)
                put("type", item.type)
                put("title", item.title)
                put("posterPath", item.posterPath ?: "")
                put("backdropPath", item.backdropPath ?: "")
                put("currentTime", item.currentTime)
                put("duration", item.duration)
                put("season", item.season ?: 0)
                put("episode", item.episode ?: 0)
                put("progressPercent", item.progressPercent)
                put("lastUpdated", item.lastUpdated)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_WATCH_HISTORY, array.toString()).apply()
    }
}
```

## 2. PlayerActivity Modifications

Modify `PlayerActivity.kt` to add WebMessageListener, watch history management with Firebase sync:

### Key Changes:

1. **Add imports** for coroutines, watch history management, and FirebaseManager
2. **Initialize WatchHistoryManager** in onCreate
3. **Check if media is already in watch history** before adding
4. **Add media to watch history** with Firebase sync on creation
5. **Set up WebMessageListener** to receive progress updates from JavaScript
6. **Implement 15-second progress update timer** that updates both local and Firebase
7. **Handle season/episode changes** for autoplay detection

```kotlin
// Add these imports to PlayerActivity.kt
import kotlinx.coroutines.*
import com.kiduyuk.klausk.kiduyutv.data.local.WatchHistoryManager

// Add these properties to the PlayerActivity class
private lateinit var watchHistoryManager: WatchHistoryManager
private val progressUpdateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private var progressUpdateJob: Job? = null
private var lastKnownSeason: Int = 1
private var lastKnownEpisode: Int = 1
private var lastKnownTmdbId: Int = -1

// In onCreate(), after detecting provider:
watchHistoryManager = WatchHistoryManager(this)

// Check if media is already in watch history before adding
GlobalScope.launch(Dispatchers.IO) {
    if (!watchHistoryManager.isInWatchHistory(tmdbId, isTv, currentSeason, currentEpisode)) {
        watchHistoryManager.addToWatchHistory(
            tmdbId = tmdbId,
            isTv = isTv,
            title = contentTitle,
            posterPath = contentPosterPath,
            backdropPath = contentBackdropPath,
            season = if (isTv) currentSeason else null,
            episode = if (isTv) currentEpisode else null
        )
        Log.i(TAG, "[WatchHistory] Added to history: $contentTitle (tmdbId: $tmdbId, isTv: $isTv)")
    } else {
        Log.i(TAG, "[WatchHistory] Already in history: $contentTitle (tmdbId: $tmdbId, isTv: $isTv)")
    }
}

// Set initial values
lastKnownTmdbId = tmdbId
lastKnownSeason = currentSeason
lastKnownEpisode = currentEpisode

// Set up WebMessageListener (add this after webView initialization)
setupWebMessageListener()
```

### WebMessageListener Implementation:

```kotlin
/**
 * Set up WebMessageListener to receive progress updates from JavaScript
 * This listener handles messages from the unified JavaScript event listener
 */
private fun setupWebMessageListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        webView.webMessageListener = object : WebView.WebMessageListener {
            override fun onMessage(message: WebView.WebMessage) {
                try {
                    val data = message.data
                    Log.d(TAG, "[WebMessage] Received: $data")
                    
                    // Parse the message based on provider format
                    parseAndUpdateProgress(data)
                } catch (e: Exception) {
                    Log.e(TAG, "[WebMessage] Error parsing message: ${e.message}")
                }
            }
        }
        
        // Enable WebMessage listener for iframe communication
        webView.post(object : Runnable {
            override fun run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webView.evaluateJavascript("""
                        (function() {
                            // Enable postMessage communication from iframe to parent
                            window.addEventListener('message', function(event) {
                                try {
                                    // Forward all messages to Android
                                    if (window.AndroidProgressCallback) {
                                        var messageData;
                                        if (typeof event.data === 'string') {
                                            try {
                                                messageData = JSON.parse(event.data);
                                            } catch (e) {
                                                messageData = { raw: event.data };
                                            }
                                        } else {
                                            messageData = event.data;
                                        }
                                        window.AndroidProgressCallback.postMessage(JSON.stringify(messageData));
                                    }
                                } catch (e) {
                                    console.error('Error forwarding message:', e);
                                }
                            });
                        })();
                    """.trimIndent(), null)
                }
            }
        })
    }
}

/**
 * Parse progress updates from various provider formats
 * Handles both direct postMessage and provider-specific localStorage patterns
 */
private fun parseAndUpdateProgress(data: String) {
    try {
        val json = JSONObject(data)
        val type = json.optString("type", "")
        
        when {
            // Format 1: Direct progress data (Videasy, Vidking style)
            // {"id": 123, "type": "movie", "progress": 45.5, "timestamp": 1234, "duration": 3600, "season": 1, "episode": 2}
            json.has("timestamp") && json.has("id") -> {
                val tmdbId = json.getInt("id")
                val currentTime = json.getLong("timestamp")
                val duration = json.getLong("duration")
                val season = json.optInt("season", lastKnownSeason)
                val episode = json.optInt("episode", lastKnownEpisode)
                
                updateWatchProgress(tmdbId, currentTime, duration, season, episode)
            }
            
            // Format 2: MEDIA_DATA type (Vidrock, Vidlink, Vidfast, etc.)
            type == "MEDIA_DATA" && json.has("data") -> {
                val mediaData = json.getJSONObject("data")
                parseMediaDataFormat(mediaData)
            }
            
            // Format 3: timeupdate type (Vidcore style)
            type == "timeupdate" && json.has("data") -> {
                val timeData = json.getJSONObject("data")
                val currentTime = (timeData.getDouble("currentTime") * 1000).toLong() // Convert to ms
                val duration = (timeData.getDouble("duration") * 1000).toLong()
                
                updateWatchProgress(lastKnownTmdbId, currentTime, duration, lastKnownSeason, lastKnownEpisode)
            }
            
            // Format 4: Progress object (from localStorage patterns)
            json.has("progress") -> {
                parseMediaDataFormat(json)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "[ParseProgress] Error: ${e.message}")
    }
}

/**
 * Parse media data format from providers like Vidrock, Vidlink, Vidfast
 */
private fun parseMediaDataFormat(mediaData: JSONObject) {
    try {
        val id = mediaData.getInt("id")
        val contentType = mediaData.getString("type") // "movie" or "tv"
        val isTv = contentType == "tv"
        
        val progressObj = mediaData.getJSONObject("progress")
        val watched = (progressObj.getDouble("watched") * 1000).toLong() // Convert to ms
        val duration = (progressObj.getDouble("duration") * 1000).toLong()
        
        var season = lastKnownSeason
        var episode = lastKnownEpisode
        
        if (isTv) {
            // Try to get specific episode progress
            if (mediaData.has("show_progress")) {
                val showProgress = mediaData.getJSONObject("show_progress")
                // Find the most recent episode key (e.g., "s1e1")
                val keys = showProgress.keys().asSequence().toList()
                if (keys.isNotEmpty()) {
                    val lastKey = keys.sorted().last()
                    val episodeData = showProgress.getJSONObject(lastKey)
                    season = episodeData.optInt("season", lastKnownSeason)
                    episode = episodeData.optInt("episode", lastKnownEpisode)
                }
            } else {
                // Fall back to last watched season/episode
                val lastSeasonStr = mediaData.optString("last_season_watched", "1")
                val lastEpisodeStr = mediaData.optString("last_episode_watched", "1")
                season = lastSeasonStr.toIntOrNull() ?: lastKnownSeason
                episode = lastEpisodeStr.toIntOrNull() ?: lastKnownEpisode
            }
        }
        
        // Check for season/episode changes (autoplay detection)
        if (season != lastKnownSeason || episode != lastKnownEpisode) {
            Log.i(TAG, "[Autoplay] Detected change: S${lastKnownSeason}E${lastKnownEpisode} -> S${season}E${episode}")
            lastKnownSeason = season
            lastKnownEpisode = episode
            
            // Check if new episode is already in watch history before adding
            GlobalScope.launch(Dispatchers.IO) {
                if (!watchHistoryManager.isInWatchHistory(lastKnownTmdbId, true, season, episode)) {
                    watchHistoryManager.addToWatchHistory(
                        tmdbId = lastKnownTmdbId,
                        isTv = true,
                        title = "", // Can be fetched from intent extras
                        season = season,
                        episode = episode
                    )
                }
            }
        }
        
        updateWatchProgress(id, watched, duration, season, episode)
    } catch (e: Exception) {
        Log.e(TAG, "[ParseMediaData] Error: ${e.message}")
    }
}

/**
 * Update watch progress and schedule periodic saves
 * Updates both local storage and Firebase every 15 seconds
 */
private fun updateWatchProgress(tmdbId: Int, currentTime: Long, duration: Long, season: Int, episode: Int) {
    val isTv = season > 0 || episode > 0
    
    // Start periodic progress update (every 15 seconds)
    progressUpdateJob?.cancel()
    progressUpdateJob = progressUpdateScope.launch {
        while (isActive) {
            delay(15000) // 15 seconds
            
            if (duration > 0) {
                val progressSeconds = currentTime / 1000 // Convert to seconds for database
                val durationSeconds = duration / 1000
                
                // Update both local and Firebase
                GlobalScope.launch(Dispatchers.IO) {
                    watchHistoryManager.updateProgress(
                        tmdbId = tmdbId,
                        isTv = isTv,
                        currentTime = progressSeconds,
                        duration = durationSeconds,
                        season = if (isTv) season else null,
                        episode = if (isTv) episode else null
                    )
                }
                Log.d(TAG, "[Progress] Updated: S${season}E${episode} - ${progressSeconds}s / ${durationSeconds}s")
            }
        }
    }
}

/**
 * Clean up progress tracking on activity destruction
 */
override fun onDestroy() {
    progressUpdateJob?.cancel()
    progressUpdateScope.cancel()
    // ... existing WebView cleanup code ...
}
```

## 3. Unified JavaScript Event Listener

Update `StreamProviderManager.generateIframeHtml()` to include the unified JavaScript listener:

```kotlin
// In StreamProviderManager.kt, update the generateIframeHtml function

fun generateIframeHtml(
    providerName: String,
    tmdbId: Int,
    isTv: Boolean,
    season: Int?,
    episode: Int?,
    timestamp: Long = 0L
): String {
    // ... existing code ...

    val unifiedJavaScript = """
        <script>
        (function() {
            // Provider origins for validation
            const PROVIDER_ORIGINS = {
                'vidrock': 'https://vidrock.ru',
                'vidlink': 'https://vidlink.pro',
                'vidfast': ['https://vidfast.pro', 'https://vidfast.in', 'https://vidfast.io', 'https://vidfast.me', 'https://vidfast.net', 'https://vidfast.pm', 'https://vidfast.xyz'],
                'vidnest': 'https://vidnest.fun',
                'vidup': 'https://vidup.to',
                'vidcore': 'https://vidcore.net',
                'peachify': 'https://peachify.top'
            };

            // Helper to validate origin
            function isValidOrigin(origin) {
                for (const key in PROVIDER_ORIGINS) {
                    const allowed = PROVIDER_ORIGINS[key];
                    if (Array.isArray(allowed)) {
                        if (allowed.includes(origin)) return true;
                    } else if (allowed === origin) return true;
                }
                return false;
            }

            // Unified message handler
            function handleProviderMessage(event) {
                try {
                    let progressData = null;
                    const origin = event.origin;

                    // Handle Videasy/Vidking format (direct JSON string)
                    if (typeof event.data === 'string' && event.data.startsWith('{')) {
                        try {
                            const data = JSON.parse(event.data);
                            if (data.id && data.timestamp !== undefined) {
                                progressData = {
                                    type: 'progress',
                                    id: data.id,
                                    mediaType: data.type || '${if (isTv) "tv" else "movie"}',
                                    timestamp: data.timestamp,
                                    duration: data.duration || 0,
                                    progress: data.progress || 0,
                                    season: data.season || ${season ?: 1},
                                    episode: data.episode || ${episode ?: 1}
                                };
                            }
                        } catch (e) {
                            // Not a parseable JSON, ignore
                        }
                    }

                    // Handle MEDIA_DATA format (Vidrock, Vidlink, Vidfast, etc.)
                    if (event.data && event.data.type === 'MEDIA_DATA' && event.data.data) {
                        const mediaData = event.data.data;
                        progressData = {
                            type: 'media_data',
                            id: mediaData.id,
                            mediaType: mediaData.type || '${if (isTv) "tv" else "movie"}',
                            title: mediaData.title || '',
                            poster: mediaData.poster_path || '',
                            backdrop: mediaData.backdrop_path || '',
                            progress: mediaData.progress || {},
                            lastSeason: mediaData.last_season_watched || ${season ?: 1},
                            lastEpisode: mediaData.last_episode_watched || ${episode ?: 1},
                            showProgress: mediaData.show_progress || {}
                        };
                    }

                    // Forward to Android if valid progress data
                    if (progressData && window.AndroidProgressCallback) {
                        window.AndroidProgressCallback.postMessage(JSON.stringify(progressData));
                    }
                } catch (e) {
                    console.error('Progress tracking error:', e);
                }
            }

            // Listen for messages from iframe content
            window.addEventListener('message', handleProviderMessage);

            // Also check localStorage periodically for providers that store data there
            let lastStorageHash = '';
            setInterval(function() {
                try {
                    const keys = Object.keys(localStorage);
                    for (const key of keys) {
                        try {
                            const value = localStorage.getItem(key);
                            if (value && (value.includes('"progress"') || value.includes('"watched"'))) {
                                const hash = btoa(value).substring(0, 50);
                                if (hash !== lastStorageHash) {
                                    lastStorageHash = hash;
                                    // Try to parse and forward
                                    try {
                                        const data = JSON.parse(value);
                                        if (data && data.id && data.progress) {
                                            const progressData = {
                                                type: 'storage_progress',
                                                id: data.id,
                                                mediaType: data.type || '${if (isTv) "tv" else "movie"}',
                                                progress: data.progress
                                            };
                                            if (window.AndroidProgressCallback) {
                                                window.AndroidProgressCallback.postMessage(JSON.stringify(progressData));
                                            }
                                        }
                                    } catch (e) {}
                                }
                            }
                        } catch (e) {}
                    }
                } catch (e) {}
            }, 10000); // Check every 10 seconds

            // Notify Android that progress tracking is ready
            if (window.AndroidProgressCallback) {
                window.AndroidProgressCallback.postMessage(JSON.stringify({
                    type: 'tracking_ready',
                    tmdbId: ${tmdbId},
                    isTv: ${isTv},
                    season: ${season ?: 1},
                    episode: ${episode ?: 1}
                }));
            }
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
            $unifiedJavaScript
        </body>
        </html>
    """.trimIndent()
}
```

## 4. Firebase Data Structure

Watch history is synced to Firebase under the following path:
```
users/{userId}/watchHistory/tv/{tmdbId}  // For TV shows
users/{userId}/watchHistory/movies/{tmdbId}  // For movies
```

Each entry contains:
```json
{
  "tmdbId": 12345,
  "isTv": true,
  "seasonNumber": 1,
  "episodeNumber": 5,
  "playbackPosition": 1234,
  "duration": 3600,
  "progress": 34.28,
  "title": "TV Show Name",
  "overview": "Episode overview...",
  "posterPath": "/path/to/poster.jpg",
  "backdropPath": "/path/to/backdrop.jpg",
  "voteAverage": 8.5,
  "releaseDate": "2024-01-15",
  "updatedAt": 1707123456789
}
```

## 5. Integration Checklist

1. **Add WatchHistoryManager.kt** to `data/local/` package
   - Include `isInWatchHistory()` check method
   - Include Firebase sync in `addToWatchHistory()`
   - Include Firebase sync in `updateProgress()`

2. **Modify PlayerActivity.kt**:
   - Add imports for coroutines, WatchHistoryManager, and FirebaseManager
   - Initialize watchHistoryManager
   - Call `isInWatchHistory()` before `addToWatchHistory()` in onCreate
   - Call `addToWatchHistory()` (which syncs to Firebase automatically)
   - Add `setupWebMessageListener()` call
   - Add `parseAndUpdateProgress()` and related functions
   - Handle `onDestroy` cleanup

3. **Update StreamProviderManager.kt**:
   - Replace the JavaScript block in `generateIframeHtml` with the unified version

4. **Note**: Continue Watching is already implemented in the homescreen, so no additional UI changes are needed

## 6. Data Flow Summary

### On PlayerActivity Creation:
1. Check if media is already in watch history using `isInWatchHistory()`
2. If not present, add to watch history using `addToWatchHistory()`
3. `addToWatchHistory()` saves to local SharedPreferences and syncs to Firebase

### During Playback (Every 15 seconds):
1. WebView JavaScript sends progress update via postMessage
2. `WebMessageListener` receives the message
3. `parseAndUpdateProgress()` extracts progress data
4. `updateWatchProgress()` starts a periodic update job
5. Every 15 seconds, `updateProgress()` is called
6. `updateProgress()` updates local storage AND syncs to Firebase

### On Season/Episode Change (Autoplay):
1. JavaScript detects season/episode change
2. `parseMediaDataFormat()` identifies the new episode
3. Check if new episode is already in watch history
4. If not present, add new episode to watch history (with Firebase sync)
5. Continue tracking progress for new episode

## Provider Message Format Summary

| Provider | Message Format | Key Fields |
|----------|---------------|------------|
| Videasy, Vidking | Direct JSON | id, timestamp, duration, season, episode |
| Vidrock, Vidlink, Vidnest, Vidup, Peachify | MEDIA_DATA with localStorage | id, type, progress{watched, duration}, show_progress |
| Vidcore | timeupdate event | currentTime, duration, percent |
| Vidfast | MEDIA_DATA with multiple origins | Same as Vidrock |

## Troubleshooting

1. **No messages received**: Ensure `setWebMessageListener` is called after API 26 and JavaScript is enabled
2. **Parse errors**: Check Logcat for "[WebMessage]" and "[ParseProgress]" tags
3. **Missing progress**: Some providers may require user interaction before sending updates - try interacting with the video player first
4. **localStorage not accessible**: Cross-origin restrictions may prevent access - the JavaScript listener falls back to postMessage only
5. **Firebase sync not working**: Ensure `FirebaseManager.init()` has been called with the correct user ID

## Performance Considerations

- Progress updates are rate-limited to every 15 seconds to reduce database and Firebase writes
- Watch history is stored in SharedPreferences for quick local access
- Firebase sync happens in parallel with local updates to minimize impact on playback
- The localStorage polling interval is 10 seconds to balance between responsiveness and performance