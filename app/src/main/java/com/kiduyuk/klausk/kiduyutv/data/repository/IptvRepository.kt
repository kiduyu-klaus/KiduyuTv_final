package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Repository for fetching and parsing IPTV playlists.
 * Handles M3U format parsing and channel categorization with streaming support.
 */
class IptvRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        // ============================================================
        // PLAYLIST_URL: M3U format IPTV playlist containing channel list
        // Source: abusaeeidx/IPTV-Scraper-Zilla (Combined IPTV Playlist)
        // ============================================================
        const val PLAYLIST_URL = "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u"

        // Cache settings
        private const val CACHE_FILE_NAME = "iptv_playlist.m3u"
        private const val CACHE_VALIDITY_MS = 6 * 60 * 60 * 1000L // 6 hours
        
        // Singleton instance
        @Volatile
        private var instance: IptvRepository? = null
        
        fun getInstance(): IptvRepository {
            return instance ?: synchronized(this) {
                instance ?: IptvRepository().also { instance = it }
            }
        }
    }
    
    private var cachedPlaylist: IptvPlaylist? = null
    
    /**
     * Fetches and parses the IPTV playlist from the remote URL.
     * Uses streaming to avoid loading the entire file into memory.
     * Implements local caching to reduce network requests.
     *
     * @param context Application context for caching
     * @param forceRefresh If true, bypasses cache and fetches from network
     * @return Result containing either the parsed IptvPlaylist or an error
     */
    suspend fun fetchPlaylist(context: Context, forceRefresh: Boolean = false): Result<IptvPlaylist> = withContext(Dispatchers.IO) {
        try {
            // Check if we have a valid cached playlist in memory
            if (!forceRefresh && cachedPlaylist != null) {
                return@withContext Result.success(cachedPlaylist!!)
            }
            
            // Try to load from local cache first
            val cachedPlaylistResult = loadFromCache(context)
            if (!forceRefresh && cachedPlaylistResult != null) {
                cachedPlaylist = cachedPlaylistResult
                return@withContext Result.success(cachedPlaylistResult)
            }
            
            // Fetch from network
            val request = Request.Builder()
                .url(PLAYLIST_URL)
                .header("User-Agent", "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36 LG Browser/8.0.0 WebOS.TV-2024/04.00.00 (LG; OLED65C4PUA;)")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                // Try cache on network failure
                cachedPlaylistResult?.let {
                    cachedPlaylist = it
                    return@withContext Result.success(it)
                }
                return@withContext Result.failure(
                    Exception("Failed to fetch playlist: ${response.code}")
                )
            }
            
            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty response from server"))
            
            // Parse and cache simultaneously using streaming
            val result = parseAndCacheM3uPlaylist(context, body)
            
            cachedPlaylist = result
            Result.success(result)
        } catch (e: Exception) {
            // Try to return cached version on error
            cachedPlaylist?.let {
                return@withContext Result.success(it)
            }
            Result.failure(e)
        }
    }
    
    /**
     * Parses M3U playlist using streaming while capturing content for caching.
     * This avoids loading the entire file into memory.
     *
     * @param context Application context for caching
     * @param body ResponseBody from OkHttp
     * @return Parsed IptvPlaylist
     */
    private fun parseAndCacheM3uPlaylist(context: Context, body: okhttp3.ResponseBody): IptvPlaylist {
        val channels = mutableListOf<IptvChannel>()
        val contentBuilder = StringBuilder()
        var currentChannel: IptvChannel? = null
        
        // Stream the response line by line while building content for cache
        BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
            reader.lineSequence().forEach { rawLine ->
                contentBuilder.appendLine(rawLine)
                val line = rawLine.trim()
                when {
                    line.startsWith("#EXTINF:") -> {
                        currentChannel = parseExtInfLine(line)
                    }
                    line.isNotEmpty() && !line.startsWith("#") && (currentChannel != null) -> {
                        channels.add(currentChannel!!.copy(url = line))
                        currentChannel = null
                    }
                    line.startsWith("#") && !line.startsWith("#EXTINF:") -> {
                        currentChannel = null
                    }
                }
            }
        }
        
        // Cache the content
        val cacheContent = contentBuilder.toString()
        if (cacheContent.isNotEmpty()) {
            saveToCache(context, cacheContent)
        }
        
        // Group channels by category
        val categories = channels
            .filter { !it.group.isNullOrBlank() }
            .groupBy { it.group!! }
        
        return IptvPlaylist(
            categories = categories,
            allChannels = channels
        )
    }
    
    /**
     * Legacy parser for cached content (when we have the full string).
     */
    /** Parses a downloaded country playlist without altering the app-wide cache. */
    fun parseM3uPlaylist(content: String): IptvPlaylist {
        val channels = mutableListOf<IptvChannel>()
        var currentChannel: IptvChannel? = null
        
        content.lineSequence().forEach { line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("#EXTINF:") -> {
                    currentChannel = parseExtInfLine(trimmedLine)
                }
                trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") && (currentChannel != null) -> {
                    channels.add(currentChannel!!.copy(url = trimmedLine))
                    currentChannel = null
                }
                trimmedLine.startsWith("#") && !trimmedLine.startsWith("#EXTINF:") -> {
                    currentChannel = null
                }
            }
        }
        
        // Group channels by category
        val categories = channels
            .filter { !it.group.isNullOrBlank() }
            .groupBy { it.group!! }
        
        return IptvPlaylist(
            categories = categories,
            allChannels = channels
        )
    }
    
    /**
     * Parses the #EXTINF line to extract channel metadata.
     * Uses more forgiving regex patterns to handle inconsistent M3U formatting.
     *
     * @param line The #EXTINF line from M3U
     * @return IptvChannel with parsed metadata
     */
    private fun parseExtInfLine(line: String): IptvChannel? {
        try {
            // Extract attributes from #EXTINF:-1 tvg-id="..." tvg-name="..." tvg-logo="..." group-title="...",Channel Name
            val attributesPart = line.substringAfter("#EXTINF:").substringBefore(",")
            val name = line.substringAfterLast(",").trim()
            
            // Parse tvg-logo - handles optional quotes, single quotes, or no quotes
            val logoMatch = Regex("""tvg-logo=["']?([^"'>\s]+)["']?""").find(attributesPart)
            val logo = logoMatch?.groupValues?.get(1)?.trim()
            
            // Keep multi-word quoted groups intact (for example, "New York").
            val groupMatch = Regex(
                """group-title=(?:"([^"]*)"|'([^']*)'|([^\s,]+))"""
            ).find(attributesPart)
            val group = groupMatch?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
            
            // Parse tvg-id - handles optional quotes, single quotes, or no quotes
            val tvgIdMatch = Regex("""tvg-id=["']?([^"'>\s]+)["']?""").find(attributesPart)
            val tvgId = tvgIdMatch?.groupValues?.get(1)?.trim()
            
            // Parse tvg-name - handles optional quotes, single quotes, or no quotes
            val tvgNameMatch = Regex("""tvg-name=["']?([^"'>\s]+)["']?""").find(attributesPart)
            val tvgName = tvgNameMatch?.groupValues?.get(1)?.trim()
            
            // Validate we have at least a name
            if (name.isBlank()) return null
            
            return IptvChannel(
                name = name,
                logo = logo,
                url = "", // Will be set from next line
                group = group,
                tvgId = tvgId,
                tvgName = tvgName
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Saves playlist content to local cache.
     */
    private fun saveToCache(context: Context, content: String) {
        try {
            val cacheDir = context.getExternalFilesDir(null) ?: context.filesDir
            val cacheFile = File(cacheDir, CACHE_FILE_NAME)
            cacheFile.writeText(content)
        } catch (e: Exception) {
            // Silently fail - cache is not critical
        }
    }
    
    /**
     * Loads playlist from local cache if valid.
     */
    private fun loadFromCache(context: Context): IptvPlaylist? {
        try {
            val cacheDir = context.getExternalFilesDir(null) ?: context.filesDir
            val cacheFile = File(cacheDir, CACHE_FILE_NAME)
            
            if (!cacheFile.exists()) return null
            
            // Check if cache is still valid
            val age = System.currentTimeMillis() - cacheFile.lastModified()
            if (age > CACHE_VALIDITY_MS) return null
            
            val content = cacheFile.readText()
            return parseM3uPlaylist(content)
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Clears the in-memory cache.
     */
    fun clearMemoryCache() {
        cachedPlaylist = null
    }
    
    /**
     * Clears both memory and disk cache.
     */
    fun clearCache(context: Context) {
        cachedPlaylist = null
        try {
            val cacheDir = context.getExternalFilesDir(null) ?: context.filesDir
            val cacheFile = File(cacheDir, CACHE_FILE_NAME)
            cacheFile.delete()
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Searches channels by name across all categories.
     *
     * @param query Search query string
     * @param channels List of channels to search in
     * @return Filtered list of matching channels
     */
    fun searchChannels(query: String, channels: List<IptvChannel>): List<IptvChannel> {
        if (query.isBlank()) return channels
        return channels.filter { 
            it.name.contains(query, ignoreCase = true)
        }
    }

}
