package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import java.util.concurrent.atomic.AtomicBoolean
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Loads one banner network at a time and safely advances after failure or timeout. */
object BannerFallbackDispatcher {
    private const val TAG = "BannerFallbackDispatcher"
    private const val PROVIDER_TIMEOUT_MS = 5_000L
    private const val UNITY_STAGE = 0
    private const val START_IO_STAGE = 1
    private const val ADMOB_STAGE = 2
    private const val COMPLETE_STAGE = 3
    private const val UNITY_LOADED_STAGE = 4
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeRequests = WeakHashMap<ViewGroup, AtomicInteger>()

    fun load(
        activity: Activity,
        container: ViewGroup,
        adMobView: AdView,
        onFinished: () -> Unit = {}
    ) {
        val stage = AtomicInteger(UNITY_STAGE)
        synchronized(activeRequests) {
            activeRequests.put(container, stage)?.set(COMPLETE_STAGE)
        }
        val completionNotified = AtomicBoolean(false)

        fun isCurrent(expected: Int): Boolean = synchronized(activeRequests) {
            activeRequests[container] === stage && stage.get() == expected
        }

        fun move(expected: Int, next: Int): Boolean = synchronized(activeRequests) {
            activeRequests[container] === stage && stage.compareAndSet(expected, next)
        }

        fun finish(expected: Int, provider: String, status: String, clearContainer: Boolean = false) {
            if (!move(expected, COMPLETE_STAGE)) return
            synchronized(activeRequests) {
                if (activeRequests[container] === stage) activeRequests.remove(container)
            }
            if (clearContainer) container.removeAllViews()
            AdEventReporter.report("banner", "banner", provider, status)
            if (completionNotified.compareAndSet(false, true)) onFinished()
        }

        fun stopIfIneligible(expected: Int): Boolean {
            if (AdEligibility.canRequestAds(activity)) return false
            Log.i(TAG, "Stopping banner fallback because consent no longer permits requests")
            finish(expected, "none", "suppressed_ineligible", clearContainer = true)
            return true
        }

        fun loadAdMob() {
            if (!isCurrent(ADMOB_STAGE) || stopIfIneligible(ADMOB_STAGE)) return
            Log.i(TAG, "Banner fallback: AdMob")
            container.removeAllViews()
            adMobView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    onMain {
                        finish(ADMOB_STAGE, "admob", "loaded")
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onMain {
                        if (!isCurrent(ADMOB_STAGE)) return@onMain
                        Log.w(TAG, "AdMob banner unavailable: ${error.message}")
                        AdManager.logMetaMediationFailure("banner", error)
                        finish(ADMOB_STAGE, "admob", "failed", clearContainer = true)
                    }
                }

                override fun onAdImpression() {
                    AdEventReporter.report("banner", "banner", "admob", "impression")
                }
            }
            container.addView(adMobView)
            AdManager.loadBannerAdView(adMobView) {
                onMain {
                    if (isCurrent(ADMOB_STAGE)) {
                        finish(ADMOB_STAGE, "none", "suppressed_ineligible", clearContainer = true)
                    }
                }
            }
            mainHandler.postDelayed({
                if (isCurrent(ADMOB_STAGE)) {
                    Log.w(TAG, "AdMob banner timed out")
                    finish(ADMOB_STAGE, "admob", "timed_out", clearContainer = true)
                }
            }, PROVIDER_TIMEOUT_MS)
        }

        fun loadStartIo() {
            if (!isCurrent(START_IO_STAGE) || stopIfIneligible(START_IO_STAGE)) return
            Log.i(TAG, "Banner fallback: Start.io")
            mainHandler.postDelayed({
                if (move(START_IO_STAGE, ADMOB_STAGE)) {
                    Log.w(TAG, "Start.io banner timed out; advancing to AdMob")
                    container.removeAllViews()
                    loadAdMob()
                }
            }, PROVIDER_TIMEOUT_MS)
            StartAppAdManager.loadBanner(
                activity = activity,
                container = container,
                onLoaded = {
                    onMain { finish(START_IO_STAGE, "startio", "loaded") }
                },
                onFailed = {
                    onMain {
                        if (move(START_IO_STAGE, ADMOB_STAGE)) loadAdMob()
                    }
                }
            )
        }

        fun loadUnity() {
            if (!isCurrent(UNITY_STAGE) || stopIfIneligible(UNITY_STAGE)) return
            Log.i(TAG, "Banner fallback: Unity")
            mainHandler.postDelayed({
                if (move(UNITY_STAGE, START_IO_STAGE)) {
                    Log.w(TAG, "Unity banner timed out; advancing to Start.io")
                    UnityAdManager.destroyBanner()
                    loadStartIo()
                }
            }, PROVIDER_TIMEOUT_MS)
            UnityAdManager.loadBanner(
                activity = activity,
                container = container,
                onFailed = {
                    onMain {
                        if (move(UNITY_STAGE, START_IO_STAGE) ||
                            move(UNITY_LOADED_STAGE, START_IO_STAGE)
                        ) loadStartIo()
                    }
                },
                onLoaded = {
                    onMain {
                        if (move(UNITY_STAGE, UNITY_LOADED_STAGE)) {
                            AdEventReporter.report("banner", "banner", "unity", "loaded")
                            if (completionNotified.compareAndSet(false, true)) onFinished()
                        }
                    }
                }
            )
        }

        onMain {
            if (stopIfIneligible(UNITY_STAGE)) return@onMain
            container.removeAllViews()
            loadUnity()
        }
    }

    /** Cancel an outstanding chain when its Compose host leaves the screen. */
    fun cancel(container: ViewGroup) {
        synchronized(activeRequests) {
            activeRequests.remove(container)?.set(COMPLETE_STAGE)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }
}
