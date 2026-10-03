package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manager for Live TV playlist caching.
 * Handles downloading M3U playlists and saving them locally.
 */
object LiveTvCacheManager {

    private const val TAG = "LiveTvCacheManager"

    // File names for cached data
    private const val PLAYLIST_FILE_NAME = "live_tv_playlist.m3u"

    // SharedPreferences keys
    private const val PREFS_NAME = "live_tv_prefs"
    private const val KEY_PLAYLIST_URL = "playlist_url"
    private const val KEY_LAST_UPDATED = "last_updated"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Get the local cached playlist file.
     */
    fun getPlaylistFile(context: Context): File {
        return File(context.filesDir, PLAYLIST_FILE_NAME)
    }

    /**
     * Get the last updated timestamp.
     */
    fun getLastUpdated(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_UPDATED, 0)
    }

    /**
     * Update the Live TV playlist from its URL.
     *
     * @param context Android context
     * @param playlistUrl M3U playlist URL
     */
    suspend fun updateLiveTvData(context: Context, playlistUrl: String) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Download and cache playlist
            if (playlistUrl.isNotBlank()) {
                try {
                    val playlistContent = downloadContent(playlistUrl)
                    if (playlistContent != null) {
                        saveToFile(getPlaylistFile(context), playlistContent)
                        editor.putString(KEY_PLAYLIST_URL, playlistUrl)
                        Log.i(TAG, "Playlist saved successfully from: $playlistUrl")
                    } else {
                        Log.w(TAG, "Failed to download playlist from: $playlistUrl")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading playlist", e)
                }
            }

            // Update last modified timestamp
            editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            editor.apply()
        }
    }

    /**
     * Clear all Live TV cached data.
     * @param context Android context
     */
    suspend fun clearLiveTvCache(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val playlistFile = getPlaylistFile(context)

                if (playlistFile.exists()) {
                    playlistFile.delete()
                    Log.i(TAG, "Playlist cache deleted")
                }

                // Clear saved URLs
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                Log.i(TAG, "Live TV cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing Live TV cache", e)
            }
        }
    }

    /**
     * Download content from URL.
     */
    private suspend fun downloadContent(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KiduyuTV/1.0")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w(TAG, "Failed to download: ${response.code} ${response.message}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading content from: $url", e)
                null
            }
        }
    }

    /**
     * Save content to file.
     */
    private suspend fun saveToFile(file: File, content: String) {
        withContext(Dispatchers.IO) {
            try {
                file.writeText(content)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to file: ${file.name}", e)
            }
        }
    }

    /**
     * Check if playlist is cached.
     * @param context Android context
     */
    fun hasPlaylist(context: Context): Boolean {
        return try {
            getPlaylistFile(context).exists() && getPlaylistFile(context).length() > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get parsed playlist channels.
     * Returns a list of channel names and their stream URLs.
     */
    fun parsePlaylist(context: Context): List<LiveTvChannel> {
        val channels = mutableListOf<LiveTvChannel>()

        try {
            val file = getPlaylistFile(context)
            if (!file.exists()) return channels

            val content = file.readText()
            val lines = content.lines()

            var currentName = ""
            var currentLogo = ""
            var currentUrl = ""

            for (line in lines) {
                val trimmedLine = line.trim()

                when {
                    trimmedLine.startsWith("#EXTINF:") -> {
                        // Parse EXTINF line: #EXTINF:-1 tvg-name="Name" tvg-logo="logo" group-title="Group",Name
                        val nameMatch = Regex("""tvg-name="([^"]*)"""").find(trimmedLine)
                        val logoMatch = Regex("""tvg-logo="([^"]*)"""").find(trimmedLine)
                        val groupMatch = Regex("""group-title="([^"]*)"""").find(trimmedLine)

                        currentName = nameMatch?.groupValues?.get(1) ?: ""
                        currentLogo = logoMatch?.groupValues?.get(1) ?: ""

                        // Name is after the last comma
                        val commaIndex = trimmedLine.lastIndexOf(',')
                        if (commaIndex >= 0 && currentName.isEmpty()) {
                            currentName = trimmedLine.substring(commaIndex + 1).trim()
                        }
                    }
                    trimmedLine.startsWith("http://") || trimmedLine.startsWith("https://") -> {
                        currentUrl = trimmedLine
                        if (currentName.isNotEmpty() && currentUrl.isNotEmpty()) {
                            channels.add(LiveTvChannel(
                                name = currentName,
                                url = currentUrl,
                                logo = currentLogo
                            ))
                        }
                        // Reset for next entry
                        currentName = ""
                        currentLogo = ""
                        currentUrl = ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing playlist", e)
        }

        return channels
    }
}

/**
 * Data class representing a Live TV channel.
 */
data class LiveTvChannel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = ""
)
