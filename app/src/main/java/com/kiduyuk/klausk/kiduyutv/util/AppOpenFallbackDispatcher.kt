package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log

/** Shows an available AdMob app-open ad and keeps its next request warm. */
object AppOpenFallbackDispatcher {
    private const val TAG = "AppOpenFallbackDispatcher"

    fun show(activity: Activity, onComplete: () -> Unit = {}) {
        if (!FullscreenAdPolicy.tryBeginRequest()) {
            Log.i(TAG, "App-open fallback: suppressed because another fullscreen request is active")
            onComplete()
            return
        }
        val complete = {
            // The request guard covers loading/showing, not the persisted cooldown.
            FullscreenAdPolicy.endRequest()
            onComplete()
        }
        if (!AdEligibility.canRequestAds(activity)) {
            complete()
            return
        }
        if (!FullscreenAdPolicy.canShowAppOpen(activity)) {
            Log.i(TAG, "App-open fallback: suppressed by fullscreen cooldown")
            complete()
            return
        }

        when {
            AdManager.isAppOpenReady -> {
                Log.i(TAG, "App-open fallback: AdMob")
                AdManager.showAppOpenIfAvailable(activity, complete)
            }
            else -> {
                Log.i(TAG, "App-open fallback: no ad ready")
                AdManager.preloadAppOpen(activity)
                complete()
            }
        }
    }
}
