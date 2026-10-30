package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log

/** Shows an available AdMob app-open ad and keeps its next request warm. */
object AppOpenFallbackDispatcher {
    private const val TAG = "AppOpenFallbackDispatcher"

    fun show(activity: Activity, onComplete: () -> Unit = {}) {
        if (!AdEligibility.canRequestAds(activity)) {
            onComplete()
            return
        }
        if (!FullscreenAdPolicy.canShowAppOpen(activity)) {
            Log.i(TAG, "App-open fallback: suppressed by fullscreen cooldown")
            onComplete()
            return
        }

        when {
            AdManager.isAppOpenReady -> {
                Log.i(TAG, "App-open fallback: AdMob")
                AdManager.showAppOpenIfAvailable(activity, onComplete)
            }
            else -> {
                Log.i(TAG, "App-open fallback: no ad ready")
                AdManager.preloadAppOpen(activity)
                onComplete()
            }
        }
    }
}
