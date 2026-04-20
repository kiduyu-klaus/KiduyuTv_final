package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.util.Log
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.local.entity.SavedMediaEntity
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Singleton manager for the user's personal list (My List).
 * Now uses Room database for persistence with Flow-based reactive updates.
 *
 * This implementation provides:
 * - Type-safe database operations
 * - Reactive updates via StateFlow
 * - Automatic persistence
 * - Efficient queries with proper indexing
 *
 * Note: This class now delegates to DatabaseManager for Room operations
 * while maintaining backward compatibility with the existing API.
 */
object MyListManager {

    private const val TAG = "MyListManager"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _myList = MutableStateFlow<List<MyListItem>>(emptyList())
    val myList: StateFlow<List<MyListItem>> = _myList.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes the manager by loading saved items from Room database.
     * Sets up reactive updates from the database.
     */
    fun init(context: Context) {
        if (isInitialized) return

        // Initialize the database
        DatabaseManager.init(context)

        // Observe database changes and convert to MyListItem
        applicationScope.launch {
            DatabaseManager.getMyList().collect { entities ->
                _myList.value = entities.map { entity ->
                    MyListItem(
                        id = entity.id,
                        title = entity.title ?: "",
                        posterPath = entity.posterPath,
                        type = entity.mediaType,
                        voteAverage = entity.voteAverage,
                        character = entity.character,
                        knownForDepartment = entity.knownForDepartment
                    )
                }
            }
        }

        isInitialized = true
    }

    /**
     * Initialize with a custom CoroutineScope.
     * Useful for testing or when you want to control the lifecycle.
     */
    fun initWithScope(context: Context, scope: CoroutineScope) {
        if (isInitialized) return

        DatabaseManager.init(context)

        scope.launch {
            DatabaseManager.getMyList().collect { entities ->
                _myList.value = entities.map { entity ->
                    MyListItem(
                        id = entity.id,
                        title = entity.title ?: "",
                        posterPath = entity.posterPath,
                        type = entity.mediaType,
                        voteAverage = entity.voteAverage,
                        character = entity.character,
                        knownForDepartment = entity.knownForDepartment
                    )
                }
            }
        }

        isInitialized = true
    }

    /**
     * Adds an item to the list and persists to Room database.
     *
     * @param item The item to add
     * @param context Context for database operations (can be null if already initialized)
     */
    fun addItem(item: MyListItem, context: Context? = null) {
        if (!isInitialized && context != null) {
            init(context)
        }

        // Check for duplicates before adding
        val currentList = _myList.value
        if (currentList.none { it.id == item.id && it.type == item.type }) {
            // Optimistically update local state
            val newList = listOf(item) + currentList
            _myList.value = newList

            // Persist to local database
            DatabaseManager.addToMyList(
                id = item.id,
                mediaType = item.type,
                title = item.title,
                posterPath = item.posterPath,
                voteAverage = item.voteAverage,
                character = item.character,
                knownForDepartment = item.knownForDepartment
            )

            // CRITICAL: Sync to Firebase Realtime Database
            // Each item type goes to its OWN table in Firebase:
            // - "movie" and "tv" -> users/{uid}/myList/
            // - "company" -> users/{uid}/savedCompanies/
            // - "network" -> users/{uid}/savedNetworks/
            // - "cast" -> users/{uid}/savedCasts/
            val currentUserPath = FirebaseManager.getCurrentUserPath()
            
            when (item.type) {
                "movie", "tv" -> {
                    // Save movies and TV shows to myList table
                    Log.i(TAG, "Syncing ${item.type} to Firebase path: $currentUserPath/myList/${item.id}")
                    FirebaseManager.syncMyListItem(
                        tmdbId = item.id,
                        isTv = item.type == "tv",
                        title = item.title,
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage
                    )
                }
                "company" -> {
                    // Save companies to savedCompanies table ONLY (not myList)
                    Log.i(TAG, "Syncing company to Firebase path: $currentUserPath/savedCompanies/${item.id}")
                    FirebaseManager.saveCompany(
                        companyId = item.id,
                        name = item.title,
                        logoPath = item.posterPath,
                        originCountry = null
                    )
                }
                "network" -> {
                    // Save networks to savedNetworks table ONLY (not myList)
                    Log.i(TAG, "Syncing network to Firebase path: $currentUserPath/savedNetworks/${item.id}")
                    FirebaseManager.saveNetwork(
                        networkId = item.id,
                        name = item.title,
                        logoPath = item.posterPath
                    )
                }
                "cast" -> {
                    // Save cast members to savedCasts table ONLY (not myList)
                    Log.i(TAG, "Syncing cast to Firebase path: $currentUserPath/savedCasts/${item.id}")
                    FirebaseManager.saveCast(
                        castId = item.id,
                        name = item.title,
                        profilePath = item.posterPath,
                        character = item.character,
                        knownForDepartment = item.knownForDepartment
                    )
                }
                else -> {
                    // For unknown types, save to myList as fallback
                    Log.w(TAG, "Unknown item type: ${item.type}, saving to myList")
                    FirebaseManager.syncMyListItem(
                        tmdbId = item.id,
                        isTv = item.type == "tv",
                        title = item.title,
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage
                    )
                }
            }
        }
    }

