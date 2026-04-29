package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdManager {

    private const val TAG = "AdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L  // 3 minutes

    @Volatile private var isInitialised = false
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var lastInterstitialShownAt = 0L

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Initialise the Mobile Ads SDK. Call once from KiduyuTvApp.onCreate().
     * Safe to call multiple times — subsequent calls are no-ops.
     * Respects the ads disabled setting from SettingsManager.
     */
    fun init(context: Context) {
        // Check if ads are disabled
        if (SettingsManager(context).isAdsDisabled()) {
            Log.i(TAG, "Ads disabled by user - skipping initialization")
            return
        }

        if (isInitialised) return
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
        }
    }

    /**
     * Check if ads should be shown based on user settings.
     */
    private fun shouldShowAds(context: Context): Boolean {
        return !SettingsManager(context).isAdsDisabled()
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
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
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
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewarded(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
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
}

