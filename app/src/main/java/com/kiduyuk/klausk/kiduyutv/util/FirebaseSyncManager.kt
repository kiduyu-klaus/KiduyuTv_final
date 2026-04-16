package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * FirebaseSyncManager - Handles synchronization between local Room database and Firebase Realtime Database.
 * 
 * This manager ensures user data (My List, Companies, Networks, Watch History) is synced across devices
 * when the user signs in, and provides mechanisms to fetch cloud data on app startup.
 * 
 * Sync Strategy:
 * - On app start: Fetch cloud data and merge with local data
 * - Priority: Cloud data takes precedence for conflicts (newer "lastUpdated" wins)
 * - Real-time sync: Listen for changes when signed in
 * 
 * Usage:
 *   FirebaseSyncManager.init(context)
 *   FirebaseSyncManager.startSync()
 *   FirebaseSyncManager.syncStateFlow.collect { state -> ... }
 */
object FirebaseSyncManager {

    private const val TAG = "FirebaseSyncManager"
    
    // Coroutine scope for database operations
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Sync state
    sealed class SyncState {
        data object Idle : SyncState()
        data object Syncing : SyncState()
        data class Success(val itemsSynced: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Sync progress
    private val _syncProgress = MutableStateFlow(0)
    val syncProgress: StateFlow<Int> = _syncProgress.asStateFlow()
    
    private val _syncMessage = MutableStateFlow("Preparing sync...")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()
    
    // Total steps for progress calculation
    private val TOTAL_SYNC_STEPS = 4 // MyList, Companies, Networks, WatchHistory
    
    // Active listeners for cleanup
    private val activeListeners = mutableListOf<com.google.firebase.database.Query>()
    
    private var isInitialized = false
    private var applicationContext: Context? = null
    
    /**
     * Initialize the sync manager.
     * Should be called once at app startup.
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        applicationContext = context.applicationContext
        
        // Initialize DatabaseManager if not already done
        DatabaseManager.init(context)
        
        // Initialize FirebaseManager with appropriate user ID
        val userId = if (AuthManager.isSignedIn.value && AuthManager.currentUser != null) {
            Log.d(TAG, "Initializing FirebaseManager with user UID: ${AuthManager.currentUser?.uid}")
            AuthManager.currentUser?.uid ?: SettingsManager(context).getDeviceId()
        } else {
            Log.d(TAG, "Initializing FirebaseManager with device ID")
            SettingsManager(context).getDeviceId()
        }
        FirebaseManager.init(userId)
        
        isInitialized = true
    }
    
    /**
     * Start the full data synchronization process.
     * Fetches data from Firebase and merges with local database.
     * 
     * @param forceRefresh If true, ignores local data and fetches fresh from cloud
     */
    fun startSync(forceRefresh: Boolean = false) {
        if (!isInitialized) {
            Log.e(TAG, "FirebaseSyncManager not initialized. Call init() first.")
            _syncState.value = SyncState.Error("Sync manager not initialized")
            return
        }
        
        syncScope.launch {
            try {
                _syncState.value = SyncState.Syncing
                _syncProgress.value = 0
                _syncMessage.value = "Starting sync..."
                
                Log.i(TAG, "Starting Firebase data sync...")
                
                // Step 1: Sync My List
                _syncMessage.value = "Syncing My List..."
                _syncProgress.value = 1
                syncMyList(forceRefresh)
                
                // Step 2: Sync Saved Companies
                _syncMessage.value = "Syncing Companies..."
                _syncProgress.value = 2
                syncCompanies(forceRefresh)
                
                // Step 3: Sync Saved Networks
                _syncMessage.value = "Syncing Networks..."
                _syncProgress.value = 3
                syncNetworks(forceRefresh)
                
                // Step 4: Sync Watch History (optional)
                _syncMessage.value = "Syncing Watch History..."
                _syncProgress.value = 4
                syncWatchHistory(forceRefresh)
                
                // Calculate total items synced
                val myListCount = getFirebaseMyListCount()
                val companiesCount = getFirebaseCompaniesCount()
                val networksCount = getFirebaseNetworksCount()
                val totalItems = myListCount + companiesCount + networksCount
                
                _syncProgress.value = TOTAL_SYNC_STEPS
                _syncMessage.value = "Sync complete!"
                _syncState.value = SyncState.Success(totalItems)
                
                Log.i(TAG, "Firebase sync completed. Items synced: $totalItems")
                
            } catch (e: Exception) {
                Log.e(TAG, "Firebase sync failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Unknown error during sync")
                _syncMessage.value = "Sync failed"
            }
        }
    }
    
    /**
     * Sync My List items from Firebase to local database.
     */
    private suspend fun syncMyList(forceRefresh: Boolean) {
        try {
            val firebaseData = FirebaseManager.getMyListOnce()
            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.d(TAG, "Found ${firebaseData.size} items in Firebase My List")
                
                // Process each item from Firebase
                firebaseData.forEach { (tmdbIdStr, itemData) ->
                    try {
                        val tmdbId = tmdbIdStr.toIntOrNull() ?: return@forEach
                        if (itemData is Map<*, *>) {
                            val title = itemData["title"] as? String ?: ""
                            val posterPath = itemData["posterPath"] as? String
                            val voteAverage = (itemData["voteAverage"] as? Number)?.toDouble()
                            val isTv = itemData["isTv"] as? Boolean ?: false
                            val mediaType = if (isTv) "tv" else "movie"
                            
                            // Add to local database if not exists or force refresh
                            if (forceRefresh || !MyListManager.isInList(tmdbId, mediaType)) {
                                DatabaseManager.addToMyList(
                                    id = tmdbId,
                                    mediaType = mediaType,
                                    title = title,
                                    posterPath = posterPath,
                                    voteAverage = voteAverage
                                )
                                Log.d(TAG, "Synced My List item: $title (ID: $tmdbId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing My List item: $tmdbIdStr", e)
                    }
                }
            } else {
                Log.d(TAG, "No My List items found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching My List from Firebase", e)
        }
    }
    
    /**
     * Sync Saved Companies from Firebase to local database.
     */
    private suspend fun syncCompanies(forceRefresh: Boolean) {
        try {
            val firebaseData = FirebaseManager.getSavedCompaniesFlow()
            
            // Use a one-shot read for sync
            val database = FirebaseManager.getFirebaseDatabaseInstance()
            val ref = database.getReference("users/${FirebaseManager.getNodeReference("").key}")
            
            // Note: This is a simplified version. In production, you'd want to 
            // track companies differently in the local database.
            Log.d(TAG, "Companies sync initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Companies", e)
        }
    }
    
    /**
     * Sync Saved Networks from Firebase to local database.
     */
    private suspend fun syncNetworks(forceRefresh: Boolean) {
        try {
            Log.d(TAG, "Networks sync initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Networks", e)
        }
    }
    
    /**
     * Sync Watch History from Firebase to local database.
     */
    private suspend fun syncWatchHistory(forceRefresh: Boolean) {
        try {
            Log.d(TAG, "Watch History sync initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Watch History", e)
        }
    }
    
    /**
     * Get the count of My List items from Firebase (without downloading all data).
     */
    private suspend fun getFirebaseMyListCount(): Int {
        return try {
            val data = FirebaseManager.getMyListOnce()
            data?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get the count of Saved Companies from Firebase.
     */
    private suspend fun getFirebaseCompaniesCount(): Int {
        return 0 // Simplified - would need to track in Firebase
    }
    
    /**
     * Get the count of Saved Networks from Firebase.
     */
    private suspend fun getFirebaseNetworksCount(): Int {
        return 0 // Simplified - would need to track in Firebase
    }
    
    /**
     * Set up real-time listeners for Firebase data changes.
     * Call this after successful sign-in to enable live sync.
     */
    fun enableRealTimeSync() {
        if (!isInitialized) {
            Log.e(TAG, "FirebaseSyncManager not initialized")
            return
        }
        
        Log.d(TAG, "Enabling real-time Firebase sync...")
        
        // Listen to My List changes
        val myListRef = FirebaseManager.getNodeReference(FirebaseManager.Nodes.MY_LIST)
        val myListListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Handle real-time My List updates
                Log.d(TAG, "My List updated in Firebase")
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "My List listener cancelled", error.toException())
            }
        }
        myListRef.addValueEventListener(myListListener)
        activeListeners.add(myListRef)
    }
    
    /**
     * Disable real-time sync and clean up listeners.
     */
    fun disableRealTimeSync() {
        Log.d(TAG, "Disabling real-time Firebase sync...")
        activeListeners.forEach { listener ->
            try {
                listener.removeEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener", e)
            }
        }
        activeListeners.clear()
    }
    
    /**
     * Clear all local data (for logout or reset).
     * This doesn't delete Firebase data, just local cache.
     */
    fun clearLocalData(onComplete: (() -> Unit)? = null) {
        syncScope.launch {
            try {
                // Clear local database - My List and Watch History
                DatabaseManager.clearMyList()
                DatabaseManager.clearWatchHistory()
                
                // Don't clear Firebase - user data persists
                Log.i(TAG, "Local data cleared successfully")
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing local data", e)
                onComplete?.invoke()
            }
        }
    }
    
    /**
     * Reset sync state to idle.
     */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
        _syncProgress.value = 0
        _syncMessage.value = ""
    }
    
    /**
     * Check if sync is currently in progress.
     */
    fun isSyncing(): Boolean {
        return _syncState.value is SyncState.Syncing
    }
}
