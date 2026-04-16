package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

class SettingsManager(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext: Context = context.applicationContext

    fun saveDefaultProvider(provider: String) {
        preferences.edit().putString(KEY_DEFAULT_PROVIDER, provider).apply()
    }

    /** Returns the saved default provider name, or [AUTO] if none is set. */
    fun getDefaultProvider(): String {
        return preferences.getString(KEY_DEFAULT_PROVIDER, AUTO) ?: AUTO
    }

    /**
     * Returns a unique device ID for Firebase user identification.
     * Uses Android ID if available, otherwise generates and saves a UUID.
     */
    fun getDeviceId(): String {
        // Try to get existing saved device ID
        val savedId = preferences.getString(KEY_DEVICE_ID, null)
        if (savedId != null) {
            return savedId
        }

        // Try to get Android ID
        val androidId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        // Generate a new unique ID
        val newId = androidId ?: UUID.randomUUID().toString()
        
        // Save for future use
        preferences.edit().putString(KEY_DEVICE_ID, newId).apply()
        
        return newId
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_DEVICE_ID = "device_id"

        /** Sentinel value meaning "ask me each time" — no automatic selection. */
        const val AUTO = "Auto"

        /** All active providers in display order. */
        val PROVIDERS = listOf("Videasy", "VidLink", "VidFast", "VidKing", "Flixer")
    }
}
