package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.ChannelProgramInfo
import com.kiduyuk.klausk.kiduyutv.data.model.EpgProgram
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import com.kiduyuk.klausk.kiduyutv.data.repository.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Live TV screen.
 *
 * @param isLoading Loading state for initial playlist fetch
 * @param isEpgLoading Loading state for EPG guide
 * @param categories List of available categories
 * @param selectedCategory Currently selected category
 * @param channels Channels in the selected category
 * @param selectedChannel Currently selected channel for playback
 * @param error Error message if playlist fetch failed
 * @param searchQuery Current search query string
 * @param searchResults Search results filtered by query
 * @param isSearchActive Whether search mode is currently active
 * @param currentProgram Current program info for selected channel
 */
data class LiveTvUiState(
    val isLoading: Boolean = true,
    val isEpgLoading: Boolean = false,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: String? = null,
    val channels: List<IptvChannel> = emptyList(),
    val selectedChannel: IptvChannel? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<IptvChannel> = emptyList(),
    val isSearchActive: Boolean = false,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null
)

/**
 * Represents a category item for display in the UI.
 *
 * @param name Category name
 * @param channelCount Number of channels in this category
 */
data class CategoryItem(
    val name: String,
    val channelCount: Int
)

/**
 * ViewModel for the Live TV screen.
 * Manages playlist fetching, category selection, channel browsing, and EPG loading.
 */
class LiveTvViewModel : ViewModel() {
    
    private val repository = IptvRepository.getInstance()
    
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()
    
    private var cachedPlaylist: IptvPlaylist? = null
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val PREFS_NAME = "live_tv_prefs"
    private val FAVORITES_KEY = "favorite_channels"
    
