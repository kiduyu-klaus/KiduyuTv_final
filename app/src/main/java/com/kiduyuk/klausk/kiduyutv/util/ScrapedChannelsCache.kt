package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.ScrapedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache manager for scraped channels from dlhd.pk
 * Saves channels to JSON file for offline access
 */
object ScrapedChannelsCache {

    private const val TAG = "ScrapedChannelsCache"
    private const val CACHE_FILE_NAME = "scraped_channels.json"
    private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Save channels to cache
     */
    suspend fun saveChannels(context: Context, channels: List<ScrapedChannel>) {
        withContext(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                channels.forEach { channel ->
                    val jsonObject = JSONObject().apply {
                        put("id", channel.id)
                        put("name", channel.name)
                        put("thumbnailUrl", channel.thumbnailUrl ?: "")
                        put("watchPageUrl", channel.watchPageUrl)
                        put("iframeUrls", JSONArray(channel.iframeUrls))
                        put("category", channel.category ?: "")
                    }
                    jsonArray.put(jsonObject)
                }

                val file = File(context.filesDir, CACHE_FILE_NAME)
                file.writeText(jsonArray.toString(2))
                Log.i(TAG, "Saved ${channels.size} channels to cache")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving channels to cache", e)
            }
        }
    }

    /**
     * Load channels from cache
     */
    suspend fun loadChannels(context: Context): List<ScrapedChannel> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (!file.exists()) {
                    Log.d(TAG, "No cached channels found")
                    return@withContext emptyList()
                }

                // Check cache validity
                val age = System.currentTimeMillis() - file.lastModified()
                if (age > CACHE_VALIDITY_MS) {
                    Log.d(TAG, "Cached channels expired (age: ${age / 1000 / 60} minutes)")
                    // Don't delete, just return empty - user can re-scrape if needed
                    return@withContext emptyList()
                }

                val content = file.readText()
                val jsonArray = JSONArray(content)
                val channels = mutableListOf<ScrapedChannel>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val iframeUrlsArray = obj.optJSONArray("iframeUrls") ?: JSONArray()
                    val iframeUrls = mutableListOf<String>()
                    for (j in 0 until iframeUrlsArray.length()) {
                        iframeUrls.add(iframeUrlsArray.getString(j))
                    }

                    channels.add(
                        ScrapedChannel(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            thumbnailUrl = obj.optString("thumbnailUrl").ifEmpty { null },
                            watchPageUrl = obj.getString("watchPageUrl"),
                            iframeUrls = iframeUrls,
                            category = obj.optString("category").ifEmpty { null }
                        )
                    )
                }

                Log.i(TAG, "Loaded ${channels.size} channels from cache")
                channels
            } catch (e: Exception) {
                Log.e(TAG, "Error loading channels from cache", e)
                emptyList()
            }
        }
    }

    /**
     * Clear the channels cache
     */
    suspend fun clearCache(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists()) {
                    file.delete()
                    Log.i(TAG, "Cleared scraped channels cache")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing channels cache", e)
            }
        }
    }

    /**
     * Check if cached channels exist and are valid
     */
    fun hasCachedChannels(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            file.exists() && file.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get cache age in milliseconds
     */
    fun getCacheAge(context: Context): Long {
        return try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (file.exists()) {
                System.currentTimeMillis() - file.lastModified()
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}