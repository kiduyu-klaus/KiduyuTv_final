package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.wortise.ads.AdError
import com.wortise.ads.RevenueData
import com.wortise.ads.appopen.AppOpenAd
import com.wortise.ads.AdSize
import com.wortise.ads.banner.BannerAd
import com.wortise.ads.interstitial.InterstitialAd
// import com.wortise.ads.Reward
import com.wortise.ads.rewarded.RewardedAd
import com.wortise.ads.rewarded.models.Reward

/**
 * Wortise Ad Manager — singleton.
 *
 * Handles banner, interstitial, rewarded video, and app open ads from Wortise.
 * Safe to call even if the SDK failed to initialise: methods no-op and
 * invoke callbacks so the app flow continues.
 *
 * Interstitial frequency is guarded by [MIN_INTERSTITIAL_INTERVAL_MS] (3 min).
 *
 * **IMPORTANT:** Replace the placeholder [AD_UNIT_…] constants with your
 * actual Wortise dashboard ad unit IDs before release.
 *
 * NOTE: this targets the current Wortise Android SDK, where the ad classes
 * are `BannerAd`, `InterstitialAd`, `RewardedAd`, and `AppOpenAd`, each with
 * a nested `Listener` abstract class. Errors are reported via `AdError` and
 * revenue events via `RevenueData`. Double-check the import package paths
 * for `AdError`, `AdSize`, `RevenueData`, and `Reward` against the SDK
 * version you have installed (use Android Studio's auto-import if any of
 * these don't resolve).
 */
object WortiseAdManager {

    private const val TAG = "WortiseAdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L

    // TODO: Replace these placeholders with your real Wortise ad unit IDs
    private const val AD_UNIT_BANNER = "90713170-a74f-4ccd-becd-324bb010b89a"
    private const val AD_UNIT_INTERSTITIAL = "2c5fbdcc-a843-4540-90cf-e6f2ccbfb4e8"
    private const val AD_UNIT_REWARDED = "f598ac5c-7b3a-4f83-93b5-60f84b07571b"
    private const val AD_UNIT_APP_OPEN = "a4e27bae-7b6a-4281-8648-c7ff875c33a4"

    @Volatile
    var isInitialised = false
        private set

