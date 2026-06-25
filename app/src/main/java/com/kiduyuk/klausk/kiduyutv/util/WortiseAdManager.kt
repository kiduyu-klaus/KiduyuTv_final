package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import com.wortise.ads.AdError
import com.wortise.ads.RevenueData
import com.wortise.ads.WortiseSdk
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
 * are BannerAd, InterstitialAd, RewardedAd, and AppOpenAd, each with
 * a nested Listener abstract class. Errors are reported via AdError and
 * revenue events via RevenueData. Double-check the import package paths
 * for AdError, AdSize, RevenueData, and Reward against the SDK
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

    private const val AD_UNIT_BANNER_TV = "90713170-a74f-4ccd-becd-324bb010b89a"
    private const val AD_UNIT_INTERSTITIAL_TV = "2c5fbdcc-a843-4540-90cf-e6f2ccbfb4e8"
    private const val AD_UNIT_REWARDED_TV = "f598ac5c-7b3a-4f83-93b5-60f84b07571b"
    private const val AD_UNIT_APP_OPEN_TV = "2a6f017f-7585-4ddd-8443-79d9bbb9ec0c"
    /**
     * Manifest meta-data key holding the Wortise App ID. Mirrors the entry in
     * AndroidManifest.xml:
     * <meta-data android:name="com.wortise.WORTISE_APP_ID" .../>
     */
    private const val WORTISE_APP_ID_META = "com.wortise.WORTISE_APP_ID"

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

    @Volatile
    private var appContext: Context? = null

    private fun shouldShowAds(context: Context): Boolean = try {
        !SettingsManager(context).isAdsDisabled()
    } catch (e: Exception) {
        true
    }

    /**
     * Reads the Wortise App ID from the manifest <meta-data> tag.
     * Returns null if the manifest does not contain the entry.
     */
    private fun readWortiseAppId(context: Context): String? = try {
        val ai = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        ai.metaData?.getString(WORTISE_APP_ID_META)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read Wortise App ID from manifest", e)
        null
    }

    /**
     * Initialises the Wortise SDK and pre-loads ad units. Call once from
     * [KiduyuTvApp.onCreate].
     *
     * The Wortise SDK requires WortiseSdk.initialize(...) to be called
     * BEFORE any ad class (BannerAd, InterstitialAd, ...) is constructed,
     * otherwise the SDK throws SdkNotInitializedException from its
     * internal coroutines. That call is performed first here.
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping Wortise preload")
            return
        }
        appContext = context.applicationContext
        try {
            // 1. Initialize the SDK FIRST — required by every Wortise ad class.
            if (!WortiseSdk.isInitialized) {
                val appId = readWortiseAppId(context)
                if (appId.isNullOrBlank() || appId == "DEFAULT") {
                    Log.w(
                        TAG,
                        "Wortise App ID missing or placeholder ('') — " +
                            "set 'wortiseAppId' in build.gradle. Skipping preload."
                    )
                    return
                }
                Log.i(TAG, "Initializing Wortise SDK with appId=")
                WortiseSdk.initialize(context, appId)
                if (!WortiseSdk.isInitialized) {
                    Log.w(TAG, "Wortise initialization requested; SDK not ready yet")
                    isInitialised = true
                    return
                }
                Log.i(TAG, "Wortise SDK initialised")
            }

            // 2. Now safe to construct ad classes.
            try {
                interstitialAd = InterstitialAd(context, AD_UNIT_INTERSTITIAL).apply {
                    listener = createInterstitialListener(context as? Activity, null)
                    loadAd()
                }
            } catch (e: com.wortise.ads.SdkNotInitializedException) {
                Log.w(TAG, "InterstitialAd skipped — SDK init race: ${e.message}")
            }
            try {
                rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                    listener = createRewardedListener(null, null)
                    loadAd()
                }
            } catch (e: com.wortise.ads.SdkNotInitializedException) {
                Log.w(TAG, "RewardedAd skipped — SDK init race: ${e.message}")
            }
            try {
                appOpenAd = AppOpenAd(context, AD_UNIT_APP_OPEN).apply {
                    autoReload = true
                    listener = createAppOpenListener(null)
                    loadAd()
                }
            } catch (e: com.wortise.ads.SdkNotInitializedException) {
                Log.w(TAG, "AppOpenAd skipped — SDK init race: ${e.message}")
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
     * No-op if the SDK is not initialised.
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        if (!isInitialised || !WortiseSdk.isInitialized) {
            Log.w(TAG, "loadBanner skipped — Wortise SDK not initialised")
            return
        }
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
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            // SDK reported ready but internal state is still warming up.
            // Swallow and let the next request retry after init settles.
            Log.w(TAG, "BannerAd constructed before SDK fully ready — skipping: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wortise banner", e)
        }
    }

    /** Call from the host Activity's onPause(). */
    fun pauseBanner() {
        if (!isInitialised) return
        currentBannerAd?.pause()
    }

    /** Call from the host Activity's onResume(). */
    fun resumeBanner() {
        if (!isInitialised) return
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
        if (!isInitialised || !WortiseSdk.isInitialized) {
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
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            Log.w(TAG, "Interstitial show skipped — SDK init race: ${e.message}")
            interstitialAd = null
            onDismissed()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise interstitial", e)
            onDismissed()
        }
    }

    private fun loadInterstitialAd(context: Context) {
        if (!isInitialised || !WortiseSdk.isInitialized) return
        try {
            interstitialAd = InterstitialAd(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                loadAd()
            }
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            Log.w(TAG, "InterstitialAd reload skipped — SDK init race: ${e.message}")
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
                Log.w(TAG, "Wortise interstitial failed: ")
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
                Log.w(TAG, "Wortise interstitial failed to show: ")
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
        if (!isInitialised || !WortiseSdk.isInitialized) {
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
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            Log.w(TAG, "Rewarded show skipped — SDK init race: ${e.message}")
            rewardedAd = null
            onDismissed()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Wortise rewarded", e)
            onDismissed()
        }
    }

    private fun loadRewardedAd(context: Context) {
        appContext = context.applicationContext
        if (!isInitialised || !WortiseSdk.isInitialized) return
        try {
            rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                loadAd()
            }
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            Log.w(TAG, "RewardedAd reload skipped — SDK init race: ${e.message}")
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
                Log.w(TAG, "Wortise rewarded failed: ")
            }

            override fun onRewardedImpression(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded impression")
            }

            override fun onRewardedRevenuePaid(ad: RewardedAd, data: RevenueData) {
                Log.i(TAG, "Wortise rewarded revenue paid")
            }

            override fun onRewardedFailedToShow(ad: RewardedAd, error: AdError) {
                Log.w(TAG, "Wortise rewarded failed to show: ")
                rewardedAd = null
                appContext?.let { loadRewardedAd(it) }
                onDismissed?.invoke()

            }

            override fun onRewardedClicked(ad: RewardedAd) {

                Log.i(TAG, "Wortise rewarded clicked")
            }

            override fun onRewardedDismissed(ad: RewardedAd) {
                Log.i(TAG, "Wortise rewarded dismissed")
                rewardedAd = null
                appContext?.let { loadRewardedAd(it) }
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
        if (!isInitialised || !WortiseSdk.isInitialized) {
            loadAppOpenAd(activity)
            return
        }
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
        if (!isInitialised || !WortiseSdk.isInitialized) return
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
                Log.w(TAG, "Wortise app open failed: ")
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
                Log.w(TAG, "Wortise app open failed to show: ")
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
        get() = isInitialised && WortiseSdk.isInitialized &&
            interstitialAd?.isAvailable == true

    val isRewardedReady: Boolean
        get() = isInitialised && WortiseSdk.isInitialized &&
            rewardedAd?.isAvailable == true

    val isAppOpenReady: Boolean
        get() = isInitialised && WortiseSdk.isInitialized &&
            appOpenAd?.isAvailable == true
}
