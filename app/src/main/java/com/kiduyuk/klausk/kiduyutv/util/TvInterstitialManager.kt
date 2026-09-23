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
        AdFallbackDispatcher.showInterstitial(
            activity,
            AdFallbackDispatcher.AdPlacement.TV_PLAYER_LAUNCH,
            onDismissed
        )
    }

    /**
     * Back navigation is never monetized; return immediately.
     */
    fun showAndThen(activity: Activity, onDismissed: () -> Unit) {
        onDismissed()
    }

    val isReady: Boolean
        get() = AdManager.isInterstitialReady
}
