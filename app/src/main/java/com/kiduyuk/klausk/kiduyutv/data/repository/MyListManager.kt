package com.kiduyuk.klausk.kiduyutv.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the user's personal list (My List).
 * Uses SharedPreferences for persistence and StateFlow for reactive updates.
 */
object MyListManager {
    private const val PREFS_NAME = "kiduyu_tv_prefs"
    private const val KEY_MY_LIST = "my_list"
    private val gson = Gson()
    
    private val _myList = MutableStateFlow<List<MyListItem>>(emptyList())
    val myList: StateFlow<List<MyListItem>> = _myList.asStateFlow()

    /**
     * Initializes the manager by loading saved items from SharedPreferences.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MY_LIST, null)
        if (json != null) {
            val type = object : TypeToken<List<MyListItem>>() {}.type
            val savedList: List<MyListItem> = gson.fromJson(json, type)
            _myList.value = savedList
        }
    }

    /**
     * Adds an item to the list and persists the change.
     */
    fun addItem(item: MyListItem, context: Context) {
        val currentList = _myList.value.toMutableList()
        if (currentList.none { it.id == item.id && it.type == item.type }) {
            currentList.add(0, item) // Add to the top
            updateList(currentList, context)
        }
    }

    /**
     * Removes an item from the list and persists the change.
     */
    fun removeItem(itemId: Int, type: String, context: Context) {
        val currentList = _myList.value.toMutableList()
        currentList.removeAll { it.id == itemId && it.type == type }
        updateList(currentList, context)
    }

    /**
     * Checks if an item is already in the list.
     */
    fun isInList(itemId: Int, type: String): Boolean {
        return _myList.value.any { it.id == itemId && it.type == type }
    }

    private fun updateList(newList: List<MyListItem>, context: Context) {
        _myList.value = newList
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(newList)
        prefs.edit().putString(KEY_MY_LIST, json).apply()
    }
}
