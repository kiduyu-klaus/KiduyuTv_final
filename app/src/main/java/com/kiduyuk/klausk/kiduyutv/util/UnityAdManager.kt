package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * Unity Ads Manager — singleton.
 *
 * Handles banner, interstitial, and rewarded video ads from Unity Ads.
 * Safe to call even if the SDK failed to initialise: methods no-op and
 * invoke callbacks so the app flow continues.
 *
 * Interstitial frequency is guarded by [MIN_INTERSTITIAL_INTERVAL_MS] (3 min).
 */
object UnityAdManager {

    private const val TAG = "UnityAdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L
    private const val PLACEMENT_REWARDED = "Rewarded_Android"
    private const val PLACEMENT_INTERSTITIAL = "Interstitial_Android"
    private const val PLACEMENT_BANNER = "Banner_Android"
    private const val UNITY_GAME_ID_META = "com.unity3d.ads.UNITY_ADS_GAME_ID"
    private const val UNITY_TEST_MODE_META = "com.unity3d.ads.UNITY_ADS_TEST_MODE"

    @Volatile
    var isInitialised = false
        private set

    @Volatile
    private var lastInterstitialShownAt = 0L

    @Volatile
    private var currentBannerView: BannerView? = null

    // Tracking states manually since UnityAds.isReady() was removed
    @Volatile
    private var _isInterstitialReady = false

    @Volatile
    private var _isRewardedReady = false

    val isInterstitialReady: Boolean
        get() = _isInterstitialReady

    val isRewardedReady: Boolean
        get() = _isRewardedReady

