package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.*
import com.kiduyuk.klausk.kiduyutv.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Schedule screen
 *
 * @property isLoading Loading state for schedule fetch
 * @property scheduleDays List of schedule days with events
 * @property expandedEventIds Set of expanded event IDs
 * @property selectedChannel Channel selected for playback
 * @property error Error message if fetch failed
 * @property isRefreshing Whether pull-to-refresh is active
 * @property searchQuery Current search query
 * @property searchResults Search results filtered by query
 */
data class ScheduleUiState(
    val isLoading: Boolean = true,
    val scheduleDays: List<ScheduleDay> = emptyList(),
    val expandedEventIds: Set<String> = emptySet(),
    val selectedChannel: ScheduleChannel? = null,
    val selectedEvent: ScheduleEvent? = null,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ScheduleEvent> = emptyList(),
    val isSearchActive: Boolean = false
)

/**
 * ViewModel for the Schedule screen
 * Manages schedule data fetching, event expansion, channel selection, and search
 */
class ScheduleViewModel : ViewModel() {

    private val repository = ScheduleRepository.getInstance()

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null

    /**
     * Initializes the ViewModel with application context
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Loads schedule data from dlhd.pk
     *
     * @param forceRefresh If true, bypasses cache
     */
    fun loadSchedule(forceRefresh: Boolean = false) {
        val context = appContext ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.fetchSchedule(context, forceRefresh).fold(
                onSuccess = { schedule ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scheduleDays = schedule,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load schedule"
                    )
                }
            )
        }
    }

    /**
     * Refreshes schedule data (pull-to-refresh)
     */
    fun refresh() {
        val context = appContext ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            repository.fetchSchedule(context, forceRefresh = true).fold(
                onSuccess = { schedule ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        scheduleDays = schedule,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = error.message ?: "Failed to refresh schedule"
                    )
                }
            )
        }
    }

    /**
     * Toggles event expansion state
     *
     * @param eventId The event ID to toggle
     */
    fun toggleEventExpansion(eventId: String) {
        val currentExpanded = _uiState.value.expandedEventIds
        val newExpanded = if (currentExpanded.contains(eventId)) {
            currentExpanded - eventId
        } else {
            currentExpanded + eventId
        }

        _uiState.value = _uiState.value.copy(expandedEventIds = newExpanded)
    }

    /**
     * Checks if an event is expanded
     *
     * @param eventId The event ID to check
     * @return True if the event is expanded
     */
    fun isEventExpanded(eventId: String): Boolean {
        return _uiState.value.expandedEventIds.contains(eventId)
    }

    /**
     * Expands a specific event
     *
     * @param eventId The event ID to expand
     */
    fun expandEvent(eventId: String) {
        val currentExpanded = _uiState.value.expandedEventIds
        if (!currentExpanded.contains(eventId)) {
            _uiState.value = _uiState.value.copy(
                expandedEventIds = currentExpanded + eventId
            )
        }
    }

    /**
     * Collapses a specific event
     *
     * @param eventId The event ID to collapse
     */
    fun collapseEvent(eventId: String) {
        val currentExpanded = _uiState.value.expandedEventIds
        if (currentExpanded.contains(eventId)) {
            _uiState.value = _uiState.value.copy(
                expandedEventIds = currentExpanded - eventId
            )
        }
    }

    /**
     * Collapses all expanded events
     */
    fun collapseAllEvents() {
        _uiState.value = _uiState.value.copy(expandedEventIds = emptySet())
    }

    /**
     * Selects a channel for playback
     *
     * @param channel The channel to select
     * @param event The event containing the channel
     */
    fun selectChannel(channel: ScheduleChannel, event: ScheduleEvent) {
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            selectedEvent = event
        )
    }

    /**
     * Clears the selected channel
     */
    fun clearSelectedChannel() {
        _uiState.value = _uiState.value.copy(
            selectedChannel = null,
            selectedEvent = null
        )
    }

    /**
     * Gets all unique categories from the schedule
     *
     * @return List of category names
     */
    fun getAllCategories(): List<String> {
        return _uiState.value.scheduleDays
            .flatMap { day -> day.categories.map { it.name } }
            .distinct()
            .sorted()
    }

    /**
     * Gets events filtered by category
     *
     * @param categoryName Category name to filter by
     * @return List of events in the category
     */
    fun getEventsByCategory(categoryName: String): List<ScheduleEvent> {
        return _uiState.value.scheduleDays
            .flatMap { day -> day.categories }
            .filter { it.name.contains(categoryName, ignoreCase = true) }
            .flatMap { it.events }
    }

    /**
     * Activates search mode
     */
    fun activateSearch() {
        _uiState.value = _uiState.value.copy(
            isSearchActive = true,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    /**
     * Deactivates search mode
     */
    fun deactivateSearch() {
        _uiState.value = _uiState.value.copy(
            isSearchActive = false,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    /**
     * Updates search query and filters events
     *
     * @param query The search query string
     */
    fun updateSearchQuery(query: String) {
        val context = appContext ?: return

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                searchResults = emptyList()
            )
            return
        }

        viewModelScope.launch {
            val results = repository.searchEvents(context, query)

            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                searchResults = results
            )
        }
    }

    /**
     * Gets iframe HTML for playing a channel
     *
     * @param channelId Channel ID
     * @param playerUrl Optional player URL (uses default if null)
     * @return HTML string for WebView
     */
    fun getIframeHtml(channelId: String, playerUrl: String? = null): String {
        val baseUrl = "https://dlhd.pk"
        val url = playerUrl ?: "$baseUrl/player/stream-$channelId.php"
        return repository.generateIframeHtml(url)
    }

    /**
     * Gets the full watch URL for a channel
     *
     * @param channel The schedule channel
     * @return Full watch URL
     */
    fun getWatchUrl(channel: ScheduleChannel): String {
        return channel.watchUrl
    }

    /**
     * Gets total event count
     *
     * @return Total number of events across all days
     */
    fun getTotalEventCount(): Int {
        return _uiState.value.scheduleDays
            .flatMap { it.categories }
            .sumOf { it.events.size }
    }

    /**
     * Gets upcoming events (next 24 hours based on display time)
     *
     * @return List of upcoming events
     */
    fun getUpcomingEvents(): List<ScheduleEvent> {
        return _uiState.value.scheduleDays
            .take(1) // Only first day for "upcoming"
            .flatMap { day -> day.categories }
            .filter { it.name.contains("Upcoming", ignoreCase = true) ||
                       !it.name.contains("FIFA", ignoreCase = true) }
            .flatMap { it.events }
            .take(5)
    }

    /**
     * Clears all cached data
     *
     * @param context Application context
     */
    fun clearCache(context: Context) {
        repository.clearCache(context)
        _uiState.value = _uiState.value.copy(
            scheduleDays = emptyList(),
            expandedEventIds = emptySet()
        )
    }
}