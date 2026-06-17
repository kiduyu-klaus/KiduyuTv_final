package com.kiduyuk.klausk.kiduyutv.data.model

/**
 * Data class representing a channel scraped from dlhd.pk
 * Contains multiple stream URLs (iframes) for playback options
 *
 * @property id The unique channel ID (extracted from URL or generated)
 * @property name The display name of the channel
 * @property thumbnailUrl Optional thumbnail URL for the channel
 * @property watchPageUrl The full URL to the channel's watch page
 * @property iframeUrls List of iframe URLs for different stream players
 * @property category Optional category the channel belongs to
 */
data class ScrapedChannel(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val watchPageUrl: String,
    val iframeUrls: List<String> = emptyList(),
    val category: String? = null
) {
    
    init {
        android.util.Log.v("ScrapedChannel", "Creating channel: id='$id', name='$name', category='$category', watchPageUrl='$watchPageUrl', iframeUrlsCount=${iframeUrls.size}")
        
        if (id.isEmpty()) {
            android.util.Log.w("ScrapedChannel", "Channel created with empty ID! name='$name'")
        }
        
        if (name.isEmpty()) {
            android.util.Log.w("ScrapedChannel", "Channel created with empty name! id='$id'")
        }
        
        if (watchPageUrl.isEmpty()) {
            android.util.Log.w("ScrapedChannel", "Channel created with empty watchPageUrl! id='$id', name='$name'")
        }
    }
    
    /**
     * Returns the number of available stream players
     */
    val playerCount: Int get() {
        val count = iframeUrls.size
        if (count > 0) {
            android.util.Log.v("ScrapedChannel", "playerCount for '$name': $count streams")
        }
        return count
    }

    /**
     * Returns true if channel has multiple stream options
     */
    val hasMultiplePlayers: Boolean get() {
        val multiple = iframeUrls.size > 1
        if (multiple) {
            android.util.Log.i("ScrapedChannel", "Channel '$name' has ${iframeUrls.size} player options")
        }
        return multiple
    }

    /**
     * Returns the first (primary) iframe URL
     */
    val primaryStreamUrl: String? get() {
        val url = iframeUrls.firstOrNull()
        if (url == null) {
            android.util.Log.w("ScrapedChannel", "primaryStreamUrl for '$name' is null - no streams available")
        } else {
            android.util.Log.v("ScrapedChannel", "primaryStreamUrl for '$name': $url")
        }
        return url
    }

    /**
     * Returns a formatted iframe HTML for the given URL
     */
    fun getIframeHtml(url: String): String {
        android.util.Log.i("ScrapedChannel", "getIframeHtml for '$name' with URL: $url")
        
        if (url.isEmpty()) {
            android.util.Log.e("ScrapedChannel", "getIframeHtml called with empty URL for channel '$name'")
            return "<html><body>Error: Empty stream URL</body></html>"
        }
        
        val html = """
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
                <iframe src="$url" width="100%" height="100%" scrolling="no" frameborder="0" allowfullscreen="true" allow="autoplay;" allowtransparency="true" id="thatframe"></iframe>
            </body>
            </html>
        """.trimIndent()
        
        android.util.Log.v("ScrapedChannel", "Generated iframe HTML for '$name' (${html.length} bytes)")
        return html
    }

    /**
     * Returns iframe HTML for the primary stream
     */
    fun getPrimaryIframeHtml(): String? {
        android.util.Log.i("ScrapedChannel", "getPrimaryIframeHtml called for '$name'")
        val primaryUrl = primaryStreamUrl
        
        return if (primaryUrl != null) {
            getIframeHtml(primaryUrl)
        } else {
            android.util.Log.w("ScrapedChannel", "getPrimaryIframeHtml returned null for '$name' - no primary stream")
            null
        }
    }
    
    /**
     * Get stream URL by player index (0-based)
     */
    fun getStreamUrlAt(index: Int): String? {
        return if (index in iframeUrls.indices) {
            val url = iframeUrls[index]
            android.util.Log.v("ScrapedChannel", "getStreamUrlAt($index) for '$name': $url")
            url
        } else {
            android.util.Log.w("ScrapedChannel", "getStreamUrlAt($index) failed for '$name' - index out of bounds (size=${iframeUrls.size})")
            null
        }
    }
    
    /**
     * Get all stream URLs with their player numbers
     */
    fun getStreamUrlsWithLabels(): List<Pair<Int, String>> {
        val labeled = iframeUrls.mapIndexed { index, url ->
            index + 1 to url
        }
        android.util.Log.v("ScrapedChannel", "getStreamUrlsWithLabels for '$name': ${labeled.size} streams")
        return labeled
    }
    
    override fun toString(): String {
        return "ScrapedChannel(id='$id', name='$name', category='$category', streams=${iframeUrls.size}, watchPageUrl='$watchPageUrl')"
    }
}