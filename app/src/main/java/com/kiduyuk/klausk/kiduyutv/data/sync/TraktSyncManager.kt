package com.kiduyuk.klausk.kiduyutv.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages synchronization between local app data and Trakt.tv.
 * Handles watch history syncing and background updates.
 */
@Singleton
class TraktSyncManager @Inject constructor(
    private val context: Context,
    private val traktRepository: TraktRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(getLastSyncTime())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    /**
     * Trigger a full sync with Trakt.tv.
     * Fetches watch history, collection, and watchlist.
     */
    fun triggerFullSync() {
        if (_syncState.value == SyncState.Syncing) {
            return
        }

        scope.launch {
            _syncState.value = SyncState.Syncing

            try {
                // Fetch watch history
                syncWatchHistory()

                // Fetch collection
                syncCollection()

                // Fetch watchlist
                syncWatchlist()

                // Update last sync time
                val currentTime = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_SYNC, currentTime).apply()
                _lastSyncTime.value = currentTime

                _syncState.value = SyncState.Success(message = null)
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(message = e.message ?: "Sync failed")
            }
        }
    }

    /**
     * Sync watch history from Trakt.
     */
    private suspend fun syncWatchHistory() {
        traktRepository.getTraktWatchHistory(page = 1, limit = 100)
            .collect { result ->
                result.fold(
                    onSuccess = { history: List<com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktHistoryItem> ->
                        // Store watch history in local cache
                        storeWatchHistory(history)
                    },
                    onFailure = { error ->
                        // Log error but continue with other syncs
                        error.printStackTrace()
                    }
                )
            }
    }

    /**
     * Sync collection from Trakt.
     */
    private suspend fun syncCollection() {
        traktRepository.getCollection("movies")
            .collect { result ->
                result.fold(
                    onSuccess = { collection ->
                        storeCollection(collection)
                    },
                    onFailure = { error ->
                        error.printStackTrace()
                    }
                )
            }
    }

    /**
     * Sync watchlist from Trakt.
     */
    private suspend fun syncWatchlist() {
        traktRepository.getWatchlist("movies")
            .collect { result ->
                result.fold(
                    onSuccess = { watchlist ->
                        storeWatchlist(watchlist)
                    },
                    onFailure = { error ->
                        error.printStackTrace()
                    }
                )
            }
    }

    /**
     * Store watch history in local SharedPreferences.
     */
    private fun storeWatchHistory(history: Any) {
        // In a production app, you would use a local database
        // For simplicity, we use SharedPreferences
        // This would be replaced with Room database in a real implementation
        val json = Gson().toJson(history)
        prefs.edit().putString(KEY_WATCH_HISTORY, json).apply()
    }

    /**
     * Store collection in local storage.
     */
    private fun storeCollection(collection: Any) {
        val json = Gson().toJson(collection)
        prefs.edit().putString(KEY_COLLECTION, json).apply()
    }

    /**
     * Store watchlist in local storage.
     */
    private fun storeWatchlist(watchlist: Any) {
        val json = Gson().toJson(watchlist)
        prefs.edit().putString(KEY_WATCHLIST, json).apply()
    }

    /**
     * Get the last sync timestamp.
     */
    private fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    /**
     * Get the time since last sync in human-readable format.
     */
    fun getTimeSinceLastSync(): String {
        val lastSync = _lastSyncTime.value
        if (lastSync == 0L) return "Never"

        val diff = System.currentTimeMillis() - lastSync
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days days ago"
            hours > 0 -> "$hours hours ago"
            minutes > 0 -> "$minutes minutes ago"
            else -> "Just now"
        }
    }

    /**
     * Check if sync is needed (more than 6 hours since last sync).
     */
    fun isSyncNeeded(): Boolean {
        val lastSync = _lastSyncTime.value
        if (lastSync == 0L) return true

        val sixHours = 6 * 60 * 60 * 1000
        return System.currentTimeMillis() - lastSync > sixHours
    }

    /**
     * Reset sync state to idle.
     */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    /**
     * Clear all synced data.
     */
    fun clearAllData() {
        prefs.edit()
            .remove(KEY_LAST_SYNC)
            .remove(KEY_WATCH_HISTORY)
            .remove(KEY_COLLECTION)
            .remove(KEY_WATCHLIST)
            .apply()
        _lastSyncTime.value = 0L
        _syncState.value = SyncState.Idle
    }

    /**
     * Sealed class representing sync states.
     */
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val message: String?) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    companion object {
        private const val PREFS_NAME = "trakt_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_WATCH_HISTORY = "watch_history"
        private const val KEY_COLLECTION = "collection"
        private const val KEY_WATCHLIST = "watchlist"
    }
}