# Favorite Channels Sync Guide

## Overview

This guide explains how to implement a feature that updates favorite channels from the current PLAYLIST_URL to prevent favorites from becoming outdated when channel URLs or details change. The solution includes a dedicated settings section where users can manually refresh their favorite channels.

## Problem Statement

The current implementation stores favorite channels with their URLs. However, when the IPTV playlist is updated:

- Channel names may change
- Channel URLs may change
- Channels may be removed or replaced
- Logo URLs may be updated

This means favorite channels saved with outdated information can become broken or show incorrect data.

## Solution Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Interface                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │  Live TV Screen │  │  Settings Screen│  │  Sync Dialog    │  │
│  │  (Favorites)    │  │  (Update Button)│  │  (Progress)     │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                    │           │
└───────────┼────────────────────┼────────────────────┼───────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ViewModel Layer                            │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              FavoriteChannelsSyncManager                 │    │
│  │  - updateFavoritesFromPlaylist()                        │    │
│  │  - validateFavoriteChannels()                           │    │
│  │  - getStaleFavoriteChannels()                           │    │
│  │  - getFavoriteChannelsWithUpdates()                      │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Repository Layer                             │
│  ┌─────────────────────┐  ┌─────────────────────────────────┐   │
│  │   IptvRepository    │  │    FirebaseSyncManager          │   │
│  │   - fetchPlaylist() │  │    - saveChannel()              │   │
│  │   - getChannel()    │  │    - getSavedChannels()         │   │
│  │   - findByUrl()     │  │    - syncFavorites()            │   │
│  └─────────────────────┘  └─────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation

### Step 1: Create FavoriteChannelsSyncManager

Create a new file `FavoriteChannelsSyncManager.kt` in the repository package:

