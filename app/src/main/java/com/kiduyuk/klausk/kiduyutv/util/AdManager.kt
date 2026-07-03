package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.components.BannerAdView

object AdManager {

    private const val TAG = "AdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 1 * 60 * 1000L  // 3 minutes

    @Volatile private var isInitialised = false
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var lastInterstitialShownAt = 0L

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

        if (isInitialised) {
            onReady()
            return
        }
        MobileAds.initialize(context) { initStatus ->
            isInitialised = true
            val statuses = initStatus.adapterStatusMap.entries
                .joinToString { "${it.key}: ${it.value.initializationState}" }
            Log.i(TAG, "MobileAds initialised — $statuses")
            // Pre-load interstitial immediately after init
            preloadInterstitial(context)
            if (BuildConfig.FLAVOR == "phone") {
                preloadRewarded(context)
            }
            // Drain any banner load that was requested before init completed.
            // The bottom-nav AndroidView factory may have already run while
            // we were still initialising — replay it now so the banner appears.
            pendingBannerLoad?.let { queued ->
                pendingBannerLoad = null
                Log.i(TAG, "Replaying queued banner load after init")
                queued.invoke()
            }
            onReady()
        }
    }

    /**
     * Check if ads should be shown based on user settings and consent state.
     *
     * Only an explicit user opt-out (the in-app "Disable ads" toggle) blocks
     * ad serving. Publisher-side UMP misconfiguration does NOT block ads —
     * it just means we'll serve non-personalized ads. This is important
     * because once UMP fails with "no form configured", `canRequestAds()`
     * permanently returns false until consent is reset, which would
     * otherwise prevent any ad from ever loading.
     */
    private fun shouldShowAds(context: Context): Boolean {
        return try {
            if (SettingsManager(context).isAdsDisabled()) {
                Log.i(TAG, "Ads disabled by user setting — skipping initialization")
                return false
            }
            if (ConsentManager.canRequestAds(context)) {
                true
            } else if (ConsentManager.isPublisherMisconfigured()) {
                Log.w(
                    TAG,
                    "AdMob consent form not configured for app ID; serving non-personalized ads. " +
                        "Configure one in the AdMob console (Privacy & messaging) to silence this warning."
                )
                true
            } else {
                Log.i(TAG, "User denied consent — skipping ad initialization")
                false
            }
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

