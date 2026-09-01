package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.kiduyuk.klausk.kiduyutv.desktop.model.StreamItem

object StreamRanker {
    private fun resolution(quality: String): Int =
        Regex("(\\d{3,4})").find(quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * Select the best stream up to 1080p, or the first available if all are below
     */
    fun automatic(streams: List<StreamItem>): StreamItem? = streams
        .filter { it.url.startsWith("http", ignoreCase = true) }
        .filter { resolution(it.quality) in 1..1080 || resolution(it.quality) == 0 }
        .sortedWith(
            compareByDescending<StreamItem> { resolution(it.quality) }
                .thenByDescending { it.type.equals("hls", true) }
                .thenBy { it.displayName }
        )
        .firstOrNull()
        ?: streams.firstOrNull { it.url.startsWith("http", ignoreCase = true) }

    /**
     * Get all streams sorted by quality for manual selection
     */
    fun forPicker(streams: List<StreamItem>): List<StreamItem> =
        streams
            .filter { it.url.startsWith("http", ignoreCase = true) }
            .sortedWith(
                compareByDescending<StreamItem> { resolution(it.quality) }
                    .thenByDescending { it.type.equals("hls", true) }
                    .thenBy { it.displayName }
            )

    /**
     * Group streams by provider for UI display
     */
    fun groupByProvider(streams: List<StreamItem>): Map<String, List<StreamItem>> =
        streams.groupBy { it.provider.ifBlank { "Unknown" } }
}