```kotlin
// FavoriteChannelsSyncManager.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manager for syncing and updating favorite channels from the current playlist.
 * 
 * This manager handles:
 * - Fetching updated channel information from the playlist
 * - Identifying stale favorite channels
 * - Auto-updating favorites with current playlist data
 * - Preserving favorite status across playlist updates
 */
object FavoriteChannelsSyncManager {

    private const val TAG = "FavoriteChannelsSync"
    
    // Preferences for storing favorite channel metadata
    private const val PREFS_NAME = "favorite_channels_sync"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_FAVORITE_URLS = "favorite_urls"
    private const val KEY_STALE_THRESHOLD_HOURS = 24
    
    private var prefs: SharedPreferences? = null
    private val iptvRepository = IptvRepository.getInstance()
    
    // State flows for UI updates
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState
    
    private val _favoriteStats = MutableStateFlow(FavoriteStats())
    val favoriteStats: StateFlow<FavoriteStats> = _favoriteStats
    
    /**
     * Initialize the manager with application context.
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Represents the current state of the sync operation.
     */
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val updatedCount: Int, val removedCount: Int) : SyncState()
        data class Error(val message: String) : SyncState()
        data class ValidationComplete(
            val totalFavorites: Int,
            val validChannels: Int,
            val staleChannels: Int,
            val missingChannels: Int
        ) : SyncState()
    }
    
    /**
     * Statistics about favorite channels.
     */
    data class FavoriteStats(
        val totalFavorites: Int = 0,
        val validChannels: Int = 0,
        val staleChannels: Int = 0,
        val missingChannels: Int = 0,
        val lastSyncTime: Long = 0
    )
    
    /**
     * Result of updating a single favorite channel.
     */
    data class ChannelUpdateResult(
        val originalChannel: IptvChannel,
        val updatedChannel: IptvChannel?,
        val status: UpdateStatus,
        val reason: String? = null
    )
    
    enum class UpdateStatus {
        UPDATED,           // Channel was found and updated
        STILL_VALID,       // Channel data is current
        NOT_FOUND,         // Channel URL not in current playlist
        URL_CHANGED,       // Channel found but URL changed
        REMOVED            // Channel no longer exists in playlist
    }
    
    /**
     * Load favorite channels from SharedPreferences.
     */
    fun loadFavoritesFromPrefs(): List<IptvChannel> {
        val context = prefs ?: return emptyList()
        val json = context.getString(KEY_FAVORITE_URLS, null) ?: return emptyList()
        
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<IptvChannel>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(IptvChannel(
                    name = obj.optString("name"),
                    logo = obj.optString("logo", null),
                    url = obj.optString("url"),
                    group = obj.optString("group", null)
                ))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorites from prefs", e)
            emptyList()
        }
    }
    
    /**
     * Save favorite channels to SharedPreferences.
     */
    fun saveFavoritesToPrefs(channels: List<IptvChannel>) {
        val context = prefs ?: return
        val arr = JSONArray()
        channels.forEach { ch ->
            val obj = JSONObject().apply {
                put("name", ch.name)
                put("logo", ch.logo)
                put("url", ch.url)
                put("group", ch.group)
            }
            arr.put(obj)
        }
        context.edit().putString(KEY_FAVORITE_URLS, arr.toString()).apply()
    }
    
    /**
     * Update the last sync timestamp.
     */
    private fun updateLastSyncTime() {
        prefs?.edit()?.putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())?.apply()
    }
    
    /**
     * Get the time since last sync in hours.
     */
    fun getHoursSinceLastSync(): Long {
        val lastSync = prefs?.getLong(KEY_LAST_SYNC_TIME, 0) ?: 0
        if (lastSync == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - lastSync) / (1000 * 60 * 60)
    }
    
    /**
     * Validate favorite channels against the current playlist.
     * Returns detailed information about each favorite channel.
     */
    suspend fun validateFavoriteChannels(
        context: Context,
        favorites: List<IptvChannel>
    ): Result<ValidationResult> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            // Fetch current playlist
            val playlistResult = iptvRepository.fetchPlaylist(context, forceRefresh = true)
            if (playlistResult.isFailure) {
                val error = playlistResult.exceptionOrNull()?.message ?: "Unknown error"
                _syncState.value = SyncState.Error(error)
                return@withContext Result.failure(Exception(error))
            }
            
            val playlist = playlistResult.getOrThrow()
            val channelMap = playlist.channels.associateBy { it.url }
            
            var validCount = 0
            var staleCount = 0
            var missingCount = 0
            val validationDetails = mutableListOf<ChannelValidationDetail>()
            
            favorites.forEach { favorite ->
                val validation = validateChannel(favorite, playlist, channelMap)
                validationDetails.add(validation)
                
                when (validation.status) {
                    UpdateStatus.STILL_VALID, UpdateStatus.UPDATED -> validCount++
                    UpdateStatus.URL_CHANGED -> staleCount++
                    UpdateStatus.NOT_FOUND, UpdateStatus.REMOVED -> missingCount++
                }
            }
            
            val result = ValidationResult(
                totalFavorites = favorites.size,
                validChannels = validCount,
                staleChannels = staleCount,
                missingChannels = missingCount,
                details = validationDetails
            )
            
            _favoriteStats.value = FavoriteStats(
                totalFavorites = favorites.size,
                validChannels = validCount,
                staleChannels = staleCount,
                missingChannels = missingCount,
                lastSyncTime = prefs?.getLong(KEY_LAST_SYNC_TIME, 0) ?: 0
            )
            
            _syncState.value = SyncState.ValidationComplete(
                totalFavorites = favorites.size,
                validChannels = validCount,
                staleChannels = staleCount,
                missingChannels = missingCount
            )
            
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error validating favorites", e)
            _syncState.value = SyncState.Error(e.message ?: "Validation failed")
            Result.failure(e)
        }
    }
    
    /**
     * Validate a single channel against the current playlist.
     */
    private fun validateChannel(
        channel: IptvChannel,
        playlist: IptvPlaylist,
        channelMap: Map<String, IptvChannel>
    ): ChannelValidationDetail {
        // Check if channel URL exists in current playlist
        val currentChannel = channelMap[channel.url]
        
        if (currentChannel != null) {
            // Check if any details have changed
            val hasChanges = currentChannel.name != channel.name ||
                    currentChannel.logo != channel.logo ||
                    currentChannel.group != channel.group
            
            return ChannelValidationDetail(
                originalChannel = channel,
                currentChannel = currentChannel,
                status = if (hasChanges) UpdateStatus.UPDATED else UpdateStatus.STILL_VALID,
                hasChanges = hasChanges,
                changeDetails = if (hasChanges) buildChangeDetails(channel, currentChannel) else null
            )
        }
        
        // URL not found - try to find by channel name
        val matchingChannel = playlist.channels.find { 
            it.name.equals(channel.name, ignoreCase = true) && 
            it.group == channel.group 
        }
        
        if (matchingChannel != null) {
            return ChannelValidationDetail(
                originalChannel = channel,
                currentChannel = matchingChannel,
                status = UpdateStatus.URL_CHANGED,
                hasChanges = true,
                changeDetails = listOf(
                    ChangeDetail("url", channel.url, matchingChannel.url)
                )
            )
        }
        
        return ChannelValidationDetail(
            originalChannel = channel,
            currentChannel = null,
            status = UpdateStatus.NOT_FOUND,
            hasChanges = false,
            changeDetails = null
        )
    }
    
    /**
     * Build a list of changes between original and current channel.
     */
    private fun buildChangeDetails(
        original: IptvChannel,
        current: IptvChannel
    ): List<ChangeDetail> {
        val changes = mutableListOf<ChangeDetail>()
        
        if (original.name != current.name) {
            changes.add(ChangeDetail("name", original.name, current.name))
        }
        if (original.logo != current.logo) {
            changes.add(ChangeDetail("logo", original.logo ?: "null", current.logo ?: "null"))
        }
        if (original.group != current.group) {
            changes.add(ChangeDetail("group", original.group ?: "null", current.group ?: "null"))
        }
        
        return changes
    }
    
    /**
     * Update all favorite channels from the current playlist.
     * This method:
     * 1. Fetches the latest playlist
     * 2. Matches favorites by URL or name
     * 3. Updates channel details while preserving favorite status
     * 4. Removes favorites that no longer exist in the playlist
     */
    suspend fun updateFavoritesFromPlaylist(
        context: Context,
        favorites: List<IptvChannel>,
        autoRemoveMissing: Boolean = false
    ): Result<UpdateResult> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing
            
            val validationResult = validateFavoriteChannels(context, favorites)
            if (validationResult.isFailure) {
                return@withContext Result.failure(
                    validationResult.exceptionOrNull() ?: Exception("Validation failed")
                )
            }
            
            val validation = validationResult.getOrThrow()
            val updatedChannels = mutableListOf<IptvChannel>()
            val removedChannels = mutableListOf<IptvChannel>()
            val results = mutableListOf<ChannelUpdateResult>()
            
            validation.details.forEach { detail ->
                when (detail.status) {
                    UpdateStatus.STILL_VALID -> {
                        // Channel is still valid, keep as-is
                        updatedChannels.add(detail.originalChannel)
                        results.add(ChannelUpdateResult(
                            originalChannel = detail.originalChannel,
                            updatedChannel = detail.originalChannel,
                            status = UpdateStatus.STILL_VALID
                        ))
                    }
                    
                    UpdateStatus.UPDATED -> {
                        // Channel details have changed, update with new info
                        detail.currentChannel?.let { newChannel ->
                            val updated = IptvChannel(
                                name = newChannel.name,
                                logo = newChannel.logo,
                                url = newChannel.url,
                                group = newChannel.group
                            )
                            updatedChannels.add(updated)
                            results.add(ChannelUpdateResult(
                                originalChannel = detail.originalChannel,
                                updatedChannel = updated,
                                status = UpdateStatus.UPDATED,
                                reason = "Channel details updated"
                            ))
                        }
                    }
                    
                    UpdateStatus.URL_CHANGED -> {
                        // Channel URL has changed, update with new URL
                        detail.currentChannel?.let { newChannel ->
                            val updated = IptvChannel(
                                name = newChannel.name,
                                logo = newChannel.logo,
                                url = newChannel.url,
                                group = newChannel.group
                            )
                            updatedChannels.add(updated)
                            results.add(ChannelUpdateResult(
                                originalChannel = detail.originalChannel,
                                updatedChannel = updated,
                                status = UpdateStatus.URL_CHANGED,
                                reason = "Channel URL updated from playlist"
                            ))
                        }
                    }
                    
                    UpdateStatus.NOT_FOUND, UpdateStatus.REMOVED -> {
                        if (autoRemoveMissing) {
                            // Auto-remove missing channels
                            removedChannels.add(detail.originalChannel)
                            results.add(ChannelUpdateResult(
                                originalChannel = detail.originalChannel,
                                updatedChannel = null,
                                status = UpdateStatus.REMOVED,
                                reason = "Channel not found in current playlist"
                            ))
                        } else {
                            // Keep the channel but mark as potentially stale
                            updatedChannels.add(detail.originalChannel)
                            results.add(ChannelUpdateResult(
                                originalChannel = detail.originalChannel,
                                updatedChannel = detail.originalChannel,
                                status = UpdateStatus.NOT_FOUND,
                                reason = "Channel not in current playlist (may be outdated)"
                            ))
                        }
                    }
                }
            }
            
            // Save updated favorites
            saveFavoritesToPrefs(updatedChannels)
            updateLastSyncTime()
            
            // Update stats
            _favoriteStats.value = _favoriteStats.value.copy(
                totalFavorites = updatedChannels.size,
                validChannels = results.count { 
                    it.status == UpdateStatus.STILL_VALID || 
                    it.status == UpdateStatus.UPDATED || 
                    it.status == UpdateStatus.URL_CHANGED 
                },
                staleChannels = results.count { it.status == UpdateStatus.NOT_FOUND },
                missingChannels = removedChannels.size,
                lastSyncTime = System.currentTimeMillis()
            )
            
            val result = UpdateResult(
                updatedChannels = updatedChannels,
                removedChannels = removedChannels,
                updateResults = results,
                totalUpdated = results.count { 
                    it.status == UpdateStatus.UPDATED || it.status == UpdateStatus.URL_CHANGED 
                },
                totalRemoved = removedChannels.size,
                totalKept = results.count { it.status == UpdateStatus.STILL_VALID }
            )
            
            _syncState.value = SyncState.Success(
                updatedCount = result.totalUpdated,
                removedCount = result.totalRemoved
            )
            
            return@withContext Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorites", e)
            _syncState.value = SyncState.Error(e.message ?: "Update failed")
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Get channels that need updating (stale or missing).
     */
    suspend fun getStaleFavoriteChannels(
        context: Context,
        favorites: List<IptvChannel>
    ): List<ChannelValidationDetail> = withContext(Dispatchers.IO) {
        val validationResult = validateFavoriteChannels(context, favorites)
        validationResult.getOrNull()?.details?.filter { 
            it.status != UpdateStatus.STILL_VALID && it.status != UpdateStatus.UPDATED 
        } ?: emptyList()
    }
    
    /**
     * Check if sync is recommended based on time since last sync.
     */
    fun isSyncRecommended(): Boolean {
        return getHoursSinceLastSync() >= KEY_STALE_THRESHOLD_HOURS
    }
    
    /**
     * Reset sync state to idle.
     */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
    
    /**
     * Validation result containing detailed information.
     */
    data class ValidationResult(
        val totalFavorites: Int,
        val validChannels: Int,
        val staleChannels: Int,
        val missingChannels: Int,
        val details: List<ChannelValidationDetail>
    )
    
    /**
     * Details about a single channel validation.
     */
    data class ChannelValidationDetail(
        val originalChannel: IptvChannel,
        val currentChannel: IptvChannel?,
        val status: UpdateStatus,
        val hasChanges: Boolean,
        val changeDetails: List<ChangeDetail>?
    )
    
    /**
     * Represents a single change between channel versions.
     */
    data class ChangeDetail(
        val field: String,
        val oldValue: String,
        val newValue: String
    )
    
    /**
     * Result of the update operation.
     */
    data class UpdateResult(
        val updatedChannels: List<IptvChannel>,
        val removedChannels: List<IptvChannel>,
        val updateResults: List<ChannelUpdateResult>,
        val totalUpdated: Int,
        val totalRemoved: Int,
        val totalKept: Int
    )
}
```

