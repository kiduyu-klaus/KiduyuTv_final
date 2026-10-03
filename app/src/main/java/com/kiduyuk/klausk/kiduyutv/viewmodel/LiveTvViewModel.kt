package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.CountryPlaylist
import com.kiduyuk.klausk.kiduyutv.data.model.CountryPlaylistCategory
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import com.kiduyuk.klausk.kiduyutv.data.model.is18PlusChannel
import com.kiduyuk.klausk.kiduyutv.data.repository.IptvRepository
import com.kiduyuk.klausk.kiduyutv.data.repository.WorldIptvRepository
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
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
    val categories: List<CategoryItem> = emptyList(),
    val countryCategories: List<CountryPlaylistCategory> = emptyList(),
    val selectedCountry: CountryPlaylistCategory? = null,
    val lastSelectedCountryCode: String? = null,
    val playlistChoices: List<CountryPlaylist> = emptyList(),
    val selectedCategory: String? = null,
    val channels: List<IptvChannel> = emptyList(),
    val selectedChannel: IptvChannel? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<IptvChannel> = emptyList(),
    val isSearchActive: Boolean = false,
    val isCountryPlaylistRefreshing: Boolean = false,
    val countryPlaylistRefreshProgress: Float? = null,
    val hide18PlusChannels: Boolean = false
)

/**
 * Represents a category item for display in the UI.
 *
 * @param name Category name
 * @param channelCount Number of channels in this category
 */
data class CategoryItem(
    val name: String,
    val channelCount: Int,
    val countryCode: String? = null,
    val flagUrl: String? = null
)

/**
 * ViewModel for the Live TV screen.
 * Manages playlist fetching, category selection, channel browsing, and favorites.
 */
class LiveTvViewModel : ViewModel() {
    
    private val repository = IptvRepository.getInstance()
    private val worldIptvRepository = WorldIptvRepository()
    
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val _favoriteChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val favoriteChannels = _favoriteChannels.asStateFlow()

    private val _scrapedChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val scrapedChannels = _scrapedChannels.asStateFlow()
    
