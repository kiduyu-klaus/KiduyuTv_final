package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.api.ScheduleApiService
import com.kiduyuk.klausk.kiduyutv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing schedule data from dlhd.pk
 * Handles fetching, caching, and providing schedule data to ViewModels
 */
class ScheduleRepository private constructor() {

    companion object {
        private const val TAG = "ScheduleRepository"
        private const val PREFS_NAME = "schedule_cache"
        private const val KEY_SCHEDULE_DATA = "schedule_data"
        private const val KEY_LAST_FETCH = "last_fetch_time"
        private const val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 minutes cache

        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepository().also { instance = it }
            }
        }
    }

    private val apiService = ScheduleApiService()
    private var cachedSchedule: List<ScheduleDay>? = null

    /**
     * Fetches schedule data from dlhd.pk
     * Uses cache if available and not expired
     *
     * @param context Application context for cache storage
     * @param forceRefresh If true, bypasses cache
     * @return Result containing list of ScheduleDay or error
     */
    suspend fun fetchSchedule(context: Context, forceRefresh: Boolean = false): Result<List<ScheduleDay>> {
        // Check memory cache first
        cachedSchedule?.let { cached ->
            if (!forceRefresh) {
                Log.d(TAG, "Returning cached schedule")
                return Result.success(cached)
            }
        }

        // Check disk cache
        if (!forceRefresh) {
            val diskCache = getCachedSchedule(context)
            diskCache?.let {
                cachedSchedule = it
                Log.d(TAG, "Returning disk-cached schedule")
                return Result.success(it)
            }
        }

        // Fetch from network
        return apiService.fetchSchedulePage().map { html ->
            val schedule = apiService.parseSchedule(html)

            // Update caches
            cachedSchedule = schedule
            saveScheduleToCache(context, schedule)

            Log.d(TAG, "Fetched and cached ${schedule.size} schedule days")
            schedule
        }
    }

    /**
     * Fetches channel watch page with player options
     *
     * @param channelId Channel ID to fetch
     * @return Result containing ChannelWatchPage or error
     */
    suspend fun fetchChannelWatchPage(channelId: String): Result<ChannelWatchPage> {
        return apiService.fetchChannelWatchPage(channelId)
    }

    /**
     * Gets schedule data, fetching if necessary
     *
     * @param context Application context
     * @return List of ScheduleDay
     */
    suspend fun getSchedule(context: Context): List<ScheduleDay> {
        return fetchSchedule(context).getOrDefault(emptyList())
    }

    /**
     * Gets upcoming events across all schedule days
     * Useful for showing a quick preview of upcoming events
     *
     * @param context Application context
     * @param limit Maximum number of events to return
     * @return List of upcoming ScheduleEvents
     */
    suspend fun getUpcomingEvents(context: Context, limit: Int = 10): List<ScheduleEvent> {
        val schedule = getSchedule(context)
        val allEvents = mutableListOf<ScheduleEvent>()

        for (day in schedule) {
            for (category in day.categories) {
                allEvents.addAll(category.events)
            }
        }

        return allEvents.take(limit)
    }

    /**
     * Gets events by category name
     *
     * @param context Application context
     * @param categoryName Category to filter by
     * @return List of ScheduleEvents in the category
     */
    suspend fun getEventsByCategory(context: Context, categoryName: String): List<ScheduleEvent> {
        val schedule = getSchedule(context)
        val events = mutableListOf<ScheduleEvent>()

        for (day in schedule) {
            for (category in day.categories) {
                if (category.name.contains(categoryName, ignoreCase = true)) {
                    events.addAll(category.events)
                }
            }
        }

        return events
    }

    /**
     * Gets all categories across all schedule days
     *
     * @param context Application context
     * @return List of unique category names
     */
    suspend fun getAllCategories(context: Context): List<String> {
        val schedule = getSchedule(context)
        val categories = mutableSetOf<String>()

        for (day in schedule) {
            for (category in day.categories) {
                categories.add(category.name)
            }
        }

        return categories.toList().sorted()
    }

    /**
     * Searches events by title
     *
     * @param context Application context
     * @param query Search query
     * @return List of matching ScheduleEvents
     */
    suspend fun searchEvents(context: Context, query: String): List<ScheduleEvent> {
        if (query.isBlank()) return emptyList()

        val schedule = getSchedule(context)
        val matchingEvents = mutableListOf<ScheduleEvent>()

        for (day in schedule) {
            for (category in day.categories) {
                for (event in category.events) {
                    if (event.title.contains(query, ignoreCase = true) ||
                        event.dataTitle.contains(query, ignoreCase = true)) {
                        matchingEvents.add(event)
                    }
                }
            }
        }

        return matchingEvents
    }

    /**
     * Generates iframe HTML for a channel
     *
     * @param iframeUrl URL to load in iframe
     * @return HTML string for WebView
     */
    fun generateIframeHtml(iframeUrl: String): String {
        return apiService.generateIframeHtml(iframeUrl)
    }

    /**
     * Clears cached schedule data
     *
     * @param context Application context
     */
    fun clearCache(context: Context) {
        cachedSchedule = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SCHEDULE_DATA)
            .remove(KEY_LAST_FETCH)
            .apply()
        Log.d(TAG, "Schedule cache cleared")
    }

    // ── Private cache methods ───────────────────────────────────────────────────

    private fun getCachedSchedule(context: Context): List<ScheduleDay>? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
            val currentTime = System.currentTimeMillis()

            // Check if cache is still valid
            if (currentTime - lastFetch > CACHE_DURATION_MS) {
                Log.d(TAG, "Cache expired, need to refresh")
                return null
            }

            // Get cached JSON
            val cachedJson = prefs.getString(KEY_SCHEDULE_DATA, null) ?: return null

            // Parse cached data
            parseCachedSchedule(cachedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache: ${e.message}")
            null
        }
    }

    private fun saveScheduleToCache(context: Context, schedule: List<ScheduleDay>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Convert to JSON
            val json = serializeSchedule(schedule)

            prefs.edit()
                .putString(KEY_SCHEDULE_DATA, json)
                .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Schedule saved to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to cache: ${e.message}")
        }
    }

    /**
     * Serializes schedule to JSON string for caching
     */
    private fun serializeSchedule(schedule: List<ScheduleDay>): String {
        val sb = StringBuilder()
        sb.append("[")

        schedule.forEachIndexed { dayIndex, day ->
            sb.append("{")
            sb.append("\"dateTitle\":\"${escapeJson(day.dateTitle)}\",")
            sb.append("\"categories\":[")

            day.categories.forEachIndexed { catIndex, category ->
                sb.append("{")
                sb.append("\"name\":\"${escapeJson(category.name)}\",")
                sb.append("\"events\":[")

                category.events.forEachIndexed { eventIndex, event ->
                    sb.append("{")
                    sb.append("\"id\":\"${escapeJson(event.id)}\",")
                    sb.append("\"title\":\"${escapeJson(event.title)}\",")
                    sb.append("\"time\":\"${escapeJson(event.time)}\",")
                    sb.append("\"displayTime\":\"${escapeJson(event.displayTime)}\",")
                    sb.append("\"dataTitle\":\"${escapeJson(event.dataTitle)}\",")
                    sb.append("\"channels\":[")

                    event.channels.forEachIndexed { chIndex, channel ->
                        sb.append("{")
                        sb.append("\"id\":\"${escapeJson(channel.id)}\",")
                        sb.append("\"name\":\"${escapeJson(channel.name)}\",")
                        sb.append("\"dataCh\":\"${escapeJson(channel.dataCh)}\",")
                        sb.append("\"watchUrl\":\"${escapeJson(channel.watchUrl)}\"")
                        sb.append("}")
                        if (chIndex < event.channels.size - 1) sb.append(",")
                    }

                    sb.append("]")
                    sb.append("}")
                    if (eventIndex < category.events.size - 1) sb.append(",")
                }

                sb.append("]")
                sb.append("}")
                if (catIndex < day.categories.size - 1) sb.append(",")
            }

            sb.append("]")
            sb.append("}")
            if (dayIndex < schedule.size - 1) sb.append(",")
        }

        sb.append("]")
        return sb.toString()
    }

    /**
     * Parses cached JSON string back to ScheduleDay objects
     */
    private fun parseCachedSchedule(json: String): List<ScheduleDay> {
        val schedule = mutableListOf<ScheduleDay>()

        try {
            // Simple JSON parsing (no external library needed)
            // Format: [{"dateTitle":"...", "categories":[{"name":"...", "events":[...]}]}]

            val daysMatch = Regex("\\[\\s*\\{").find(json) ?: return emptyList()

            // Extract each day object
            val dayObjects = mutableListOf<String>()
            var depth = 0
            var currentObj = StringBuilder()
            var inString = false
            var escaped = false

            for (char in json) {
                when {
                    escaped -> {
                        currentObj.append(char)
                        escaped = false
                    }
                    char == '\\' -> {
                        currentObj.append(char)
                        escaped = true
                    }
                    char == '"' -> {
                        inString = !inString
                        currentObj.append(char)
                    }
                    !inString && char == '{' -> {
                        depth++
                        currentObj.append(char)
                    }
                    !inString && char == '}' -> {
                        depth--
                        currentObj.append(char)
                        if (depth == 0) {
                            dayObjects.add(currentObj.toString())
                            currentObj = StringBuilder()
                        }
                    }
                    else -> currentObj.append(char)
                }
            }

            // Parse each day object
            for (dayObj in dayObjects) {
                val day = parseDayObject(dayObj)
                day?.let { schedule.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached schedule: ${e.message}")
        }

        return schedule
    }

    /**
     * Parses a single day object from JSON
     */
    private fun parseDayObject(dayJson: String): ScheduleDay? {
        try {
            val dateTitle = extractJsonValue(dayJson, "dateTitle") ?: "Unknown"

            // Extract categories array
            val categoriesMatch = Regex("\"categories\"\\s*:\\s*\\[").find(dayJson)
                ?: return ScheduleDay(dateTitle, emptyList())

            val startIdx = categoriesMatch.range.last + 1
            val endIdx = findMatchingBracket(dayJson, startIdx)
            val categoriesJson = dayJson.substring(startIdx, endIdx)

            val categories = parseCategoriesArray(categoriesJson)

            return ScheduleDay(dateTitle, categories)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing day object: ${e.message}")
            return null
        }
    }

    /**
     * Parses categories array from JSON
     */
    private fun parseCategoriesArray(json: String): List<ScheduleCategory> {
        val categories = mutableListOf<ScheduleCategory>()

        try {
            // Find each category object
            val categoryObjects = extractJsonObjects(json)

            for (catObj in categoryObjects) {
                val name = extractJsonValue(catObj, "name") ?: "Events"

                // Extract events array
                val eventsMatch = Regex("\"events\"\\s*:\\s*\\[").find(catObj)
                if (eventsMatch == null) {
                    categories.add(ScheduleCategory(name, emptyList()))
                    continue
                }

                val startIdx = eventsMatch.range.last + 1
                val endIdx = findMatchingBracket(catObj, startIdx)
                val eventsJson = catObj.substring(startIdx, endIdx)

                val events = parseEventsArray(eventsJson)
                categories.add(ScheduleCategory(name, events))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing categories: ${e.message}")
        }

        return categories
    }

    /**
     * Parses events array from JSON
     */
    private fun parseEventsArray(json: String): List<ScheduleEvent> {
        val events = mutableListOf<ScheduleEvent>()

        try {
            val eventObjects = extractJsonObjects(json)

            for (eventObj in eventObjects) {
                val id = extractJsonValue(eventObj, "id") ?: ""
                val title = extractJsonValue(eventObj, "title") ?: ""
                val time = extractJsonValue(eventObj, "time") ?: ""
                val displayTime = extractJsonValue(eventObj, "displayTime") ?: ""
                val dataTitle = extractJsonValue(eventObj, "dataTitle") ?: ""

                // Parse channels
                val channelsMatch = Regex("\"channels\"\\s*:\\s*\\[").find(eventObj)
                val channels = if (channelsMatch != null) {
                    val startIdx = channelsMatch.range.last + 1
                    val endIdx = findMatchingBracket(eventObj, startIdx)
                    val channelsJson = eventObj.substring(startIdx, endIdx)
                    parseChannelsArray(channelsJson)
                } else {
                    emptyList()
                }

                events.add(ScheduleEvent(
                    id = id,
                    title = title,
                    time = time,
                    displayTime = displayTime,
                    dataTitle = dataTitle,
                    channels = channels
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing events: ${e.message}")
        }

        return events
    }

    /**
     * Parses channels array from JSON
     */
    private fun parseChannelsArray(json: String): List<ScheduleChannel> {
        val channels = mutableListOf<ScheduleChannel>()

        try {
            val channelObjects = extractJsonObjects(json)

            for (chObj in channelObjects) {
                val id = extractJsonValue(chObj, "id") ?: ""
                val name = extractJsonValue(chObj, "name") ?: ""
                val dataCh = extractJsonValue(chObj, "dataCh") ?: ""
                val watchUrl = extractJsonValue(chObj, "watchUrl") ?: ""

                channels.add(ScheduleChannel(
                    id = id,
                    name = name,
                    dataCh = dataCh,
                    watchUrl = watchUrl
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels: ${e.message}")
        }

        return channels
    }

    /**
     * Extracts JSON objects (curly brace blocks) from JSON array string
     */
    private fun extractJsonObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var currentObj = StringBuilder()
        var inString = false
        var escaped = false

        for (char in json) {
            when {
                escaped -> {
                    currentObj.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    currentObj.append(char)
                    escaped = true
                }
                char == '"' -> {
                    inString = !inString
                    currentObj.append(char)
                }
                !inString && char == '{' -> {
                    depth++
                    currentObj.append(char)
                }
                !inString && char == '}' -> {
                    depth--
                    currentObj.append(char)
                    if (depth == 0) {
                        objects.add(currentObj.toString())
                        currentObj = StringBuilder()
                    }
                }
                depth > 0 -> currentObj.append(char)
            }
        }

        return objects
    }

    /**
     * Finds matching closing bracket for opening bracket at startIdx
     */
    private fun findMatchingBracket(json: String, startIdx: Int): Int {
        var depth = 1
        var inString = false
        var escaped = false

        for (i in startIdx until json.length) {
            val char = json[i]
            when {
                escaped -> {
                    escaped = false
                }
                char == '\\' -> {
                    escaped = true
                }
                char == '"' -> {
                    inString = !inString
                }
                !inString && char == '{' -> depth++
                !inString && char == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }

        return json.length
    }

    /**
     * Extracts a string value from JSON object by key
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.let { unescapeJson(it) }
    }

    /**
     * Escapes special characters for JSON string
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Unescapes JSON string
     */
    private fun unescapeJson(str: String): String {
        return str
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}