### Step 2: Create Favorite Channels Settings UI Component

Add this composable to your settings screen:

```kotlin
// FavoriteChannelsUpdateSection.kt
@Composable
fun FavoriteChannelsUpdateSection(
    viewModel: LiveTvViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { FavoriteChannelsSyncManager }
    
    // Initialize sync manager
    LaunchedEffect(Unit) {
        syncManager.initialize(context)
    }
    
    val syncState by syncManager.syncState.collectAsState()
    val stats by syncManager.favoriteStats.collectAsState()
    val favoriteChannels = remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    
    // Load favorites on first composition
    LaunchedEffect(Unit) {
        favoriteChannels.value = viewModel.getFavoriteChannels()
        syncManager.validateFavoriteChannels(context, favoriteChannels.value)
    }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showValidationDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.padding(16.dp)) {
        // Header
        Text(
            text = "Favorite Channels",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        label = "Total",
                        value = stats.totalFavorites.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatItem(
                        label = "Valid",
                        value = stats.validChannels.toString(),
                        color = Color(0xFF4CAF50)
                    )
                    StatItem(
                        label = "Stale",
                        value = stats.staleChannels.toString(),
                        color = Color(0xFFFF9800)
                    )
                    StatItem(
                        label = "Missing",
                        value = stats.missingChannels.toString(),
                        color = Color(0xFFF44336)
                    )
                }
                
                if (stats.lastSyncTime > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last updated: ${formatLastSyncTime(stats.lastSyncTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sync Status
        when (val state = syncState) {
            is FavoriteChannelsSyncManager.SyncState.Syncing -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Checking for updates...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            is FavoriteChannelsSyncManager.SyncState.ValidationComplete -> {
                if (state.staleChannels > 0 || state.missingChannels > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${state.staleChannels + state.missingChannels} channels need updating",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
            
            is FavoriteChannelsSyncManager.SyncState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Updated ${state.updatedCount} channels, removed ${state.removedCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
            
            is FavoriteChannelsSyncManager.SyncState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
            
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Validate Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        favoriteChannels.value = viewModel.getFavoriteChannels()
                        syncManager.validateFavoriteChannels(context, favoriteChannels.value)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validate")
            }
            
            // Update Button
            Button(
                onClick = { showUpdateDialog = true },
                modifier = Modifier.weight(1f),
                enabled = syncState !is FavoriteChannelsSyncManager.SyncState.Syncing
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update")
            }
        }
        
        // Sync Recommendation Banner
        if (syncManager.isSyncRecommended()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD)
                ),
                onClick = { showUpdateDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color(0xFF1976D2)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Channels may be outdated",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap to sync with current playlist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Update Confirmation Dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Favorite Channels") },
            text = {
                Column {
                    Text("This will:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Update channel names, logos, and groups from the current playlist")
                    Text("2. Update URLs for channels that have moved")
                    if (stats.missingChannels > 0) {
                        Text("3. Remove ${stats.missingChannels} channels that no longer exist")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = { /* Handle auto-remove option */ }
                        )
                        Text("Automatically remove missing channels")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            favoriteChannels.value = viewModel.getFavoriteChannels()
                            val result = syncManager.updateFavoritesFromPlaylist(
                                context = context,
                                favorites = favoriteChannels.value,
                                autoRemoveMissing = false
                            )
                            if (result.isSuccess) {
                                // Update ViewModel with new favorites
                                result.getOrNull()?.updatedChannels?.let { updated ->
                                    // Clear and re-add favorites
                                    favoriteChannels.value.forEach { viewModel.removeFavorite(it) }
                                    updated.forEach { viewModel.addFavorite(it) }
                                }
                            }
                            showUpdateDialog = false
                        }
                    }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatLastSyncTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val hours = diff / (1000 * 60 * 60)
    val days = hours / 24
    
    return when {
        days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        else -> "Just now"
    }
}
```

### Step 3: Integrate into Settings Screen

Add the FavoriteChannelsUpdateSection to your settings screen:

```kotlin
// In SettingsScreen.kt or MobileSettingsScreen.kt

@Composable
fun LiveTvSettingsSection(
    viewModel: LiveTvViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Column {
        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Playlist") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Favorites") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("EPG") }
            )
        }
        
        // Tab Content
        when (selectedTab) {
            0 -> PlaylistSettingsSection(viewModel)
            1 -> FavoriteChannelsUpdateSection(viewModel)
            2 -> EpgSettingsSection(viewModel)
        }
    }
}

@Composable
private fun PlaylistSettingsSection(viewModel: LiveTvViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentPlaylistUrl by remember { mutableStateOf(IptvRepository.PLAYLIST_URL) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "IPTV Playlist",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = currentPlaylistUrl,
            onValueChange = { currentPlaylistUrl = it },
            label = { Text("Playlist URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                scope.launch {
                    isRefreshing = true
                    IptvRepository.getInstance().fetchPlaylist(context, forceRefresh = true)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRefreshing
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh Playlist")
        }
    }
}

@Composable
private fun EpgSettingsSection(viewModel: LiveTvViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentEpgUrl by remember { mutableStateOf(IptvRepository.PLAYLIST_EPG_URL) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Electronic Program Guide (EPG)",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = currentEpgUrl,
            onValueChange = { currentEpgUrl = it },
            label = { Text("EPG URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                scope.launch {
                    isRefreshing = true
                    IptvRepository.getInstance().fetchEpg(context, forceRefresh = true)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRefreshing
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh EPG")
        }
    }
}
```

### Step 4: Add to Firebase Configuration

Add the favorite channels sync settings to your `app_config` in Firebase:

```json
{
  "favorite_channels_sync_Configuration": {
    "auto_sync_enabled": false,
    "sync_on_playlist_update": true,
    "auto_remove_missing": false,
    "sync_interval_hours": 24,
    "notify_stale_channels": true,
    "show_sync_recommendation": true
  }
}
```

### Step 5: Update LiveTvViewModel

Add methods to support the sync manager:

```kotlin
// Add to LiveTvViewModel.kt

/**
 * Get all favorite channels including those from Firebase.
 */
fun getAllFavoriteChannels(): List<IptvChannel> {
    val localFavorites = getFavoriteChannels()
    // Also include Firebase-synced favorites if needed
    return localFavorites
}

/**
 * Refresh favorites from storage.
 */
fun refreshFavorites() {
    viewModelScope.launch {
        // Trigger a reload of favorites
        _uiState.value = _uiState.value.copy(
            selectedChannel = _uiState.value.selectedChannel
        )
    }
}

/**
 * Update a specific favorite channel.
 */
fun updateFavoriteChannel(original: IptvChannel, updated: IptvChannel) {
    viewModelScope.launch {
        removeFavorite(original)
        addFavorite(updated)
    }
}

/**
 * Bulk update favorite channels.
 */
fun bulkUpdateFavorites(updates: List<Pair<IptvChannel, IptvChannel>>) {
    viewModelScope.launch {
        updates.forEach { (original, updated) ->
            updateFavoriteChannel(original, updated)
        }
    }
}
```

## Usage Flow

