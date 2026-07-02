package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log

/**
 * TV interstitial facade — delegates to [AdFallbackDispatcher] which handles
 * the active AdMob-only ad path and enforces the 3-minute cooldown.
 */
object TvInterstitialManager {

    private const val TAG = "TvInterstitialManager"

    /**
     * Shows an interstitial ad, then launches [onDismissed].
     * Used before PlayerActivity launches on TV.
     */
    fun showAndThenLaunch(activity: Activity, onDismissed: () -> Unit) {
        Log.i(TAG, "Requesting interstitial via AdFallbackDispatcher")
        AdFallbackDispatcher.showInterstitial(activity, onDismissed)
    }

    /**
     * Shows an interstitial ad, then calls [onDismissed].
     * Used for back navigation on TV detail screens.
     */
    fun showAndThen(activity: Activity, onDismissed: () -> Unit) {
        Log.i(TAG, "Requesting interstitial via AdFallbackDispatcher")
        AdFallbackDispatcher.showInterstitial(activity, onDismissed)
    }

    val isReady: Boolean
        get() = AdManager.isInterstitialReady
}
