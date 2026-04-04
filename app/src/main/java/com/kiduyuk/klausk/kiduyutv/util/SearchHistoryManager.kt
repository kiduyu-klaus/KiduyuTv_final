package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages search history persistence using SharedPreferences.
 * Stores recent search queries and allows retrieval, addition, and clearing.
 */
object SearchHistoryManager {
    private const val TAG = "SearchHistoryManager"
    private const val PREFS_NAME = "search_history_prefs"
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    private const val MAX_RECENT_SEARCHES = 10

    private var prefs: SharedPreferences? = null

    /**
     * Initialize the SearchHistoryManager with a context.
     * Must be called before any other operations.
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "SearchHistoryManager initialized")
        }
    }

    /**
     * Gets the list of recent search queries.
     * @return List of recent search queries, most recent first.
     */
    fun getRecentSearches(): List<String> {
        val preferences = prefs
        if (preferences == null) {
            Log.w(TAG, "SearchHistoryManager not initialized, returning empty list")
            return emptyList()
        }

        return try {
            val searchesString = preferences.getString(KEY_RECENT_SEARCHES, "") ?: ""
            if (searchesString.isEmpty()) {
                emptyList()
            } else {
                searchesString.split("|||").filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent searches: ${e.message}")
            emptyList()
        }
    }

    /**
     * Adds a search query to the recent searches list.
     * If the query already exists, it's moved to the top.
     * Maximum of MAX_RECENT_SEARCHES are kept.
     * 
     * @param query The search query to add.
     */
    fun addRecentSearch(query: String) {
        val preferences = prefs
        if (preferences == null) {
            Log.w(TAG, "SearchHistoryManager not initialized, cannot add search")
            return
        }

        if (query.isBlank()) {
            return
        }

        try {
            val currentSearches = getRecentSearches().toMutableList()

            // Remove if already exists (to move it to the top)
            currentSearches.remove(query)

            // Add to the beginning
            currentSearches.add(0, query)

            // Keep only the most recent searches
            val trimmedSearches = currentSearches.take(MAX_RECENT_SEARCHES)

            // Save to SharedPreferences
            val searchesString = trimmedSearches.joinToString("|||")
            preferences.edit().putString(KEY_RECENT_SEARCHES, searchesString).apply()

            Log.d(TAG, "Added search: $query. Total searches: ${trimmedSearches.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding recent search: ${e.message}")
        }
    }

    /**
     * Removes a specific search query from the recent searches list.
     * 
     * @param query The search query to remove.
     */
    fun removeRecentSearch(query: String) {
        val preferences = prefs
        if (preferences == null) {
            Log.w(TAG, "SearchHistoryManager not initialized, cannot remove search")
            return
        }

        try {
            val currentSearches = getRecentSearches().toMutableList()
            currentSearches.remove(query)

            val searchesString = currentSearches.joinToString("|||")
            preferences.edit().putString(KEY_RECENT_SEARCHES, searchesString).apply()

            Log.d(TAG, "Removed search: $query")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing recent search: ${e.message}")
        }
    }

    /**
     * Clears all recent searches.
     */
    fun clearRecentSearches() {
        val preferences = prefs
        if (preferences == null) {
            Log.w(TAG, "SearchHistoryManager not initialized, cannot clear searches")
            return
        }

        try {
            preferences.edit().remove(KEY_RECENT_SEARCHES).apply()
            Log.d(TAG, "Cleared all recent searches")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing recent searches: ${e.message}")
        }
    }

    /**
     * Checks if there are any recent searches.
     * 
     * @return true if there are recent searches, false otherwise.
     */
    fun hasRecentSearches(): Boolean {
        return getRecentSearches().isNotEmpty()
    }
}
