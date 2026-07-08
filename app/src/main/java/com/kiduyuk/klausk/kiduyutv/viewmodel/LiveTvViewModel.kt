package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.EpgProgram
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import com.kiduyuk.klausk.kiduyutv.data.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

    private val _favoriteChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val favoriteChannels = _favoriteChannels.asStateFlow()
    
    private var cachedPlaylist: IptvPlaylist? = null
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val PREFS_NAME = "live_tv_prefs"
    private val FAVORITES_KEY = "favorite_channels"
    
    // Debounce search to prevent excessive recompositions and main thread work
    private val searchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    
    /**
     * Initializes the ViewModel with application context for caching.
     * Call this in the Composable with rememberUpdatedState or via LaunchedEffect.
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refreshFavoriteChannels()
    }

    private fun refreshFavoriteChannels() {
        _favoriteChannels.value = getFavoriteChannels()
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
        _favoriteChannels.value = channels
    }

    /**
     * Adds channel to favorites if not already present and syncs to Firebase.
     * Performs bidirectional sync to ensure both SharedPreferences and Firebase have the same channels.
     */
    fun addFavorite(channel: IptvChannel) {
        viewModelScope.launch {
            try {
                // Get current local favorites
                val localFavorites = getFavoriteChannels().toMutableList()
                
                // Check if already exists locally
                if (localFavorites.any { it.url == channel.url }) {
                    return@launch
                }
                
                // Add to local list (at the beginning)
                localFavorites.add(0, channel)
                
                // Save to SharedPreferences
                saveFavoriteChannels(localFavorites)
                
                // Get Firebase favorites
                val firebaseFavorites = com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.getSavedChannelsOnce()
                val firebaseUrls = firebaseFavorites?.values?.mapNotNull { it as? Map<*, *> }?.map { it["url"] as? String }?.toSet() ?: emptySet()
                
                // Perform bidirectional sync
                // 1. Add missing channels from SharedPreferences to Firebase
                val key = Base64.encodeToString(channel.url.toByteArray(), Base64.NO_WRAP)
                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.saveChannel(
                    key = key,
                    name = channel.name,
                    logo = channel.logo,
                    url = channel.url,
                    group = channel.group
                )
                
                // 2. Add missing channels from Firebase to SharedPreferences
                if (firebaseFavorites != null) {
                    val localUrls = localFavorites.map { it.url }.toSet()
                    val newLocalFavorites = localFavorites.toMutableList()
                    
                    firebaseFavorites.values.forEach { value ->
                        if (value is Map<*, *>) {
                            val fbUrl = value["url"] as? String
                            if (fbUrl != null && !localUrls.contains(fbUrl)) {
                                // Channel exists in Firebase but not in SharedPreferences
                                val existingChannel = localFavorites.find { it.url == fbUrl }
                                if (existingChannel == null) {
                                    // Add the missing channel to SharedPreferences
                                    newLocalFavorites.add(IptvChannel(
                                        name = value["name"] as? String ?: "",
                                        logo = value["logo"] as? String,
                                        url = fbUrl,
                                        group = value["group"] as? String
                                    ))
                                }
                            }
                        }
                    }
                    
                    // Only update if we added new channels
                    if (newLocalFavorites.size > localFavorites.size) {
                        saveFavoriteChannels(newLocalFavorites)
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("LiveTvViewModel", "Error adding favorite", e)
            }
        }
    }

    /**
     * Removes a channel from favorites locally and from Firebase.
     * Performs bidirectional sync to ensure both SharedPreferences and Firebase have the same channels.
     */
    fun removeFavorite(channel: IptvChannel) {
        viewModelScope.launch {
            try {
                // Get current local favorites
                val localFavorites = getFavoriteChannels().toMutableList()
                
                // Remove from local list
                localFavorites.removeAll { it.url == channel.url }
                
                // Save updated list to SharedPreferences
                saveFavoriteChannels(localFavorites)
                
                // Remove from Firebase
                val key = Base64.encodeToString(channel.url.toByteArray(), Base64.NO_WRAP)
                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.removeSavedChannel(key)
                
                // Perform bidirectional sync
                // Get Firebase favorites and add any channels that are in Firebase but not in SharedPreferences
                val firebaseFavorites = com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.getSavedChannelsOnce()
                if (firebaseFavorites != null) {
                    val localUrls = localFavorites.map { it.url }.toSet()
                    val updatedLocalFavorites = localFavorites.toMutableList()
                    var hasNewChannels = false
                    
                    firebaseFavorites.values.forEach { value ->
                        if (value is Map<*, *>) {
                            val fbUrl = value["url"] as? String
                            if (fbUrl != null && !localUrls.contains(fbUrl) && fbUrl != channel.url) {
                                // Channel exists in Firebase but not in SharedPreferences (and it's not the one we just removed)
                                val newChannel = IptvChannel(
                                    name = value["name"] as? String ?: "",
                                    logo = value["logo"] as? String,
                                    url = fbUrl,
                                    group = value["group"] as? String
                                )
                                updatedLocalFavorites.add(newChannel)
                                hasNewChannels = true
                                
                                // Add to Firebase (in case it was somehow missing)
                                val fbKey = Base64.encodeToString(fbUrl.toByteArray(), Base64.NO_WRAP)
                                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.saveChannel(
                                    key = fbKey,
                                    name = newChannel.name,
                                    logo = newChannel.logo,
                                    url = newChannel.url,
                                    group = newChannel.group
                                )
                            }
                        }
                    }
                    
                    // Only update if we found new channels from Firebase
                    if (hasNewChannels) {
                        saveFavoriteChannels(updatedLocalFavorites)
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("LiveTvViewModel", "Error removing favorite", e)
            }
        }
    }

    fun isFavorite(channel: IptvChannel): Boolean {
        return getFavoriteChannels().any { it.url == channel.url }
    }

    /**
     * Clears all favorite channels from local storage only (does not affect Firebase).
     */
    fun clearAllLocalFavorites() {
        prefs?.edit()?.putString(FAVORITES_KEY, "[]")?.apply()
        _favoriteChannels.value = emptyList()
    }
    
    /**
     * Syncs favorite channels bidirectionally with Firebase.
     * Call this when user explicitly requests refresh of their favorite channels.
     * Implements two-way sync:
     * 1. Downloads favorites from Firebase
     * 2. Merges with local favorites
     * 3. Uploads merged list back to Firebase
     * 4. Updates local storage
     */
    fun syncFavoriteChannelsWithFirebase() {
        viewModelScope.launch {
            try {
                val context = appContext ?: return@launch
                
                // 1. Download from Firebase
                val cloudFavorites = mutableListOf<IptvChannel>()
                val firebaseData = com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.getSavedChannelsOnce()
                
                if (firebaseData != null && firebaseData.isNotEmpty()) {
                    firebaseData.forEach { (_, value) ->
                        if (value is Map<*, *>) {
                            val channel = IptvChannel(
                                name = value["name"] as? String ?: "",
                                url = value["url"] as? String ?: "",
                                logo = value["logo"] as? String ?: "",
                                tvgId = value["tvgId"] as? String ?: "",
                                tvgName = value["tvgName"] as? String ?: "",
                                group = value["group"] as? String ?: ""
                            )
                            cloudFavorites.add(channel)
                        }
                    }
                }
                
                // 2. Get local favorites
                val localFavorites = getFavoriteChannels().toMutableList()
                
                // 3. Merge (cloud + local-only)
                val merged = mutableListOf<IptvChannel>()
                val seenUrls = mutableSetOf<String>()
                
                // Add cloud first
                cloudFavorites.forEach { fav ->
                    if (!seenUrls.contains(fav.url)) {
                        merged.add(fav)
                        seenUrls.add(fav.url)
                    }
                }
                
                // Add local-only (not in cloud)
                localFavorites.forEach { localFav ->
                    if (!seenUrls.contains(localFav.url)) {
                        merged.add(localFav)
                        seenUrls.add(localFav.url)
                    }
                }
                
                // 4. Clear Firebase and re-upload merged
                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.clearSavedChannels()
                merged.forEach { channel ->
                    val key = Base64.encodeToString(channel.url.toByteArray(), Base64.NO_WRAP)
                    com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.saveChannel(
                        key = key,
                        name = channel.name,
                        logo = channel.logo,
                        url = channel.url,
                        group = channel.group
                    )
                }
                
                // 5. Update local storage
                saveFavoriteChannels(merged)
                
                android.util.Log.i("LiveTvViewModel", "Synced ${merged.size} favorite channels with Firebase")
            } catch (e: Exception) {
                android.util.Log.e("LiveTvViewModel", "Error syncing favorites with Firebase", e)

            }
        }
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
     * Updates search query with debouncing to prevent excessive recompositions.
     * The actual filtering is done off the main thread using withContext(Dispatchers.Default).
     *
     * @param query The search query string
     */
    fun updateSearchQuery(query: String) {
        // Update the query state immediately for responsive UI
        _uiState.update { it.copy(searchQuery = query) }
        
        // Cancel any existing search job
        searchJob?.cancel()
        
        // If query is blank, clear results immediately
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        
        // Debounce and filter off the main thread
        searchJob = viewModelScope.launch {
            // Brief debounce to prevent excessive work while typing
            kotlinx.coroutines.delay(150)
            
            // Filter channels off the main thread
            val allChannels = cachedPlaylist?.allChannels ?: emptyList()
            val results = withContext(Dispatchers.Default) {
                if (query.isBlank()) {
                    emptyList()
                } else {
                    allChannels.filter { channel ->
                        channel.name.contains(query, ignoreCase = true) ||
                        channel.group?.contains(query, ignoreCase = true) == true
                    }
                }
            }
            
            // Update results (these are cheap - just StateFlow updates)
            _uiState.update { it.copy(searchResults = results) }
        }
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
