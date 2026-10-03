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
 * @param tvgId The TVG ID from playlist metadata
 * @param tvgName The TVG name from playlist metadata
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

/** A country entry discovered from the world_ip_tv `country/` directory. */
data class CountryPlaylistCategory(
    val countryCode: String,
    val displayName: String,
    val flagUrl: String,
    val flagFallbackUrl: String,
    val playlists: List<CountryPlaylist>
)

/** One country or regional M3U file, for example `us.m3u` or `us-ny.m3u`. */
data class CountryPlaylist(
    val countryCode: String,
    val regionCode: String?,
    val displayName: String,
    val url: String
)