    @Volatile
    private var lastInterstitialShownAt = 0L

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var currentBannerAd: BannerAd? = null

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
    } catch (e: Exception) {
        true
    }

    /**
     * Pre-load Wortise ads. Call once from [KiduyuTvApp] after Wortise SDK init.
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping Wortise preload")
            return
        }
        try {
            interstitialAd = InterstitialAd(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                loadAd()
            }
            rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                loadAd()
            }
            appOpenAd = AppOpenAd(context, AD_UNIT_APP_OPEN).apply {
                autoReload = true
                listener = createAppOpenListener(null)
                loadAd()
            }
            isInitialised = true
            Log.i(TAG, "Wortise ads pre-loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Wortise preload failed", e)
        }
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a Wortise banner into the supplied [container].
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        try {
            currentBannerAd?.destroy()
            container.removeAllViews()

            val bannerAd = BannerAd(activity).apply {
                adUnitId = AD_UNIT_BANNER
                adSize = AdSize.HEIGHT_50
                listener = object : BannerAd.Listener {
                    override fun onBannerLoaded(ad: BannerAd) {
                        Log.i(TAG, "Wortise banner loaded")
                    }

                    override fun onBannerFailedToLoad(ad: BannerAd, error: AdError) {
                        Log.w(TAG, "Wortise banner failed: ${error.message}")
                    }

                    override fun onBannerClicked(ad: BannerAd) {
                        Log.i(TAG, "Wortise banner clicked")
                    }

                    override fun onBannerImpression(ad: BannerAd) {
                        Log.i(TAG, "Wortise banner impression")
                    }

                    override fun onBannerRevenuePaid(ad: BannerAd, data: RevenueData) {
                        Log.i(TAG, "Wortise banner revenue paid")
                    }
                }
            }

            currentBannerAd = bannerAd
            container.addView(
                bannerAd,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            bannerAd.loadAd()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise banner", e)
        }
    }

    /** Call from the host Activity's onPause(). */
    fun pauseBanner() {
        currentBannerAd?.pause()
    }

    /** Call from the host Activity's onResume(). */
    fun resumeBanner() {
        currentBannerAd?.resume()
    }

    /** Call from the host Activity's onDestroy(). */
    fun destroyBanner() {
        currentBannerAd?.destroy()
        currentBannerAd = null
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a Wortise interstitial if ready and the cooldown has elapsed.
     * Always calls [onDismissed] when done.
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

        val ad = interstitialAd
        if (ad == null || !ad.isAvailable) {
            Log.i(TAG, "Wortise interstitial not ready")
            onDismissed()
            loadInterstitialAd(activity)
            return
        }
        try {
            ad.listener = createInterstitialListener(activity, onDismissed)
            ad.showAd(activity)
            lastInterstitialShownAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise interstitial", e)
            onDismissed()
        }
    }

    private fun loadInterstitialAd(context: Context) {
        try {
            interstitialAd = InterstitialAd(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                loadAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise interstitial", e)
        }
    }

    private fun createInterstitialListener(
        activity: Activity?,
        onDismissed: (() -> Unit)?
    ): InterstitialAd.Listener {
        return object : InterstitialAd.Listener {
            override fun onInterstitialLoaded(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial loaded")
            }

            override fun onInterstitialFailedToLoad(ad: InterstitialAd, error: AdError) {
                Log.w(TAG, "Wortise interstitial failed: ${error.message}")
            }

            override fun onInterstitialShown(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial shown")
            }

            override fun onInterstitialImpression(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial impression")
            }

            override fun onInterstitialRevenuePaid(ad: InterstitialAd, data: RevenueData) {
                Log.i(TAG, "Wortise interstitial revenue paid")
            }

            override fun onInterstitialFailedToShow(ad: InterstitialAd, error: AdError) {
                Log.w(TAG, "Wortise interstitial failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onInterstitialClicked(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial clicked")
            }

            override fun onInterstitialDismissed(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial dismissed")
                interstitialAd = null
                activity?.let { loadInterstitialAd(it) }
                onDismissed?.invoke()
            }
        }
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a Wortise rewarded video ad.
     * [onRewarded] fires when the user earns the reward.
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
        val ad = rewardedAd
        if (ad == null || !ad.isAvailable) {
            Log.i(TAG, "Wortise rewarded not ready")
            onDismissed()
            loadRewardedAd(activity)
            return
        }
        try {
            ad.listener = createRewardedListener(onRewarded, onDismissed)
            ad.showAd(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise rewarded", e)
            onDismissed()
        }
    }

    private fun loadRewardedAd(context: Context) {
        try {
            rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                loadAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise rewarded", e)
        }
    }

    private fun createRewardedListener(
        onRewarded: (() -> Unit)?,
        onDismissed: (() -> Unit)?
    ): RewardedAd.Listener {
        return object : RewardedAd.Listener {
            override fun onRewardedLoaded(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded loaded")
            }

            override fun onRewardedFailedToLoad(ad: RewardedAd, error: AdError) {
                Log.w(TAG, "Wortise rewarded failed: ${error.message}")
            }

            override fun onRewardedImpression(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded impression")
            }

            override fun onRewardedRevenuePaid(ad: RewardedAd, data: RevenueData) {
                Log.i(TAG, "Wortise rewarded revenue paid")
            }

            override fun onRewardedFailedToShow(ad: RewardedAd, error: AdError) {
                Log.w(TAG, "Wortise rewarded failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onRewardedClicked(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded clicked")
            }

            override fun onRewardedDismissed(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded dismissed")
                rewardedAd = null
                onDismissed?.invoke()
            }

             override fun onRewardedCompleted(ad: RewardedAd, reward: Reward) {
                 Log.i(TAG, "Wortise rewarded completed")
                 onRewarded?.invoke()
             }

            // Depending on SDK version, the signature might be different or Reward might be in a different package.
            // Based on error log, the expected signature is:
            // fun onRewardedCompleted(ad: RewardedAd, reward: Reward): Unit
            // But Reward is unresolved. Let's try to use the fully qualified name if possible or comment out if not critical.
            // Since we don't know the exact package for Reward in 1.7.2, we'll use a more generic approach or comment it.
            
            // The error says 'onRewardedCompleted' overrides nothing when using (RewardedAd, Reward).
            // It also says 'Reward' is an unresolved reference.
            // This suggests Reward is not in com.wortise.ads.
            // Let's try to remove the override keyword and use the signature that doesn't cause a conflict.
            
            // If both (RewardedAd, Reward) and (RewardedAd) fail to override, 
            // it's possible the method name is different or the listener doesn't have it.
            // Let's comment it out to fix the build, as the error log explicitly said:
            // 'onRewardedCompleted' overrides nothing.
            
            /*
            override fun onRewardedCompleted(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded completed")
                onRewarded?.invoke()
            }
            */
        }
    }

    // ── App Open ──────────────────────────────────────────────────────────

    /**
     * Shows a Wortise app open ad if available; otherwise requests a new
     * load so it's ready next time. [AppOpenAd.tryToShowAd] handles both
     * cases internally.
     */
    fun showAppOpenIfAvailable(activity: Activity) {
        if (!shouldShowAds(activity)) return
        val ad = appOpenAd
        if (ad == null) {
            Log.i(TAG, "Wortise app open not ready")
            loadAppOpenAd(activity)
            return
        }
        try {
            ad.listener = createAppOpenListener(null)
            ad.tryToShowAd(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise app open", e)
        }
    }

    private fun loadAppOpenAd(context: Context) {
        try {
            appOpenAd = AppOpenAd(context, AD_UNIT_APP_OPEN).apply {
                autoReload = true
                listener = createAppOpenListener(null)
                loadAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise app open", e)
        }
    }

    private fun createAppOpenListener(onDismissed: (() -> Unit)?): AppOpenAd.Listener {
        return object : AppOpenAd.Listener {
            override fun onAppOpenLoaded(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open loaded")
            }

            override fun onAppOpenFailedToLoad(ad: AppOpenAd, error: AdError) {
                Log.w(TAG, "Wortise app open failed: ${error.message}")
            }

            override fun onAppOpenShown(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open shown")
            }

            override fun onAppOpenImpression(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open impression")
            }

            override fun onAppOpenRevenuePaid(ad: AppOpenAd, data: RevenueData) {
                Log.i(TAG, "Wortise app open revenue paid")
            }

            override fun onAppOpenFailedToShow(ad: AppOpenAd, error: AdError) {
                Log.w(TAG, "Wortise app open failed to show: ${error.message}")
                onDismissed?.invoke()
            }

            override fun onAppOpenClicked(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open clicked")
            }

            override fun onAppOpenDismissed(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open dismissed")
                onDismissed?.invoke()
            }
        }
    }

    // ── Readiness checks ──────────────────────────────────────────────────

    val isInterstitialReady: Boolean
        get() = interstitialAd?.isAvailable == true

    val isRewardedReady: Boolean
        get() = rewardedAd?.isAvailable == true

    val isAppOpenReady: Boolean
        get() = appOpenAd?.isAvailable == true
}
