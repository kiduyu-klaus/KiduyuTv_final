package com.kiduyuk.klausk.kiduyutv.data.model

/**
 * Represents a TV channel category from the IPTV playlist.
 *
 * @param name The name of the category (e.g., "Sports", "Movies", "News")
 * @param channels The list of channels belonging to this category
 */
data class IptvCategory(
    val name: String,
    val channels: List<IptvChannel> = emptyList()
)

/**
 * Represents a single TV channel from the IPTV playlist.
 *
 * @param name The name of the channel
 * @param logo The URL of the channel's logo image
 * @param url The streaming URL for the channel
 * @param group The category/group the channel belongs to
 * @param tvgId The TVG ID for EPG integration
 * @param tvgName The TVG name for EPG integration
 */
data class IptvChannel(
    val name: String,
    val logo: String?,
    val url: String,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null
) {
    /**
     * Unique identifier for use as key in LazyColumn/LazyGrid.
     * Uses tvgId if available, otherwise generates a hash from name+url.
     */
    val id: String get() = tvgId ?: "${name}_${url}".hashCode().toString()
}

/**
 * Represents the parsed IPTV playlist data.
 *
 * @param categories Map of category names to their channels
 * @param allChannels Flat list of all channels (useful for "All" category)
 */
data class IptvPlaylist(
    val categories: Map<String, List<IptvChannel>>,
    val allChannels: List<IptvChannel>
)

// ================================================================
// EPG (Electronic Program Guide) Models
// Source: XMLTV format from lg_epg_us.xml
// ================================================================

/**
 * Represents the EPG guide data containing channel programs.
 *
 * @param channels Map of channel ID (tvg-id) to list of programs
 */
data class EpgGuide(
    val channels: Map<String, List<EpgProgram>>
)

/**
 * Represents a single TV program in the EPG.
 *
 * @param channelId The tvg-id of the channel this program belongs to
 * @param title Program title
 * @param startTime Program start time (Unix timestamp milliseconds)
 * @param endTime Program end time (Unix timestamp milliseconds)
 * @param description Program description/synopsis
 * @param category Program category (e.g., "Sports", "News", "Movie")
 * @param icon URL of program thumbnail/cover image
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String? = null,
    val category: String? = null,
    val icon: String? = null
) {
    /**
     * Checks if the program is currently airing.
     */
    fun isAiring(): Boolean {
        val now = System.currentTimeMillis()
        return now in startTime..endTime
    }

    /**
     * Gets the duration of the program in minutes.
     */
    fun durationMinutes(): Long {
        return (endTime - startTime) / 60_000
    }

    /**
     * Gets progress through the program (0.0 to 1.0).
     */
    fun progress(): Float {
        val now = System.currentTimeMillis()
        if (now < startTime) return 0f
        if (now > endTime) return 1f
        return ((now - startTime).toFloat() / (endTime - startTime)).coerceIn(0f, 1f)
    }
}

/**
 * Represents current program info for a channel.
 *
 * @param channel The IPTV channel
 * @param currentProgram The currently airing program (if any)
 * @param nextProgram The next program (if any)
 */
data class ChannelProgramInfo(
    val channel: IptvChannel,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null
)