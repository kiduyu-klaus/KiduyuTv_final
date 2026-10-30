package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Selects one interstitial network at a time and guarantees completion. */
object InterstitialFallbackDispatcher {
    private const val TAG = "InterstitialFallbackDispatcher"
    private const val ADMOB_WAIT_MS = 1_500L
    private val mainHandler = Handler(Looper.getMainLooper())

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
        val task = Runnable { showOnMain(activity, placement, onDismissed) }
        if (Looper.myLooper() == Looper.getMainLooper()) task.run() else mainHandler.post(task)
    }

    private fun showOnMain(activity: Activity, placement: Placement, onDismissed: () -> Unit) {
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

        fun showAdMobOrFinish() {
            if (!fallbackStarted.compareAndSet(false, true)) return

            fun showReadyAdMob() {
                if (activity.isFinishing || !AdEligibility.canRequestAds(activity)) {
                    AdEventReporter.report(placement.name, "interstitial", "none", "suppressed_ineligible")
                    complete()
                    return
                }
                if (!AdManager.isInterstitialReady) {
                    AdEventReporter.report(placement.name, "interstitial", "none", "not_ready")
                    complete()
                    return
                }
                Log.i(TAG, "Interstitial fallback: AdMob")
                AdEventReporter.report(placement.name, "interstitial", "admob", "show_requested")
                AdManager.showInterstitial(activity, complete)
            }

            if (AdManager.isInterstitialReady) {
                showReadyAdMob()
                return
            }
            Log.i(TAG, "AdMob interstitial not ready; requesting inventory for a bounded wait")
            AdInventoryWaiter.awaitReady(
                timeoutMs = ADMOB_WAIT_MS,
                isReady = { AdManager.isInterstitialReady },
                requestLoad = { AdManager.preloadInterstitial(activity) },
                shouldContinue = {
                    !activity.isFinishing && AdEligibility.canRequestAds(activity)
                },
                onReady = ::showReadyAdMob,
                onTimeout = {
                    if (!activity.isFinishing && AdEligibility.canRequestAds(activity)) {
                        Log.i(TAG, "Interstitial fallback: no AdMob inventory after bounded wait")
                        AdEventReporter.report(placement.name, "interstitial", "none", "not_ready")
                        UnityAdManager.preloadAds(activity)
                    } else {
                        AdEventReporter.report(
                            placement.name,
                            "interstitial",
                            "none",
                            "suppressed_ineligible"
                        )
                    }
                    complete()
                }
            )
        }

        fun showStartIoOrFallback() {
            Log.i(TAG, "Interstitial fallback: Start.io")
            AdEventReporter.report(placement.name, "interstitial", "startio", "show_requested")
            StartAppAdManager.showInterstitial(
                activity = activity,
                onDismissed = complete,
                onUnavailable = { mainHandler.post { showAdMobOrFinish() } }
            )
        }

        if (UnityAdManager.isInterstitialReady) {
            Log.i(TAG, "Interstitial fallback: Unity")
            AdEventReporter.report(placement.name, "interstitial", "unity", "show_requested")
            UnityAdManager.showInterstitial(
                activity = activity,
                onDismissed = complete,
                onUnavailable = { mainHandler.post { showStartIoOrFallback() } }
            )
        } else {
            showStartIoOrFallback()
        }
    }

    private fun once(callback: () -> Unit): () -> Unit {
        val invoked = AtomicBoolean(false)
        return {
            if (invoked.compareAndSet(false, true)) {
                if (Looper.myLooper() == Looper.getMainLooper()) callback()
                else mainHandler.post { callback() }
            }
        }
    }
}
