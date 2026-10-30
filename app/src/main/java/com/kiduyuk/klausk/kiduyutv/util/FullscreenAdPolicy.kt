package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context

/** One persisted cooldown shared by interstitial and app-open fullscreen ads. */
object FullscreenAdPolicy {
    private const val PREFS = "ad_policy"
    private const val KEY_LAST_INTERSTITIAL_WALL_MS = "last_interstitial_wall_ms"
    private const val KEY_LAST_APP_OPEN_WALL_MS = "last_app_open_wall_ms"
    private const val FULLSCREEN_COOLDOWN_MS = 2 * 60 * 1000L

    fun canShowInterstitial(context: Context): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return cooldownElapsed(now, preferences.getLong(KEY_LAST_INTERSTITIAL_WALL_MS, 0L)) &&
            cooldownElapsed(now, preferences.getLong(KEY_LAST_APP_OPEN_WALL_MS, 0L))
    }

    fun recordInterstitialShown(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_INTERSTITIAL_WALL_MS, System.currentTimeMillis())
            .apply()
    }

    fun canShowAppOpen(context: Context): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return cooldownElapsed(now, preferences.getLong(KEY_LAST_INTERSTITIAL_WALL_MS, 0L)) &&
            cooldownElapsed(now, preferences.getLong(KEY_LAST_APP_OPEN_WALL_MS, 0L))
    }

    fun recordAppOpenShown(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_APP_OPEN_WALL_MS, System.currentTimeMillis())
            .apply()
    }

    /** A backward clock adjustment fails open instead of suppressing ads indefinitely. */
    private fun cooldownElapsed(now: Long, previous: Long): Boolean =
        previous <= 0L || now < previous || now - previous >= FULLSCREEN_COOLDOWN_MS
}
