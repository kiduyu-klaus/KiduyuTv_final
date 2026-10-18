package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified ad dispatcher.
 *
 * Provides a single entry-point for Unity Ads, Start.io, AdMob, and Wortise. Only one
 * network is shown for each request, so enabling both SDKs never produces
 * stacked interstitials or banners.
 *
 * All methods are safe to call from any thread and always invoke the
 * callback so the app flow never stalls.
 */
object AdFallbackDispatcher {

    private const val TAG = "AdFallbackDispatcher"

    // ── Interstitial ──────────────────────────────────────────────────────

    /** Shows Unity first, then Start.io, AdMob, and Wortise fallback inventory. */
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
        val fallbackStarted = AtomicBoolean(false)
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
        fun showAdMobOrWortise() {
            if (!fallbackStarted.compareAndSet(false, true)) return
            if (AdManager.isInterstitialReady) {
                Log.i(TAG, "Interstitial flow: AdMob fallback")
                AdEventReporter.report(placement.name, "interstitial", "admob", "show_requested")
                AdManager.showInterstitial(activity, complete)
            } else if (WortiseAdManager.isInterstitialReady) {
                Log.i(TAG, "Interstitial flow: Wortise fallback")
                AdEventReporter.report(placement.name, "interstitial", "wortise", "show_requested")
                WortiseAdManager.showInterstitial(activity, complete)
            } else {
                Log.i(TAG, "Interstitial flow: no ad ready; continuing immediately")
                AdEventReporter.report(placement.name, "interstitial", "none", "not_ready")
                complete()
                AdManager.preloadInterstitial(activity)
                UnityAdManager.preloadAds(activity)
                WortiseAdManager.preloadAds(activity)
            }
        }

        fun showStartIoOrFallback() {
            Log.i(TAG, "Interstitial flow: Start.io fallback")
            AdEventReporter.report(placement.name, "interstitial", "startio", "show_requested")
            StartAppAdManager.showInterstitial(
                activity = activity,
                onDismissed = complete,
                onUnavailable = ::showAdMobOrWortise
            )
        }

        if (UnityAdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial flow: Unity primary")
            AdEventReporter.report(placement.name, "interstitial", "unity", "show_requested")
            UnityAdManager.showInterstitial(
                activity = activity,
                onDismissed = complete,
                onUnavailable = ::showStartIoOrFallback
            )
        } else {
            showStartIoOrFallback()
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
        } else {
            fun showAdMobOrWortise() {
                if (AdManager.isRewardedReady) {
                    Log.i(TAG, "Rewarded flow: AdMob fallback")
                    AdManager.showRewarded(activity, onRewarded, complete)
                } else if (WortiseAdManager.isRewardedReady) {
                    Log.i(TAG, "Rewarded flow: Wortise fallback")
                    WortiseAdManager.showRewarded(activity, onRewarded, complete)
                } else {
                    Log.i(TAG, "Rewarded flow: no ad ready")
                    AdEventReporter.report("reward_unlock", "rewarded", "none", "not_ready")
                    complete()
                    AdManager.preloadRewarded(activity)
                    UnityAdManager.preloadAds(activity)
                    WortiseAdManager.preloadAds(activity)
                }
            }
            fun showStartIoOrFallback() {
                Log.i(TAG, "Rewarded flow: Start.io fallback")
                StartAppAdManager.showRewarded(
                    activity = activity,
                    onRewarded = onRewarded,
                    onDismissed = complete,
                    onUnavailable = ::showAdMobOrWortise
                )
            }
            if (UnityAdManager.isRewardedReady) {
                Log.i(TAG, "Rewarded flow: Unity primary")
                UnityAdManager.showRewarded(
                    activity = activity,
                    onRewarded = onRewarded,
                    onDismissed = complete,
                    onUnavailable = ::showStartIoOrFallback
                )
            } else {
                showStartIoOrFallback()
            }
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
        } else {
            fun showAdMobOrWortise() {
                if (AdManager.isRewardedInterstitialReady) {
                    Log.i(TAG, "Rewarded interstitial flow: AdMob fallback")
                    AdManager.showRewardedInterstitial(activity, onRewarded, complete)
                } else if (WortiseAdManager.isRewardedReady) {
                    Log.i(TAG, "Rewarded interstitial flow: Wortise fallback")
                    WortiseAdManager.showRewarded(activity, onRewarded, complete)
                } else {
                    Log.i(TAG, "Rewarded interstitial flow: no ad ready")
                    AdEventReporter.report("reward_unlock", "rewarded_interstitial", "none", "not_ready")
                    complete()
                    AdManager.preloadRewardedInterstitial(activity)
                    UnityAdManager.preloadAds(activity)
                    WortiseAdManager.preloadAds(activity)
                }
            }
            fun showStartIoOrFallback() {
                Log.i(TAG, "Rewarded interstitial flow: Start.io fallback")
                StartAppAdManager.showRewarded(
                    activity = activity,
                    onRewarded = onRewarded,
                    onDismissed = complete,
                    onUnavailable = ::showAdMobOrWortise
                )
            }
            if (UnityAdManager.isRewardedReady) {
                Log.i(TAG, "Rewarded interstitial flow: Unity primary")
                UnityAdManager.showRewarded(
                    activity = activity,
                    onRewarded = onRewarded,
                    onDismissed = complete,
                    onUnavailable = ::showStartIoOrFallback
                )
            } else {
                showStartIoOrFallback()
            }
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