    private var cachedPlaylist: IptvPlaylist? = null
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val PREFS_NAME = "live_tv_prefs"
    private val FAVORITES_KEY = "favorite_channels"
    private val SCRAPED_CHANNELS_KEY = "saved_scraped_channels"
    
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
        _uiState.update {
            it.copy(hide18PlusChannels = SettingsManager(context).isHide18PlusChannelsEnabled())
        }
        refreshFavoriteChannels()
        refreshScrapedChannels()
    }

    /** Updates the Live TV visibility preference and reapplies it to the current channel list. */
    fun setHide18PlusChannels(enabled: Boolean) {
        appContext?.let { SettingsManager(it).setHide18PlusChannels(enabled) }
        _uiState.update { state ->
            val baseChannels = state.selectedCategory?.let { category ->
                cachedPlaylist?.categories?.get(category)
            } ?: state.channels
            val baseSearchResults = if (state.searchQuery.isBlank()) {
                emptyList()
            } else {
                cachedPlaylist?.allChannels.orEmpty().filter { channel ->
                    channel.name.contains(state.searchQuery, ignoreCase = true) ||
                        channel.group?.contains(state.searchQuery, ignoreCase = true) == true
                }
            }
            state.copy(
                hide18PlusChannels = enabled,
                channels = visibleChannels(baseChannels, enabled),
                searchResults = visibleChannels(baseSearchResults, enabled)
            )
        }
    }

    private fun visibleChannels(
        channels: List<IptvChannel>,
        hide18PlusChannels: Boolean = _uiState.value.hide18PlusChannels
    ): List<IptvChannel> = if (hide18PlusChannels) {
        channels.filterNot(IptvChannel::is18PlusChannel)
    } else {
        channels
    }

    private fun refreshFavoriteChannels() {
        _favoriteChannels.value = getFavoriteChannels()
    }

    private fun refreshScrapedChannels() {
        _scrapedChannels.value = getScrapedChannels()
    }

    /**
     * Returns DaddyLive channels the viewer explicitly saved from the scraped
     * directory. These are intentionally separate from IPTV favorites because
     * the DaddyLive channel ID is required to rediscover the current servers.
     */
    fun getScrapedChannels(): List<IptvChannel> {
        val json = prefs?.getString(SCRAPED_CHANNELS_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val channels = mutableListOf<IptvChannel>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val channelId = item.optString("tvgId").takeIf { it.isNotBlank() } ?: continue
                val watchUrl = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                channels.add(
                    IptvChannel(
                        name = item.optString("name"),
                        logo = item.optString("logo").takeIf { it.isNotBlank() },
                        url = watchUrl,
                        group = item.optString("group").takeIf { it.isNotBlank() },
                        tvgId = channelId,
                        tvgName = item.optString("tvgName").takeIf { it.isNotBlank() }
                    )
                )
            }
            channels
        } catch (error: Exception) {
            android.util.Log.w("LiveTvViewModel", "Unable to read saved scraped channels", error)
            emptyList()
        }
    }

    private fun saveScrapedChannels(channels: List<IptvChannel>) {
        val array = JSONArray()
        channels.forEach { channel ->
            array.put(
                JSONObject().apply {
                    put("name", channel.name)
                    put("logo", channel.logo)
                    put("url", channel.url)
                    put("group", channel.group)
                    put("tvgId", channel.tvgId)
                    put("tvgName", channel.tvgName)
                }
            )
        }
        prefs?.edit()?.putString(SCRAPED_CHANNELS_KEY, array.toString())?.apply()
        _scrapedChannels.value = channels
    }

    /** Adds one DaddyLive channel to the local Scraped channels collection. */
    fun addScrapedChannel(channel: IptvChannel) {
        val channelId = channel.tvgId?.takeIf { it.all(Char::isDigit) } ?: return
        val watchUrl = channel.url.takeIf { it.isNotBlank() } ?: return
        val saved = getScrapedChannels().toMutableList()
        if (saved.any { it.tvgId == channelId || it.url == watchUrl }) return
        saved.add(
            0,
            channel.copy(
                url = watchUrl,
                tvgId = channelId,
                group = channel.group ?: "DaddyLive"
            )
        )
        saveScrapedChannels(saved)
    }

    fun isScrapedChannel(channel: IptvChannel): Boolean {
        val channelId = channel.tvgId
        return getScrapedChannels().any { saved ->
            saved.tvgId == channelId || saved.url == channel.url
        }
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
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                isCountryPlaylistRefreshing = forceRefresh,
                countryPlaylistRefreshProgress = if (forceRefresh) 0.1f else null
            )
            if (forceRefresh) {
                _uiState.update { it.copy(countryPlaylistRefreshProgress = 0.25f) }
            }
            
            worldIptvRepository.fetchCountryCategories(context, forceRefresh).fold(
                onSuccess = { countries ->
                    if (forceRefresh) {
                        _uiState.update { it.copy(countryPlaylistRefreshProgress = 0.2f) }

                        val refreshResults = worldIptvRepository.refreshAllPlaylists(countries) { completed, total ->
                            val playlistProgress = if (total == 0) 1f else completed.toFloat() / total
                            _uiState.update {
                                it.copy(
                                    countryPlaylistRefreshProgress =
                                        (0.2f + playlistProgress * 0.8f).coerceIn(0f, 1f)
                                )
                            }
                        }
                        val failedCount = refreshResults.count { it.isFailure }
                        if (failedCount > 0) {
                            android.util.Log.w(
                                "LiveTvViewModel",
                                "Country playlist refresh completed with $failedCount " +
                                    "failed playlist(s) out of ${refreshResults.size}"
                            )
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCountryPlaylistRefreshing = false,
                        countryPlaylistRefreshProgress = null,
                        categories = countries.map { country ->
                            CategoryItem(
                                name = country.displayName,
                                channelCount = country.playlists.size,
                                countryCode = country.countryCode,
                                flagUrl = country.flagUrl
                            )
                        },
                        countryCategories = countries,
                        selectedCountry = null,
                        lastSelectedCountryCode = null,
                        playlistChoices = emptyList(),
                        selectedCategory = null,
                        channels = emptyList(),
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCountryPlaylistRefreshing = false,
                        countryPlaylistRefreshProgress = null,
                        error = error.message ?: "Failed to load playlist"
                    )
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
            val channels = visibleChannels(playlist.categories[categoryName] ?: emptyList())
            _uiState.value = _uiState.value.copy(
                selectedCategory = categoryName,
                channels = channels,
                selectedChannel = null
            )
        }
    }

    /** Opens the playlist picker for a country, or loads its only playlist. */
    fun selectCountry(countryCode: String) {
        val country = _uiState.value.countryCategories
            .firstOrNull { it.countryCode.equals(countryCode, ignoreCase = true) }
            ?: return
        if (country.playlists.size == 1) {
            selectCountryPlaylist(country.playlists.first())
        } else {
            _uiState.update {
                it.copy(
                    selectedCountry = country,
                    lastSelectedCountryCode = country.countryCode,
                    playlistChoices = country.playlists,
                    selectedCategory = null,
                    channels = emptyList(),
                    searchQuery = "",
                    searchResults = emptyList()
                )
            }
            resolveRegionalPlaylistNames(country)
        }
    }

    /** Resolves names such as `br-sp` from the first M3U group-title without blocking the picker. */
    private fun resolveRegionalPlaylistNames(country: CountryPlaylistCategory) {
        viewModelScope.launch {
            val namedPlaylists = worldIptvRepository.resolveRegionalPlaylistNames(country.playlists)
            _uiState.update { state ->
                // Do not resurrect the picker after the viewer has selected a playlist or gone back.
                if (state.selectedCountry?.countryCode != country.countryCode ||
                    state.selectedCategory != null ||
                    state.playlistChoices.isEmpty()
                ) {
                    state
                } else {
                    val updatedCountry = country.copy(playlists = namedPlaylists)
                    state.copy(
                        countryCategories = state.countryCategories.map { listedCountry ->
                            if (listedCountry.countryCode == country.countryCode) updatedCountry else listedCountry
                        },
                        selectedCountry = updatedCountry,
                        playlistChoices = namedPlaylists
                    )
                }
            }
        }
    }

    /** Fetches and parses only the regional/country playlist selected by the viewer. */
    fun selectCountryPlaylist(playlist: CountryPlaylist) {
        val country = _uiState.value.countryCategories
            .firstOrNull { it.countryCode.equals(playlist.countryCode, ignoreCase = true) }
            ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            worldIptvRepository.fetchPlaylist(playlist).fold(
                onSuccess = { parsed ->
                    cachedPlaylist = parsed
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedCountry = country,
                            lastSelectedCountryCode = country.countryCode,
                            playlistChoices = emptyList(),
                            selectedCategory = playlist.displayName,
                            channels = visibleChannels(parsed.allChannels),
                            selectedChannel = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load country playlist"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Clears the category selection and returns to categories view.
     */
    fun clearCategorySelection() {
        _uiState.value = _uiState.value.copy(
            selectedCountry = null,
            playlistChoices = emptyList(),
            selectedCategory = null,
            channels = emptyList(),
            selectedChannel = null,
        )
    }
    
    /** Selects a channel for playback. */
    fun selectChannel(channel: IptvChannel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
    }

    /** Clears the selected channel. */
    fun clearSelectedChannel() {
        _uiState.value = _uiState.value.copy(selectedChannel = null)
    }

    /**
     * Gets all channels for search across entire playlist.
     *
     * @return List of all channels in the playlist
     */
    fun getAllChannels(): List<IptvChannel> {
        return visibleChannels(cachedPlaylist?.allChannels ?: emptyList())
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
            val allChannels = visibleChannels(cachedPlaylist?.allChannels ?: emptyList())
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
        return getAllChannels().size
    }
}
