package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log

/**
 * TV interstitial facade — delegates to [InterstitialFallbackDispatcher].
 */
object TvInterstitialManager {

    private const val TAG = "TvInterstitialManager"

    /**
     * Shows an interstitial ad, then launches [onDismissed].
     * Used before PlayerActivity launches on TV.
     */
    fun showAndThenLaunch(activity: Activity, onDismissed: () -> Unit) {
        Log.i(TAG, "Requesting interstitial via InterstitialFallbackDispatcher")
        InterstitialFallbackDispatcher.show(
            activity,
            InterstitialFallbackDispatcher.Placement.TV_PLAYER_LAUNCH,
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
