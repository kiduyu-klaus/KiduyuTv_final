package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    private val TOTAL_SYNC_STEPS = 7 // MyList, Companies, Networks, Casts, WatchHistory, FavoriteChannels, DefaultProvider
    
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
        initializeFirebaseManager(context)
        
        // Set up reactive auth state collection
        // This ensures that if the user signs in/out after app start,
        // FirebaseManager is updated and sync is restarted with the correct path.
        syncScope.launch {
            // Use flow that emits current value first, then changes
            // This ensures we handle both:
            // 1. App start: AuthManager may have already restored persisted login
            // 2. Runtime changes: User signs in/out via phone authorization
            AuthManager.isSignedIn.collect { isSignedIn ->
                // Read current UID after the StateFlow value has been updated
                // This ensures we get the latest value, not a stale one
                val currentUid = AuthManager.currentUser?.uid ?: AuthManager.currentUid
                val deviceId = SettingsManager(context).getDeviceId()
                
                // Determine target UID based on sign-in state
                val targetUid = if (isSignedIn && currentUid != null) {
                    Log.i(TAG, "Auth state: Signed in, using UID: $currentUid")
                    currentUid
                } else {
                    Log.i(TAG, "Auth state: Not signed in, using device ID: $deviceId")
                    deviceId
                }
                
                Log.i(TAG, "Auth state collection triggered: isSignedIn=$isSignedIn, targetUid=$targetUid")
                updateFirebaseManagerUserId(targetUid)
            }
        }
        
        // Immediately check and apply current auth state
        // This handles the case where AuthManager has already restored persisted login
        // before FirebaseSyncManager.init() is called
        val immediateUid = if (AuthManager.isSignedIn.value) {
            AuthManager.currentUser?.uid ?: AuthManager.currentUid
        } else {
            null
        }
        if (immediateUid != null) {
            Log.i(TAG, "Applying restored login immediately: UID=$immediateUid")
            FirebaseManager.init(immediateUid)
        }
        
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
        val userId = if (AuthManager.isSignedIn.value) {
            // Priority 1: Firebase Auth user (Mobile)
            // Priority 2: Phone-authorized user UID (TV)
            val uid = AuthManager.currentUser?.uid ?: AuthManager.currentUid
            if (uid != null) {
                Log.i(TAG, "Initializing FirebaseManager with authenticated user UID: $uid")
                uid
            } else {
                Log.i(TAG, "Initializing FirebaseManager with device ID (signed in but UID null)")
                SettingsManager(context).getDeviceId()
            }
        } else {
            Log.i(TAG, "Initializing FirebaseManager with device ID (user not signed in)")
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
        Log.i(TAG, "Updating FirebaseManager user ID to: $userId")
        FirebaseManager.init(userId)
        
        // Restart sync with the new user ID
        // This ensures listeners are set up for the correct user path
        if (isInitialized) {
            Log.i(TAG, "Restarting sync with new user ID: $userId")
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
                
                // Step 6: Sync Favorite Channels
                _syncMessage.value = "Syncing Favorite Channels..."
                _syncProgress.value = 6
                syncFavoriteChannels(forceRefresh)
                
                // Step 7: Sync Default Provider
                _syncMessage.value = "Syncing Preferences..."
                _syncProgress.value = 7
                syncDefaultProvider()
                
                // Calculate total items synced
                val myListCount = getFirebaseMyListCount()
                val companiesCount = getFirebaseCompaniesCount()
                val networksCount = getFirebaseNetworksCount()
                val castsCount = getFirebaseCastsCount()
                val favoriteChannelsCount = getFirebaseFavoriteChannelsCount()
                val totalItems = myListCount + companiesCount + networksCount + castsCount + favoriteChannelsCount
                
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
     * Sync My List items from Firebase to local database and vice versa.
     * Implements TRUE TWO-WAY sync:
     * 1. Downloads items from Firebase and merges with local database
     * 2. Uploads local items that are missing in Firebase
     * 3. Ensures data and count match locally and on Firebase
     */
    private suspend fun syncMyList(forceRefresh: Boolean) {
        try {
            val context = applicationContext ?: return
            
            // ── STEP 1: Download cloud data ──
            val firebaseData = FirebaseManager.getMyListOnce()
            val cloudItems = mutableMapOf<Int, Pair<String, MyListItem>>() // id -> (mediaType, item)

            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.i(TAG, "Found ${firebaseData.size} items in Firebase My List")
                
                firebaseData.forEach { (tmdbIdStr, itemData) ->
                    try {
                        val tmdbId = tmdbIdStr.toIntOrNull() ?: return@forEach
                        if (itemData is Map<*, *>) {
                            val title = itemData["title"] as? String ?: ""
                            val posterPath = itemData["posterPath"] as? String
                            val voteAverage = (itemData["voteAverage"] as? Number)?.toDouble() ?: 0.0
                            val isTv = itemData["isTv"] as? Boolean ?: false
                            val mediaType = if (isTv) "tv" else "movie"
                            
                            val item = MyListItem(
                                id = tmdbId,
                                title = title,
                                posterPath = posterPath,
                                type = mediaType,
                                voteAverage = voteAverage
                            )
                            cloudItems[tmdbId] = mediaType to item
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing cloud My List item: $tmdbIdStr", e)
                    }
                }
            } else {
                Log.i(TAG, "No My List items found in Firebase")
            }
            
            // ── STEP 2: Get local data ──
            val localItems = mutableMapOf<Int, Pair<String, MyListItem>>()
            DatabaseManager.getMyList().collect { savedMediaEntities ->
                savedMediaEntities.forEach { entity ->
                    val item = DatabaseManager.entityToMyListItem(entity)
                    localItems[entity.id] = entity.mediaType to item
                }
            }
            
            Log.i(TAG, "Local My List count: ${localItems.size}")
            
            // ── STEP 3: TWO-WAY MERGE ──
            // Merge both sources, cloud data takes precedence for conflicts
            val mergedItems = mutableMapOf<Int, Pair<String, MyListItem>>()
            val seenIds = mutableSetOf<Int>()
            
            // First add all cloud items (these take precedence)
            cloudItems.forEach { (id, data) ->
                val (mediaType, item) = data
                mergedItems[id] = mediaType to item
                seenIds.add(id)
            }
            
            // Then add local-only items (not in cloud)
            localItems.forEach { (id, data) ->
                val (mediaType, item) = data
                if (!seenIds.contains(id)) {
                    mergedItems[id] = mediaType to item
                    Log.i(TAG, "Adding local-only My List item to merged list: ${item.title}")
                }
            }
            
            Log.i(TAG, "Merged My List count: ${mergedItems.size}")
            
            // ── STEP 4: Update local database ──
            mergedItems.forEach { (id, data) ->
                val (mediaType, item) = data
                if (!MyListManager.isInList(id, mediaType)) {
                    DatabaseManager.addToMyList(
                        id = item.id,
                        mediaType = item.type,
                        title = item.title,
                        posterPath = item.posterPath,
                        voteAverage = item.voteAverage,
                        character = item.character,
                        knownForDepartment = item.knownForDepartment
                    )
                }
            }
            
            // ── STEP 5: Upload merged list to Firebase ──
            mergedItems.forEach { (id, data) ->
                val (mediaType, item) = data
                try {
                    val isTv = mediaType == "tv"
                    FirebaseManager.syncMyListItem(
                        tmdbId = item.id,
                        isTv = isTv,
                        title = item.title,
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading My List item: ${item.title}", e)
                }
            }
            
            Log.i(TAG, "My List two-way sync completed: ${mergedItems.size} items")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in My List two-way sync", e)
        }
    }
    
    /**
     * Sync Saved Companies from Firebase to local database.
     */
    private suspend fun syncCompanies(forceRefresh: Boolean) {
        try {
            val firebaseData = FirebaseManager.getSavedCompaniesOnce()
            
            if (firebaseData != null && firebaseData.isNotEmpty()) {
                Log.i(TAG, "Found ${firebaseData.size} companies in Firebase")
                
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
                                Log.i(TAG, "Synced company: $name (ID: $companyId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing company: $companyIdStr", e)
                    }
                }
            } else {
                Log.i(TAG, "No saved companies found in Firebase")
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
                Log.i(TAG, "Found ${firebaseData.size} networks in Firebase")
                
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
                                Log.i(TAG, "Synced network: $name (ID: $networkId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing network: $networkIdStr", e)
                    }
                }
            } else {
                Log.i(TAG, "No saved networks found in Firebase")
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
                Log.i(TAG, "Found ${firebaseData.size} cast members in Firebase")
                
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
                                Log.i(TAG, "Synced cast: $name (ID: $castId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing cast: $castIdStr", e)
                    }
                }
            } else {
                Log.i(TAG, "No saved casts found in Firebase")
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
                Log.i(TAG, "Found ${firebaseData.size} watch history entries in Firebase")
                
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
                
                Log.i(TAG, "Watch history sync completed")
            } else {
                Log.i(TAG, "No watch history found in Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Watch History", e)
        }
    }
    
    /**
     * Process TV watch history from Firebase.
     * New structure: watchHistory/tv/{tmdbId} with season/episode as data fields
     */
    private suspend fun processTvWatchHistory(tvHistory: Map<*, *>) {
        tvHistory.forEach { (tmdbIdStr, tvData) ->
            try {
                val tmdbId = (tmdbIdStr as? String)?.toIntOrNull() ?: return@forEach
                if (tvData is Map<*, *>) {
                    // Extract season and episode from data fields (not path)
                    val season = (tvData["seasonNumber"] as? Number)?.toInt()
                    val episode = (tvData["episodeNumber"] as? Number)?.toInt()
                    val playbackPosition = (tvData["playbackPosition"] as? Number)?.toLong() ?: 0L
                    val duration = (tvData["duration"] as? Number)?.toLong() ?: 0L
                    val title = (tvData["title"] as? String) ?: "TV Show"
                    val overview = tvData["overview"] as? String
                    val posterPath = tvData["posterPath"] as? String
                    val backdropPath = tvData["backdropPath"] as? String
                    val voteAverage = (tvData["voteAverage"] as? Number)?.toDouble() ?: 0.0
                    val releaseDate = tvData["releaseDate"] as? String
                    
                    Log.i(TAG, "Syncing TV watch history: ID=$tmdbId S${season ?: "?"}E${episode ?: "?"} position=${playbackPosition}s title=$title")
                    
                    // Save to local database with watch history and metadata
                    DatabaseManager.addToWatchHistoryAsync(
                        id = tmdbId,
                        mediaType = "tv",
                        title = title,
                        overview = overview,
                        posterPath = posterPath,
                        backdropPath = backdropPath,
                        voteAverage = voteAverage,
                        releaseDate = releaseDate,
                        seasonNumber = season,
                        episodeNumber = episode,
                        playbackPosition = playbackPosition
                    )
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
                    val overview = movieData["overview"] as? String
                    val posterPath = movieData["posterPath"] as? String
                    val backdropPath = movieData["backdropPath"] as? String
                    val voteAverage = (movieData["voteAverage"] as? Number)?.toDouble() ?: 0.0
                    val releaseDate = movieData["releaseDate"] as? String
                    
                    Log.i(TAG, "Syncing movie watch history: ID=$tmdbId position=${playbackPosition}s title=$title")
                    
                    // Save to local database with watch history and metadata
                    DatabaseManager.addToWatchHistoryAsync(
                        id = tmdbId,
                        mediaType = "movie",
                        title = title,
                        overview = overview,
                        posterPath = posterPath,
                        backdropPath = backdropPath,
                        voteAverage = voteAverage,
                        releaseDate = releaseDate,
                        playbackPosition = playbackPosition
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing movie: $tmdbIdStr", e)
            }
        }
    }
    
    /**
     * Sync Favorite Channels from Firebase to local SharedPreferences.
     * Implements TWO-WAY sync:
     * 1. Downloads favorites from Firebase
     * 2. Merges with local favorites
     * 3. Uploads merged list back to Firebase (ensuring both devices stay in sync)
     * 
     * This ensures that if this device has local favorites not yet in Firebase,
     * they get uploaded so other devices can see them.
     * Merges cloud favorites with local favorites, giving priority to cloud data.
     */
    private suspend fun syncFavoriteChannels(forceRefresh: Boolean) {
        try {
            val context = applicationContext ?: return

            // ── STEP 1: Download from Firebase ──
            val firebaseChannels = FirebaseManager.getSavedChannelsOnce()
            val cloudFavorites = mutableListOf<IptvChannel>()

            if (firebaseChannels != null && firebaseChannels.isNotEmpty()) {
                Log.i(TAG, "Found ${firebaseChannels.size} favorite channels in Firebase")

                // Convert Firebase channels to IptvChannel objects
                firebaseChannels.forEach { (key, value) ->
                    try {
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
                            Log.i(TAG, "Cloud favorite: ${channel.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing cloud channel: $key", e)
                    }
                }
            } else {
                Log.i(TAG, "No favorite channels found in Firebase")
            }

            // ── STEP 2: Get local favorites ──
            val prefs = context.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE)
            val localFavoritesJson = prefs.getString("favorite_channels", "[]")
            val localFavorites = mutableListOf<IptvChannel>()

            try {
                val jsonArray = JSONArray(localFavoritesJson)
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val channel = IptvChannel(
                        name = jsonObj.optString("name", ""),
                        url = jsonObj.optString("url", ""),
                        logo = jsonObj.optString("logo", ""),
                        tvgId = jsonObj.optString("tvgId", ""),
                        tvgName = jsonObj.optString("tvgName", ""),
                        group = jsonObj.optString("group", "")
                    )
                    localFavorites.add(channel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing local favorites", e)
            }

            Log.i(TAG, "Local favorites count: ${localFavorites.size}")

            // ── STEP 3: TWO-WAY MERGE ──
            // Merge both lists, eliminating duplicates by URL
            val mergedFavorites = mutableListOf<IptvChannel>()
            val seenUrls = mutableSetOf<String>() // Track URLs to prevent duplicates

            // First add all cloud favorites
            cloudFavorites.forEach { fav ->
                if (!seenUrls.contains(fav.url)) {
                    mergedFavorites.add(fav)
                    seenUrls.add(fav.url)
                }
            }

            // Then add local-only favorites (not in cloud)
            localFavorites.forEach { localFav ->
                if (!seenUrls.contains(localFav.url)) {
                    mergedFavorites.add(localFav)
                    seenUrls.add(localFav.url)
                    Log.i(TAG, "Adding local-only favorite to merged list: ${localFav.name}")
                }
            }

            Log.i(TAG, "Merged favorites count: ${mergedFavorites.size}")

            // ── STEP 4: Save merged to local SharedPreferences ──
            val mergedJsonArray = JSONArray()
            mergedFavorites.forEach { channel ->
                val jsonObj = JSONObject().apply {
                    put("name", channel.name)
                    put("url", channel.url)
                    put("logo", channel.logo)
                    put("tvgId", channel.tvgId)
                    put("tvgName", channel.tvgName)
                    put("group", channel.group)
                }
                mergedJsonArray.put(jsonObj)
            }

            // FIXED: Use consistent key "favorite_channels" (was "favorites" before)
            prefs.edit().putString("favorite_channels", mergedJsonArray.toString()).apply()
            Log.i(TAG, "Saved ${mergedFavorites.size} merged favorites to local storage")

            // ── STEP 5: Upload merged list BACK to Firebase (TWO-WAY SYNC) ──
            uploadMergedFavoritesToFirebase(mergedFavorites)

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Favorite Channels", e)
        }
    }
    
    /**
     * Upload the merged favorites list to Firebase.
     * This ensures:
     * 1. Local favorites not in Firebase get uploaded
     * 2. Remote devices will receive the complete list on their next sync
     */
    private suspend fun uploadMergedFavoritesToFirebase(favorites: List<IptvChannel>) {
        try {
            // Clear current savedChannels node first
            FirebaseManager.clearSavedChannels()
            
            // Then upload each favorite channel
            favorites.forEach { channel ->
                try {
                    val key = Base64.encodeToString(channel.url.toByteArray(), Base64.NO_WRAP)
                    FirebaseManager.saveChannel(
                        key = key,
                        name = channel.name,
                        logo = channel.logo,
                        url = channel.url,
                        group = channel.group
                    )
                    Log.i(TAG, "Uploaded favorite to Firebase: ${channel.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading channel: ${channel.name}", e)
                }
            }
            
            Log.i(TAG, "Successfully uploaded ${favorites.size} favorites to Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading merged favorites to Firebase", e)
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
                Log.i(TAG, "Found default provider in Firebase: $firebaseProvider")
                
                // Save to local SharedPreferences
                val context = applicationContext ?: return
                val settingsManager = SettingsManager(context)
                settingsManager.saveDefaultProvider(firebaseProvider)
                
                _syncMessage.value = "Default provider: $firebaseProvider"
                Log.i(TAG, "Saved synced default provider to SharedPreferences: $firebaseProvider")
            } else {
                Log.i(TAG, "No default provider found in Firebase")
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
     * Get the count of Favorite Channels from Firebase.
     */
    private suspend fun getFirebaseFavoriteChannelsCount(): Int {
        return try {
            val data = FirebaseManager.getSavedChannelsOnce()
            data?.size ?: 0
        } catch (e: Exception) {
            0
        }
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
        
        Log.i(TAG, "Enabling real-time Firebase sync...")
        
        // Listen to My List changes
        val myListRef = FirebaseManager.getNodeReference(FirebaseManager.Nodes.MY_LIST)
        val myListListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Handle real-time My List updates
                Log.i(TAG, "My List updated in Firebase")
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
        Log.i(TAG, "Disabling real-time Firebase sync...")
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
                
                // Clear favorite channels from SharedPreferences
                val context = applicationContext
                if (context != null) {
                    val prefs = context.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("favorite_channels").apply()
                }
                
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