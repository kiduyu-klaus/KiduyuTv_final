package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity

/**
 * TV-specific entry point for fullscreen ads shown before launching playback.
 */
object TvInterstitialManager {

    /** Shows a real interstitial when available, then launches the player. */
    fun showAndThenLaunch(activity: Activity, onDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismissed()
            return
        }
        InterstitialFallbackDispatcher.show(
            activity = activity,
            placement = InterstitialFallbackDispatcher.Placement.TV_PLAYER_LAUNCH,
            onDismissed = onDismissed
        )
    }

    /** Back navigation is never monetized; return immediately. */
    fun showAndThen(activity: Activity, onDismissed: () -> Unit) {
        onDismissed()
    }

    val isReady: Boolean
        get() = AdManager.isInterstitialReady
}