    /**
     * Initializes the ViewModel with application context for caching.
     * Call this in the Composable with rememberUpdatedState or via LaunchedEffect.
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get favorite channels saved locally (SharedPreferences JSON array).
     */
    fun getFavoriteChannels(): List<IptvChannel> {
        val json = prefs?.getString(FAVORITES_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<IptvChannel>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val logo = obj.optString("logo", null)
                val url = obj.optString("url")
                val group = obj.optString("group", null)
                list.add(IptvChannel(name = name, logo = if (logo.isNullOrBlank()) null else logo, url = url, group = if (group.isNullOrBlank()) null else group))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavoriteChannels(channels: List<IptvChannel>) {
        val arr = JSONArray()
        channels.forEach { ch ->
            val obj = JSONObject()
            obj.put("name", ch.name)
            obj.put("logo", ch.logo)
            obj.put("url", ch.url)
            obj.put("group", ch.group)
            arr.put(obj)
        }
        prefs?.edit()?.putString(FAVORITES_KEY, arr.toString())?.apply()
    }

    /**
     * Adds channel to favorites if not already present and syncs to Firebase.
     */
    fun addFavorite(channel: IptvChannel) {
        val current = getFavoriteChannels().toMutableList()
        if (current.any { it.url == channel.url }) return
        current.add(0, channel)
        saveFavoriteChannels(current)

        // Sync to Firebase savedChannels node if FirebaseManager initialized
        try {
            val key = Base64.encodeToString(channel.url.toByteArray(), Base64.NO_WRAP)
            com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.saveChannel(
                key = key,
                name = channel.name,
                logo = channel.logo,
                url = channel.url,
                group = channel.group
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Removes a channel from favorites locally and from Firebase.
     */
    fun removeFavorite(channel: IptvChannel) {
        val current = getFavoriteChannels().filter { it.url != channel.url }
        saveFavoriteChannels(current)
        try {
            val key = Base64.encodeToString(channel.url.toByteArray(), Base64.NO_WRAP)
            com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.removeSavedChannel(key)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun isFavorite(channel: IptvChannel): Boolean {
        return getFavoriteChannels().any { it.url == channel.url }
    }
    
    /**
     * Loads the IPTV playlist from the remote server or cache.
     *
     * @param forceRefresh If true, bypasses cache and fetches from network
     */
    fun loadPlaylist(forceRefresh: Boolean = false) {
        val context = appContext ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.fetchPlaylist(context, forceRefresh).fold(
                onSuccess = { playlist ->
                    cachedPlaylist = playlist
                    val categoryItems = playlist.categories.keys.map { name ->
                        CategoryItem(
                            name = name,
                            channelCount = playlist.categories[name]?.size ?: 0
                        )
                    }.sortedBy { it.name }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        categories = categoryItems,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load playlist"
                    )
                }
            )
        }
    }

    /**
     * Loads EPG guide data for program information.
     * Should be called when channel is selected for playback.
     *
     * @param channel The channel to load EPG for
     */
    fun loadEpgForChannel(channel: IptvChannel) {
        val context = appContext ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEpgLoading = true)

            repository.getChannelProgramInfo(channel, context).let { programInfo ->
                _uiState.value = _uiState.value.copy(
                    isEpgLoading = false,
                    currentProgram = programInfo.currentProgram,
                    nextProgram = programInfo.nextProgram
                )
            }
        }
    }

    /**
     * Loads EPG guide data for all channels.
     * Call on app startup to pre-cache EPG data.
     *
     * @param forceRefresh If true, bypasses cache and fetches from network
     */
    fun loadEpg(forceRefresh: Boolean = false) {
        val context = appContext ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEpgLoading = true)

            repository.fetchEpg(context, forceRefresh).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isEpgLoading = false)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isEpgLoading = false)
                }
            )
        }
    }
    
    /**
     * Selects a category and loads its channels.
     *
     * @param categoryName The name of the category to select
     */
    fun selectCategory(categoryName: String) {
        cachedPlaylist?.let { playlist ->
            val channels = playlist.categories[categoryName] ?: emptyList()
            _uiState.value = _uiState.value.copy(
                selectedCategory = categoryName,
                channels = channels,
                selectedChannel = null,
                currentProgram = null,
                nextProgram = null
            )
        }
    }
    
    /**
     * Clears the category selection and returns to categories view.
     */
    fun clearCategorySelection() {
        _uiState.value = _uiState.value.copy(
            selectedCategory = null,
            channels = emptyList(),
            selectedChannel = null,
            currentProgram = null,
            nextProgram = null
        )
    }
    
    /**
     * Selects a channel for playback and loads its EPG.
     *
     * @param channel The channel to play
     */
    fun selectChannel(channel: IptvChannel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
        loadEpgForChannel(channel)
    }
    
    /**
     * Clears the selected channel.
     */
    fun clearSelectedChannel() {
        _uiState.value = _uiState.value.copy(
            selectedChannel = null,
            currentProgram = null,
            nextProgram = null
        )
    }

    /**
     * Gets the current program for the selected channel.
     *
     * @return Current EpgProgram or null
     */
    fun getCurrentProgram(): EpgProgram? {
        return _uiState.value.currentProgram
    }

    /**
     * Gets the next program for the selected channel.
     *
     * @return Next EpgProgram or null
     */
    fun getNextProgram(): EpgProgram? {
        return _uiState.value.nextProgram
    }
    
    /**
     * Gets all channels for search across entire playlist.
     *
     * @return List of all channels in the playlist
     */
    fun getAllChannels(): List<IptvChannel> {
        return cachedPlaylist?.allChannels ?: emptyList()
    }

    /**
     * Clears in-memory cached playlist (for testing or forced refresh).
     */
    fun clearMemoryCache() {
        cachedPlaylist = null
    }

    /**
     * Clears all cached data (both memory and disk).
     *
     * @param context Application context for disk operations
     */
    fun clearCache(context: Context) {
        cachedPlaylist = null
        repository.clearCache(context)
        repository.clearEpgCache(context)
    }

    /**
     * Activates search mode.
     */
    fun activateSearch() {
        _uiState.value = _uiState.value.copy(
            isSearchActive = true,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    /**
     * Deactivates search mode and clears search state.
     */
    fun deactivateSearch() {
        _uiState.value = _uiState.value.copy(
            isSearchActive = false,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    /**
     * Updates search query and filters channels.
     *
     * @param query The search query string
     */
    fun updateSearchQuery(query: String) {
        val allChannels = cachedPlaylist?.allChannels ?: emptyList()
        val results = if (query.isBlank()) {
            emptyList()
        } else {
            allChannels.filter { channel ->
                channel.name.contains(query, ignoreCase = true) ||
                channel.group?.contains(query, ignoreCase = true) == true
            }
        }

        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchResults = results
        )
    }

    /**
     * Gets total channel count across all categories.
     *
     * @return Total number of channels
     */
    fun getTotalChannelCount(): Int {
        return cachedPlaylist?.allChannels?.size ?: 0
    }
}