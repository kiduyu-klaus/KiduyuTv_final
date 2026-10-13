package com.kiduyuk.klausk.kiduyutv.data.model

/** A live or upcoming event exposed by the PPV or TIMST sports feeds. */
data class LiveSportsEvent(
    val id: String,
    val source: LiveSportsSource,
    val name: String,
    val category: String,
    val league: String,
    val posterUrl: String,
    val isLive: Boolean,
    val startsAtMillis: Long?,
    val viewerCount: Int,
    /** Named-feed URLs supplied by the source. */
    val streamUrls: List<String>,
    /** Fallback embed used only when the source returned no stream URLs. */
    val embedUrl: String
) {
    /** Preserve every supplied stream as Schedule Player servers, or use the embed fallback. */
    val playbackUrls: List<String>
        get() = streamUrls.ifEmpty { listOf(embedUrl) }
            .filter { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            .distinct()
}

enum class LiveSportsSource(val tabLabel: String) {
    PPV("PPV"),
    TIMST("TIMST")
}