    // Central listener implementation for handling ad load events
    private val loadListener = object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String) {
            Log.i(TAG, "Unity ad successfully loaded: $placementId")
            when (placementId) {
                PLACEMENT_INTERSTITIAL -> _isInterstitialReady = true
                PLACEMENT_REWARDED -> _isRewardedReady = true
            }
        }

        override fun onUnityAdsFailedToLoad(
            placementId: String,
            error: UnityAds.UnityAdsLoadError,
            message: String
        ) {
            Log.w(TAG, "Unity ad failed to load: $placementId, error: $error, message: $message")
            when (placementId) {
                PLACEMENT_INTERSTITIAL -> _isInterstitialReady = false
                PLACEMENT_REWARDED -> _isRewardedReady = false
            }
        }
    }

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
    } catch (e: Exception) {
        true
    }

    private fun readUnityGameId(context: Context): String? = try {
        val ai = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        ai.metaData?.getString(UNITY_GAME_ID_META)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read Unity game id from manifest", e)
        null
    }

    private fun readUnityTestMode(context: Context): Boolean = try {
        val ai = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        when (val value = ai.metaData?.get(UNITY_TEST_MODE_META)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read Unity test mode from manifest", e)
        false
    }

    /**
     * Pre-load Unity Ads placements. Call from [KiduyuTvApp.onInitializationComplete].
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping Unity preload")
            return
        }
        try {
            if (UnityAds.isInitialized) {
                isInitialised = true
                loadFullscreenPlacements()
                return
            }

            val gameId = readUnityGameId(context)
            if (gameId.isNullOrBlank()) {
                Log.w(TAG, "Unity game id missing - skipping preload")
                return
            }

            UnityAds.initialize(
                context.applicationContext,
                gameId,
                readUnityTestMode(context),
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isInitialised = true
                        Log.i(TAG, "Unity Ads initialized")
                        loadFullscreenPlacements()
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String
                    ) {
                        isInitialised = false
                        _isInterstitialReady = false
                        _isRewardedReady = false
                        Log.w(TAG, "Unity initialization failed: $error, message: $message")
                    }
                }
            )
            Log.i(TAG, "Unity Ads initialization requested")
        } catch (e: Exception) {
            Log.e(TAG, "Unity preload failed", e)
        }
    }

    private fun loadFullscreenPlacements() {
        UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
        UnityAds.load(PLACEMENT_REWARDED, loadListener)
        Log.i(TAG, "Unity ad preload requested")
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a Unity banner into the supplied [container].
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        if (!isInitialised || !UnityAds.isInitialized) {
            Log.w(TAG, "Unity banner skipped - SDK not initialized")
            return
        }
        try {
            destroyBanner()
            container.removeAllViews()
            val bannerView = BannerView(
                activity,
                PLACEMENT_BANNER,
                UnityBannerSize(320, 50)
            )
            bannerView.listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView) {
                    Log.i(TAG, "Unity banner loaded")
                }

                override fun onBannerFailedToLoad(
                    bannerAdView: BannerView,
                    errorInfo: BannerErrorInfo
                ) {
                    Log.w(TAG, "Unity banner failed: ${errorInfo.errorMessage}")
                }

                override fun onBannerClick(bannerAdView: BannerView) {
                    Log.i(TAG, "Unity banner clicked")
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView) {
                    Log.i(TAG, "Unity banner left app")
                }

                override fun onBannerShown(bannerAdView: BannerView) {
                    Log.i(TAG, "Unity banner shown")
                }
            }
            container.addView(
                bannerView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            bannerView.load()
            currentBannerView = bannerView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Unity banner", e)
        }
    }

    /**
     * Destroys the current Unity banner and removes it from its parent.
     * Call when the banner is no longer needed (e.g. Composable disposal).
     */
    fun destroyBanner() {
        currentBannerView?.let { banner ->
            try {
                (banner.parent as? ViewGroup)?.removeView(banner)
                banner.destroy()
            } catch (_: Exception) {
            }
        }
        currentBannerView = null
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a Unity interstitial if one is ready and the cooldown has elapsed.
     * Always calls [onDismissed] when done.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        if (!isInitialised || !UnityAds.isInitialized) {
            Log.i(TAG, "Unity interstitial skipped - SDK not initialized")
            onDismissed()
            preloadAds(activity)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            onDismissed()
            return
        }
        if (!isInterstitialReady) {
            Log.i(TAG, "Unity interstitial not ready")
            onDismissed()
            UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
            return
        }
        try {
            _isInterstitialReady = false // Mark consumed immediately before showing
            UnityAds.show(
                activity,
                PLACEMENT_INTERSTITIAL,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        Log.w(TAG, "Unity interstitial show failed: $message")
                        UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
                        onDismissed()
                    }

                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.i(TAG, "Unity interstitial started")
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.i(TAG, "Unity interstitial clicked")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        Log.i(TAG, "Unity interstitial complete: $state")
                        lastInterstitialShownAt = System.currentTimeMillis()
                        UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
                        onDismissed()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Unity interstitial", e)
            onDismissed()
        }
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a Unity rewarded video ad.
     * [onRewarded] fires only when the user fully watches the ad.
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
        if (!isInitialised || !UnityAds.isInitialized) {
            Log.i(TAG, "Unity rewarded skipped - SDK not initialized")
            onDismissed()
            preloadAds(activity)
            return
        }
        if (!isRewardedReady) {
            Log.i(TAG, "Unity rewarded not ready")
            onDismissed()
            UnityAds.load(PLACEMENT_REWARDED, loadListener)
            return
        }
        try {
            _isRewardedReady = false // Mark consumed immediately before showing
            UnityAds.show(
                activity,
                PLACEMENT_REWARDED,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        Log.w(TAG, "Unity rewarded show failed: $message")
                        UnityAds.load(PLACEMENT_REWARDED, loadListener)
                        onDismissed()
                    }

                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.i(TAG, "Unity rewarded started")
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.i(TAG, "Unity rewarded clicked")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        Log.i(TAG, "Unity rewarded complete: $state")
                        UnityAds.load(PLACEMENT_REWARDED, loadListener)
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            onRewarded()
                        }
                        onDismissed()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Unity rewarded", e)
            onDismissed()
        }
    }
}
