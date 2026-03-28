package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDefaultProvider(provider: String) {
        preferences.edit().putString(KEY_DEFAULT_PROVIDER, provider).apply()
    }

    fun getDefaultProvider(): String? {
        return preferences.getString(KEY_DEFAULT_PROVIDER, null)
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
    }
}
