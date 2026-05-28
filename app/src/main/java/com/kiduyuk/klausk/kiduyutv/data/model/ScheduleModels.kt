package com.kiduyuk.klausk.kiduyutv.data.model

/**
 * Represents a schedule event from dlhd.pk
 *
 * @property id Unique identifier for the event
 * @property title Event title (e.g., "French Open Tennis", "FIFA 2026 — Mexico vs South Africa")
 * @property time Event time (e.g., "15:00")
 * @property displayTime Display time with timezone (e.g., "18:00")
 * @property dataTitle Raw data-title attribute from HTML
 * @property channels List of channels broadcasting this event
 */
data class ScheduleEvent(
    val id: String,
    val title: String,
    val time: String,
    val displayTime: String,
    val dataTitle: String,
    val channels: List<ScheduleChannel>
)

/**
 * Represents a channel that broadcasts a schedule event
 *
 * @property id Channel ID (e.g., "772")
 * @property name Channel name (e.g., "Eurosport 1 France")
 * @property dataCh Channel data attribute from HTML
 * @property watchUrl Full watch URL for the channel
 */
data class ScheduleChannel(
    val id: String,
    val name: String,
    val dataCh: String,
    val watchUrl: String
)

/**
 * Represents a schedule category (e.g., "Upcoming Events", "FIFA World Cup 2026")
 *
 * @property name Category name
 * @property events List of events in this category
 */
data class ScheduleCategory(
    val name: String,
    val events: List<ScheduleEvent>
)

/**
 * Represents a complete schedule day
 *
 * @property dateTitle Day title (e.g., "Wednesday 27th May 2026 - Schedule Time UK GMT")
 * @property categories List of categories for this day
 */
data class ScheduleDay(
    val dateTitle: String,
    val categories: List<ScheduleCategory>
)

/**
 * Represents player options for a channel
 *
 * @property playerNumber Player number (1-6)
 * @property url Player stream URL
 * @property isActive Whether this player is selected by default
 */
data class PlayerOption(
    val playerNumber: Int,
    val url: String,
    val isActive: Boolean = false
)

/**
 * Represents channel watch page with player options and iframe
 *
 * @property channelId Channel ID
 * @property channelName Channel name
 * @property playerOptions Available player options
 * @property defaultIframeUrl Default iframe URL
 */
data class ChannelWatchPage(
    val channelId: String,
    val channelName: String,
    val playerOptions: List<PlayerOption>,
    val defaultIframeUrl: String
)