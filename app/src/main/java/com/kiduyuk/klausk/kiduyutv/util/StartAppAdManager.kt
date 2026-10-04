package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.VideoListener
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * StartApp (Start.io) Ad Manager — singleton.
 *
 * Handles banner, interstitial, rewarded video, and splash ads from StartApp.
 * All public methods are safe to call even if the SDK failed to initialise:
 * they simply no-op and invoke the callback so the app flow continues.
 *
 * Interstitial frequency is guarded by [FullscreenAdPolicy] (3 min globally).
 */
object StartAppAdManager {

    private const val TAG = "StartAppAdManager"
    @Volatile
    var isInitialised = false
        private set

    /**
     * Observable init state. Start.io is the primary TV banner leg, so a
     * placement must be able to mount on Start.io being ready without waiting
     * for AdMob — otherwise an AdMob init failure silently removes the network
     * that was supposed to be the first attempt.
     */
    private val _isInitialisedState = MutableStateFlow(false)
    val isInitialisedState: StateFlow<Boolean> = _isInitialisedState.asStateFlow()

    /**
     * Initialize Start.io once after UMP consent has resolved.
     */
    @Synchronized
    fun preloadAds(context: Context) {
        if (isInitialised) return
        if (!shouldShowAds(context)) {
            // Deferred, not abandoned: consent can still resolve later via the
            // privacy options form or a restored network, and
            // retryInitIfEligible picks this up from there.
            Log.i(TAG, "Ads not eligible yet - deferring StartApp initialisation")
            return
        }
        try {
            // Code initialization is intentionally delayed until UMP resolves.
            // The final false disables return ads so AdMob and Start.io cannot
            // stack full-screen ads when the app returns to the foreground.
            StartAppSDK.init(
                context.applicationContext,
                BuildConfig.START_IO_APP_ID,
                false
            )

            applyConsent(context)
            isInitialised = true
            _isInitialisedState.value = true
            Log.i(TAG, "StartApp ads pre-loaded")
        } catch (e: Exception) {
            Log.e(TAG, "StartApp preload failed", e)
        }
    }

    /**
     * Pushes the current UMP decision to Start.io. Safe to call repeatedly —
     * this is what makes a consent change reach an already-initialised SDK,
     * since consent used to be applied only during [preloadAds].
     */
    fun applyConsent(context: Context) {
        try {
            StartAppSDK.setUserConsent(
                context,
                "pas",
                System.currentTimeMillis(),
                ConsentManager.canShowPersonalizedAds(context)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply consent to StartApp", e)
        }
    }

    /**
     * Re-checks eligibility and initialises Start.io when it was previously
     * suppressed, then refreshes consent. Called on every process foreground and
     * after the UMP privacy options form completes, so a blocked first attempt
     * is not lost for the rest of the process.
     */
    @Synchronized
    fun retryInitIfEligible(context: Context) {
        if (!isInitialised) {
            if (!shouldShowAds(context)) return
            Log.i(TAG, "Ads are now eligible; retrying StartApp initialisation")
            preloadAds(context)
            return
        }
        // Already running: make sure a later consent change is honoured.
        applyConsent(context)
    }

    private fun shouldShowAds(context: Context): Boolean = AdEligibility.canRequestAds(context)

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a StartApp banner into the supplied [container].
     * The caller is responsible for placing the container in the layout.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        onLoaded: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            onFailed()
            return
        }
        if (!isInitialised) preloadAds(activity)
        if (!isInitialised) {
            onFailed()
            return
        }
        try {
            container.removeAllViews()
            val banner = Banner(activity)
            banner.setBannerListener(object : BannerListener {
                override fun onReceiveAd(view: View) {
                    Log.i(TAG, "StartApp banner received")
                    onLoaded()
                }

                override fun onFailedToReceiveAd(view: View) {
                    Log.w(TAG, "StartApp banner failed")
                    onFailed()
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
            onFailed()
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a StartApp interstitial if ads are enabled and the cooldown has elapsed.
     * Always calls [onDismissed] when done (or immediately if skipped).
     */
    fun showInterstitial(
        activity: Activity,
        onDismissed: () -> Unit = {},
        onUnavailable: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            onUnavailable()
            return
        }
        if (!isInitialised) preloadAds(activity)
        if (!isInitialised) {
            onUnavailable()
            return
        }
        if (!FullscreenAdPolicy.canShowInterstitial(activity)) {
            onDismissed()
            return
        }

        try {
            val startAppAd = StartAppAd(activity)
            startAppAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.i(TAG, "StartApp interstitial loaded")
                    startAppAd.showAd(object : AdDisplayListener {
                        override fun adDisplayed(ad: Ad?) {
                            Log.i(TAG, "StartApp interstitial displayed")
                            FullscreenAdPolicy.recordInterstitialShown(activity)
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
                            onUnavailable()
                        }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.w(TAG, "StartApp interstitial failed to load")
                    onUnavailable()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show StartApp interstitial", e)
            onUnavailable()
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
        if (!isInitialised) preloadAds(activity)
        if (!isInitialised) {
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
