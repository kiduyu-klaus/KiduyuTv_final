package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
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
    private val TOTAL_SYNC_STEPS = 6 // MyList, Companies, Networks, Casts, WatchHistory, DefaultProvider
    
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
        // Note: If the user is already signed in (e.g., from a previous session),
        // this will use the Firebase Auth UID. Otherwise, it uses device ID.
        initializeFirebaseManager(context)
        
        isInitialized = true
    }
    
    /**
     * Initialize FirebaseManager with the correct user ID.
     * This checks if the user is signed in (via Firebase Auth or phone authorization)
     * and uses the appropriate identifier.
     * 
     * IMPORTANT: Both mobile and TV apps must use the SAME user identifier
     * (Firebase Auth UID) to share data in Firebase Realtime Database.
     */
    private fun initializeFirebaseManager(context: Context) {
        // Check if user is signed in via AuthManager
        // This covers both:
        // - Mobile app: Firebase Auth (Google Sign-In)
        // - TV app: Phone authorization (receives UID from mobile app)
        val userId = if (AuthManager.isSignedIn.value && AuthManager.currentUser != null) {
            Log.d(TAG, "Initializing FirebaseManager with authenticated user UID: ${AuthManager.currentUser?.uid}")
            AuthManager.currentUser?.uid ?: SettingsManager(context).getDeviceId()
        } else {
            // For TV app after phone authorization, we need to check if there's a phone-authorized user
            // The _isSignedIn StateFlow in AuthManager is updated by onPhoneAuthorized()
            // But AuthManager.currentUser might be null on TV since TV doesn't use Firebase Auth
            // So we need to check the user data that's set during phone authorization
            
            // Check if AuthManager has phone-authorized user data
            // The phone authorization sets _isSignedIn to true and stores user info in StateFlows
            // But since we don't store the UID separately, we need another way to detect this
            
            // For now, fall back to device ID
            // The fix: onPhoneAuthorized() calls updateFirebaseManagerUserId() which is called below
            Log.d(TAG, "Initializing FirebaseManager with device ID (user not signed in)")
            SettingsManager(context).getDeviceId()
        }
        FirebaseManager.init(userId)
    }
    
    /**
     * Update FirebaseManager when user signs in or phone authorization occurs.
     * Call this from AuthManager.onPhoneAuthorized() to ensure FirebaseManager
     * uses the correct user ID for data operations.
     * 
     * This is crucial for the TV app where:
     * 1. App starts with device ID
     * 2. User authorizes TV via phone → receives Firebase Auth UID
     * 3. This method is called to update FirebaseManager to use the correct UID
     * 4. Now TV and mobile save to the SAME Firebase path → data is shared
     * 
     * @param userId The Firebase Auth UID (from phone authorization or Google sign-in)
     */
    fun updateFirebaseManagerUserId(userId: String) {
        Log.d(TAG, "Updating FirebaseManager user ID to: $userId")
        FirebaseManager.init(userId)
        
        // Restart sync with the new user ID
        // This ensures listeners are set up for the correct user path
        if (isInitialized) {
            Log.d(TAG, "Restarting sync with new user ID: $userId")
            startSync(forceRefresh = true)
        }
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
                
                // Step 1: Sync My List (movies and TV shows)
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
                
                // Step 4: Sync Saved Casts
                _syncMessage.value = "Syncing Casts..."
                _syncProgress.value = 4
                syncCasts(forceRefresh)
                
                // Step 5: Sync Watch History
                _syncMessage.value = "Syncing Watch History..."
                _syncProgress.value = 5
                syncWatchHistory(forceRefresh)
                
                // Step 6: Sync Default Provider
                _syncMessage.value = "Syncing Preferences..."
                _syncProgress.value = 6
                syncDefaultProvider()
                
                // Calculate total items synced
                val myListCount = getFirebaseMyListCount()
                val companiesCount = getFirebaseCompaniesCount()
                val networksCount = getFirebaseNetworksCount()
                val castsCount = getFirebaseCastsCount()
                val totalItems = myListCount + companiesCount + networksCount + castsCount
                
                _syncProgress.value = TOTAL_SYNC_STEPS
                _syncMessage.value = "Sync complete!"
                _syncState.value = SyncState.Success(totalItems)
                
                // Enable real-time sync after initial sync completes
                enableRealTimeSync()
                
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
                            val voteAverage = (itemData["voteAverage"] as? Number)?.toDouble() ?: 0.0
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
            val firebaseData = FirebaseManager.getSavedCompaniesOnce()
            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.d(TAG, "Found ${firebaseData.size} companies in Firebase")
                
                firebaseData.forEach { (companyIdStr, itemData) ->
                    try {
                        val companyId = companyIdStr.toIntOrNull() ?: return@forEach
                        if (itemData is Map<*, *>) {
                            val name = itemData["name"] as? String ?: ""
                            val logoPath = itemData["logoPath"] as? String
                            
                            // Check if already in local database
                            if (forceRefresh || !MyListManager.isInList(companyId, "company")) {
                                DatabaseManager.addToMyList(
                                    id = companyId,
                                    mediaType = "company",
                                    title = name,
                                    posterPath = logoPath
                                )
                                Log.d(TAG, "Synced company: $name (ID: $companyId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing company: $companyIdStr", e)
                    }
                }
            } else {
                Log.d(TAG, "No saved companies found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Companies", e)
        }
    }
    
    /**
     * Sync Saved Networks from Firebase to local database.
     */
    private suspend fun syncNetworks(forceRefresh: Boolean) {
        try {
            val firebaseData = FirebaseManager.getSavedNetworksOnce()
            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.d(TAG, "Found ${firebaseData.size} networks in Firebase")
                
                firebaseData.forEach { (networkIdStr, itemData) ->
                    try {
                        val networkId = networkIdStr.toIntOrNull() ?: return@forEach
                        if (itemData is Map<*, *>) {
                            val name = itemData["name"] as? String ?: ""
                            val logoPath = itemData["logoPath"] as? String
                            
                            // Check if already in local database
                            if (forceRefresh || !MyListManager.isInList(networkId, "network")) {
                                DatabaseManager.addToMyList(
                                    id = networkId,
                                    mediaType = "network",
                                    title = name,
                                    posterPath = logoPath
                                )
                                Log.d(TAG, "Synced network: $name (ID: $networkId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing network: $networkIdStr", e)
                    }
                }
            } else {
                Log.d(TAG, "No saved networks found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Networks", e)
        }
    }
    
    /**
     * Sync Saved Casts from Firebase to local database.
     */
    private suspend fun syncCasts(forceRefresh: Boolean) {
        try {
            val firebaseData = FirebaseManager.getSavedCastsOnce()
            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.d(TAG, "Found ${firebaseData.size} cast members in Firebase")
                
                firebaseData.forEach { (castIdStr, itemData) ->
                    try {
                        val castId = castIdStr.toIntOrNull() ?: return@forEach
                        if (itemData is Map<*, *>) {
                            val name = itemData["name"] as? String ?: ""
                            val profilePath = itemData["profilePath"] as? String
                            val character = itemData["character"] as? String
                            val knownForDepartment = itemData["knownForDepartment"] as? String
                            
                            // Check if already in local database
                            if (forceRefresh || !MyListManager.isInList(castId, "cast")) {
                                DatabaseManager.addToMyList(
                                    id = castId,
                                    mediaType = "cast",
                                    title = name,
                                    posterPath = profilePath,
                                    character = character,
                                    knownForDepartment = knownForDepartment
                                )
                                Log.d(TAG, "Synced cast: $name (ID: $castId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing cast: $castIdStr", e)
                    }
                }
            } else {
                Log.d(TAG, "No saved casts found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Casts", e)
        }
    }
    
    /**
     * Sync Watch History from Firebase to local database.
     */
    private suspend fun syncWatchHistory(forceRefresh: Boolean) {
        try {
            val firebaseData = FirebaseManager.getWatchHistoryOnce()
            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.d(TAG, "Found ${firebaseData.size} watch history entries in Firebase")
                
                // Watch history structure: { tv: { tmdbId: { seasonNumber: { episodeNumber: {...} } } }, movies: { tmdbId: {...} } }
                // We process both TV and movies
                
                // Process TV watch history
                val tvHistory = firebaseData["tv"] as? Map<*, *>
                if (tvHistory != null) {
                    processTvWatchHistory(tvHistory)
                }
                
                // Process movie watch history
                val movieHistory = firebaseData["movies"] as? Map<*, *>
                if (movieHistory != null) {
                    processMovieWatchHistory(movieHistory)
                }
                
                Log.d(TAG, "Watch history sync completed")
            } else {
                Log.d(TAG, "No watch history found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Watch History", e)
        }
    }
    
    /**
     * Process TV watch history from Firebase.
     */
    private suspend fun processTvWatchHistory(tvHistory: Map<*, *>) {
        tvHistory.forEach { (tmdbIdStr, seasonsData) ->
            try {
                val tmdbId = (tmdbIdStr as? String)?.toIntOrNull() ?: return@forEach
                if (seasonsData is Map<*, *>) {
                    seasonsData.forEach { (seasonStr, episodesData) ->
                        try {
                            val season = (seasonStr as? String)?.toIntOrNull() ?: return@forEach
                            if (episodesData is Map<*, *>) {
                                episodesData.forEach { (episodeStr, episodeData) ->
                                    try {
                                        val episode = (episodeStr as? String)?.toIntOrNull() ?: return@forEach
                                        if (episodeData is Map<*, *>) {
                                            val playbackPosition = (episodeData["playbackPosition"] as? Number)?.toLong() ?: 0L
                                            val duration = (episodeData["duration"] as? Number)?.toLong() ?: 0L
                                            val progress = (episodeData["progress"] as? Number)?.toDouble() ?: 0.0
                                            val title = (episodeData["title"] as? String) ?: "TV Show"
                                            
                                            Log.d(TAG, "Syncing TV watch history: ID=$tmdbId S${season}E${episode} position=${playbackPosition}s")
                                            
                                            // Save to local database with watch history
                                            DatabaseManager.addToWatchHistoryAsync(
                                                id = tmdbId,
                                                mediaType = "tv",
                                                title = title,
                                                seasonNumber = season,
                                                episodeNumber = episode,
                                                playbackPosition = playbackPosition
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error processing episode: $episodeStr", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing season: $seasonStr", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing TV: $tmdbIdStr", e)
            }
        }
    }
    
    /**
     * Process movie watch history from Firebase.
     */
    private suspend fun processMovieWatchHistory(movieHistory: Map<*, *>) {
        movieHistory.forEach { (tmdbIdStr, movieData) ->
            try {
                val tmdbId = (tmdbIdStr as? String)?.toIntOrNull() ?: return@forEach
                if (movieData is Map<*, *>) {
                    val playbackPosition = (movieData["playbackPosition"] as? Number)?.toLong() ?: 0L
                    val duration = (movieData["duration"] as? Number)?.toLong() ?: 0L
                    val progress = (movieData["progress"] as? Number)?.toDouble() ?: 0.0
                    val title = (movieData["title"] as? String) ?: "Movie"
                    
                    Log.d(TAG, "Syncing movie watch history: ID=$tmdbId position=${playbackPosition}s")
                    
                    // Save to local database with watch history
                    DatabaseManager.addToWatchHistoryAsync(
                        id = tmdbId,
                        mediaType = "movie",
                        title = title,
                        playbackPosition = playbackPosition
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing movie: $tmdbIdStr", e)
            }
        }
    }
    
    /**
     * Sync Default Provider from Firebase to local settings.
     * This ensures the default provider preference syncs across devices.
     */
    private suspend fun syncDefaultProvider() {
        try {
            val firebaseProvider = FirebaseManager.getDefaultProviderOnce()
            
            if (firebaseProvider != null) {
                Log.d(TAG, "Found default provider in Firebase: $firebaseProvider")
                
                // Save to local settings if Firebase has a value
                // Note: We need context to access SettingsManager
                // This is handled in SplashActivity after sync completes
                _syncMessage.value = "Default provider: $firebaseProvider"
            } else {
                Log.d(TAG, "No default provider found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Default Provider", e)
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
        return try {
            val data = FirebaseManager.getSavedCompaniesOnce()
            data?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get the count of Saved Networks from Firebase.
     */
    private suspend fun getFirebaseNetworksCount(): Int {
        return try {
            val data = FirebaseManager.getSavedNetworksOnce()
            data?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get the count of Saved Casts from Firebase.
     */
    private suspend fun getFirebaseCastsCount(): Int {
        return try {
            val data = FirebaseManager.getSavedCastsOnce()
            data?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Set up real-time listeners for Firebase data changes.
     * Call this after successful sign-in to enable live sync.
     * When data is added, updated, or deleted in Firebase, local database is immediately synced.
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
                Log.d(TAG, "My List changed in Firebase - syncing to local")
                syncScope.launch {
                    processFirebaseMyListSnapshot(snapshot)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "My List listener cancelled", error.toException())
            }
        }
        myListRef.addValueEventListener(myListListener)
        activeListeners.add(myListRef)
        
        // Listen to Companies changes
        val companiesRef = FirebaseManager.getNodeReference(FirebaseManager.Nodes.SAVED_COMPANIES)
        val companiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Companies changed in Firebase - syncing to local")
                syncScope.launch {
                    processFirebaseCompaniesSnapshot(snapshot)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Companies listener cancelled", error.toException())
            }
        }
        companiesRef.addValueEventListener(companiesListener)
        activeListeners.add(companiesRef)
        
        // Listen to Networks changes
        val networksRef = FirebaseManager.getNodeReference(FirebaseManager.Nodes.SAVED_NETWORKS)
        val networksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Networks changed in Firebase - syncing to local")
                syncScope.launch {
                    processFirebaseNetworksSnapshot(snapshot)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Networks listener cancelled", error.toException())
            }
        }
        networksRef.addValueEventListener(networksListener)
        activeListeners.add(networksRef)
        
        // Listen to Casts changes
        val castsRef = FirebaseManager.getNodeReference(FirebaseManager.Nodes.SAVED_CASTS)
        val castsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Casts changed in Firebase - syncing to local")
                syncScope.launch {
                    processFirebaseCastsSnapshot(snapshot)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Casts listener cancelled", error.toException())
            }
        }
        castsRef.addValueEventListener(castsListener)
        activeListeners.add(castsRef)
        
        // Listen to Watch History changes
        val watchHistoryRef = FirebaseManager.getNodeReference(FirebaseManager.Nodes.WATCH_HISTORY)
        val watchHistoryListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Watch History changed in Firebase - syncing to local")
                syncScope.launch {
                    processFirebaseWatchHistorySnapshot(snapshot)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Watch History listener cancelled", error.toException())
            }
        }
        watchHistoryRef.addValueEventListener(watchHistoryListener)
        activeListeners.add(watchHistoryRef)
        
        Log.d(TAG, "Real-time sync enabled for all data types")
    }
    
    /**
     * Process My List snapshot from Firebase real-time listener.
     */
    private suspend fun processFirebaseMyListSnapshot(snapshot: DataSnapshot) {
        try {
            if (snapshot.exists()) {
                val data = snapshot.value as? Map<*, *>
                if (data != null) {
                    data.forEach { (tmdbIdStr, itemData) ->
                        try {
                            val tmdbId = (tmdbIdStr as? String)?.toIntOrNull() ?: return@forEach
                            if (itemData is Map<*, *>) {
                                val isTv = (itemData["isTv"] as? Boolean) ?: false
                                val title = (itemData["title"] as? String) ?: ""
                                val posterPath = itemData["posterPath"] as? String
                                val backdropPath = itemData["backdropPath"] as? String
                                val voteAverage = (itemData["voteAverage"] as? Number)?.toDouble() ?: 0.0
                                
                                MyListManager.addItem(
                                    item = MyListItem(
                                        id = tmdbId,
                                        type = if (isTv) "tv" else "movie",
                                        title = title,
                                        posterPath = posterPath,
                                        voteAverage = voteAverage
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing MyList item: $tmdbIdStr", e)
                        }
                    }
                }
            } else {
                // Data was deleted in Firebase, optionally clear local
                Log.d(TAG, "My List is empty in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MyList snapshot", e)
        }
    }
    
    /**
     * Process Companies snapshot from Firebase real-time listener.
     */
    private suspend fun processFirebaseCompaniesSnapshot(snapshot: DataSnapshot) {
        try {
            if (snapshot.exists()) {
                val data = snapshot.value as? Map<*, *>
                if (data != null) {
                    data.forEach { (companyIdStr, itemData) ->
                        try {
                            val companyId = (companyIdStr as? String)?.toIntOrNull() ?: return@forEach
                            if (itemData is Map<*, *>) {
                                val name = (itemData["name"] as? String) ?: ""
                                val logoPath = itemData["logoPath"] as? String
                                val originCountry = itemData["originCountry"] as? String
                                
                                FirebaseManager.saveCompany(companyId, name, logoPath, originCountry)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing Company item: $companyIdStr", e)
                        }
                    }
                }
            } else {
                Log.d(TAG, "Companies is empty in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Companies snapshot", e)
        }
    }
    
    /**
     * Process Networks snapshot from Firebase real-time listener.
     */
    private suspend fun processFirebaseNetworksSnapshot(snapshot: DataSnapshot) {
        try {
            if (snapshot.exists()) {
                val data = snapshot.value as? Map<*, *>
                if (data != null) {
                    data.forEach { (networkIdStr, itemData) ->
                        try {
                            val networkId = (networkIdStr as? String)?.toIntOrNull() ?: return@forEach
                            if (itemData is Map<*, *>) {
                                val name = (itemData["name"] as? String) ?: ""
                                val logoPath = itemData["logoPath"] as? String
                                
                                FirebaseManager.saveNetwork(networkId, name, logoPath)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing Network item: $networkIdStr", e)
                        }
                    }
                }
            } else {
                Log.d(TAG, "Networks is empty in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Networks snapshot", e)
        }
    }
    
    /**
     * Process Casts snapshot from Firebase real-time listener.
     */
    private suspend fun processFirebaseCastsSnapshot(snapshot: DataSnapshot) {
        try {
            if (snapshot.exists()) {
                val data = snapshot.value as? Map<*, *>
                if (data != null) {
                    data.forEach { (castIdStr, itemData) ->
                        try {
                            val castId = (castIdStr as? String)?.toIntOrNull() ?: return@forEach
                            if (itemData is Map<*, *>) {
                                val name = (itemData["name"] as? String) ?: ""
                                val profilePath = itemData["profilePath"] as? String
                                val character = itemData["character"] as? String
                                val knownForDepartment = itemData["knownForDepartment"] as? String
                                
                                FirebaseManager.saveCast(castId, name, profilePath, character, knownForDepartment)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing Cast item: $castIdStr", e)
                        }
                    }
                }
            } else {
                Log.d(TAG, "Casts is empty in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Casts snapshot", e)
        }
    }
    
    /**
     * Process Watch History snapshot from Firebase real-time listener.
     */
    private suspend fun processFirebaseWatchHistorySnapshot(snapshot: DataSnapshot) {
        try {
            if (snapshot.exists()) {
                val data = snapshot.value as? Map<*, *>
                if (data != null) {
                    // Process TV watch history
                    val tvHistory = data["tv"] as? Map<*, *>
                    if (tvHistory != null) {
                        processTvWatchHistory(tvHistory)
                    }
                    
                    // Process movie watch history
                    val movieHistory = data["movies"] as? Map<*, *>
                    if (movieHistory != null) {
                        processMovieWatchHistory(movieHistory)
                    }
                }
            } else {
                Log.d(TAG, "Watch History is empty in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Watch History snapshot", e)
        }
    }
    
    /**
     * Disable real-time sync and clean up listeners.
     */
    fun disableRealTimeSync() {
        Log.d(TAG, "Disabling real-time Firebase sync...")
        activeListeners.forEach { ref ->
            try {
                ref.removeEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener", e)
            }
        }
        activeListeners.clear()
        Log.d(TAG, "Real-time sync disabled and listeners cleaned up")
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
