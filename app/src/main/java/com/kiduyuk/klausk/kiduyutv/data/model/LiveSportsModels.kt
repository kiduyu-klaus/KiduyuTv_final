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
    /** Named feeds supplied by the source. */
    val streams: List<LiveSportsStream>,
    /** Fallback embed used only when the source returned no stream URLs. */
    val embedUrl: String,
    val fallbackStreamName: String = ""
) {
    /** Preserve every supplied stream as Schedule Player servers, or use the embed fallback. */
    val playbackStreams: List<LiveSportsStream>
        get() = streams.ifEmpty { listOf(LiveSportsStream(fallbackStreamName, embedUrl)) }
            .filter { it.url.startsWith("https://", ignoreCase = true) || it.url.startsWith("http://", ignoreCase = true) }
            .distinctBy { it.url }

    val playbackUrls: List<String> get() = playbackStreams.map { it.url }
    val playbackNames: List<String> get() = playbackStreams.mapIndexed { index, stream ->
        stream.name.ifBlank { "Server ${index + 1}" }
    }
}

data class LiveSportsStream(
    val name: String,
    val url: String
)

enum class LiveSportsSource(val tabLabel: String) {
    PPV("PPV"),
    TIMST("TIMST")
}
