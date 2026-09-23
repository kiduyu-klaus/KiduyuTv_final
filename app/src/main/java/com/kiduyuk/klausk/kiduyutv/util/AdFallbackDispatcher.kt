package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import java.util.concurrent.atomic.AtomicBoolean

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
     * Shows a ready AdMob interstitial, then a ready Unity ad. If neither is
     * already loaded, playback/navigation continues immediately; this method
     * never starts an on-demand ad load that can hold the caller's callback.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        showInterstitial(activity, AdPlacement.CONTENT_TRANSITION, onDismissed)
    }

    /**
     * Shows only already-loaded inventory. The placement is intentionally
     * reported without user identifiers and lets callers keep product policy
     * visible at the call site.
     */
    fun showInterstitial(
        activity: Activity,
        placement: AdPlacement,
        onDismissed: () -> Unit
    ) {
        val complete = once(onDismissed)
        if (!AdEligibility.canRequestAds(activity)) {
            AdEventReporter.report(placement.name, "interstitial", "none", "suppressed_ineligible")
            complete()
            return
        }
        if (!FullscreenAdPolicy.canShowInterstitial(activity)) {
            AdEventReporter.report(placement.name, "interstitial", "none", "suppressed_cooldown")
            complete()
            return
        }
        if (AdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial flow: AdMob")
            AdEventReporter.report(placement.name, "interstitial", "admob", "show_requested")
            AdManager.showInterstitial(activity, complete)
        } else if (UnityAdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial flow: Unity Ads fallback")
            AdEventReporter.report(placement.name, "interstitial", "unity", "show_requested")
            UnityAdManager.showInterstitial(activity, complete)
        } else {
            Log.i(TAG, "Interstitial flow: no ad ready; continuing immediately")
            AdEventReporter.report(placement.name, "interstitial", "none", "not_ready")
            complete()
            AdManager.preloadInterstitial(activity)
            UnityAdManager.preloadAds(activity)
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
        val complete = once(onDismissed)
        if (!AdEligibility.canRequestAds(activity)) {
            AdEventReporter.report("reward_unlock", "rewarded", "none", "suppressed_ineligible")
            complete()
        } else if (AdManager.isRewardedReady) {
            Log.i(TAG, "Rewarded flow: AdMob")
            AdManager.showRewarded(activity, onRewarded, complete)
        } else if (UnityAdManager.isRewardedReady) {
            Log.i(TAG, "Rewarded flow: Unity Ads fallback")
            UnityAdManager.showRewarded(activity, onRewarded, complete)
        } else {
            // Do not make a user wait while Start.io fetches an on-demand ad.
            Log.i(TAG, "Rewarded flow: no ready ad")
            AdEventReporter.report("reward_unlock", "rewarded", "none", "not_ready")
            complete()
            AdManager.preloadRewarded(activity)
            UnityAdManager.preloadAds(activity)
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
        val complete = once(onDismissed)
        if (!AdEligibility.canRequestAds(activity)) {
            AdEventReporter.report("reward_unlock", "rewarded_interstitial", "none", "suppressed_ineligible")
            complete()
        } else if (AdManager.isRewardedInterstitialReady) {
            Log.i(TAG, "Rewarded interstitial flow: AdMob")
            AdManager.showRewardedInterstitial(activity, onRewarded, complete)
        } else if (UnityAdManager.isRewardedReady) {
            Log.i(TAG, "Rewarded interstitial flow: Unity rewarded fallback")
            UnityAdManager.showRewarded(activity, onRewarded, complete)
        } else {
            Log.i(TAG, "Rewarded interstitial flow: no ready ad")
            AdEventReporter.report("reward_unlock", "rewarded_interstitial", "none", "not_ready")
            complete()
            AdManager.preloadRewardedInterstitial(activity)
            UnityAdManager.preloadAds(activity)
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
        if (!AdEligibility.canRequestAds(activity)) {
            container.removeAllViews()
            AdEventReporter.report("banner", "banner", "none", "suppressed_ineligible")
            return
        }
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

    enum class AdPlacement {
        CONTENT_TRANSITION,
        PLAYER_LAUNCH,
        TV_PLAYER_LAUNCH
    }

    private fun once(callback: () -> Unit): () -> Unit {
        val invoked = AtomicBoolean(false)
        return { if (invoked.compareAndSet(false, true)) callback() }
    }
}