    /**
     * Removes an item from the list and removes from Room database.
     *
     * @param itemId The ID of the item to remove
     * @param type The type of the item ("movie", "tv", "company", "network", or "cast")
     * @param context Context for database operations (can be null if already initialized)
     */
    fun removeItem(itemId: Int, type: String, context: Context? = null) {
        if (!isInitialized && context != null) {
            init(context)
        }

        // Optimistically update local state
        val currentList = _myList.value
        val newList = currentList.filter { !(it.id == itemId && it.type == type) }
        _myList.value = newList

        // Remove from local database only
        // As per user requirement, Firebase data should not be deleted even if local data is cleared/removed
        DatabaseManager.removeFromMyList(itemId, type)
    }

    /**
     * Checks if an item is already in the list.
     * This now queries the local state which is synced with the database.
     *
     * @param itemId The ID of the item
     * @param type The type of the item ("movie", "tv", "company", "network", or "cast")
     * @return true if the item is in the list
     */
    fun isInList(itemId: Int, type: String): Boolean {
        return _myList.value.any { it.id == itemId && it.type == type }
    }

    /**
     * Checks if an item is in the list by querying the database directly.
     * Use this when you need the most up-to-date information.
     *
     * @param itemId The ID of the item
     * @param type The type of the item ("movie", "tv", "company", "network", or "cast")
     * @return true if the item is in the list
     */
    suspend fun isInListAsync(itemId: Int, type: String): Boolean {
        return DatabaseManager.isInMyList(itemId, type)
    }

    /**
     * Get the count of items in the list.
     */
    fun getListCount(): Int = _myList.value.size

    /**
     * Get items of a specific type (movies or TV shows).
     */
    fun getItemsByType(type: String): List<MyListItem> {
        return _myList.value.filter { it.type == type }
    }

    /**
     * Clear all items from the list.
     */
    fun clearAll(context: Context? = null) {
        if (!isInitialized && context != null) {
            init(context)
        }

        _myList.value = emptyList()

        applicationScope.launch {
            DatabaseManager.savedMediaDao().deleteAllSavedMedia()
        }
    }

    /**
     * Clear items from the list by type.
     *
     * @param type The type of items to clear ("movie", "tv", "company", "network", or "cast")
     * @param context Context for database operations
     */
    fun clearByType(type: String, context: Context? = null) {
        if (!isInitialized && context != null) {
            init(context)
        }

        // Optimistically update local state
        _myList.value = _myList.value.filter { it.type != type }

        // Persist to database
        DatabaseManager.clearMyListByType(type)
    }

    /**
     * Search items in the list by title.
     */
    fun searchItems(query: String): List<MyListItem> {
        return _myList.value.filter { item ->
            item.title.contains(query, ignoreCase = true)
        }
    }

    /**
     * Get items as Flow for reactive updates in Compose.
     */
    fun getMyListFlow(): Flow<List<MyListItem>> {
        return _myList
    }

    /**
     * Toggle an item in/out of the list.
     * If the item is in the list, it will be removed.
     * If the item is not in the list, it will be added.
     *
     * @param item The item to toggle
     * @param context Context for database operations
     * @return true if the item is now in the list, false if it was removed
     */
    fun toggleItem(item: MyListItem, context: Context? = null): Boolean {
        return if (isInList(item.id, item.type)) {
            removeItem(item.id, item.type, context)
            false
        } else {
            addItem(item, context)
            true
        }
    }

    /**
     * Check if the manager is initialized.
     */
    fun isReady(): Boolean = isInitialized
}