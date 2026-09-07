package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup

/**
 * Unified ad dispatcher.
 *
 * Provides a single entry-point for AdMob, Unity Ads, and Start.io. Only one
 * network is shown for each request, so enabling both SDKs never produces
 * stacked interstitials or banners.
 *
 * All methods are safe to call from any thread and always invoke the
 * callback so the app flow never stalls.
 */
object AdFallbackDispatcher {

    private const val TAG = "AdFallbackDispatcher"

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a ready AdMob interstitial, then Unity Ads, then requests Start.io.
     * Always calls [onDismissed] when the ad closes (or immediately if none ready).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (AdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial flow: AdMob")
            AdManager.showInterstitial(activity, onDismissed)
        } else if (UnityAdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial flow: Unity Ads fallback")
            UnityAdManager.showInterstitial(activity, onDismissed)
        } else {
            Log.i(TAG, "Interstitial flow: Start.io fallback")
            StartAppAdManager.showInterstitial(activity, onDismissed)
        }
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
        if (AdManager.isRewardedReady) {
            Log.i(TAG, "Rewarded flow: AdMob")
            AdManager.showRewarded(activity, onRewarded, onDismissed)
        } else if (UnityAdManager.isRewardedReady) {
            Log.i(TAG, "Rewarded flow: Unity Ads fallback")
            UnityAdManager.showRewarded(activity, onRewarded, onDismissed)
        } else {
            Log.i(TAG, "Rewarded flow: Start.io fallback")
            StartAppAdManager.showRewarded(activity, onRewarded, onDismissed)
        }
    }

    /**
     * Shows an AdMob rewarded interstitial ad if one is ready.
     */
    fun showRewardedInterstitial(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        if (AdManager.isRewardedInterstitialReady) {
            Log.i(TAG, "Rewarded interstitial flow: AdMob")
            AdManager.showRewardedInterstitial(activity, onRewarded, onDismissed)
        } else if (UnityAdManager.isRewardedReady) {
            Log.i(TAG, "Rewarded interstitial flow: Unity rewarded fallback")
            UnityAdManager.showRewarded(activity, onRewarded, onDismissed)
        } else {
            Log.i(TAG, "Rewarded interstitial flow: Start.io rewarded fallback")
            StartAppAdManager.showRewarded(activity, onRewarded, onDismissed)
        }
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads the requested banner network. Unknown/legacy networks use AdMob.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        preferred: BannerNetwork? = null
    ) {
        when (preferred) {
            BannerNetwork.STARTAPP -> {
                Log.i(TAG, "Loading banner from: Start.io")
                StartAppAdManager.loadBanner(activity, container)
            }
            BannerNetwork.UNITY -> {
                Log.i(TAG, "Loading banner from: Unity Ads")
                UnityAdManager.loadBanner(activity, container)
            }
            BannerNetwork.WORTISE -> {
                Log.i(TAG, "Loading banner from: Wortise")
                WortiseAdManager.loadBanner(activity, container)
            }
            BannerNetwork.ADMOB, null -> {
                Log.i(TAG, "Loading banner from: AdMob")
                AdManager.loadBanner(activity, container)
            }
        }
    }

    /**
     * Which banner network to request.
     */
    enum class BannerNetwork {
        STARTAPP,
        ADMOB,
        WORTISE,
        UNITY
    }
}
