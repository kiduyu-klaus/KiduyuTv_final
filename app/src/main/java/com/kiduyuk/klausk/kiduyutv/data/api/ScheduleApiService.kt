package com.kiduyuk.klausk.kiduyutv.data.api

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * API service for fetching schedule data from dlhd.pk
 */
class ScheduleApiService {

    companion object {
        private const val TAG = "ScheduleApi"
        const val BASE_URL = "https://dlhd.pk"
        private const val SCHEDULE_URL = "https://dlhd.pk/schedule/"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Fetches the schedule page from dlhd.pk
     *
     * @return HTML content of the schedule page
     */
    suspend fun fetchSchedulePage(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching schedule from: $SCHEDULE_URL")

            val request = Request.Builder()
                .url(SCHEDULE_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    Log.d(TAG, "Schedule page fetched successfully, length: ${body.length}")
                    Result.success(body)
                } else {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Log.e(TAG, "HTTP error: ${response.code}")
                Result.failure(Exception("HTTP error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch schedule: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches channel watch page to get player options and iframe
     *
     * @param channelId The channel ID (e.g., "772")
     * @return ChannelWatchPage with player options and iframe URL
     */
    suspend fun fetchChannelWatchPage(channelId: String): Result<ChannelWatchPage> = withContext(Dispatchers.IO) {
        try {
            val watchUrl = "$BASE_URL/watch.php?id=$channelId"
            Log.d(TAG, "Fetching channel watch page: $watchUrl")

            val request = Request.Builder()
                .url(watchUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val watchPage = parseWatchPage(channelId, body)
                Result.success(watchPage)
            } else {
                Result.failure(Exception("HTTP error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch channel watch page: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Parses the schedule HTML into ScheduleDay objects
     *
     * @param html Raw HTML content from schedule page
     * @return List of ScheduleDay objects
     */
    fun parseSchedule(html: String): List<ScheduleDay> {
        val scheduleDays = mutableListOf<ScheduleDay>()

        try {
            // Parse each day section
            val dayPattern = Regex("<div class=\"schedule__day\">([\\s\\S]*?)<div class=\"schedule__day\">|$", RegexOption.IGNORE_CASE)
            val days = dayPattern.findAll(html).toList()

            for (dayMatch in days) {
                val dayHtml = dayMatch.groupValues.getOrNull(1) ?: continue

                // Extract date title
                val dateTitle = extractRegex(
                    dayHtml,
                    Regex("<div class=\"schedule__dayTitle\">([^<]+)</div>", RegexOption.IGNORE_CASE)
                ) ?: "Unknown Date"

                // Extract categories
                val categories = parseCategories(dayHtml)

                scheduleDays.add(ScheduleDay(dateTitle, categories))
            }

            // If no days found, try alternative parsing
            if (scheduleDays.isEmpty()) {
                scheduleDays.addAll(parseScheduleAlternative(html))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing schedule: ${e.message}")
        }

        return scheduleDays
    }

    /**
     * Alternative parsing method for schedule HTML
     */
    private fun parseScheduleAlternative(html: String): List<ScheduleDay> {
        val scheduleDays = mutableListOf<ScheduleDay>()

        try {
            // Find all schedule category headers and their content
            val categoryPattern = Regex(
                "<div class=\"schedule__category[^>]*>([\\s\\S]*?)(?=<div class=\"schedule__category[^>]*>|</div>\\s*<div class=\"schedule__day\">)",
                RegexOption.IGNORE_CASE
            )

            val categories = categoryPattern.findAll(html).toList()
            if (categories.isEmpty()) return scheduleDays

            // Group categories by day (every 2-3 categories = 1 day typically)
            val currentDay = ScheduleDay("Schedule", mutableListOf())

            for (categoryMatch in categories) {
                val categoryHtml = categoryMatch.groupValues[1]

                // Check if this is a new day
                val dateMatch = Regex("<div class=\"schedule__dayTitle\">([^<]+)</div>", RegexOption.IGNORE_CASE)
                    .find(categoryHtml)

                if (dateMatch != null) {
                    if (currentDay.categories.isNotEmpty()) {
                        scheduleDays.add(currentDay)
                    }
                    val newDay = ScheduleDay(dateMatch.groupValues[1], mutableListOf())
                    scheduleDays.add(newDay)
                }

                // Parse category
                val categoryName = extractRegex(
                    categoryHtml,
                    Regex("<div class=\"card__meta\">([^<]+)</div>", RegexOption.IGNORE_CASE)
                ) ?: "Events"

                val events = parseEvents(categoryHtml)
                val category = ScheduleCategory(categoryName, events)

                if (scheduleDays.isNotEmpty()) {
                    val lastDay = scheduleDays.last()
                    val updatedCategories = lastDay.categories.toMutableList()
                    updatedCategories.add(category)
                    scheduleDays[scheduleDays.lastIndex] = lastDay.copy(categories = updatedCategories)
                } else {
                    val updatedCategories = currentDay.categories.toMutableList()
                    updatedCategories.add(category)
                    scheduleDays.add(currentDay.copy(categories = updatedCategories))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in alternative parsing: ${e.message}")
        }

        return scheduleDays
    }

    /**
     * Parses category sections from day HTML
     */
    private fun parseCategories(dayHtml: String): List<ScheduleCategory> {
        val categories = mutableListOf<ScheduleCategory>()

        try {
            // Match each category block
            val categoryPattern = Regex(
                "<div class=\"schedule__category[^>]*>([\\s\\S]*?)</div>\\s*</div>\\s*<div class=\"schedule__category|</div>\\s*</div>\\s*</div>\\s*<div class=\"schedule__day",
                RegexOption.IGNORE_CASE
            )

            val matches = categoryPattern.findAll(dayHtml)

            for (match in matches) {
                val categoryHtml = match.groupValues[1]

                // Extract category name
                val categoryName = extractRegex(
                    categoryHtml,
                    Regex("<div class=\"card__meta\">([\\s\\S]*?)</div>", RegexOption.IGNORE_CASE)
                )?.trim() ?: "Events"

                // Parse events in this category
                val events = parseEvents(categoryHtml)

                categories.add(ScheduleCategory(categoryName, events))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing categories: ${e.message}")
        }

        return categories
    }

    /**
     * Parses events from category HTML
     */
    private fun parseEvents(categoryHtml: String): List<ScheduleEvent> {
        val events = mutableListOf<ScheduleEvent>()

        try {
            // Match each schedule event
            val eventPattern = Regex(
                "<div class=\"schedule__event[^>]*>([\\s\\S]*?)</div>\\s*</div>\\s*<div class=\"schedule__event|<div class=\"schedule__event[^>]*>([\\s\\S]*?)</div>\\s*</div>\\s*</div>\\s*</div>",
                RegexOption.IGNORE_CASE
            )

            val matches = eventPattern.findAll(categoryHtml)

            for (match in matches) {
                val eventHtml = match.groupValues[1]

                // Extract time
                val time = extractRegex(eventHtml, Regex("<span class=\"schedule__time\"[^>]*data-time=\"([^\"]+)\"", RegexOption.IGNORE_CASE)) ?: ""
                val displayTime = extractRegex(eventHtml, Regex("<span class=\"schedule__time\"[^>]*>([^<]+)</span>", RegexOption.IGNORE_CASE)) ?: ""

                // Extract title from data-title attribute
                val dataTitle = extractRegex(
                    eventHtml,
                    Regex("data-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                ) ?: ""

                // Extract display title
                val title = extractRegex(
                    eventHtml,
                    Regex("<span class=\"schedule__eventTitle\">([^<]+)</span>", RegexOption.IGNORE_CASE)
                )?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#039;", "'") ?: dataTitle

                // Generate unique ID from title and time
                val id = "${title.hashCode()}_${time.hashCode()}"

                // Parse channels
                val channels = parseChannels(eventHtml)

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
     * Parses channel links from event HTML
     */
    private fun parseChannels(eventHtml: String): List<ScheduleChannel> {
        val channels = mutableListOf<ScheduleChannel>()

        try {
            // Match channel links
            val channelPattern = Regex(
                "<a[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[^>]*data-ch=\"([^\"]+)\"[^>]*>",
                RegexOption.IGNORE_CASE
            )

            val matches = channelPattern.findAll(eventHtml)

            for (match in matches) {
                val href = match.groupValues[1]
                val title = match.groupValues[2]
                val dataCh = match.groupValues[3]

                // Extract channel ID from href
                val channelId = extractRegex(href, Regex("id=(\\d+)")) ?: "0"

                // Build full watch URL
                val watchUrl = if (href.startsWith("/")) {
                    "$BASE_URL$href"
                } else if (!href.startsWith("http")) {
                    "$BASE_URL/$href"
                } else {
                    href
                }

                channels.add(ScheduleChannel(
                    id = channelId,
                    name = title,
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
     * Parses watch page HTML to extract player options and iframe URL
     */
    private fun parseWatchPage(channelId: String, html: String): ChannelWatchPage {
        val playerOptions = mutableListOf<PlayerOption>()

        try {
            // Parse player buttons
            val playerBtnPattern = Regex(
                "<button[^>]*class=\"btn player-btn[^>]*\"[^>]*data-url=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[^>]*>",
                RegexOption.IGNORE_CASE
            )

            val matches = playerBtnPattern.findAll(html)

            for (match in matches) {
                val url = match.groupValues[1]
                val title = match.groupValues[2]

                // Extract player number
                val playerNum = extractRegex(title, Regex("PLAYER\\s*(\\d+)"))?.toIntOrNull() ?: 1

                // Check if this player is active
                val isActive = html.contains("player-btn\" class=\"btn player-btn is-active") &&
                        html.contains("data-url=\"$url\"")

                playerOptions.add(PlayerOption(
                    playerNumber = playerNum,
                    url = url,
                    isActive = isActive
                ))
            }

            // Extract default iframe URL
            val iframePattern = Regex(
                "<iframe[^>]*id=\"playerFrame\"[^>]*src=\"([^\"]+)\"",
                RegexOption.IGNORE_CASE
            )

            val defaultIframeUrl = extractRegex(html, iframePattern) ?: "$BASE_URL/player/stream-$channelId.php"

            // Get channel name from page
            val channelName = extractRegex(
                html,
                Regex("<title>([^<]+)</title>",
                RegexOption.IGNORE_CASE)
            ) ?: "Channel $channelId"

            return ChannelWatchPage(
                channelId = channelId,
                channelName = channelName,
                playerOptions = playerOptions,
                defaultIframeUrl = defaultIframeUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing watch page: ${e.message}")
            return ChannelWatchPage(
                channelId = channelId,
                channelName = "Channel $channelId",
                playerOptions = emptyList(),
                defaultIframeUrl = "$BASE_URL/player/stream-$channelId.php"
            )
        }
    }

    /**
     * Generates iframe HTML for a given URL
     *
     * @param iframeUrl The URL to load in the iframe
     * @return HTML string containing the iframe
     */
    fun generateIframeHtml(iframeUrl: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                    iframe { width: 100%; height: 100%; border: none; }
                </style>
            </head>
            <body>
                <iframe src="$iframeUrl" allowfullscreen allow="autoplay; fullscreen"></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Extracts a value from HTML using regex
     */
    private fun extractRegex(html: String, pattern: Regex): String? {
        return pattern.find(html)?.groupValues?.getOrNull(1)?.trim()
    }
}