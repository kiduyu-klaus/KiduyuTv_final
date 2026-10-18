package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log

/** Selects one rewarded provider at a time and preserves reward/dismiss callbacks. */
object RewardedFallbackDispatcher {
    private const val TAG = "RewardedFallbackDispatcher"

    fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        showInternal(activity, "rewarded", onRewarded, onDismissed) {
            AdManager.isRewardedReady
        }
    }

    fun showInterstitial(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        showInternal(activity, "rewarded_interstitial", onRewarded, onDismissed) {
            AdManager.isRewardedInterstitialReady
        }
    }

    private fun showInternal(
        activity: Activity,
        format: String,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit,
        isAdMobReady: () -> Boolean
    ) {
        if (!AdEligibility.canRequestAds(activity)) {
            AdEventReporter.report("reward_unlock", format, "none", "suppressed_ineligible")
            onDismissed()
            return
        }

        fun fallBackToStandardInterstitial() {
            Log.i(TAG, "$format fallback: no rewarded ad ready; trying standard interstitial")
            AdEventReporter.report(
                "reward_unlock",
                format,
                "none",
                "rewarded_not_ready_fallback_interstitial"
            )

            // Keep the rewarded inventory warm for the next voluntary support request.
            if (format == "rewarded") AdManager.preloadRewarded(activity)
            else AdManager.preloadRewardedInterstitial(activity)
            UnityAdManager.preloadAds(activity)

            // A standard interstitial has no reward callback. It is only an optional
            // monetisation fallback, so completion simply returns control to the UI.
            InterstitialFallbackDispatcher.show(
                activity = activity,
                placement = InterstitialFallbackDispatcher.Placement.CONTENT_TRANSITION,
                onDismissed = onDismissed
            )
        }

        fun showAdMobOrInterstitialFallback() {
            when {
                isAdMobReady() && format == "rewarded" -> {
                    Log.i(TAG, "$format fallback: AdMob")
                    AdManager.showRewarded(activity, onRewarded, onDismissed)
                }
                isAdMobReady() && format == "rewarded_interstitial" -> {
                    Log.i(TAG, "$format fallback: AdMob")
                    AdManager.showRewardedInterstitial(activity, onRewarded, onDismissed)
                }
                else -> {
                    fallBackToStandardInterstitial()
                }
            }
        }

        fun showStartIoOrFallback() {
            Log.i(TAG, "$format fallback: Start.io")
            StartAppAdManager.showRewarded(
                activity = activity,
                onRewarded = onRewarded,
                onDismissed = onDismissed,
                onUnavailable = ::showAdMobOrInterstitialFallback
            )
        }

        if (UnityAdManager.isRewardedReady) {
            Log.i(TAG, "$format fallback: Unity")
            UnityAdManager.showRewarded(
                activity = activity,
                onRewarded = onRewarded,
                onDismissed = onDismissed,
                onUnavailable = ::showStartIoOrFallback
            )
        } else {
            showStartIoOrFallback()
        }
    }
}
