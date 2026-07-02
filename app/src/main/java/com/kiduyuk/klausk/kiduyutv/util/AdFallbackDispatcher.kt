package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup

/**
 * Unified ad dispatcher.
 *
 * Provides a single entry-point for showing ads while non-AdMob providers are
 * paused. The enum is kept for source compatibility with older call sites, but
 * every request is routed to AdMob.
 *
 * All methods are safe to call from any thread and always invoke the
 * callback so the app flow never stalls.
 */
object AdFallbackDispatcher {

    private const val TAG = "AdFallbackDispatcher"

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows an AdMob interstitial ad if one is ready.
     * Always calls [onDismissed] when the ad closes (or immediately if none ready).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        Log.i(TAG, "Interstitial flow: AdMob only")
        AdManager.showInterstitial(activity, onDismissed)
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows an AdMob rewarded video ad if one is ready.
     *
     * [onRewarded] fires only when the user fully watches the ad.
     * [onDismissed] always fires when the ad closes.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        Log.i(TAG, "Rewarded flow: AdMob only")
        AdManager.showRewarded(activity, onRewarded, onDismissed)
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads an AdMob banner into [container]. [preferred] is ignored while
     * non-AdMob providers are paused.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        preferred: BannerNetwork? = null
    ) {
        if (preferred != null && preferred != BannerNetwork.ADMOB) {
            Log.i(TAG, "Ignoring paused banner provider $preferred; loading AdMob")
        } else {
            Log.i(TAG, "Loading banner from: ADMOB")
        }
        AdManager.loadBanner(activity, container)
    }

    /**
     * Which banner network to request. Non-AdMob values are currently paused
     * and route through AdMob for compatibility.
     */
    enum class BannerNetwork {
        STARTAPP,
        ADMOB,
        WORTISE,
        UNITY
    }
}
