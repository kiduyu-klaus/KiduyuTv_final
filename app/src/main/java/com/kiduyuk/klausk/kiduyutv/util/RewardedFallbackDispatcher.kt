package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Selects one rewarded provider at a time and preserves reward/dismiss callbacks. */
object RewardedFallbackDispatcher {
    private const val TAG = "RewardedFallbackDispatcher"
    private const val ADMOB_WAIT_MS = 1_500L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        showMainThread(activity, "rewarded", onRewarded, onDismissed) {
            AdManager.isRewardedReady
        }
    }

    fun showInterstitial(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        showMainThread(activity, "rewarded_interstitial", onRewarded, onDismissed) {
            AdManager.isRewardedInterstitialReady
        }
    }

    private fun showMainThread(
        activity: Activity,
        format: String,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit,
        isAdMobReady: () -> Boolean
    ) {
        val task = Runnable {
            showInternal(activity, format, onRewarded, onDismissed, isAdMobReady)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) task.run() else mainHandler.post(task)
    }

    private fun showInternal(
        activity: Activity,
        format: String,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit,
        isAdMobReady: () -> Boolean
    ) {
        val dismissed = AtomicBoolean(false)
        val rewardDelivered = AtomicBoolean(false)
        val finish = {
            if (dismissed.compareAndSet(false, true)) runOnMain(onDismissed)
        }
        val deliverReward = {
            if (!dismissed.get() && rewardDelivered.compareAndSet(false, true)) {
                runOnMain {
                    if (!dismissed.get()) onRewarded()
                }
            }
        }

        if (!AdEligibility.canRequestAds(activity)) {
            AdEventReporter.report("reward_unlock", format, "none", "suppressed_ineligible")
            finish()
            return
        }

        fun showStandardFallback() {
            if (activity.isFinishing || !AdEligibility.canRequestAds(activity)) {
                finish()
                return
            }
            Log.i(TAG, "$format fallback: no rewarded ad after bounded wait; trying standard interstitial")
            AdEventReporter.report(
                "reward_unlock",
                format,
                "none",
                "rewarded_not_ready_fallback_interstitial"
            )
            // Keep rewarded inventory warm for the next explicit support request.
            if (format == "rewarded") AdManager.preloadRewarded(activity)
            else AdManager.preloadRewardedInterstitial(activity)
            UnityAdManager.preloadAds(activity)
            InterstitialFallbackDispatcher.show(
                activity = activity,
                placement = InterstitialFallbackDispatcher.Placement.CONTENT_TRANSITION,
                onDismissed = finish
            )
        }

        fun showAdMobOrFallback() {
            fun showReadyAdMob() {
                if (activity.isFinishing || !AdEligibility.canRequestAds(activity)) {
                    finish()
                    return
                }
                if (!isAdMobReady()) {
                    showStandardFallback()
                    return
                }
                Log.i(TAG, "$format fallback: AdMob")
                if (format == "rewarded") {
                    AdManager.showRewarded(activity, deliverReward, finish)
                } else {
                    AdManager.showRewardedInterstitial(activity, deliverReward, finish)
                }
            }

            if (isAdMobReady()) {
                showReadyAdMob()
                return
            }

            Log.i(TAG, "$format AdMob inventory not ready; requesting a bounded preload wait")
            AdInventoryWaiter.awaitReady(
                timeoutMs = ADMOB_WAIT_MS,
                isReady = isAdMobReady,
                requestLoad = {
                    if (format == "rewarded") AdManager.preloadRewarded(activity)
                    else AdManager.preloadRewardedInterstitial(activity)
                },
                shouldContinue = {
                    !activity.isFinishing && AdEligibility.canRequestAds(activity)
                },
                onReady = ::showReadyAdMob,
                onTimeout = ::showStandardFallback
            )
        }

        fun showStartIoOrFallback() {
            if (activity.isFinishing || !AdEligibility.canRequestAds(activity)) {
                finish()
                return
            }
            Log.i(TAG, "$format fallback: Start.io")
            StartAppAdManager.showRewarded(
                activity = activity,
                onRewarded = deliverReward,
                onDismissed = finish,
                onUnavailable = { mainHandler.post { showAdMobOrFallback() } }
            )
        }

        if (UnityAdManager.isRewardedReady) {
            Log.i(TAG, "$format fallback: Unity")
            UnityAdManager.showRewarded(
                activity = activity,
                onRewarded = deliverReward,
                onDismissed = finish,
                onUnavailable = { mainHandler.post { showStartIoOrFallback() } }
            )
        } else {
            showStartIoOrFallback()
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }
}