### User Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     User Opens Settings                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              User Navigates to "Live TV" Section                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              User Selects "Favorites" Tab                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│           System Shows Favorite Channels Stats                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Total: 25  │  Valid: 20  │  Stale: 3  │  Missing: 2    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  Last updated: 3 days ago                                       │
│                                                                 │
│  ⚠️ 5 channels need updating                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              User Taps "Validate" Button                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              System Fetches Latest Playlist                      │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │            🔄 Checking for updates...                  │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              System Compares Favorites with Playlist             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              User Taps "Update" Button                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              System Shows Confirmation Dialog                   │
│                                                                 │
│  This will:                                                     │
│  1. Update channel names, logos, and groups                     │
│  2. Update URLs for channels that have moved                    │
│  3. Keep channels that no longer exist                          │
│                                                                 │
│  ☐ Automatically remove missing channels                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              User Confirms Update                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              System Updates All Favorites                        │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              ✅ Updated 5 channels                     │    │
│  │              Removed 0 channels                       │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              System Shows Success Message                        │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Total: 25  │  Valid: 25  │  Stale: 0  │  Missing: 0   │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  Last updated: Just now                                         │
└─────────────────────────────────────────────────────────────────┘
```

## Best Practices

### 1. Automatic Sync on Playlist Update

```kotlin
// In IptvRepository
suspend fun fetchPlaylist(context: Context, forceRefresh: Boolean = false): Result<IptvPlaylist> {
    val result = withContext(Dispatchers.IO) {
        // ... existing fetch logic ...
    }
    
    // After successful fetch, notify sync manager
    if (result.isSuccess) {
        FavoriteChannelsSyncManager.onPlaylistUpdated(context)
    }
    
    return result
}

