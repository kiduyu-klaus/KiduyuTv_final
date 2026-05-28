package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.ScrapedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Repository for scraping live TV channels from dlhd.pk
 * Uses Jsoup for HTML parsing
 */
object ChannelScraper {

    private const val TAG = "ChannelScraper"
    private const val BASE_URL = "https://dlhd.pk"
    private const val CHANNELS_URL = "$BASE_URL/24-7-channels.php"
    private const val TIMEOUT_MS = 15000
    private const val MAX_CHANNELS_TO_SCRAPE = 50 // Limit to prevent too many requests

    /**
     * Fetches and parses all channels from the 24-7 channels page
     * Then fetches each channel page to extract stream URLs
     *
     * @param fetchStreamUrls If true, fetches each channel page to get stream URLs
     * @return Result containing list of ScrapedChannel with iframeUrls or an exception
     */
    suspend fun fetchChannels(fetchStreamUrls: Boolean = true): Result<List<ScrapedChannel>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.i(TAG, "Fetching channels from: $CHANNELS_URL")
            
            // Fetch the HTML document
            val document: Document = Jsoup.connect(CHANNELS_URL)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer(BASE_URL)
                .get()

            // Parse channels from grid elements
            val channels = parseChannelsFromGrid(document)
            
            android.util.Log.i(TAG, "Parsed ${channels.size} channels from grid")

