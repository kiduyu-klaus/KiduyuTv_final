package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.inmobi.ads.InMobiBanner
import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.InMobiNative
import com.inmobi.ads.InMobiAdRequestStatus
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.ads.listeners.NativeAdEventListener
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import java.util.concurrent.atomic.AtomicBoolean

/** Centralized, consent-gated InMobi integration. All SDK calls are serialized on the main thread. */
object InMobiAdManager {
    private const val TAG = "InMobiAdManager"
    private const val APP_KEY = "10000259716"
    const val INTERSTITIAL_PLACEMENT = 10000893847L
    const val BANNER_PLACEMENT = 10000893848L
    const val REWARDED_PLACEMENT = 10000893849L
    const val NATIVE_PLACEMENT = 10000893850L
    private const val BANNER_REFRESH_SECONDS = 60
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initializationStarted = AtomicBoolean(false)
    private val interstitialLoadStarted = AtomicBoolean(false)
    private val rewardedLoadStarted = AtomicBoolean(false)
    @Volatile var isInitialised = false
        private set
    @Volatile private var interstitial: InMobiInterstitial? = null
    @Volatile private var rewarded: InMobiInterstitial? = null
    @Volatile private var banner: InMobiBanner? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var pendingInterstitialDismissed: (() -> Unit)? = null
    @Volatile private var pendingInterstitialUnavailable: (() -> Unit)? = null
    @Volatile private var pendingRewarded: (() -> Unit)? = null
    @Volatile private var pendingRewardedDismissed: (() -> Unit)? = null
    @Volatile private var pendingRewardedUnavailable: (() -> Unit)? = null

    val isInterstitialReady: Boolean
        get() = isInitialised && interstitial?.isReady == true
    val isRewardedReady: Boolean
        get() = isInitialised && rewarded?.isReady == true

    fun preloadAds(context: Context) {
        runOnMain {
            if (!AdEligibility.canRequestAds(context)) {
                Log.i(TAG, "Consent does not permit InMobi requests")
                return@runOnMain
            }
            appContext = context.applicationContext
            if (!isInitialised) {
                initialize(context.applicationContext)
            } else {
                preloadFullscreenAds()
            }
        }
    }

    fun retryInitIfEligible(context: Context) = preloadAds(context)

    private fun initialize(context: Context) {
        if (!initializationStarted.compareAndSet(false, true)) return
        try {
            InMobiSdk.init(context, APP_KEY, null, SdkInitializationListener { error ->
                runOnMain {
                    if (error == null) {
                        isInitialised = true
                        Log.i(TAG, "InMobi initialized: ${InMobiSdk.getVersion()}")
                        preloadFullscreenAds()
                    } else {
                        initializationStarted.set(false)
                        Log.w(TAG, "InMobi initialization failed: $error")
                    }
                }
            })
        } catch (error: Exception) {
            initializationStarted.set(false)
            Log.e(TAG, "InMobi initialization threw", error)
        }
    }

    private fun preloadFullscreenAds() {
        loadInterstitial()
        loadRewarded()
    }

    private fun loadInterstitial() {
        if (!isInitialised || interstitial?.isReady == true ||
            !interstitialLoadStarted.compareAndSet(false, true)) return
        try {
            val ad = interstitial ?: InMobiInterstitial(
                appContext ?: return,
                INTERSTITIAL_PLACEMENT,
                interstitialListener(false)
            ).also { interstitial = it }
            ad.load()
        } catch (error: Exception) {
            interstitialLoadStarted.set(false)
            Log.w(TAG, "InMobi interstitial load failed", error)
        }
    }

    private fun loadRewarded() {
        if (!isInitialised || rewarded?.isReady == true ||
            !rewardedLoadStarted.compareAndSet(false, true)) return
        try {
            val ad = rewarded ?: InMobiInterstitial(
                appContext ?: return,
                REWARDED_PLACEMENT,
                interstitialListener(true)
            ).also { rewarded = it }
            ad.load()
        } catch (error: Exception) {
            rewardedLoadStarted.set(false)
            Log.w(TAG, "InMobi rewarded load failed", error)
        }
    }

