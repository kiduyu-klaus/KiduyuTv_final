package com.kiduyuk.klausk.kiduyutv.data.model

/**
 * Data class representing a channel from the enriched CSV.
 * Parsed from lists/enriched_channels.csv
 *
 * @property name The display name of the channel (channel_name)
 * @property id The unique channel ID (channel_id)
 * @property streamUrl The direct iframe URL for playback (stream_url)
 * @property logoUrl The channel logo/thumbnail URL (logo_url)
 * @property categories Comma-separated category string (categories)
 * @property country Country code (country)
 * @property isLive Whether the channel is live (is_live)
 */
data class CsvChannel(
    val name: String,
    val id: String,
    val streamUrl: String,
    val logoUrl: String?,
    val categories: String?,
    val country: String?,
    val isLive: Boolean
) {
    val thumbnailUrl: String? get() = logoUrl
}