            // If we need stream URLs, fetch them from each channel page
            if (fetchStreamUrls && channels.isNotEmpty()) {
                val channelsWithStreams = mutableListOf<ScrapedChannel>()
                
                // Limit the number of channels to scrape to avoid too many requests
                val channelsToProcess = channels.take(MAX_CHANNELS_TO_SCRAPE)
                
                for ((index, channel) in channelsToProcess.withIndex()) {
                    try {
                        android.util.Log.d(TAG, "Fetching stream URLs for channel ${index + 1}/${channelsToProcess.size}: ${channel.name}")
                        
                        val streamUrls = fetchStreamUrlsFromChannel(channel.watchPageUrl)
                        val updatedChannel = channel.copy(iframeUrls = streamUrls)
                        channelsWithStreams.add(updatedChannel)
                        
                        // Small delay to avoid overwhelming the server
                        kotlinx.coroutines.delay(100)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to fetch streams for ${channel.name}: ${e.message}")
                        // Add channel with empty iframeUrls
                        channelsWithStreams.add(channel)
                    }
                }
                
                android.util.Log.i(TAG, "Successfully processed ${channelsWithStreams.size} channels with stream URLs")
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
     * Fetches stream URLs from a channel's watch page
     * Parses the playerBtns div to extract all data-url attributes
     *
     * @param watchPageUrl The URL of the channel's watch page
     * @return List of stream URLs from the channel page
     */
    private fun fetchStreamUrlsFromChannel(watchPageUrl: String): List<String> {
        return try {
            val document: Document = Jsoup.connect(watchPageUrl)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer(BASE_URL)
                .get()

            // First try to get the iframe URL from watch__playerFrame
            val iframeUrl = document.selectFirst("div.watch__playerFrame iframe[id=playerFrame]")?.attr("src")
            
            // Parse all player buttons from playerBtns
            val playerButtons = document.select("div#playerBtns button.player-btn[data-url]")
            
            val streamUrls = mutableListOf<String>()
            
            // If we found player buttons, extract their data-url attributes
            if (playerButtons.isNotEmpty()) {
                for (button in playerButtons) {
                    val dataUrl = button.attr("data-url").trim()
                    if (dataUrl.isNotEmpty() && dataUrl.startsWith("http")) {
                        streamUrls.add(dataUrl)
                    }
                }
                android.util.Log.d(TAG, "Found ${streamUrls.size} stream URLs from playerBtns")
            }
            
            // If no player buttons found, try to get the iframe URL from playerFrame
            if (streamUrls.isEmpty() && !iframeUrl.isNullOrEmpty()) {
                streamUrls.add(iframeUrl)
                android.util.Log.d(TAG, "Added iframe URL from playerFrame: $iframeUrl")
            }
            
            // Try alternative selectors if still no URLs
            if (streamUrls.isEmpty()) {
                // Try to find any iframe with stream URL
                val iframes = document.select("iframe[src*=stream]")
                for (iframe in iframes) {
                    val src = iframe.attr("src")
                    if (src.isNotEmpty() && src.contains("stream")) {
                        streamUrls.add(if (src.startsWith("http")) src else "$BASE_URL$src")
                    }
                }
            }
            
            streamUrls.distinct()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching stream URLs from $watchPageUrl: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parses channel elements from the grid container
     * Expected HTML structure: <div class="grid"> contains channel items
     */
    private fun parseChannelsFromGrid(document: Document): List<ScrapedChannel> {
        val channels = mutableListOf<ScrapedChannel>()

        // Try different selectors to find grid items
        val gridSelectors = listOf(
            "div.grid",
            "div.grid-item",
            "div.channel-item",
            "a.channel-link",
            ".grid a",
            "[class*=channel]",
            "[class*=grid] a[href*='stream']"
        )

        var gridContainer: Element? = null
        
        // Find the grid container
        for (selector in gridSelectors) {
            gridContainer = document.selectFirst(selector)
            if (gridContainer != null) {
                android.util.Log.d(TAG, "Found grid with selector: $selector")
                break
            }
        }

        // If no specific grid found, look for all channel links
        if (gridContainer == null) {
            // Try to find any links that look like channel pages
            val allLinks = document.select("a[href*='stream']")
            for (link in allLinks) {
                val channel = parseChannelFromLink(link)
                if (channel != null) {
                    channels.add(channel)
                }
            }
        } else {
            // Extract channels from grid items
            val gridItems = when {
                gridContainer.hasClass("grid") -> {
                    // Grid container itself contains items
                    gridContainer.select("> *")
                }
                else -> {
                    // Grid items are children
                    gridContainer.select("div, a")
                }
            }

            for (item in gridItems) {
                val channel = parseChannelFromElement(item)
                if (channel != null) {
                    channels.add(channel)
                }
            }
        }

        // Remove duplicates based on ID
        return channels.distinctBy { it.id }
    }

    /**
     * Parses a single channel from a DOM element
     */
    private fun parseChannelFromElement(element: Element): ScrapedChannel? {
        try {
            // Try to find the link within the element
            val link = element.selectFirst("a[href*='stream']") ?: 
                       if (element.tagName() == "a" && element.attr("href").contains("stream")) element else null

            if (link == null) return null

            val href = link.attr("href")
            val watchPageUrl = if (href.startsWith("http")) href else "$BASE_URL/$href"

            // Extract channel ID from URL
            val channelId = extractChannelId(watchPageUrl)

            // Get channel name
            val name = extractChannelName(element, link)

            // Try to get thumbnail
            val thumbnailUrl = extractThumbnail(element)

            return ScrapedChannel(
                id = channelId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                watchPageUrl = watchPageUrl
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse element: ${e.message}")
            return null
        }
    }

    /**
     * Parses a channel from a link element directly
     */
    private fun parseChannelFromLink(link: Element): ScrapedChannel? {
        try {
            val href = link.attr("href")
            if (!href.contains("stream")) return null

            val watchPageUrl = if (href.startsWith("http")) href else "$BASE_URL/$href"
            val channelId = extractChannelId(watchPageUrl)
            val name = link.text().trim().ifEmpty { 
                link.attr("title").ifEmpty { "Channel $channelId" }
            }

            // Try to get thumbnail from img tag
            val thumbnail = link.selectFirst("img[src]")
            val thumbnailUrl = thumbnail?.attr("src")?.let { 
                if (it.startsWith("http")) it else "$BASE_URL/$it"
            }

            return ScrapedChannel(
                id = channelId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                watchPageUrl = watchPageUrl
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse link: ${e.message}")
            return null
        }
    }

    /**
     * Extracts channel ID from the watch page URL
     */
    private fun extractChannelId(url: String): String {
        // Pattern: stream-XXX.php or stream/XXX or similar
        val patterns = listOf(
            Regex("stream[-_]?(\\d+)"),
            Regex("channel[-_]?(\\d+)"),
            Regex("/(\\d+)/?"),
            Regex("id=(\\d+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // Fallback: encode the URL
        return URLEncoder.encode(url, "UTF-8").take(32)
    }

    /**
     * Extracts the channel name from an element
     */
    private fun extractChannelName(element: Element, link: Element): String {
        // Try different text sources
        val name = listOf(
            link.text().trim(),
            link.attr("title").trim(),
            element.selectFirst("img")?.attr("alt")?.trim(),
            element.selectFirst("[class*=name]")?.text()?.trim(),
            element.selectFirst("[class*=title]")?.text()?.trim(),
            element.text().trim()
        ).filter { it.isNotEmpty() }.firstOrNull()

        return name ?: "Unknown Channel"
    }

    /**
     * Extracts thumbnail URL from an element
     */
    private fun extractThumbnail(element: Element): String? {
        // Try to find an image
        val img = element.selectFirst("img[src]")
        
        if (img != null) {
            val src = img.attr("src")
            return if (src.startsWith("http")) {
                src
            } else if (src.startsWith("/")) {
                "$BASE_URL$src"
            } else {
                "$BASE_URL/$src"
            }
        }

        return null
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