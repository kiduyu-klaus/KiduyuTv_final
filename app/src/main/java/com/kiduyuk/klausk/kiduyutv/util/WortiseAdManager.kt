package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import com.wortise.ads.AdError
import com.wortise.ads.AdSize
import com.wortise.ads.RevenueData
import com.wortise.ads.WortiseSdk
import com.wortise.ads.appopen.AppOpenAd
import com.wortise.ads.banner.BannerAd
import com.wortise.ads.interstitial.InterstitialAd
import com.wortise.ads.rewarded.RewardedAd
import com.wortise.ads.rewarded.models.Reward
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wortise Ad Manager — singleton.
 *
 * Handles banner, interstitial, rewarded video, and app open ads from Wortise.
 * Safe to call even if the SDK failed to initialise: methods no-op and
 * invoke callbacks so the app flow continues.
 *
 * Interstitial frequency is guarded by [FullscreenAdPolicy] (3 min globally).
 *
 * Uses the app's configured Wortise placements for both phone and TV variants.
 */
object WortiseAdManager {

    private const val TAG = "WortiseAdManager"
    private const val AD_UNIT_APP_OPEN = "5845bc27-cc4f-4a4d-b765-92a0ce8e8322"
    private const val AD_UNIT_BANNER = "47c5c956-d872-4246-aee5-78f87d4a94f4"
    private const val AD_UNIT_INTERSTITIAL = "6fae5513-bc81-4186-b0a1-69204056b6de"
    private const val AD_UNIT_NATIVE = "2a0f156a-57eb-47d5-a258-94023037c4e0"
    private const val AD_UNIT_REWARDED = "d07aa9c3-77af-46f5-a22e-61a8f923bb2e"
    /**
     * Manifest meta-data key holding the Wortise App ID. Mirrors the entry in
     * AndroidManifest.xml:
     * <meta-data android:name="com.wortise.WORTISE_APP_ID" .../>
     */
    private const val WORTISE_APP_ID_META = "com.wortise.WORTISE_APP_ID"

    @Volatile
    var isInitialised = false
        private set
    @Volatile private var sdkInitialized = false
    private val _isInitialisedState = MutableStateFlow(false)
    val isInitialisedState: StateFlow<Boolean> = _isInitialisedState.asStateFlow()

    private val initializationInProgress = AtomicBoolean(false)
    private val consentRequestInProgress = AtomicBoolean(false)
    private val interstitialLoadInProgress = AtomicBoolean(false)
    private val rewardedLoadInProgress = AtomicBoolean(false)
    private val appOpenLoadInProgress = AtomicBoolean(false)
    @Volatile private var wortiseConsentHandled = false
    @Volatile var isAppOpenShowing = false
        private set
    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var currentBannerAd: BannerAd? = null
    @Volatile private var pendingBannerRequest: Pair<WeakReference<Activity>, WeakReference<ViewGroup>>? = null

    @Volatile
    private var appContext: Context? = null

    private fun shouldShowAds(context: Context): Boolean = AdEligibility.canRequestAds(context)

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
     * after the app's primary UMP flow has completed.
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
            if (WortiseSdk.isInitialized) {
                onSdkInitialized(context)
                return
            }

            val appId = readWortiseAppId(context)
            if (appId.isNullOrBlank()) {
                Log.e(TAG, "Wortise App ID is missing; ad initialization aborted")
                return
            }
            if (!initializationInProgress.compareAndSet(false, true)) return

