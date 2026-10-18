package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log

/** Selects one app-open provider at a time and preloads both for the next foreground. */
object AppOpenFallbackDispatcher {
    private const val TAG = "AppOpenFallbackDispatcher"

    fun show(activity: Activity, onComplete: () -> Unit = {}) {
        if (!AdEligibility.canRequestAds(activity)) {
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
