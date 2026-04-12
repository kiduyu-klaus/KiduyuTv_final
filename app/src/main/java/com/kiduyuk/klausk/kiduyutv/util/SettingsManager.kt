package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDefaultProvider(provider: String) {
        preferences.edit().putString(KEY_DEFAULT_PROVIDER, provider).apply()
    }

    /** Returns the saved default provider name, or [AUTO] if none is set. */
    fun getDefaultProvider(): String {
        return preferences.getString(KEY_DEFAULT_PROVIDER, AUTO) ?: AUTO
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"

        /** Sentinel value meaning "ask me each time" — no automatic selection. */
        const val AUTO = "Auto"

        /** All active providers in display order. */
        val PROVIDERS = listOf("Videasy", "VidLink", "VidFast", "VidKing", "Flixer")
    }
}