            Log.i(TAG, "Initializing Wortise SDK")
            WortiseSdk.initialize(context.applicationContext, appId) {
                initializationInProgress.set(false)
                onSdkInitialized(context)
            }
        } catch (e: Exception) {
            initializationInProgress.set(false)
            Log.e(TAG, "Wortise preload failed", e)
        }
    }

    private fun onSdkInitialized(context: Context) {
        sdkInitialized = true
        Log.i(TAG, "Wortise SDK initialized")
        requestWortiseConsentAndLoad(context)
    }

    private fun requestWortiseConsentAndLoad(context: Context) {
        if (wortiseConsentHandled) {
            activateAndLoadAds(context)
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            Log.i(TAG, "Waiting for a foreground Activity to complete Wortise consent")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            Log.i(TAG, "Deferring Wortise consent until an Activity is active")
            return
        }
        if (!consentRequestInProgress.compareAndSet(false, true)) return

        com.wortise.ads.consent.ConsentManager.requestIfRequired(activity) { shown ->
            consentRequestInProgress.set(false)
            wortiseConsentHandled = true
            Log.i(TAG, "Wortise consent flow completed: shown=$shown")
            if (shouldShowAds(activity)) {
                activateAndLoadAds(activity.applicationContext)
            }
        }
    }

    private fun activateAndLoadAds(context: Context) {
        isInitialised = true
        _isInitialisedState.value = true
        loadAds(context)
    }

    private fun loadAds(context: Context) {
        if (!shouldShowAds(context) || !WortiseSdk.isInitialized) return
        loadInterstitialAd(context)
        loadRewardedAd(context)
        loadAppOpenAd(context)
        val pending = pendingBannerRequest
        val activity = pending?.first?.get()
        val container = pending?.second?.get()
        if (activity != null && container != null && container.isAttachedToWindow) {
            pendingBannerRequest = null
            loadBanner(activity, container)
        }
        Log.i(TAG, "Wortise ad loads requested")
    }

    fun retryInitIfEligible(context: Context) {
        if (!sdkInitialized || !WortiseSdk.isInitialized) {
            preloadAds(context)
        } else {
            requestWortiseConsentAndLoad(context)
        }
    }

    fun nativeAdUnitId(): String = AD_UNIT_NATIVE

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a Wortise banner into the supplied [container].
     * No-op if the SDK is not initialised.
     */
    fun loadBanner(activity: Activity, container: ViewGroup) {
        if (!shouldShowAds(activity)) return
        if (!isInitialised || !WortiseSdk.isInitialized) {
            Log.w(TAG, "loadBanner skipped — Wortise SDK not initialised")
            pendingBannerRequest = Pair(WeakReference(activity), WeakReference(container))
            retryInitIfEligible(activity)
            return
        }
        try {
            // Always release the previous SDK view before replacing it. Wortise
            // registers an Activity receiver for each BannerAd, so retaining a
            // stale instance causes an IntentReceiver leak when that Activity
            // is destroyed during navigation.
            destroyBanner()
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
        pendingBannerRequest = null
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
        if (!FullscreenAdPolicy.canShowInterstitial(activity)) {
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
        if (!isInitialised || !WortiseSdk.isInitialized ||
            interstitialAd?.isAvailable == true ||
            !interstitialLoadInProgress.compareAndSet(false, true)
        ) return
        try {
            interstitialAd = InterstitialAd(context, AD_UNIT_INTERSTITIAL).apply {
                listener = createInterstitialListener(context as? Activity, null)
                loadAd()
            }
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            interstitialLoadInProgress.set(false)
            Log.w(TAG, "InterstitialAd reload skipped — SDK init race: ${e.message}")
        } catch (e: Exception) {
            interstitialLoadInProgress.set(false)
            Log.e(TAG, "Failed to load Wortise interstitial", e)
        }
    }

    private fun createInterstitialListener(
        activity: Activity?,
        onDismissed: (() -> Unit)?
    ): InterstitialAd.Listener {
        return object : InterstitialAd.Listener {
            override fun onInterstitialLoaded(ad: InterstitialAd) {
                interstitialLoadInProgress.set(false)
                Log.i(TAG, "Wortise interstitial loaded")
            }

            override fun onInterstitialFailedToLoad(ad: InterstitialAd, error: AdError) {
                interstitialLoadInProgress.set(false)
                Log.w(TAG, "Wortise interstitial failed: ${error.message}")
            }

            override fun onInterstitialShown(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial shown")
                activity?.let(FullscreenAdPolicy::recordInterstitialShown)
            }

            override fun onInterstitialImpression(ad: InterstitialAd) {
                Log.i(TAG, "Wortise interstitial impression")
            }

            override fun onInterstitialRevenuePaid(ad: InterstitialAd, data: RevenueData) {
                Log.i(TAG, "Wortise interstitial revenue paid")
            }


            override fun onInterstitialFailedToShow(ad: InterstitialAd, error: AdError) {
                Log.w(TAG, "Wortise interstitial failed to show: ${error.message}")
                interstitialAd = null
                activity?.let { loadInterstitialAd(it) }
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
        if (!isInitialised || !WortiseSdk.isInitialized ||
            rewardedAd?.isAvailable == true ||
            !rewardedLoadInProgress.compareAndSet(false, true)
        ) return
        try {
            rewardedAd = RewardedAd(context, AD_UNIT_REWARDED).apply {
                listener = createRewardedListener(null, null)
                loadAd()
            }
        } catch (e: com.wortise.ads.SdkNotInitializedException) {
            rewardedLoadInProgress.set(false)
            Log.w(TAG, "RewardedAd reload skipped — SDK init race: ${e.message}")
        } catch (e: Exception) {
            rewardedLoadInProgress.set(false)
            Log.e(TAG, "Failed to load Wortise rewarded", e)
        }
    }

    private fun createRewardedListener(
        onRewarded: (() -> Unit)?,
        onDismissed: (() -> Unit)?
    ): RewardedAd.Listener {
        return object : RewardedAd.Listener {
            override fun onRewardedLoaded(ad: RewardedAd) {
                rewardedLoadInProgress.set(false)
                Log.i(TAG, "Wortise rewarded loaded")
            }

            override fun onRewardedFailedToLoad(ad: RewardedAd, error: AdError) {
                rewardedLoadInProgress.set(false)
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
        if (!isInitialised || !WortiseSdk.isInitialized ||
            appOpenAd?.isAvailable == true ||
            !appOpenLoadInProgress.compareAndSet(false, true)
        ) return
        try {
            appOpenAd = AppOpenAd(context, AD_UNIT_APP_OPEN).apply {
                autoReload = true
                listener = createAppOpenListener(null)
                loadAd()
            }

        } catch (e: Exception) {
            appOpenLoadInProgress.set(false)

            Log.e(TAG, "Failed to load Wortise app open", e)

        }

    }


    private fun createAppOpenListener(onDismissed: (() -> Unit)?): AppOpenAd.Listener {
        return object : AppOpenAd.Listener {
            override fun onAppOpenLoaded(ad: AppOpenAd) {
                appOpenLoadInProgress.set(false)

                Log.i(TAG, "Wortise app open loaded")
            }

            override fun onAppOpenFailedToLoad(ad: AppOpenAd, error: AdError) {
                appOpenLoadInProgress.set(false)
                Log.w(TAG, "Wortise app open failed: ${error.message}")
            }

            override fun onAppOpenShown(ad: AppOpenAd) {
                isAppOpenShowing = true
                Log.i(TAG, "Wortise app open shown")
            }

            override fun onAppOpenImpression(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open impression")
            }

            override fun onAppOpenRevenuePaid(ad: AppOpenAd, data: RevenueData) {
                Log.i(TAG, "Wortise app open revenue paid")
            }

            override fun onAppOpenFailedToShow(ad: AppOpenAd, error: AdError) {
                isAppOpenShowing = false
                Log.w(TAG, "Wortise app open failed to show: ${error.message}")
                onDismissed?.invoke()
            }


            override fun onAppOpenClicked(ad: AppOpenAd) {
                Log.i(TAG, "Wortise app open clicked")
            }

            override fun onAppOpenDismissed(ad: AppOpenAd) {
                isAppOpenShowing = false
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
