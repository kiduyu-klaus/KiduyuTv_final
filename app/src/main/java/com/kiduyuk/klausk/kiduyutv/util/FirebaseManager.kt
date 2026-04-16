package com.kiduyuk.klausk.kiduyutv.util

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * FirebaseManager - Centralized manager for Firebase Realtime Database operations.
 * 
 * This class provides a clean API for syncing user data across devices:
 * - My List (favorited movies/TV shows)
 * - Saved Companies (production companies)
 * - Saved Networks (TV networks)
 * - Watch History (optional sync)
 * - User Preferences
 * 
 * Usage:
 *   FirebaseManager.syncMyListItem(movie)
 *   FirebaseManager.removeFromMyList(tmdbId)
 *   FirebaseManager.getMyListFlow().collect { items -> ... }
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"
    
    // Database reference
    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }
    
    // Base path for all user data
    private const val USERS_PATH = "users"
    
    // Database node names
    object Nodes {
        const val MY_LIST = "myList"
        const val SAVED_COMPANIES = "savedCompanies"
        const val SAVED_NETWORKS = "savedNetworks"
        const val WATCH_HISTORY = "watchHistory"
        const val PREFERENCES = "preferences"
    }
    
    /**
     * Get the user-specific base path.
     * For anonymous users, we use a device ID.
     * For authenticated users, we use their UID.
     */
    private fun getUserPath(userId: String): String {
        return "$USERS_PATH/$userId"
    }
    
    // Default user ID (device-based, can be replaced with Firebase Auth user ID)
    private var currentUserId: String = "anonymous_device"
    
    /**
     * Initialize FirebaseManager with a user ID.
     * Call this after getting or creating a user identifier.
     */
    fun init(userId: String) {
        currentUserId = userId
    }
    
    /**
     * Get the current user path
     */
    private fun getCurrentUserPath(): String {
        return getUserPath(currentUserId)
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // MY LIST OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Add or update an item in the user's My List.
     * 
     * @param tmdbId The TMDB ID of the movie or TV show
     * @param isTv Whether it's a TV show (true) or movie (false)
     * @param title The title of the content
     * @param posterPath The poster image path
     * @param backdropPath The backdrop image path
     * @param voteAverage The vote average rating
     * @param addedAt Timestamp when added (optional)
     */
    fun syncMyListItem(
        tmdbId: Int,
        isTv: Boolean,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        voteAverage: Double?,
        addedAt: Long = System.currentTimeMillis()
    ) {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.MY_LIST}/$tmdbId")
        
        val item = mapOf(
            "tmdbId" to tmdbId,
            "isTv" to isTv,
            "title" to title,
            "posterPath" to posterPath,
            "backdropPath" to backdropPath,
            "voteAverage" to voteAverage,
            "addedAt" to addedAt,
            "lastUpdated" to System.currentTimeMillis()
        )
        
        ref.setValue(item)
    }
    
    /**
     * Remove an item from the user's My List.
     */
    fun removeFromMyList(tmdbId: Int) {
        database.getReference("${getCurrentUserPath()}/${Nodes.MY_LIST}/$tmdbId")
            .removeValue()
    }
    
    /**
     * Check if an item is in the user's My List.
     */
    fun isInMyList(tmdbId: Int, callback: (Boolean) -> Unit) {
        database.getReference("${getCurrentUserPath()}/${Nodes.MY_LIST}/$tmdbId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.exists())
                }
                
                override fun onCancelled(error: DatabaseError) {
                    callback(false)
                }
            })
    }
    
    /**
     * Get a Flow of the user's My List for reactive updates.
     */
    fun getMyListFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.MY_LIST}")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.value as? Map<String, Any>
                trySend(data)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    
    /**
     * Get My List items synchronously (for one-time read).
     */
    suspend fun getMyListOnce(): Map<String, Any>? {
        return try {
            database.getReference("${getCurrentUserPath()}/${Nodes.MY_LIST}")
                .get()
                .await()
                .value as? Map<String, Any>
        } catch (e: Exception) {
            null
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // SAVED COMPANIES OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Add a company to saved companies list.
     */
    fun saveCompany(
        companyId: Int,
        name: String,
        logoPath: String?,
        originCountry: String?
    ) {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.SAVED_COMPANIES}/$companyId")
        
        val company = mapOf(
            "companyId" to companyId,
            "name" to name,
            "logoPath" to logoPath,
            "originCountry" to originCountry,
            "savedAt" to System.currentTimeMillis()
        )
        
        ref.setValue(company)
    }
    
    /**
     * Remove a company from saved companies list.
     */
    fun unsaveCompany(companyId: Int) {
        database.getReference("${getCurrentUserPath()}/${Nodes.SAVED_COMPANIES}/$companyId")
            .removeValue()
    }
    
    /**
     * Get saved companies Flow.
     */
    fun getSavedCompaniesFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.SAVED_COMPANIES}")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.value as? Map<String, Any>
                trySend(data)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // SAVED NETWORKS OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Add a network to saved networks list.
     */
    fun saveNetwork(
        networkId: Int,
        name: String,
        logoPath: String?
    ) {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.SAVED_NETWORKS}/$networkId")
        
        val network = mapOf(
            "networkId" to networkId,
            "name" to name,
            "logoPath" to logoPath,
            "savedAt" to System.currentTimeMillis()
        )
        
        ref.setValue(network)
    }
    
    /**
     * Remove a network from saved networks list.
     */
    fun unsaveNetwork(networkId: Int) {
        database.getReference("${getCurrentUserPath()}/${Nodes.SAVED_NETWORKS}/$networkId")
            .removeValue()
    }
    
    /**
     * Get saved networks Flow.
     */
    fun getSavedNetworksFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.SAVED_NETWORKS}")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.value as? Map<String, Any>
                trySend(data)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // WATCH HISTORY SYNC (Optional)
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Sync watch history item to Firebase.
     * Useful for continuing playback on another device.
     */
    fun syncWatchHistory(
        tmdbId: Int,
        isTv: Boolean,
        seasonNumber: Int?,
        episodeNumber: Int?,
        playbackPosition: Long,
        duration: Long,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val path = if (isTv) {
            "${getCurrentUserPath()}/${Nodes.WATCH_HISTORY}/tv/$tmdbId/$seasonNumber/$episodeNumber"
        } else {
            "${getCurrentUserPath()}/${Nodes.WATCH_HISTORY}/movies/$tmdbId"
        }
        
        val item = mapOf(
            "tmdbId" to tmdbId,
            "isTv" to isTv,
            "seasonNumber" to seasonNumber,
            "episodeNumber" to episodeNumber,
            "playbackPosition" to playbackPosition,
            "duration" to duration,
            "progress" to if (duration > 0) (playbackPosition.toDouble() / duration * 100) else 0.0,
            "updatedAt" to updatedAt
        )
        
        database.getReference(path).setValue(item)
    }
    
    /**
     * Get watch history Flow.
     */
    fun getWatchHistoryFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.WATCH_HISTORY}")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.value as? Map<String, Any>
                trySend(data)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // USER PREFERENCES SYNC
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Save a user preference.
     */
    fun savePreference(key: String, value: Any) {
        database.getReference("${getCurrentUserPath()}/${Nodes.PREFERENCES}/$key")
            .setValue(value)
    }
    
    /**
     * Get a user preference.
     */
    suspend fun getPreference(key: String): Any? {
        return try {
            database.getReference("${getCurrentUserPath()}/${Nodes.PREFERENCES}/$key")
                .get()
                .await()
                .value
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get preferences Flow.
     */
    fun getPreferencesFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val ref = database.getReference("${getCurrentUserPath()}/${Nodes.PREFERENCES}")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.value as? Map<String, Any>
                trySend(data)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // CLEAR ALL DATA
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Clear all user data from Firebase (for logout or reset).
     */
    fun clearAllUserData(onComplete: (() -> Unit)? = null) {
        database.getReference(getCurrentUserPath())
            .removeValue()
            .addOnCompleteListener {
                onComplete?.invoke()
            }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // DATABASE REFERENCE ACCESS (Advanced Usage)
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Get direct reference to a specific node.
     * Use for advanced operations not covered by this manager.
     */
    fun getNodeReference(nodePath: String): DatabaseReference {
        return database.getReference("${getCurrentUserPath()}/$nodePath")
    }
    
    /**
     * Get the root database reference.
     */
    fun getDatabase(): FirebaseDatabase = database
}
