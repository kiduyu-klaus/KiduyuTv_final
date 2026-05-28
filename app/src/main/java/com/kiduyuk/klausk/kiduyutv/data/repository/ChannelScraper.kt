package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.ScrapedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Repository for scraping live TV channels from dlhd.pk
 * Uses Jsoup for HTML parsing
 *
 * Correct flow:
 * 1. Load https://dlhd.pk/24-7-channels.php
 * 2. Inside div.grid, get all a tags with class="card"
 * 3. Save href as watchPageUrl, div.card__title text as name, id from link, category="Channels"
 * 4. When channel is clicked, open watchPageUrl, get iframeUrls from button data-url in div#playerBtns
 * 5. Build iframes and play in SchedulePlayerActivity
 */
object ChannelScraper {

    private const val TAG = "ChannelScraper"
    private const val BASE_URL = "https://dlhd.pk"
    private const val CHANNELS_URL = "$BASE_URL/24-7-channels.php"
    private const val TIMEOUT_MS = 15000

    /**
     * Fetches and parses all channels from the 24-7 channels page
     *
     * @param fetchStreamUrls If true, fetches each channel page to get stream URLs
     * @return Result containing list of ScrapedChannel
     */
    suspend fun fetchChannels(fetchStreamUrls: Boolean = true): Result<List<ScrapedChannel>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.i(TAG, "Fetching channels from: $CHANNELS_URL")

            val document: Document = Jsoup.connect(CHANNELS_URL)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer(BASE_URL)
                .get()

            val channels = parseChannelsFromGrid(document)

            android.util.Log.i(TAG, "Parsed ${channels.size} channels from grid")

            if (fetchStreamUrls && channels.isNotEmpty()) {
                // Fetch stream URLs for each channel
                val channelsWithStreams = mutableListOf<ScrapedChannel>()
                for (channel in channels) {
                    try {
                        val streamUrls = fetchStreamUrlsFromChannel(channel.watchPageUrl)
                        channelsWithStreams.add(channel.copy(iframeUrls = streamUrls))
                        kotlinx.coroutines.delay(50)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to fetch streams for ${channel.name}: ${e.message}")
                        channelsWithStreams.add(channel)
                    }
                }
                Result.success(channelsWithStreams)
            } else {
                Result.success(channels)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch channels: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Parses channel elements from div.grid > a.card
     *
     * HTML structure:
     * <div class="grid">
     *   <a class="card" href="/watch.php?id=51" data-title="abc usa" data-first="A">
     *     <div class="card__title">ABC USA</div>
     *     <div class="">ID: 51</div>
     *   </a>
     * </div>
     */
    private fun parseChannelsFromGrid(document: Document): List<ScrapedChannel> {
        val channels = mutableListOf<ScrapedChannel>()

        // Find div.grid
        val grid = document.selectFirst("div.grid")
        if (grid == null) {
            android.util.Log.w(TAG, "No div.grid found in document")
            return channels
        }

        // Get all a.card elements
        val cardLinks = grid.select("a.card")
        android.util.Log.d(TAG, "Found ${cardLinks.size} a.card elements")

        for (link in cardLinks) {
            try {
                // Get href and build watchPageUrl
                val href = link.attr("href")
                if (!href.contains("/watch.php?id=")) continue

                val watchPageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                // Get name from div.card__title
                val titleElement = link.selectFirst("div.card__title")
                val name = titleElement?.text()?.trim() ?: "Unknown Channel"

                // Get id from href (e.g., /watch.php?id=51 -> 51)
                val idMatch = Regex("""id=(\d+)""").find(href)
                val channelId = idMatch?.groupValues?.get(1) ?: "0"

                // Category is always "Channels"
                val category = "Channels"

                channels.add(
                    ScrapedChannel(
                        id = channelId,
                        name = name,
                        thumbnailUrl = null,
                        watchPageUrl = watchPageUrl,
                        iframeUrls = emptyList(),
                        category = category
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to parse card: ${e.message}")
            }
        }

        return channels.sortedBy { it.name }
    }

    /**
     * Fetches stream URLs from a channel's watch page
     *
     * HTML structure:
     * <div class="btn-group" id="playerBtns">
     *   <button type="button" class="btn player-btn is-active" data-url="https://dlhd.pk/stream/stream-304.php" title="PLAYER 1">
     *     Player 1
     *   </button>
     *   ...
     * </div>
     */
    private fun fetchStreamUrlsFromChannel(watchPageUrl: String): List<String> {
        return try {
            val document: Document = Jsoup.connect(watchPageUrl)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer(BASE_URL)
                .get()

            val streamUrls = mutableListOf<String>()

            // Get all buttons in div#playerBtns and extract data-url
            val playerButtons = document.select("div#playerBtns button.player-btn[data-url]")
            android.util.Log.d(TAG, "Found ${playerButtons.size} player buttons")

            for (button in playerButtons) {
                val dataUrl = button.attr("data-url").trim()
                if (dataUrl.isNotEmpty() && dataUrl.startsWith("http")) {
                    streamUrls.add(dataUrl)
                }
            }

            // Fallback: if no buttons found, try to get iframe src from embed code
            if (streamUrls.isEmpty()) {
                val iframe = document.selectFirst("iframe[src*='dlhd.pk/stream'], iframe[src*='stream-']")
                if (iframe != null) {
                    val src = iframe.attr("src").trim()
                    if (src.isNotEmpty()) {
                        streamUrls.add(src)
                    }
                }
            }

            // Fallback: construct URLs from channel ID
            if (streamUrls.isEmpty()) {
                val idMatch = Regex("""id=(\d+)""").find(watchPageUrl)
                if (idMatch != null) {
                    val channelId = idMatch.groupValues[1]
                    val baseUrls = listOf(
                        "$BASE_URL/stream/stream-$channelId.php",
                        "$BASE_URL/cast/stream-$channelId.php",
                        "$BASE_URL/watch/stream-$channelId.php",
                        "$BASE_URL/plus/stream-$channelId.php",
                        "$BASE_URL/casting/stream-$channelId.php",
                        "$BASE_URL/player/stream-$channelId.php"
                    )
                    streamUrls.addAll(baseUrls)
                }
            }

            streamUrls.distinct()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching stream URLs from $watchPageUrl: ${e.message}")
            emptyList()
        }
    }

    /**
     * Generates iframe HTML for a given stream URL
     */
    fun generateIframeHtml(streamUrl: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                    iframe { width: 100%; height: 100%; border: 0; }
                </style>
            </head>
            <body>
                <iframe src="$streamUrl" width="100%" height="100%" style="border:0;" allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Searches channels by query
     */
    fun searchChannels(channels: List<ScrapedChannel>, query: String): List<ScrapedChannel> {
        if (query.isBlank()) return channels
        val lowerQuery = query.lowercase()
        return channels.filter { channel ->
            channel.name.lowercase().contains(lowerQuery) ||
            channel.category?.lowercase()?.contains(lowerQuery) == true
        }
    }
}