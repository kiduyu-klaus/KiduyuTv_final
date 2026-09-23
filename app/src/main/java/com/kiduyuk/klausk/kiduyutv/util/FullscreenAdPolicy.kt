package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.os.SystemClock

/** One process-wide, persisted cooldown for every interstitial network. */
object FullscreenAdPolicy {
    private const val PREFS = "ad_policy"
    private const val KEY_LAST_INTERSTITIAL = "last_interstitial_elapsed_ms"
    private const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L

    fun canShowInterstitial(context: Context): Boolean {
        val lastShown = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_INTERSTITIAL, 0L)
        return lastShown == 0L ||
            SystemClock.elapsedRealtime() - lastShown >= INTERSTITIAL_COOLDOWN_MS
    }

    fun recordInterstitialShown(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_INTERSTITIAL, SystemClock.elapsedRealtime())
            .apply()
    }
}
