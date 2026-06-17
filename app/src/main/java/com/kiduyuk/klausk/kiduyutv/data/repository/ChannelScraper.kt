package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.ScrapedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        android.util.Log.i(TAG, "========== STARTING CHANNEL FETCH ==========")
        android.util.Log.i(TAG, "Fetching channels from: $CHANNELS_URL")
        android.util.Log.i(TAG, "Fetch stream URLs: $fetchStreamUrls")

        try {
            val startTime = System.currentTimeMillis()
            val document: Document = Jsoup.connect(CHANNELS_URL)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer(BASE_URL)
                .get()
            val loadTime = System.currentTimeMillis() - startTime
            android.util.Log.i(TAG, "Document loaded in ${loadTime}ms")

            val channels = parseChannelsFromGrid(document)
            android.util.Log.i(TAG, "Parsed ${channels.size} channels from grid")

            if (fetchStreamUrls && channels.isNotEmpty()) {
                android.util.Log.i(TAG, "Starting to fetch stream URLs for ${channels.size} channels...")
                val fetchStartTime = System.currentTimeMillis()
                
                // Fetch stream URLs for each channel
                val channelsWithStreams = mutableListOf<ScrapedChannel>()
                var successCount = 0
                var failureCount = 0
                var totalStreamsFound = 0
                
                for ((index, channel) in channels.withIndex()) {
                    try {
                        android.util.Log.i(TAG, "[${index + 1}/${channels.size}] Fetching streams for: ${channel.name} (ID: ${channel.id})")
                        val streamUrls = fetchStreamUrlsFromChannel(channel.watchPageUrl)
                        
                        if (streamUrls.isNotEmpty()) {
                            successCount++
                            totalStreamsFound += streamUrls.size
                            android.util.Log.i(TAG, "✓ Successfully fetched ${streamUrls.size} stream(s) for ${channel.name}: $streamUrls")
                        } else {
                            failureCount++
                            android.util.Log.w(TAG, "⚠ No streams found for ${channel.name} (ID: ${channel.id})")
                        }
                        
                        channelsWithStreams.add(channel.copy(iframeUrls = streamUrls))
                        delay(50) // Rate limiting
                    } catch (e: Exception) {
                        failureCount++
                        android.util.Log.e(TAG, "✗ Failed to fetch streams for ${channel.name}: ${e.message}", e)
                        channelsWithStreams.add(channel)
                    }
                }
                
                val fetchTime = System.currentTimeMillis() - fetchStartTime
                android.util.Log.i(TAG, "========== STREAM FETCH COMPLETE ==========")
                android.util.Log.i(TAG, "Total channels: ${channels.size}")
                android.util.Log.i(TAG, "Successful: $successCount")
                android.util.Log.i(TAG, "Failed: $failureCount")
                android.util.Log.i(TAG, "Total streams found: $totalStreamsFound")
                android.util.Log.i(TAG, "Average streams per channel: ${if (successCount > 0) totalStreamsFound / successCount else 0}")
                android.util.Log.i(TAG, "Total time: ${fetchTime}ms")
                android.util.Log.i(TAG, "Average time per channel: ${fetchTime / channels.size}ms")
                
                Result.success(channelsWithStreams)
            } else {
                android.util.Log.i(TAG, "Skipping stream URL fetch (fetchStreamUrls=$fetchStreamUrls, channels.isEmpty=${channels.isEmpty()})")
                Result.success(channels)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ CRITICAL: Failed to fetch channels: ${e.message}", e)
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
        android.util.Log.i(TAG, "---------- PARSING CHANNELS FROM GRID ----------")
        val channels = mutableListOf<ScrapedChannel>()

        // Find div.grid
        val grid = document.selectFirst("div.grid")
        if (grid == null) {
            android.util.Log.w(TAG, "No div.grid found in document - HTML structure may have changed")
            android.util.Log.w(TAG, "Document title: ${document.title()}")
            android.util.Log.w(TAG, "Document body length: ${document.body()?.text()?.length ?: 0}")
            return channels
        }
        android.util.Log.i(TAG, "Found div.grid element")

        // Get all a.card elements
        val cardLinks = grid.select("a.card")
        android.util.Log.i(TAG, "Found ${cardLinks.size} a.card elements inside div.grid")

        for ((index, link) in cardLinks.withIndex()) {
            try {
                // Get href and build watchPageUrl
                val href = link.attr("href")
                android.util.Log.i(TAG, "[${index + 1}] Processing card with href: $href")
                
                if (!href.contains("/watch.php?id=")) {
                    android.util.Log.i(TAG, "[${index + 1}] Skipping - href doesn't contain '/watch.php?id='")
                    continue
                }

                val watchPageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                // Get name from div.card__title
                val titleElement = link.selectFirst("div.card__title")
                val name = titleElement?.text()?.trim() ?: "Unknown Channel"
                android.util.Log.i(TAG, "[${index + 1}] Channel name: '$name'")

                // Get id from href (e.g., /watch.php?id=51 -> 51)
                val idMatch = Regex("""id=(\d+)""").find(href)
                val channelId = idMatch?.groupValues?.get(1) ?: "0"
                android.util.Log.i(TAG, "[${index + 1}] Channel ID: $channelId")

                // Get additional attributes if available
                val dataTitle = link.attr("data-title")
                val dataFirst = link.attr("data-first")
                android.util.Log.v(TAG, "[${index + 1}] data-title: '$dataTitle', data-first: '$dataFirst'")

                // Category is always "Channels"
                val category = "Channels"

                val channel = ScrapedChannel(
                    id = channelId,
                    name = name,
                    thumbnailUrl = null,
                    watchPageUrl = watchPageUrl,
                    iframeUrls = emptyList(),
                    category = category
                )
                
                channels.add(channel)
                android.util.Log.i(TAG, "[${index + 1}] ✓ Added channel: $channel")

            } catch (e: Exception) {
                android.util.Log.e(TAG, "[${index + 1}] ✗ Failed to parse card: ${e.message}", e)
            }
        }

        val sortedChannels = channels.sortedBy { it.name }
        android.util.Log.i(TAG, "Parsing complete. Total channels parsed: ${sortedChannels.size}")
        android.util.Log.i(TAG, "First 5 channels: ${sortedChannels.take(5).map { "${it.name} (ID:${it.id})" }}")
        
        return sortedChannels
    }

    /**
     * Fetches stream URLs from a channel's watch page
     *
     * HTML structure:
     * <div class="watch__player">
     *   <div class="watch__actions is-scrollable" id="playerActions">
     *     <div class="btn-group" id="playerBtns">
     *       <button type="button" class="btn player-btn is-active" data-url="https://dlhd.pk/stream/stream-283.php">
     *         Player 1
     *       </button>
     *       ...
     *     </div>
     *   </div>
     * </div>
     */
    private fun fetchStreamUrlsFromChannel(watchPageUrl: String): List<String> {
        android.util.Log.i(TAG, "  >>> Fetching stream URLs from: $watchPageUrl")
        val startTime = System.currentTimeMillis()
        
        return try {
            val document: Document = Jsoup.connect(watchPageUrl)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer(BASE_URL)
                .get()
            
            val loadTime = System.currentTimeMillis() - startTime
            android.util.Log.i(TAG, "  Watch page loaded in ${loadTime}ms")
            android.util.Log.i(TAG, "  Page title: ${document.title()}")

            val streamUrls = mutableListOf<String>()

            // Method 1: Direct selector for div#playerBtns (most specific)
            android.util.Log.i(TAG, "  Trying selector: div#playerBtns button.player-btn[data-url]")
            var playerButtons = document.select("div#playerBtns button.player-btn[data-url]")
            
            // Method 2: If not found, try with the full path
            if (playerButtons.isEmpty()) {
                android.util.Log.i(TAG, "  No buttons found, trying: div.watch__actions div#playerBtns button.player-btn[data-url]")
                playerButtons = document.select("div.watch__actions div#playerBtns button.player-btn[data-url]")
            }
            
            // Method 3: Try without the player-btn class
            if (playerButtons.isEmpty()) {
                android.util.Log.i(TAG, "  No buttons found, trying: div#playerBtns button[data-url]")
                playerButtons = document.select("div#playerBtns button[data-url]")
            }
            
            // Method 4: Try finding by class only
            if (playerButtons.isEmpty()) {
                android.util.Log.i(TAG, "  No buttons found, trying: button.player-btn[data-url]")
                playerButtons = document.select("button.player-btn[data-url]")
            }
            
            // Method 5: Most generic - any button with data-url inside watch area
            if (playerButtons.isEmpty()) {
                android.util.Log.i(TAG, "  No buttons found, trying: .watch__player button[data-url]")
                playerButtons = document.select(".watch__player button[data-url]")
            }
            
            android.util.Log.i(TAG, "  Found ${playerButtons.size} player buttons with data-url attribute")

            for ((index, button) in playerButtons.withIndex()) {
                val dataUrl = button.attr("data-url").trim()
                val title = button.attr("title")
                val text = button.text()
                val isActive = button.hasClass("is-active")
                
                android.util.Log.i(TAG, "  Button ${index + 1}: title='$title', text='$text', isActive='$isActive', data-url='$dataUrl'")
                
                if (dataUrl.isNotEmpty() && dataUrl.startsWith("http")) {
                    streamUrls.add(dataUrl)
                    android.util.Log.i(TAG, "  ✓ Added stream URL ${index + 1}: $dataUrl${if (isActive) " (ACTIVE)" else ""}")
                } else {
                    android.util.Log.w(TAG, "  ✗ Invalid stream URL ${index + 1}: '$dataUrl' (doesn't start with http)")
                }
            }

            // Also extract the iframe src as a fallback
            if (streamUrls.isEmpty()) {
                android.util.Log.i(TAG, "  No button URLs found, trying to extract iframe src...")
                val iframe = document.selectFirst("iframe#playerFrame")
                if (iframe != null) {
                    val iframeSrc = iframe.attr("src").trim()
                    if (iframeSrc.isNotEmpty() && iframeSrc.startsWith("http")) {
                        streamUrls.add(iframeSrc)
                        android.util.Log.i(TAG, "  ✓ Added iframe src as fallback: $iframeSrc")
                    }
                }
            }

            val distinctUrls = streamUrls.distinct()
            if (distinctUrls.size != streamUrls.size) {
                android.util.Log.w(TAG, "  Duplicate URLs found: ${streamUrls.size} -> ${distinctUrls.size} after deduplication")
            }
            
            val fetchTime = System.currentTimeMillis() - startTime
            android.util.Log.i(TAG, "  <<< Fetched ${distinctUrls.size} unique stream URL(s) in ${fetchTime}ms")
            
            if (distinctUrls.isEmpty()) {
                android.util.Log.w(TAG, "  ⚠ WARNING: No stream URLs found for watch page: $watchPageUrl")
                // Log a snippet of the HTML for debugging
                val bodyText = document.body()?.text()?.take(500)
                android.util.Log.v(TAG, "  Page body snippet: $bodyText")
                
                // Debug the HTML structure around player buttons
                debugPlayerButtonsStructure(document)
            }
            
            distinctUrls
        } catch (e: Exception) {
            val fetchTime = System.currentTimeMillis() - startTime
            android.util.Log.e(TAG, "  ✗ ERROR fetching stream URLs from $watchPageUrl after ${fetchTime}ms: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Debug function to log the HTML structure around player buttons
     * Useful for troubleshooting when selectors fail
     */
    private fun debugPlayerButtonsStructure(document: Document) {
        android.util.Log.i(TAG, "========== DEBUG: Player Buttons Structure ==========")
        
        // Check for watch__player div
        val watchPlayer = document.selectFirst(".watch__player")
        if (watchPlayer != null) {
            android.util.Log.i(TAG, "Found .watch__player")
            
            val playerActions = watchPlayer.selectFirst(".watch__actions")
            if (playerActions != null) {
                android.util.Log.i(TAG, "  Found .watch__actions")
                
                val playerBtns = playerActions.selectFirst("#playerBtns")
                if (playerBtns != null) {
                    android.util.Log.i(TAG, "    Found #playerBtns")
                    val buttons = playerBtns.select("button")
                    android.util.Log.i(TAG, "    Found ${buttons.size} buttons")
                    
                    buttons.forEachIndexed { index, button ->
                        android.util.Log.i(TAG, "      Button $index: class='${button.className()}', data-url='${button.attr("data-url")}'")
                    }
                } else {
                    android.util.Log.w(TAG, "    No #playerBtns found in .watch__actions")
                }
            } else {
                android.util.Log.w(TAG, "  No .watch__actions found")
            }
        } else {
            android.util.Log.w(TAG, "No .watch__player found")
        }
        
        // Alternative: direct search
        val directBtns = document.select("#playerBtns")
        if (directBtns.isNotEmpty()) {
            android.util.Log.i(TAG, "Direct #playerBtns search found ${directBtns.size} elements")
            directBtns.forEachIndexed { index, element ->
                android.util.Log.i(TAG, "  Direct #playerBtns $index: ${element.className()}, buttons: ${element.select("button").size}")
            }
        } else {
            android.util.Log.w(TAG, "Direct #playerBtns search found nothing")
        }
        
        // Check for any button with data-url in the entire document
        val allDataUrlButtons = document.select("button[data-url]")
        android.util.Log.i(TAG, "Total buttons with data-url in document: ${allDataUrlButtons.size}")
        
        android.util.Log.i(TAG, "====================================================")
    }

    /**
     * Generates iframe HTML for a given stream URL
     */
    fun generateIframeHtml(streamUrl: String): String {
        android.util.Log.v(TAG, "Generating iframe HTML for URL: $streamUrl")
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
                <iframe src="$streamUrl" width="100%" height="100%" scrolling="no" frameborder="0" allowfullscreen="true" allow="autoplay;" allowtransparency="true" id="thatframe"></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Searches channels by query
     */
    fun searchChannels(channels: List<ScrapedChannel>, query: String): List<ScrapedChannel> {
        android.util.Log.i(TAG, "Searching channels with query: '$query' (total channels: ${channels.size})")
        if (query.isBlank()) {
            android.util.Log.i(TAG, "Query is blank, returning all channels")
            return channels
        }
        
        val lowerQuery = query.lowercase()
        val results = channels.filter { channel ->
            val nameMatch = channel.name.lowercase().contains(lowerQuery)
            val categoryMatch = channel.category?.lowercase()?.contains(lowerQuery) == true
            val match = nameMatch || categoryMatch
            
            if (match) {
                android.util.Log.v(TAG, "  Match found: ${channel.name} (${channel.category})")
            }
            
            match
        }
        
        android.util.Log.i(TAG, "Search complete: ${results.size} results found for '$query'")
        return results
    }
}