// In FavoriteChannelsSyncManager
fun onPlaylistUpdated(context: Context) {
    val config = AppConfigRepository.playlistConfig.value
    if (config.auto_sync_enabled || config.sync_on_playlist_update) {
        viewModelScope.launch {
            val favorites = loadFavoritesFromPrefs()
            if (favorites.isNotEmpty()) {
                validateFavoriteChannels(context, favorites)
            }
        }
    }
}
```

### 2. Background Sync with WorkManager

```kotlin
// FavoriteChannelsSyncWorker.kt
class FavoriteChannelsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            FavoriteChannelsSyncManager.initialize(applicationContext)
            val favorites = FavoriteChannelsSyncManager.loadFavoritesFromPrefs()
            
            if (favorites.isNotEmpty()) {
                val result = FavoriteChannelsSyncManager.updateFavoritesFromPlaylist(
                    context = applicationContext,
                    favorites = favorites,
                    autoRemoveMissing = false
                )
                
                if (result.isSuccess) {
                    // Notify user of updates
                    notifyUserOfUpdates(result.getOrNull())
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    private fun notifyUserOfUpdates(updateResult: FavoriteChannelsSyncManager.UpdateResult?) {
        // Show notification if there were updates
        updateResult?.let {
            if (it.totalUpdated > 0 || it.totalRemoved > 0) {
                // Show notification
            }
        }
    }
}
```

### 3. Conflict Resolution Strategy

```kotlin
/**
 * Handle conflicts when a channel has been updated in both Firebase and locally.
 */
suspend fun resolveConflicts(
    localChannel: IptvChannel,
    remoteChannel: IptvChannel,
    strategy: ConflictResolutionStrategy
): IptvChannel {
    return when (strategy) {
        ConflictResolutionStrategy.KEEP_LOCAL -> localChannel
        ConflictResolutionStrategy.KEEP_REMOTE -> remoteChannel
        ConflictResolutionStrategy.MERGE -> {
            // Keep local name if user customized it, otherwise use remote
            IptvChannel(
                name = if (localChannel.name != remoteChannel.name && hasUserEdited(localChannel)) 
                    localChannel.name else remoteChannel.name,
                logo = localChannel.logo ?: remoteChannel.logo,
                url = remoteChannel.url, // Always use latest URL
                group = localChannel.group ?: remoteChannel.group
            )
        }
        ConflictResolutionStrategy.ASK_USER -> {
            // Return null to trigger UI prompt
            throw ConflictResolutionRequiredException(localChannel, remoteChannel)
        }
    }
}

enum class ConflictResolutionStrategy {
    KEEP_LOCAL,
    KEEP_REMOTE,
    MERGE,
    ASK_USER
}
```

## Testing Checklist

- [ ] Validate favorites against valid playlist
- [ ] Validate favorites against empty playlist
- [ ] Validate favorites against playlist with changed URLs
- [ ] Validate favorites against playlist with removed channels
- [ ] Update favorites with valid channels
- [ ] Update favorites with changed URLs
- [ ] Update favorites with removed channels (auto-remove off)
- [ ] Update favorites with removed channels (auto-remove on)
- [ ] Verify Firebase sync after update
- [ ] Verify SharedPreferences persistence
- [ ] Test offline mode with cached playlist
- [ ] Test with empty favorites list
- [ ] Test with large favorites list (100+ channels)
- [ ] Verify UI updates correctly during sync

## Summary

This implementation provides:

1. **Validation**: Check all favorites against the current playlist
2. **Auto-Update**: Update channel information from playlist
3. **URL Resolution**: Handle channels that have moved to new URLs
4. **Clean-Up**: Option to remove channels no longer in playlist
5. **Statistics**: Show clear status of favorite channel health
6. **User Control**: Manual update button with confirmation dialog
7. **Recommendations**: Suggest updates when favorites are stale

The solution preserves user-favorited channels while keeping them synchronized with the latest playlist data.---

## Firebase Synchronization After Channel Updates

### Overview

After updating favorite channels from the playlist, it's essential to sync the changes with Firebase Realtime Database. This ensures:

- User's favorite channels are backed up to the cloud
- Changes are available across multiple devices
- Firebase data stays consistent with local storage
- Multi-device synchronization is maintained

### Firebase Sync Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              FavoriteChannelsSyncManager                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  updateFavoritesFromPlaylist()                         │    │
│  │  - Validates channels against playlist                  │    │
│  │  - Updates channel details                             │    │
│  │  - Preserves favorite status                           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                            │                                     │
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  syncUpdatedChannelsToFirebase()                       │    │
│  │  - Saves updated channels to Firebase                  │    │
│  │  - Removes channels no longer in favorites              │    │
│  │  - Updates timestamps                                   │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Firebase Realtime Database                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  /users/{userId}/savedChannels                          │    │
│  │  - Channel data with updated info                       │    │
│  │  - Timestamps for sync tracking                         │    │
│  │  - Metadata for conflict resolution                     │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Step 1: Create FirebaseChannelSyncManager

Add this new class to handle Firebase synchronization:

```kotlin
// FirebaseChannelSyncManager.kt
package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manager for synchronizing favorite channels with Firebase Realtime Database.
 * 
 * This manager handles:
 * - Saving updated channels to Firebase
 * - Removing stale channels from Firebase
 * - Bidirectional sync between local and cloud
 * - Conflict resolution for multi-device scenarios
 */
object FirebaseChannelSyncManager {

    private const val TAG = "FirebaseChannelSync"
    
    // Firebase database paths
    private const val USERS_NODE = "users"
    private const val SAVED_CHANNELS_NODE = "savedChannels"
    private const val SYNC_METADATA_NODE = "syncMetadata"
    
    // Sync metadata keys
    private const val KEY_LAST_LOCAL_SYNC = "last_local_sync"
    private const val KEY_LAST_FIREBASE_SYNC = "last_firebase_sync"
    private const val KEY_SYNC_VERSION = "sync_version"
    
    private val database = FirebaseDatabase.getInstance()
    
    // State flows for sync status
    private val _syncState = MutableStateFlow<FirebaseSyncState>(FirebaseSyncState.Idle)
    val syncState: StateFlow<FirebaseSyncState> = _syncState
    
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount
    
    /**
     * Represents the state of Firebase synchronization.
     */
    sealed class FirebaseSyncState {
        object Idle : FirebaseSyncState()
        object Syncing : FirebaseSyncState()
        data class Synced(val channelsCount: Int, val timestamp: Long) : FirebaseSyncState()
        data class Error(val message: String) : FirebaseSyncState()
        data class PartialSync(val saved: Int, val failed: Int, val removed: Int) : FirebaseSyncState()
    }
    
    /**
     * Metadata for tracking sync state.
     */
    data class SyncMetadata(
        val lastLocalSync: Long = 0,
        val lastFirebaseSync: Long = 0,
        val syncVersion: Int = 0,
        val deviceId: String = ""
    )
    
    /**
     * Result of Firebase sync operation.
     */
    data class FirebaseSyncResult(
        val savedChannels: Int,
        val removedChannels: Int,
        val failedChannels: Int,
        val timestamp: Long,
        val errors: List<String> = emptyList()
    )
    
    /**
     * Sync a single updated channel to Firebase.
     */
    suspend fun saveChannel(
        userId: String,
        channel: IptvChannel,
        updateMetadata: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = FirebaseSyncState.Syncing
            
            val userRef = database.getReference("$USERS_NODE/$userId/$SAVED_CHANNELS_NODE")
            val channelKey = generateChannelKey(channel.url)
            
            val channelData = mapOf(
                "name" to channel.name,
                "logo" to channel.logo,
                "url" to channel.url,
                "group" to channel.group,
                "addedAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis(),
                "source" to "playlist_update",
                "version" to 1
            )
            
            userRef.child(channelKey).setValue(channelData).await()
            
            if (updateMetadata) {
                updateSyncMetadata(userId)
            }
            
            Log.d(TAG, "Channel saved to Firebase: ${channel.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving channel to Firebase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save multiple updated channels to Firebase.
     */
    suspend fun saveChannels(
        userId: String,
        channels: List<IptvChannel>,
        autoRemoveMissing: Boolean = false,
        previousFavorites: List<IptvChannel> = emptyList()
    ): FirebaseSyncResult = withContext(Dispatchers.IO) {
        var savedCount = 0
        var removedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        _syncState.value = FirebaseSyncState.Syncing
        
        try {
            // Get current Firebase channels
            val firebaseChannels = getFirebaseChannels(userId)
            val firebaseUrls = firebaseChannels.map { it.url }.toSet()
            val localUrls = channels.map { it.url }.toSet()
            
            // Save/Update all channels from the update
            channels.forEach { channel ->
                try {
                    val result = saveChannel(userId, channel, updateMetadata = false)
                    if (result.isSuccess) {
                        savedCount++
                    } else {
                        failedCount++
                        errors.add("Failed to save: ${channel.name}")
                    }
                } catch (e: Exception) {
                    failedCount++
                    errors.add("Error saving ${channel.name}: ${e.message}")
                }
            }
            
            // Remove channels that are no longer in favorites
            if (autoRemoveMissing && previousFavorites.isNotEmpty()) {
                val previousUrls = previousFavorites.map { it.url }.toSet()
                val removedUrls = previousUrls - localUrls
                
                removedUrls.forEach { url ->
                    try {
                        val key = generateChannelKey(url)
                        database.getReference("$USERS_NODE/$userId/$SAVED_CHANNELS_NODE")
                            .child(key)
                            .removeValue()
                            .await()
                        removedCount++
                        Log.d(TAG, "Removed channel from Firebase: $url")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing channel from Firebase", e)
                    }
                }
            }
            
            // Update sync metadata
            updateSyncMetadata(userId)
            
            val result = FirebaseSyncResult(
                savedChannels = savedCount,
                removedChannels = removedCount,
                failedChannels = failedCount,
                timestamp = System.currentTimeMillis(),
                errors = errors
            )
            
            _syncState.value = FirebaseSyncState.PartialSync(
                saved = savedCount,
                failed = failedCount,
                removed = removedCount
            )
            
            Log.d(TAG, "Firebase sync complete: saved=$savedCount, removed=$removedCount, failed=$failedCount")
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Firebase sync", e)
            _syncState.value = FirebaseSyncState.Error(e.message ?: "Unknown error")
            return@withContext FirebaseSyncResult(
                savedChannels = savedCount,
                removedChannels = removedCount,
                failedChannels = failedCount,
                timestamp = System.currentTimeMillis(),
                errors = errors + e.message!!
            )
        }
    }
    
    /**
     * Get all channels from Firebase for a user.
     */
    suspend fun getFirebaseChannels(userId: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.getReference("$USERS_NODE/$userId/$SAVED_CHANNELS_NODE")
                .get()
                .await()
            
            if (!snapshot.exists()) {
                return@withContext emptyList()
            }
            
            val channels = mutableListOf<IptvChannel>()
            snapshot.children.forEach { child ->
                try {
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val logo = child.child("logo").getValue(String::class.java)
                    val url = child.child("url").getValue(String::class.java) ?: ""
                    val group = child.child("group").getValue(String::class.java)
                    
                    if (url.isNotEmpty()) {
                        channels.add(IptvChannel(
                            name = name,
                            logo = logo,
                            url = url,
                            group = group
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing channel from Firebase", e)
                }
            }
            
            Log.d(TAG, "Retrieved ${channels.size} channels from Firebase")
            return@withContext channels
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting channels from Firebase", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Remove a channel from Firebase.
     */
    suspend fun removeChannel(userId: String, channel: IptvChannel): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = generateChannelKey(channel.url)
            database.getReference("$USERS_NODE/$userId/$SAVED_CHANNELS_NODE")
                .child(key)
                .removeValue()
                .await()
            
            Log.d(TAG, "Channel removed from Firebase: ${channel.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing channel from Firebase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove multiple channels from Firebase.
     */
    suspend fun removeChannels(userId: String, channels: List<IptvChannel>): Result<Int> = withContext(Dispatchers.IO) {
        var removedCount = 0
        
        try {
            channels.forEach { channel ->
                try {
                    val key = generateChannelKey(channel.url)
                    database.getReference("$USERS_NODE/$userId/$SAVED_CHANNELS_NODE")
                        .child(key)
                        .removeValue()
                        .await()
                    removedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing channel: ${channel.name}", e)
                }
            }
            
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Perform bidirectional sync between local and Firebase.
     * This ensures both local and cloud have the same channels.
     */
    suspend fun bidirectionalSync(
        userId: String,
        localChannels: List<IptvChannel>
    ): BidirectionalSyncResult = withContext(Dispatchers.IO) {
        val addedToFirebase = mutableListOf<IptvChannel>()
        val addedToLocal = mutableListOf<IptvChannel>()
        val updatedInFirebase = mutableListOf<IptvChannel>()
        val removedFromFirebase = mutableListOf<IptvChannel>()
        val errors = mutableListOf<String>()
        
        _syncState.value = FirebaseSyncState.Syncing
        
        try {
            // Get current Firebase channels
            val firebaseChannels = getFirebaseChannels(userId)
            val firebaseUrls = firebaseChannels.associateBy { it.url }
            val localUrls = localChannels.associateBy { it.url }
            
            // 1. Add channels from local to Firebase (if not exists)
            localChannels.forEach { localChannel ->
                val firebaseChannel = firebaseUrls[localChannel.url]
                
                if (firebaseChannel == null) {
                    // Channel doesn't exist in Firebase - add it
                    try {
                        saveChannel(userId, localChannel, updateMetadata = false)
                        addedToFirebase.add(localChannel)
                    } catch (e: Exception) {
                        errors.add("Failed to add ${localChannel.name}: ${e.message}")
                    }
                } else if (hasChannelChanged(localChannel, firebaseChannel)) {
                    // Channel exists but has changed - update it
                    try {
                        saveChannel(userId, localChannel, updateMetadata = false)
                        updatedInFirebase.add(localChannel)
                    } catch (e: Exception) {
                        errors.add("Failed to update ${localChannel.name}: ${e.message}")
                    }
                }
            }
            
            // 2. Add channels from Firebase to local (if not exists locally)
            firebaseChannels.forEach { firebaseChannel ->
                if (!localUrls.containsKey(firebaseChannel.url)) {
                    addedToLocal.add(firebaseChannel)
                }
            }
            
            // 3. Update sync metadata
            updateSyncMetadata(userId)
            
            val result = BidirectionalSyncResult(
                addedToFirebase = addedToFirebase,
                addedToLocal = addedToLocal,
                updatedInFirebase = updatedInFirebase,
                removedFromFirebase = removedFromFirebase,
                totalChanges = addedToFirebase.size + addedToLocal.size + 
                               updatedInFirebase.size + removedFromFirebase.size,
                errors = errors,
                timestamp = System.currentTimeMillis()
            )
            
            _syncState.value = FirebaseSyncState.Synced(
                channelsCount = localChannels.size,
                timestamp = result.timestamp
            )
            
            Log.d(TAG, "Bidirectional sync complete: addedToFirebase=${addedToFirebase.size}, " +
                    "addedToLocal=${addedToLocal.size}, updated=${updatedInFirebase.size}")
            
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during bidirectional sync", e)
            _syncState.value = FirebaseSyncState.Error(e.message ?: "Sync failed")
            return@withContext BidirectionalSyncResult(
                addedToFirebase = addedToFirebase,
                addedToLocal = addedToLocal,
                updatedInFirebase = updatedInFirebase,
                removedFromFirebase = removedFromFirebase,
                totalChanges = 0,
                errors = errors + e.message!!,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update sync metadata in Firebase.
     */
    private suspend fun updateSyncMetadata(userId: String) {
        try {
            val metadataRef = database.getReference("$USERS_NODE/$userId/$SYNC_METADATA_NODE")
            val updates = mapOf(
                KEY_LAST_LOCAL_SYNC to System.currentTimeMillis(),
                KEY_SYNC_VERSION to (getCurrentSyncVersion(userId) + 1)
            )
            metadataRef.updateChildren(updates).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating sync metadata", e)
        }
    }
    
    /**
     * Get current sync version for a user.
     */
    private suspend fun getCurrentSyncVersion(userId: String): Int {
        return try {
            val snapshot = database.getReference("$USERS_NODE/$userId/$SYNC_METADATA_NODE")
                .child(KEY_SYNC_VERSION)
                .get()
                .await()
            snapshot.getValue(Int::class.java) ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get sync metadata for a user.
     */
    suspend fun getSyncMetadata(userId: String): SyncMetadata = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.getReference("$USERS_NODE/$userId/$SYNC_METADATA_NODE")
                .get()
                .await()
            
            if (!snapshot.exists()) {
                return@withContext SyncMetadata()
            }
            
            return@withContext SyncMetadata(
                lastLocalSync = snapshot.child(KEY_LAST_LOCAL_SYNC).getValue(Long::class.java) ?: 0,
                lastFirebaseSync = snapshot.child(KEY_LAST_FIREBASE_SYNC).getValue(Long::class.java) ?: 0,
                syncVersion = snapshot.child(KEY_SYNC_VERSION).getValue(Int::class.java) ?: 0,
                deviceId = snapshot.child("deviceId").getValue(String::class.java) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync metadata", e)
            return@withContext SyncMetadata()
        }
    }
    
    /**
     * Generate a unique key for a channel URL.
     */
    private fun generateChannelKey(url: String): String {
        return Base64.encodeToString(url.toByteArray(), Base64.NO_WRAP)
    }
    
    /**
     * Check if a channel has changed from its Firebase version.
     */
    private fun hasChannelChanged(local: IptvChannel, firebase: IptvChannel): Boolean {
        return local.name != firebase.name ||
               local.logo != firebase.logo ||
               local.group != firebase.group
    }
    
    /**
     * Clear Firebase sync state.
     */
    fun clearSyncState() {
        _syncState.value = FirebaseSyncState.Idle
        _pendingSyncCount.value = 0
    }
    
    /**
     * Result of bidirectional sync operation.
     */
    data class BidirectionalSyncResult(
        val addedToFirebase: List<IptvChannel>,
        val addedToLocal: List<IptvChannel>,
        val updatedInFirebase: List<IptvChannel>,
        val removedFromFirebase: List<IptvChannel>,
        val totalChanges: Int,
        val errors: List<String>,
        val timestamp: Long
    )
}
```

### Step 2: Update FavoriteChannelsSyncManager to Include Firebase Sync

Modify the `updateFavoritesFromPlaylist` method to automatically sync with Firebase:

```kotlin
// Add to FavoriteChannelsSyncManager.kt

// Firebase sync manager instance
private val firebaseSyncManager = FirebaseChannelSyncManager

/**
 * Current user ID for Firebase operations.
 * Set this during app initialization or login.
 */
var currentUserId: String? = null

/**
 * Update favorite channels from playlist and sync to Firebase.
 * 
 * This method:
 * 1. Validates favorites against current playlist
 * 2. Updates channel details
 * 3. Saves updated favorites to SharedPreferences
 * 4. Syncs changes to Firebase
 */
suspend fun updateFavoritesWithFirebaseSync(
    context: Context,
    favorites: List<IptvChannel>,
    autoRemoveMissing: Boolean = false
): Result<CompleteSyncResult> = withContext(Dispatchers.IO) {
    try {
        _syncState.value = SyncState.Syncing
        
        // First, perform the playlist update
        val updateResult = updateFavoritesFromPlaylist(context, favorites, autoRemoveMissing)
        
        if (updateResult.isFailure) {
            return@withContext Result.failure(
                updateResult.exceptionOrNull() ?: Exception("Update failed")
            )
        }
        
        val playlistUpdateResult = updateResult.getOrThrow()
        
        // Now sync to Firebase
        val userId = currentUserId
        if (userId.isNullOrEmpty()) {
            Log.w(TAG, "No user ID set, skipping Firebase sync")
            return@withContext Result.success(
                CompleteSyncResult(
                    playlistUpdateResult = playlistUpdateResult,
                    firebaseSyncResult = null,
                    success = true
                )
            )
        }
        
        // Sync updated channels to Firebase
        val firebaseResult = firebaseSyncManager.saveChannels(
            userId = userId,
            channels = playlistUpdateResult.updatedChannels,
            autoRemoveMissing = autoRemoveMissing,
            previousFavorites = favorites
        )
        
        // If auto-remove is enabled, remove missing channels from Firebase
        if (autoRemoveMissing && playlistUpdateResult.removedChannels.isNotEmpty()) {
            firebaseSyncManager.removeChannels(
                userId = userId,
                channels = playlistUpdateResult.removedChannels
            )
        }
        
        val completeResult = CompleteSyncResult(
            playlistUpdateResult = playlistUpdateResult,
            firebaseSyncResult = firebaseResult,
            success = true
        )
        
        _syncState.value = SyncState.Success(
            updatedCount = playlistUpdateResult.totalUpdated,
            removedCount = playlistUpdateResult.totalRemoved
        )
        
        return@withContext Result.success(completeResult)
        
    } catch (e: Exception) {
        Log.e(TAG, "Error during complete sync", e)
        _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        return@withContext Result.failure(e)
    }
}

/**
 * Perform full sync including bidirectional sync with Firebase.
 */
suspend fun performFullSync(
    context: Context,
    userId: String,
    localFavorites: List<IptvChannel>
): Result<FullSyncResult> = withContext(Dispatchers.IO) {
    try {
        _syncState.value = SyncState.Syncing
        
        // 1. Update from playlist
        val playlistResult = updateFavoritesFromPlaylist(context, localFavorites, autoRemoveMissing = false)
        
        // 2. Bidirectional sync with Firebase
        val bidirSyncResult = firebaseSyncManager.bidirectionalSync(
            userId = userId,
            localChannels = playlistResult.getOrNull()?.updatedChannels ?: localFavorites
        )
        
        // 3. Get merged favorites (local + Firebase)
        val mergedFavorites = mergeFavorites(
            localFavorites = playlistResult.getOrNull()?.updatedChannels ?: localFavorites,
            firebaseChannels = firebaseSyncManager.getFirebaseChannels(userId)
        )
        
        // 4. Save merged favorites to SharedPreferences
        saveFavoritesToPrefs(mergedFavorites)
        
        val fullResult = FullSyncResult(
            playlistUpdates = playlistResult.getOrNull(),
            bidirectionalSync = bidirSyncResult,
            mergedFavorites = mergedFavorites,
            success = true
        )
        
        _syncState.value = SyncState.Success(
            updatedCount = bidirSyncResult.updatedInFirebase.size,
            removedCount = bidirSyncResult.removedFromFirebase.size
        )
        
        return@withContext Result.success(fullResult)
        
    } catch (e: Exception) {
        Log.e(TAG, "Error during full sync", e)
        _syncState.value = SyncState.Error(e.message ?: "Full sync failed")
        return@withContext Result.failure(e)
    }
}

/**
 * Merge local and Firebase favorites, preferring newer data.
 */
private fun mergeFavorites(
    localFavorites: List<IptvChannel>,
    firebaseChannels: List<IptvChannel>
): List<IptvChannel> {
    val merged = mutableMapOf<String, IptvChannel>()
    
    // Add all local favorites first
    localFavorites.forEach { channel ->
        merged[channel.url] = channel
    }
    
    // Add Firebase channels that don't exist locally
    firebaseChannels.forEach { channel ->
        if (!merged.containsKey(channel.url)) {
            merged[channel.url] = channel
        }
    }
    
    return merged.values.toList()
}

/**
 * Complete sync result containing both playlist update and Firebase sync results.
 */
data class CompleteSyncResult(
    val playlistUpdateResult: UpdateResult,
    val firebaseSyncResult: FirebaseChannelSyncManager.FirebaseSyncResult?,
    val success: Boolean
)

/**
 * Full sync result with all sync operations.
 */
data class FullSyncResult(
    val playlistUpdates: UpdateResult?,
    val bidirectionalSync: FirebaseChannelSyncManager.BidirectionalSyncResult,
    val mergedFavorites: List<IptvChannel>,
    val success: Boolean
)
```

### Step 3: Update the Settings UI for Firebase Sync

Update the FavoriteChannelsUpdateSection to show Firebase sync status:

```kotlin
// Updated FavoriteChannelsUpdateSection.kt

@Composable
fun FavoriteChannelsUpdateSection(
    viewModel: LiveTvViewModel,
    userId: String, // Add user ID parameter
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { FavoriteChannelsSyncManager }
    val firebaseSyncManager = remember { FirebaseChannelSyncManager }
    
    // Initialize sync managers
    LaunchedEffect(Unit) {
        syncManager.initialize(context)
        syncManager.currentUserId = userId
    }
    
    val syncState by syncManager.syncState.collectAsState()
    val firebaseSyncState by firebaseSyncManager.syncState.collectAsState()
    val stats by syncManager.favoriteStats.collectAsState()
    val favoriteChannels = remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    
    // Load favorites on first composition
    LaunchedEffect(Unit) {
        favoriteChannels.value = viewModel.getFavoriteChannels()
        syncManager.validateFavoriteChannels(context, favoriteChannels.value)
    }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSyncDetailsDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.padding(16.dp)) {
        // Header with Firebase status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favorite Channels",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // Firebase sync status indicator
            FirebaseSyncStatusIndicator(firebaseSyncState)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        label = "Total",
                        value = stats.totalFavorites.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatItem(
                        label = "Valid",
                        value = stats.validChannels.toString(),
                        color = Color(0xFF4CAF50)
                    )
                    StatItem(
                        label = "Stale",
                        value = stats.staleChannels.toString(),
                        color = Color(0xFFFF9800)
                    )
                    StatItem(
                        label = "Missing",
                        value = stats.missingChannels.toString(),
                        color = Color(0xFFF44336)
                    )
                }
                
                if (stats.lastSyncTime > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last updated: ${formatLastSyncTime(stats.lastSyncTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Firebase: ${getFirebaseSyncStatus(firebaseSyncState)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sync Status
        when (val state = syncState) {
            is FavoriteChannelsSyncManager.SyncState.Syncing -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Syncing with playlist...")
                }
            }
            
            is FavoriteChannelsSyncManager.SyncState.Success -> {
                // Show Firebase sync status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    ),
                    onClick = { showSyncDetailsDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Updated ${state.updatedCount} channels",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "Firebase sync complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Validate Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        favoriteChannels.value = viewModel.getFavoriteChannels()
                        syncManager.validateFavoriteChannels(context, favoriteChannels.value)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validate")
            }
            
            // Update Button
            Button(
                onClick = { showUpdateDialog = true },
                modifier = Modifier.weight(1f),
                enabled = syncState !is FavoriteChannelsSyncManager.SyncState.Syncing
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update & Sync")
            }
        }
    }
    
    // Update Confirmation Dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Favorite Channels") },
            text = {
                Column {
                    Text("This will:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Update channel names, logos, and groups from the current playlist")
                    Text("2. Update URLs for channels that have moved")
                    Text("3. Sync all changes to Firebase for cloud backup")
                    if (stats.missingChannels > 0) {
                        Text("4. Remove ${stats.missingChannels} channels that no longer exist")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = { /* Handle auto-remove option */ }
                        )
                        Text("Automatically remove missing channels")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            favoriteChannels.value = viewModel.getFavoriteChannels()
                            val result = syncManager.updateFavoritesWithFirebaseSync(
                                context = context,
                                favorites = favoriteChannels.value,
                                autoRemoveMissing = false
                            )
                            
                            if (result.isSuccess) {
                                val completeResult = result.getOrNull()
                                // Update ViewModel with new favorites
                                completeResult?.playlistUpdateResult?.updatedChannels?.let { updated ->
                                    viewModel.clearAndReAddFavorites(updated)
                                }
                                
                                // Show success message with Firebase sync info
                                completeResult?.firebaseSyncResult?.let { fbResult ->
                                    Log.d(TAG, "Firebase sync: saved=${fbResult.savedChannels}, " +
                                            "removed=${fbResult.removedChannels}, " +
                                            "failed=${fbResult.failedChannels}")
                                }
                            }
                            showUpdateDialog = false
                        }
                    }
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Sync Details Dialog
    if (showSyncDetailsDialog) {
        SyncDetailsDialog(
            syncState = syncState,
            firebaseSyncState = firebaseSyncState,
            onDismiss = { showSyncDetailsDialog = false }
        )
    }
}

@Composable
private fun FirebaseSyncStatusIndicator(state: FirebaseChannelSyncManager.FirebaseSyncState) {
    val (icon, color, text) = when (state) {
        is FirebaseChannelSyncManager.FirebaseSyncState.Idle -> 
            Triple(Icons.Default.CloudOff, Color(0xFF9E9E9E), "Not synced")
        is FirebaseChannelSyncManager.FirebaseSyncState.Syncing -> 
            Triple(Icons.Default.Sync, Color(0xFF2196F3), "Syncing...")
        is FirebaseChannelSyncManager.FirebaseSyncState.Synced -> 
            Triple(Icons.Default.CloudDone, Color(0xFF4CAF50), "Synced")
        is FirebaseChannelSyncManager.FirebaseSyncState.PartialSync -> 
            Triple(Icons.Default.CloudUpload, Color(0xFFFF9800), "Partial")
        is FirebaseChannelSyncManager.FirebaseSyncState.Error -> 
            Triple(Icons.Default.CloudOff, Color(0xFFF44336), "Error")
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun SyncDetailsDialog(
    syncState: FavoriteChannelsSyncManager.SyncState,
    firebaseSyncState: FirebaseChannelSyncManager.FirebaseSyncState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Details") },
        text = {
            Column {
                // Playlist Update Section
                Text(
                    text = "Playlist Update",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (syncState) {
                    is FavoriteChannelsSyncManager.SyncState.Success -> {
                        Text("Updated: ${syncState.updatedCount} channels")
                        Text("Removed: ${syncState.removedCount} channels")
                    }
                    else -> Text("No playlist updates")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                // Firebase Sync Section
                Text(
                    text = "Firebase Sync",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (firebaseSyncState) {
                    is FirebaseChannelSyncManager.FirebaseSyncState.Synced -> {
                        Text("Channels: ${firebaseSyncState.channelsCount}")
                        Text("Last sync: ${formatTimestamp(firebaseSyncState.timestamp)}")
                    }
                    is FirebaseChannelSyncManager.FirebaseSyncState.PartialSync -> {
                        Text("Saved: ${firebaseSyncState.saved}")
                        Text("Removed: ${firebaseSyncState.removed}")
                        Text("Failed: ${firebaseSyncState.failed}")
                    }
                    is FirebaseChannelSyncManager.FirebaseSyncState.Error -> {
                        Text(
                            text = "Error: ${firebaseSyncState.message}",
                            color = Color(0xFFF44336)
                        )
                    }
                    else -> Text("No Firebase sync data")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun getFirebaseSyncStatus(state: FirebaseChannelSyncManager.FirebaseSyncState): String {
    return when (state) {
        is FirebaseChannelSyncManager.FirebaseSyncState.Idle -> "Pending"
        is FirebaseChannelSyncManager.FirebaseSyncState.Syncing -> "Syncing"
        is FirebaseChannelSyncManager.FirebaseSyncState.Synced -> "Synced"
        is FirebaseChannelSyncManager.FirebaseSyncState.PartialSync -> "Partial"
        is FirebaseChannelSyncManager.FirebaseSyncState.Error -> "Error"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    
    return when {
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
        else -> "Just now"
    }
}
```

### Step 4: Add Firebase Configuration to app_config

Add Firebase sync settings to your Firebase configuration:

```json
{
  "firebase_sync_Configuration": {
    "auto_sync_enabled": true,
    "sync_on_playlist_update": true,
    "sync_interval_minutes": 30,
    "bidirectional_sync_enabled": true,
    "auto_remove_missing_channels": false,
    "sync_wifi_only": true,
    "conflict_resolution_strategy": "latest_wins"
  },
  "favorite_channels_sync_Configuration": {
    "auto_sync_enabled": false,
    "sync_on_playlist_update": true,
    "auto_remove_missing": false,
    "sync_interval_hours": 24,
    "notify_stale_channels": true,
    "show_sync_recommendation": true,
    "preserve_custom_names": true
  }
}
```

### Step 5: Initialize User ID on App Start

Add initialization to your app's main activity or application class:

```kotlin
// In your Application class or MainActivity

class KiduyuTvApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize FavoriteChannelsSyncManager with user ID
        val userId = FirebaseAuth.getInstance().currentUser?.uid 
            ?: AuthManager.getUserId() 
            ?: generateAnonymousUserId()
        
        FavoriteChannelsSyncManager.currentUserId = userId
        
        // Schedule periodic sync if enabled
        if (isAutoSyncEnabled()) {
            schedulePeriodicSync()
        }
    }
    
    private fun isAutoSyncEnabled(): Boolean {
        // Check from Firebase config or local preferences
        val config = AppConfigRepository.playlistConfig.value
        return config.favorite_channels_sync?.auto_sync_enabled ?: false
    }
    
    private fun schedulePeriodicSync() {
        // Use WorkManager for periodic sync
        val syncRequest = PeriodicWorkRequestBuilder<FavoriteChannelsSyncWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "favorite_channels_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
```

### Complete Sync Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│              User Taps "Update & Sync" Button                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│         FavoriteChannelsSyncManager.updateFavoritesWithFirebaseSync()
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   Step 1: Playlist      │     │   Step 2: Firebase     │
│   Update                │     │   Sync                 │
│                         │     │                         │
│ 1. Fetch current        │     │ 1. Get current Firebase│
│    playlist             │     │    channels            │
│                         │     │                         │
│ 2. Compare with         │     │ 2. Save updated        │
│    favorites            │     │    channels            │
│                         │     │                         │
│ 3. Update channel       │     │ 3. Remove missing      │
│    details              │     │    channels            │
│                         │     │                         │
│ 4. Remove stale         │     │ 4. Update sync         │
│    channels             │     │    metadata            │
│                         │     │                         │
│ 5. Save to              │     │ 5. Update ViewModel    │
│    SharedPreferences    │     │    with results        │
└─────────────────────────┘     └─────────────────────────┘
              │                               │
              └───────────────┬───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Show Success with Details                          │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  ✅ Playlist Update                                      │   │
│  │     Updated 5 channels, Removed 0 channels              │   │
│  │                                                          │   │
│  │  ✅ Firebase Sync                                         │   │
│  │     Saved 5, Failed 0, Removed 0                         │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Error Handling

```kotlin
/**
 * Handle Firebase sync errors gracefully.
 */
suspend fun handleSyncError(
    error: Exception,
    context: Context,
    localFavorites: List<IptvChannel>
): SyncErrorResult {
    Log.e(TAG, "Firebase sync error", error)
    
    // 1. Log the error for debugging
    logSyncError(error)
    
    // 2. Continue with local update even if Firebase fails
    val playlistResult = updateFavoritesFromPlaylist(context, localFavorites, autoRemoveMissing = false)
    
    // 3. Save locally even if Firebase fails
    playlistResult.getOrNull()?.updatedChannels?.let {
        saveFavoritesToPrefs(it)
    }
    
    // 4. Queue Firebase sync for retry
    queueFirebaseSyncRetry(playlistResult.getOrNull()?.updatedChannels ?: emptyList())
    
    return SyncErrorResult(
        playlistUpdateSuccess = playlistResult.isSuccess,
        playlistUpdateResult = playlistResult.getOrNull(),
        firebaseSyncQueued = true,
        errorMessage = error.message
    )
}

/**
 * Queue Firebase sync for retry when connectivity is restored.
 */
private fun queueFirebaseSyncRetry(channels: List<IptvChannel>) {
    // Store channels to sync in preferences for later
    val pendingSync = JSONArray()
    channels.forEach { channel ->
        val obj = JSONObject().apply {
            put("name", channel.name)
            put("logo", channel.logo)
            put("url", channel.url)
            put("group", channel.group)
        }
        pendingSync.put(obj)
    }
    
    prefs?.edit()
        ?.putString("pending_firebase_sync", pendingSync.toString())
        ?.putLong("pending_sync_timestamp", System.currentTimeMillis())
        ?.apply()
}
```

### Testing Checklist for Firebase Sync

- [ ] Test sync when Firebase is available
- [ ] Test sync when Firebase is unavailable (queue for retry)
- [ ] Test sync with no internet connection
- [ ] Test sync with multiple devices
- [ ] Test bidirectional sync (local changes sync to Firebase)
- [ ] Test bidirectional sync (Firebase changes sync to local)
- [ ] Test conflict resolution when same channel is updated on multiple devices
- [ ] Test auto-remove missing channels
- [ ] Test that sync preserves favorite status
- [ ] Test that sync updates all channel fields (name, logo, group, url)
- [ ] Test sync metadata updates correctly
- [ ] Test error handling and retry mechanism