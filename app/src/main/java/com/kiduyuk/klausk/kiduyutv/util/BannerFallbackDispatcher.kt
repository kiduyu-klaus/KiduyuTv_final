package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import java.util.concurrent.atomic.AtomicBoolean

/** Selects one banner network at a time and falls back only after a real failure. */
object BannerFallbackDispatcher {
    private const val TAG = "BannerFallbackDispatcher"

    fun load(
        activity: Activity,
        container: ViewGroup,
        adMobView: AdView,
        onFinished: () -> Unit = {}
    ) {
        if (!AdEligibility.canRequestAds(activity)) {
            container.removeAllViews()
            AdEventReporter.report("banner", "banner", "none", "suppressed_ineligible")
            onFinished()
            return
        }

        val completed = AtomicBoolean(false)
        fun finish() {
            if (completed.compareAndSet(false, true)) onFinished()
        }

        fun loadAdMob() {
            Log.i(TAG, "Banner fallback: AdMob")
            container.removeAllViews()
            container.addView(adMobView)
            adMobView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    AdEventReporter.report("banner", "banner", "admob", "loaded")
                    finish()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "AdMob banner unavailable: ${error.message}")
                    AdManager.logMetaMediationFailure("banner", error)
                    finish()
                }

                override fun onAdImpression() {
                    AdEventReporter.report("banner", "banner", "admob", "impression")
                }
            }
            AdManager.loadBannerAdView(adMobView)
        }

        fun loadStartIo() {
            Log.i(TAG, "Banner fallback: Start.io")
            StartAppAdManager.loadBanner(
                activity = activity,
                container = container,
                onLoaded = {
                    AdEventReporter.report("banner", "banner", "startio", "loaded")
                    finish()
                },
                onFailed = ::loadAdMob
            )
        }

        Log.i(TAG, "Banner fallback: Unity")
        UnityAdManager.loadBanner(
            activity = activity,
            container = container,
            onFailed = ::loadStartIo
        )
    }
}
