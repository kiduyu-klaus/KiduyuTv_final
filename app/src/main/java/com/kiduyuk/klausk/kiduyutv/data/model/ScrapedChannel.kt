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
    /**
     * Returns the number of available stream players
     */
    val playerCount: Int get() = iframeUrls.size

    /**
     * Returns true if channel has multiple stream options
     */
    val hasMultiplePlayers: Boolean get() = iframeUrls.size > 1

    /**
     * Returns the first (primary) iframe URL
     */
    val primaryStreamUrl: String? get() = iframeUrls.firstOrNull()

    /**
     * Returns a formatted iframe HTML for the given URL
     */
    fun getIframeHtml(url: String): String = """
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
            <iframe src="$url" allowfullscreen frameborder="0"></iframe>
        </body>
        </html>
    """.trimIndent()

    /**
     * Returns iframe HTML for the primary stream
     */
    fun getPrimaryIframeHtml(): String? = primaryStreamUrl?.let { getIframeHtml(it) }
}