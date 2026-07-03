package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnAdInspectorClosedListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.components.BannerAdView
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object AdManager {

    private const val TAG = "AdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 1 * 60 * 1000L  // 3 minutes

    @Volatile private var isInitialised = false
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    @Volatile private var isRewardedInterstitialLoading = false
    @Volatile private var appOpenAd: AppOpenAd? = null
    @Volatile private var isAppOpenLoading = false
    @Volatile private var appOpenShowing = false
    @Volatile private var appOpenLoadTime = 0L
    @Volatile private var lastInterstitialShownAt = 0L
    private val pendingInitCallbacks = mutableListOf<() -> Unit>()

    /**
     * Banner load requests that arrived before MobileAds finished initialising.
     * The first queued request is fired from inside the [MobileAds.initialize]
     * callback so the bottom-nav banner still appears even when the splash
     * navigates away before the SDK is ready.
     */
    @Volatile private var pendingBannerLoad: (() -> Unit)? = null

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Initialise the Mobile Ads SDK after UMP consent has resolved.
     * Safe to call multiple times — subsequent calls are no-ops.
     * Respects the ads disabled setting from SettingsManager.
     */
    fun init(context: Context) {
        initAndAwait(context) { /* fire and forget */ }
    }

    /**
     * Initialise the Mobile Ads SDK and invoke [onReady] only after the SDK
     * has finished initialising. Safe to call multiple times — subsequent
     * calls are no-ops and [onReady] fires immediately if already initialised.
     *
     * If ads are disabled / consent unavailable, [onReady] still fires so
     * callers (e.g. the splash screen) can proceed without blocking the
     * user indefinitely.
     */
    fun initAndAwait(context: Context, onReady: () -> Unit) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled or consent unavailable - skipping initialization")
            onReady()
            return
        }

        var runCallbackNow = false
        var shouldStartInitialization = false
        synchronized(this) {
            if (isInitialised) {
                runCallbackNow = true
            } else {
                pendingInitCallbacks.add(onReady)
                shouldStartInitialization = !isMobileAdsInitializeCalled.getAndSet(true)
            }
        }

        if (runCallbackNow) {
            onReady()
            return
        }

        if (!shouldStartInitialization) {
            return
        }

        Log.d(TAG, "Google Mobile Ads SDK Version: ${MobileAds.getVersion()}")
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
            )
        }

        MobileAds.initialize(context.applicationContext) { initStatus ->
            val callbacks = synchronized(this) {
                isInitialised = true
                pendingInitCallbacks.toList().also { pendingInitCallbacks.clear() }
            }
            val statuses = initStatus.adapterStatusMap.entries
                .joinToString { "${it.key}: ${it.value.initializationState}" }
            Log.i(TAG, "MobileAds initialised — $statuses")
            // Pre-load interstitial immediately after init
            preloadInterstitial(context)
            if (BuildConfig.FLAVOR == "phone") {
                preloadRewarded(context)
            }
            preloadRewardedInterstitial(context)
            preloadAppOpen(context)
            // Drain any banner load that was requested before init completed.
            // The bottom-nav AndroidView factory may have already run while
            // we were still initialising — replay it now so the banner appears.
            pendingBannerLoad?.let { queued ->
                pendingBannerLoad = null
                Log.i(TAG, "Replaying queued banner load after init")
                queued.invoke()
            }
            callbacks.forEach { it.invoke() }
        }
    }

    /**
     * Opens Google Mobile Ads Ad Inspector for debugging ad requests.
     */
    fun openAdInspector(
        context: Context,
        listener: OnAdInspectorClosedListener? = null
    ) {
        MobileAds.openAdInspector(context) { error ->
            if (error != null) {
                Log.e(TAG, "Ad Inspector error: ${error.message}")
            }
            listener?.onAdInspectorClosed(error)
        }
    }

    /**
     * Check if ads should be shown based on user settings.
     */
    private fun shouldShowAds(context: Context): Boolean {
        return try {
            !SettingsManager(context).isAdsDisabled() && ConsentManager.canRequestAds(context)
        } catch (e: Exception) {
            true
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Pre-loads an interstitial ad in the background so it is ready to show
     * without delay when needed.
     * Respects the ads disabled setting from SettingsManager.
     */
    fun preloadInterstitial(context: Context) {
        if (!isInitialised) return
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping interstitial preload")
            return
        }
        val unitId = if (BuildConfig.FLAVOR == "tv")
            AdUnitIds.TV_INTERSTITIAL
        else
            AdUnitIds.PHONE_INTERSTITIAL

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, unitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.i(TAG, "Interstitial loaded")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
                Log.w(TAG, "Interstitial failed to load: ${error.message}")
            }
        })
    }

    /**
     * Shows the pre-loaded interstitial if available, then immediately
     * pre-loads the next one. Calls [onDismissed] when the ad closes
     * (or immediately if no ad is ready).
     * Respects the ads disabled setting from SettingsManager.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (!shouldShowAds(activity)) {
            Log.i(TAG, "Ads disabled - skipping interstitial show")
            onDismissed()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
            Log.i(TAG, "Interstitial skipped - too soon since last show")
            onDismissed()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            Log.i(TAG, "No interstitial ready — proceeding without ad")
            onDismissed()
            preloadInterstitial(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis()
                Log.i(TAG, "Interstitial shown")
            }

            override fun onAdImpression() {
                Log.i(TAG, "Interstitial impression")
            }

            override fun onAdClicked() {
                Log.i(TAG, "Interstitial clicked")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "Interstitial dismissed")
                interstitialAd = null
                preloadInterstitial(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                interstitialAd = null
                preloadInterstitial(activity)
                onDismissed()
            }
        }
        ad.show(activity)
    }

    // ── Rewarded (phone only) ─────────────────────────────────────────────

    /**
     * Pre-loads a rewarded ad in the background.
     * Respects the ads disabled setting from SettingsManager.
     */
    fun preloadRewarded(context: Context) {
        if (BuildConfig.FLAVOR != "phone") return
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping rewarded preload")
            return
        }
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AdUnitIds.PHONE_REWARDED, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.i(TAG, "Rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed: ${error.message}")
                }
            })
    }

    /**
     * Shows a rewarded ad. [onRewarded] is only called when the user
     * earns the reward (watched the full ad). [onDismissed] always fires.
     * Respects the ads disabled setting from SettingsManager.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            Log.i(TAG, "Ads disabled - skipping rewarded show")
            onDismissed()
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            Log.i(TAG, "No rewarded ad ready")
            onDismissed()
            preloadRewarded(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.i(TAG, "Rewarded ad shown")
            }

            override fun onAdImpression() {
                Log.i(TAG, "Rewarded ad impression")
            }

            override fun onAdClicked() {
                Log.i(TAG, "Rewarded ad clicked")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                preloadRewarded(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                preloadRewarded(activity)
                onDismissed()
            }
        }
        ad.show(activity) { rewardItem ->
            Log.i(TAG, "User rewarded: ${rewardItem.amount} ${rewardItem.type}")
            onRewarded()
        }
    }

    val isInterstitialReady: Boolean get() = interstitialAd != null
    val isRewardedReady: Boolean get() = rewardedAd != null
    val isRewardedInterstitialReady: Boolean get() = rewardedInterstitialAd != null
    val isAppOpenShowing: Boolean get() = appOpenShowing

    // ── Rewarded Interstitial ─────────────────────────────────────────────

    fun preloadRewardedInterstitial(context: Context) {
        if (!isInitialised) return
        if (isRewardedInterstitialLoading || rewardedInterstitialAd != null) return
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping rewarded interstitial preload")
            return
        }

        isRewardedInterstitialLoading = true
        val unitId = if (BuildConfig.FLAVOR == "tv") {
            AdUnitIds.TV_REWARDED_INTERSTITIAL
        } else {
            AdUnitIds.PHONE_REWARDED_INTERSTITIAL
        }

        RewardedInterstitialAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    isRewardedInterstitialLoading = false
                    Log.i(TAG, "Rewarded interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                    isRewardedInterstitialLoading = false
                    Log.w(TAG, "Rewarded interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    fun showRewardedInterstitial(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            Log.i(TAG, "Ads disabled - skipping rewarded interstitial show")
            onDismissed()
            return
        }

        val ad = rewardedInterstitialAd
        if (ad == null) {
            Log.i(TAG, "No rewarded interstitial ready")
            onDismissed()
            preloadRewardedInterstitial(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "Rewarded interstitial dismissed")
                rewardedInterstitialAd = null
                preloadRewardedInterstitial(activity)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded interstitial failed to show: ${error.message}")
                rewardedInterstitialAd = null
                preloadRewardedInterstitial(activity)
                onDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.i(TAG, "Rewarded interstitial shown")
            }

            override fun onAdImpression() {
                Log.i(TAG, "Rewarded interstitial impression")
            }

            override fun onAdClicked() {
                Log.i(TAG, "Rewarded interstitial clicked")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.i(TAG, "Rewarded interstitial reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewarded()
        }
    }

    // ── App Open ──────────────────────────────────────────────────────────

    fun preloadAppOpen(context: Context) {
        if (!isInitialised) return
        if (isAppOpenLoading || isAppOpenAdAvailable()) return
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping app open preload")
            return
        }

        isAppOpenLoading = true
        val unitId = if (BuildConfig.FLAVOR == "tv") {
            AdUnitIds.TV_APP_OPEN
        } else {
            AdUnitIds.PHONE_APP_OPEN
        }

        AppOpenAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isAppOpenLoading = false
                    appOpenLoadTime = Date().time
                    Log.i(TAG, "App open ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isAppOpenLoading = false
                    Log.w(TAG, "App open ad failed to load: ${error.message}")
                }
            }
        )
    }

    fun showAppOpenIfAvailable(
        activity: Activity,
        onShowAdComplete: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) {
            onShowAdComplete()
            return
        }

        if (appOpenShowing) {
            Log.i(TAG, "App open ad is already showing")
            return
        }

        val ad = appOpenAd
        if (ad == null || !isAppOpenAdAvailable()) {
            Log.i(TAG, "App open ad is not ready")
            onShowAdComplete()
            preloadAppOpen(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                appOpenShowing = false
                Log.i(TAG, "App open ad dismissed")
                onShowAdComplete()
                preloadAppOpen(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                appOpenShowing = false
                Log.w(TAG, "App open ad failed to show: ${error.message}")
                onShowAdComplete()
                preloadAppOpen(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.i(TAG, "App open ad shown")
            }

            override fun onAdImpression() {
                Log.i(TAG, "App open ad impression")
            }

            override fun onAdClicked() {
                Log.i(TAG, "App open ad clicked")
            }
        }

        appOpenShowing = true
        ad.show(activity)
    }

    private fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && wasAppOpenLoadTimeLessThanNHoursAgo(4)
    }

    private fun wasAppOpenLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - appOpenLoadTime
        val millisecondsPerHour = 3_600_000L
        return dateDifference < millisecondsPerHour * numHours
    }

    // ── Native Advanced ───────────────────────────────────────────────────

    fun loadNativeAd(
        context: Context,
        startMuted: Boolean = true,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (LoadAdError) -> Unit = {}
    ) {
        if (!isInitialised) {
            Log.i(TAG, "AdMob not initialised yet - skipping native ad load")
            return
        }
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping native ad load")
            return
        }

        val unitId = if (BuildConfig.FLAVOR == "tv") {
            AdUnitIds.TV_NATIVE
        } else {
            AdUnitIds.PHONE_NATIVE
        }

        val adLoader = AdLoader.Builder(context, unitId)
            .forNativeAd { nativeAd ->
                Log.i(TAG, "Native ad loaded")
                onLoaded(nativeAd)
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(startMuted).build())
                    .build()
            )
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Native ad failed to load: ${error.message}")
                        onFailed(error)
                    }

                    override fun onAdImpression() {
                        Log.i(TAG, "Native ad impression")
                    }

                    override fun onAdClicked() {
                        Log.i(TAG, "Native ad clicked")
                    }
                }
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    // ── Banner (AdMob) ────────────────────────────────────────────────────

    /**
     * Loads a banner ad using BannerAdView Composable into the supplied [container].
     * The caller is responsible for placing the container in the layout.
     * No-op if ads are disabled by the user.
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) {
            Log.i(TAG, "Ads disabled - skipping banner")
            return
        }
        val doLoad = {
            try {
                container.removeAllViews()
                val composeView = ComposeView(activity).apply {
                    setContent {
                        BannerAdView()
                    }
                }
                container.addView(composeView)
                Log.i(TAG, "Banner ad loading")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load banner ad", e)
            }
            Unit  // Explicitly return Unit
        }
        if (!isInitialised) {
            Log.w(TAG, "AdMob not initialised yet - queuing banner load")
            pendingBannerLoad = doLoad
            return
        }
        doLoad()
    }
}