    private fun interstitialListener(isRewarded: Boolean) = object : InterstitialAdEventListener() {
        override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
            if (isRewarded) rewardedLoadStarted.set(false) else interstitialLoadStarted.set(false)
            Log.i(TAG, "InMobi ${if (isRewarded) "rewarded" else "interstitial"} loaded")
        }
        override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
            if (isRewarded) rewardedLoadStarted.set(false) else interstitialLoadStarted.set(false)
            Log.w(TAG, "InMobi ${if (isRewarded) "rewarded" else "interstitial"} failed: ${status.message}")
        }
        override fun onAdDisplayFailed(ad: InMobiInterstitial) {
            Log.w(TAG, "InMobi ${if (isRewarded) "rewarded" else "interstitial"} display failed")
            if (isRewarded) pendingRewardedUnavailable?.invoke()
            else pendingInterstitialUnavailable?.invoke()
            reloadAfterFullscreen(isRewarded)
        }
        override fun onAdDismissed(ad: InMobiInterstitial) {
            if (isRewarded) {
                pendingRewardedDismissed?.invoke()
                pendingRewarded = null
                pendingRewardedDismissed = null
                pendingRewardedUnavailable = null
            } else {
                pendingInterstitialDismissed?.invoke()
                pendingInterstitialDismissed = null
                pendingInterstitialUnavailable = null
            }
            reloadAfterFullscreen(isRewarded)
        }
        override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: Map<Any, Any>) {
            if (isRewarded) pendingRewarded?.invoke()
        }
        override fun onAdDisplayed(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
            Log.i(TAG, "InMobi ${if (isRewarded) "rewarded" else "interstitial"} displayed")
        }
    }

    private fun reloadAfterFullscreen(isRewarded: Boolean) {
        runOnMain {
            if (isRewarded) {
                rewarded = null
                rewardedLoadStarted.set(false)
                loadRewarded()
            } else {
                interstitial = null
                interstitialLoadStarted.set(false)
                loadInterstitial()
            }
        }
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
        runOnMain {
            if (!isInterstitialReady || activity.isFinishing || activity.isDestroyed) {
                onUnavailable()
                return@runOnMain
            }
            try {
                pendingInterstitialDismissed = onDismissed
                pendingInterstitialUnavailable = onUnavailable
                interstitial?.show(activity)
                FullscreenAdPolicy.recordInterstitialShown(activity)
            } catch (error: Exception) {
                pendingInterstitialDismissed = null
                pendingInterstitialUnavailable = null
                Log.w(TAG, "InMobi interstitial show failed", error)
                reloadAfterFullscreen(false)
                onUnavailable()
            }
        }
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit = {}, onDismissed: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
        runOnMain {
            if (!isRewardedReady || activity.isFinishing || activity.isDestroyed) {
                onUnavailable()
                return@runOnMain
            }
            try {
                pendingRewarded = onRewarded
                pendingRewardedDismissed = onDismissed
                pendingRewardedUnavailable = onUnavailable
                rewarded?.show(activity)
                FullscreenAdPolicy.recordInterstitialShown(activity)
            } catch (error: Exception) {
                pendingRewarded = null
                pendingRewardedDismissed = null
                pendingRewardedUnavailable = null
                Log.w(TAG, "InMobi rewarded show failed", error)
                reloadAfterFullscreen(true)
                onUnavailable()
            }
        }
    }

    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        onLoaded: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        runOnMain {
            if (!AdEligibility.canRequestAds(activity) || !isInitialised || activity.isFinishing) {
                onFailed()
                return@runOnMain
            }
            destroyBanner()
            val ad = try {
                InMobiBanner(activity, BANNER_PLACEMENT).apply {
                    setEnableAutoRefresh(true)
                    setRefreshInterval(BANNER_REFRESH_SECONDS)
                    setListener(object : BannerAdEventListener() {
                        override fun onAdLoadSucceeded(ad: InMobiBanner, info: com.inmobi.ads.AdMetaInfo) {
                            Log.i(TAG, "InMobi banner loaded")
                            onLoaded()
                        }
                        override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                            Log.w(TAG, "InMobi banner failed: ${status.message}")
                            onFailed()
                        }
                        override fun onAdImpression(ad: InMobiBanner) {
                            AdEventReporter.report("banner", "banner", "inmobi", "impression")
                        }
                    })
                }
            } catch (error: Exception) {
                Log.w(TAG, "InMobi banner construction failed", error)
                onFailed()
                return@runOnMain
            }
            banner = ad
            container.removeAllViews()
            val heightPx = (50f * activity.resources.displayMetrics.density).toInt()
            container.addView(ad, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx))
            try {
                ad.load()
            } catch (error: Exception) {
                Log.w(TAG, "InMobi banner load failed", error)
                destroyBanner()
                onFailed()
            }
        }
    }

    fun destroyBanner() {
        runOnMain {
            try { banner?.destroy() } catch (error: Exception) { Log.w(TAG, "InMobi banner destroy failed", error) }
            banner?.let { (it.parent as? ViewGroup)?.removeView(it) }
            banner = null
        }
    }

    fun loadNative(context: Context, onLoaded: (InMobiNative) -> Unit, onFailed: () -> Unit) {
        runOnMain {
            if (!AdEligibility.canRequestAds(context) || !isInitialised) {
                onFailed()
                return@runOnMain
            }
            try {
                val ad = InMobiNative(context.applicationContext, NATIVE_PLACEMENT, object : NativeAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiNative, info: com.inmobi.ads.AdMetaInfo) {
                        onLoaded(ad)
                    }
                    override fun onAdLoadFailed(ad: InMobiNative, status: InMobiAdRequestStatus) {
                        Log.w(TAG, "InMobi native failed: ${status.message}")
                        onFailed()
                    }
                })
                ad.load()
            } catch (error: Exception) {
                Log.w(TAG, "InMobi native load failed", error)
                onFailed()
            }
        }
    }

    fun destroyNative(ad: InMobiNative?) {
        try { ad?.unTrackViews(); ad?.destroy() } catch (error: Exception) { Log.w(TAG, "InMobi native destroy failed", error) }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }
}
