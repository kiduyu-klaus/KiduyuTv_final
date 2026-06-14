package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.VideoListener
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

/**
 * StartApp (Start.io) Ad Manager — singleton.
 *
 * Handles banner, interstitial, rewarded video, and splash ads from StartApp.
 * All public methods are safe to call even if the SDK failed to initialise:
 * they simply no-op and invoke the callback so the app flow continues.
 *
 * Interstitial frequency is guarded by [MIN_INTERSTITIAL_INTERVAL_MS] (3 min).
 */
object StartAppAdManager {

    private const val TAG = "StartAppAdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L
    private const val APP_ID = "YOUR_STARTAPP_APP_ID" // TODO: Replace with your actual Start.io App ID

    @Volatile
    var isInitialised = false
        private set

    @Volatile
    private var lastInterstitialShownAt = 0L

    /**
     * Pre-load StartApp ads. Call once from [KiduyuTvApp.onCreate].
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping StartApp preload")
            return
        }
        try {
            // FIX: Use modern StartAppSDK.initParams builder pattern
            StartAppSDK.initParams(context, APP_ID)
                .setReturnAdsEnabled(false) // Blocks annoying ads when users re-open the app
                .init()

            isInitialised = true
            Log.i(TAG, "StartApp ads pre-loaded")
        } catch (e: Exception) {
            Log.e(TAG, "StartApp preload failed", e)
        }
    }

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
    } catch (e: Exception) {
        true
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a StartApp banner into the supplied [container].
     * The caller is responsible for placing the container in the layout.
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        try {
            container.removeAllViews()
            val banner = Banner(activity)
            banner.setBannerListener(object : BannerListener {
                override fun onReceiveAd(view: View) {
                    Log.i(TAG, "StartApp banner received")
                }

                override fun onFailedToReceiveAd(view: View) {
                    Log.w(TAG, "StartApp banner failed")
                }

                override fun onClick(view: View) {
                    Log.i(TAG, "StartApp banner clicked")
                }

                override fun onImpression(view: View) {
                    Log.i(TAG, "StartApp banner impression")
                }
            })
            container.addView(
                banner,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load StartApp banner", e)
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a StartApp interstitial if ads are enabled and the cooldown has elapsed.
     * Always calls [onDismissed] when done (or immediately if skipped).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            onDismissed()
            return
        }

        try {
            val startAppAd = StartAppAd(activity)
            // FIX: Exact overrides and method naming corrected to fit SDK specifications
            startAppAd.showAd(object : AdDisplayListener {
                override fun adDisplayed(ad: Ad?) {
                    Log.i(TAG, "StartApp interstitial displayed")
                }

                override fun adHidden(ad: Ad?) {
                    Log.i(TAG, "StartApp interstitial hidden")
                    onDismissed()
                }

                override fun adClicked(ad: Ad?) {
                    Log.i(TAG, "StartApp interstitial clicked")
                }

                override fun adNotDisplayed(ad: Ad?) {
                    Log.w(TAG, "StartApp interstitial not displayed")
                    onDismissed()
                }
            })
            startAppAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.i(TAG, "StartApp interstitial loaded")
                    startAppAd.showAd()
                    lastInterstitialShownAt = System.currentTimeMillis()
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "StartApp interstitial failed to load")
                    onDismissed()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show StartApp interstitial", e)
            onDismissed()
        }
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a StartApp rewarded video ad.
     * [onRewarded] fires when the user completes the video.
     * [onDismissed] always fires when the ad closes.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        try {
            val rewardedVideo = StartAppAd(activity)
            rewardedVideo.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    Log.i(TAG, "StartApp rewarded video completed")
                    onRewarded()
                }
            })
            // FIX: Added the explicit AdMode.REWARDED_VIDEO constraint parameter
            rewardedVideo.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.i(TAG, "StartApp rewarded loaded")
                    rewardedVideo.showAd(object : AdDisplayListener {
                        override fun adDisplayed(ad: Ad?) {
                            Log.i(TAG, "StartApp rewarded displayed")
                        }

                        override fun adHidden(ad: Ad?) {
                            Log.i(TAG, "StartApp rewarded hidden")
                            onDismissed()
                        }

                        override fun adClicked(ad: Ad?) {
                            Log.i(TAG, "StartApp rewarded clicked")
                        }

                        override fun adNotDisplayed(ad: Ad?) {
                            Log.w(TAG, "StartApp rewarded not displayed")
                            onDismissed()
                        }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "StartApp rewarded failed")
                    onDismissed()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show StartApp rewarded", e)
            onDismissed()
        }
    }

    // ── Splash ────────────────────────────────────────────────────────────

    /**
     * Shows a StartApp splash ad. Call from [SplashActivity.onCreate].
     */
    fun showSplash(activity: Activity, savedInstanceState: Bundle? = null) {
        if (!shouldShowAds(activity)) return
        try {
            // FIX: Signature updated to standard (Activity, Bundle?)
            StartAppAd.showSplash(activity, savedInstanceState)
            Log.i(TAG, "StartApp splash setup requested")
        } catch (e: Exception) {
            Log.e(TAG, "StartApp splash failed", e)
        }
    }
}
