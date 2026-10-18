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

        fun showAdMobOrWortise() {
            when {
                isAdMobReady() && format == "rewarded" -> {
                    Log.i(TAG, "$format fallback: AdMob")
                    AdManager.showRewarded(activity, onRewarded, onDismissed)
                }
                isAdMobReady() && format == "rewarded_interstitial" -> {
                    Log.i(TAG, "$format fallback: AdMob")
                    AdManager.showRewardedInterstitial(activity, onRewarded, onDismissed)
                }
                WortiseAdManager.isRewardedReady -> {
                    Log.i(TAG, "$format fallback: Wortise")
                    WortiseAdManager.showRewarded(activity, onRewarded, onDismissed)
                }
                else -> {
                    Log.i(TAG, "$format fallback: no ad ready")
                    AdEventReporter.report("reward_unlock", format, "none", "not_ready")
                    onDismissed()
                    if (format == "rewarded") AdManager.preloadRewarded(activity)
                    else AdManager.preloadRewardedInterstitial(activity)
                    UnityAdManager.preloadAds(activity)
                    WortiseAdManager.preloadAds(activity)
                }
            }
        }

        fun showStartIoOrFallback() {
            Log.i(TAG, "$format fallback: Start.io")
            StartAppAdManager.showRewarded(
                activity = activity,
                onRewarded = onRewarded,
                onDismissed = onDismissed,
                onUnavailable = ::showAdMobOrWortise
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
