package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Selects one interstitial network at a time and guarantees completion. */
object InterstitialFallbackDispatcher {
    private const val TAG = "InterstitialFallbackDispatcher"

    enum class Placement {
        CONTENT_TRANSITION,
        PLAYER_LAUNCH,
        TV_PLAYER_LAUNCH
    }

    fun show(
        activity: Activity,
        placement: Placement = Placement.CONTENT_TRANSITION,
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
            when {
                AdManager.isInterstitialReady -> {
                    Log.i(TAG, "Interstitial fallback: AdMob")
                    AdEventReporter.report(placement.name, "interstitial", "admob", "show_requested")
                    AdManager.showInterstitial(activity, complete)
                }
                WortiseAdManager.isInterstitialReady -> {
                    Log.i(TAG, "Interstitial fallback: Wortise")
                    AdEventReporter.report(placement.name, "interstitial", "wortise", "show_requested")
                    WortiseAdManager.showInterstitial(activity, complete)
                }
                else -> {
                    Log.i(TAG, "Interstitial fallback: no ad ready")
                    AdEventReporter.report(placement.name, "interstitial", "none", "not_ready")
                    complete()
                    AdManager.preloadInterstitial(activity)
                    UnityAdManager.preloadAds(activity)
                    WortiseAdManager.preloadAds(activity)
                }
            }
        }

        fun showStartIoOrFallback() {
            Log.i(TAG, "Interstitial fallback: Start.io")
            AdEventReporter.report(placement.name, "interstitial", "startio", "show_requested")
            StartAppAdManager.showInterstitial(
                activity = activity,
                onDismissed = complete,
                onUnavailable = ::showAdMobOrWortise
            )
        }

        if (UnityAdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial fallback: Unity")
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

    private fun once(callback: () -> Unit): () -> Unit {
        val invoked = AtomicBoolean(false)
        return { if (invoked.compareAndSet(false, true)) callback() }
    }
}
