package com.kiduyuk.klausk.kiduyutv.data.api

import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.ChannelWatchPage
import com.kiduyuk.klausk.kiduyutv.data.model.PlayerOption
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleCategory
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleChannel
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleDay
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * API service for fetching schedule data from dlhd.pk using Jsoup for robust parsing
 */
class ScheduleApiService {

    companion object {
        private const val TAG = "ScheduleApi"
        const val BASE_URL = "https://dlhd.pk"
        private const val SCHEDULE_URL = BASE_URL
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
     * Parses the schedule HTML into ScheduleDay objects using Jsoup
     *
     * @param html Raw HTML content from schedule page
     * @return List of ScheduleDay objects
     */
    fun parseSchedule(html: String): List<ScheduleDay> {
        val scheduleDays = mutableListOf<ScheduleDay>()

        try {
            val doc: Document = Jsoup.parse(html)
            val dayElements = doc.select("div.schedule__day")

            for (dayElement in dayElements) {
                // Extract date title
                val dateTitle = dayElement.selectFirst("div.schedule__dayTitle")?.text() ?: "Unknown Date"

                // Extract categories
                val categories = mutableListOf<ScheduleCategory>()
                val categoryElements = dayElement.select("div.schedule__category")

                for (categoryElement in categoryElements) {
                    val categoryName = categoryElement.selectFirst("div.card__meta")?.text()?.trim() ?: "Events"
                    
                    // Parse events in this category
                    val events = mutableListOf<ScheduleEvent>()
                    val eventElements = categoryElement.select("div.schedule__event")

                    for (eventElement in eventElements) {
                        val timeElement = eventElement.selectFirst("span.schedule__time")
                        val time = timeElement?.attr("data-time") ?: ""
                        val displayTime = timeElement?.text() ?: ""
                        
                        val dataTitle = eventElement.attr("data-title")
                        val titleElement = eventElement.selectFirst("span.schedule__eventTitle")
                        val title = titleElement?.text() ?: dataTitle

                        // Generate unique ID
                        val id = "${title.hashCode()}_${time.hashCode()}"

                        // Parse channels
                        val channels = mutableListOf<ScheduleChannel>()
                        val channelLinks = eventElement.select("a[data-ch]")

                        for (link in channelLinks) {
                            val href = link.attr("href")
                            val channelTitle = link.attr("title")
                            val dataCh = link.attr("data-ch")

                            // Extract channel ID from href
                            val channelId = extractChannelId(href) ?: "0"

                            // Build full watch URL
                            val watchUrl = when {
                                href.startsWith("/") -> "$BASE_URL$href"
                                !href.startsWith("http") -> "$BASE_URL/$href"
                                else -> href
                            }

                            channels.add(ScheduleChannel(
                                id = channelId,
                                name = channelTitle,
                                dataCh = dataCh,
                                watchUrl = watchUrl
                            ))
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

                    categories.add(ScheduleCategory(categoryName, events))
                }

                scheduleDays.add(ScheduleDay(dateTitle, categories))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing schedule with Jsoup: ${e.message}")
        }

        return scheduleDays
    }

    /**
     * Parses watch page HTML to extract player options and iframe URL using Jsoup
     */
    private fun parseWatchPage(channelId: String, html: String): ChannelWatchPage {
        val playerOptions = mutableListOf<PlayerOption>()

        try {
            val doc: Document = Jsoup.parse(html)
            
            // Parse player buttons
            val playerButtons = doc.select("button.player-btn")
            for (button in playerButtons) {
                val url = button.attr("data-url")
                val title = button.attr("title")
                val isActive = button.hasClass("is-active")

                // Extract player number
                val playerNum = Regex("PLAYER\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

                playerOptions.add(PlayerOption(
                    playerNumber = playerNum,
                    url = url,
                    isActive = isActive
                ))
            }

            // Extract default iframe URL
            val iframe = doc.selectFirst("iframe#playerFrame")
            val defaultIframeUrl = iframe?.attr("src") ?: "$BASE_URL/player/stream-$channelId.php"

            // Get channel name from page
            val channelName = doc.title().replace("Watch ", "").replace(" Live Stream", "").trim()

            return ChannelWatchPage(
                channelId = channelId,
                channelName = if (channelName.isNotEmpty()) channelName else "Channel $channelId",
                playerOptions = playerOptions,
                defaultIframeUrl = defaultIframeUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing watch page with Jsoup: ${e.message}")
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
                <iframe src="$iframeUrl" width="100%" height="100%" scrolling="no" frameborder="0" allowfullscreen="true" allow="autoplay;" allowtransparency="true" id="thatframe"></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Helper to extract channel ID from URL
     */
    private fun extractChannelId(url: String): String? {
        return Regex("id=(\\d+)").find(url)?.groupValues?.getOrNull(1)
    }
}
