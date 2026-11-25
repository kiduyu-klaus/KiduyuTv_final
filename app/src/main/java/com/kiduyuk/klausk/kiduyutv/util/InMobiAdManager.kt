package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.inmobi.ads.InMobiAdRequestStatus
import com.inmobi.ads.InMobiBanner
import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.InMobiNative
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.ads.listeners.NativeAdEventListener
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Centralized, consent-gated InMobi integration. All SDK calls are serialized on the main thread. */
object InMobiAdManager {
    private const val TAG = "InMobiAdManager"
    private val accountId: String
        get() = BuildConfig.INMOBI_ACCOUNT_ID.trim()

    const val INTERSTITIAL_PLACEMENT = 10000893847L
    const val BANNER_PLACEMENT = 10000893848L
    const val REWARDED_PLACEMENT = 10000893849L
    const val NATIVE_PLACEMENT = 10000893850L
    private const val BANNER_REFRESH_SECONDS = 60

    private val mainHandler = Handler(Looper.getMainLooper())
    private val initializationStarted = AtomicBoolean(false)
    private val interstitialLoadStarted = AtomicBoolean(false)
    private val rewardedLoadStarted = AtomicBoolean(false)
    private val bannerRequestId = AtomicLong(0L)

    @Volatile var isInitialised = false
        private set
    @Volatile private var interstitial: InMobiInterstitial? = null
    @Volatile private var rewarded: InMobiInterstitial? = null
    @Volatile private var banner: InMobiBanner? = null
    @Volatile private var appContext: Context? = null

    // All accesses happen on the main thread. The nullable callbacks also act as
    // the guard that prevents overlapping InMobi fullscreen shows.
    private var pendingInterstitialDismissed: (() -> Unit)? = null
    private var pendingInterstitialUnavailable: (() -> Unit)? = null
    private var pendingRewarded: (() -> Unit)? = null
    private var pendingRewardedDismissed: (() -> Unit)? = null
    private var pendingRewardedUnavailable: (() -> Unit)? = null

    val isInterstitialReady: Boolean
        get() = isInitialised && interstitial?.isReady() == true
    val isRewardedReady: Boolean
        get() = isInitialised && rewarded?.isReady() == true

    fun preloadAds(context: Context) {
        runOnMain {
            if (!AdEligibility.canRequestAds(context)) {
                Log.i(TAG, "Consent does not permit InMobi requests")
                return@runOnMain
            }
            appContext = context.applicationContext
            if (!isInitialised) initialize(context.applicationContext) else preloadFullscreenAds()
        }
    }

    fun retryInitIfEligible(context: Context) = preloadAds(context)

    private fun initialize(context: Context) {
        if (accountId.length !in 32..36) {
            Log.e(TAG, "Invalid InMobi account ID: expected 32-36 characters; configure INMOBI_ACCOUNT_ID")
            return
        }
        if (!initializationStarted.compareAndSet(false, true)) return
        try {
            val consent = ConsentManager.getInMobiConsentObject(context)
            InMobiSdk.init(context, accountId, consent, object : SdkInitializationListener {
                override fun onInitializationComplete(error: java.lang.Error?) {
                    runOnMain {
                        if (error == null) {
                            isInitialised = true
                            Log.i(TAG, "InMobi initialized: ${InMobiSdk.getVersion()}")
                            preloadFullscreenAds()
                        } else {
                            isInitialised = false
                            initializationStarted.set(false)
                            Log.w(TAG, "InMobi initialization failed: $error")
                        }
                    }
                }
            })
        } catch (error: Exception) {
            isInitialised = false
            initializationStarted.set(false)
            Log.e(TAG, "InMobi initialization threw", error)
        }
    }

    /** Updates the SDK after UMP privacy options are changed in an existing process. */
    fun updateConsent(context: Context) {
        runOnMain {
            if (!isInitialised) return@runOnMain
            try {
                ConsentManager.getInMobiConsentObject(context)?.let(InMobiSdk::updateGDPRConsent)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to update InMobi GDPR consent", error)
            }
        }
    }

    private fun preloadFullscreenAds() {
        loadInterstitial()
        loadRewarded()
    }

    private fun loadInterstitial() {
        if (!isInitialised || interstitial?.isReady() == true ||
            !interstitialLoadStarted.compareAndSet(false, true)
        ) return
        try {
            val context = appContext ?: run {
                interstitialLoadStarted.set(false)
                return
            }
            val ad = interstitial ?: InMobiInterstitial(
                context,
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
        if (!isInitialised || rewarded?.isReady() == true ||
            !rewardedLoadStarted.compareAndSet(false, true)
        ) return
        try {
            val context = appContext ?: run {
                rewardedLoadStarted.set(false)
                return
            }
            val ad = rewarded ?: InMobiInterstitial(
                context,
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
            if (isRewarded) {
                val unavailable = pendingRewardedUnavailable
                clearRewardedCallbacks()
                unavailable?.invoke()
            } else {
                val unavailable = pendingInterstitialUnavailable
                clearInterstitialCallbacks()
                unavailable?.invoke()
            }
            reloadAfterFullscreen(isRewarded)
        }

        override fun onAdDismissed(ad: InMobiInterstitial) {
            if (isRewarded) {
                val dismissed = pendingRewardedDismissed
                clearRewardedCallbacks()
                dismissed?.invoke()
            } else {
                val dismissed = pendingInterstitialDismissed
                clearInterstitialCallbacks()
                dismissed?.invoke()
            }
            reloadAfterFullscreen(isRewarded)
        }

        override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: Map<Any, Any>) {
            if (isRewarded) {
                val reward = pendingRewarded
                pendingRewarded = null
                reward?.invoke()
            }
        }

        override fun onAdDisplayed(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
            Log.i(TAG, "InMobi ${if (isRewarded) "rewarded" else "interstitial"} displayed")
        }
    }

    private fun clearInterstitialCallbacks() {
        pendingInterstitialDismissed = null
        pendingInterstitialUnavailable = null
    }

    private fun clearRewardedCallbacks() {
        pendingRewarded = null
        pendingRewardedDismissed = null
        pendingRewardedUnavailable = null
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

    fun showInterstitial(
        activity: Activity,
        onDismissed: () -> Unit = {},
        onUnavailable: () -> Unit = {}
    ) {
        runOnMain {
            if (!isInterstitialReady || activity.isFinishing || activity.isDestroyed ||
                pendingInterstitialDismissed != null || pendingRewardedDismissed != null
            ) {
                onUnavailable()
                return@runOnMain
            }
            try {
                pendingInterstitialDismissed = onDismissed
                pendingInterstitialUnavailable = onUnavailable
                interstitial?.show(activity)
                FullscreenAdPolicy.recordInterstitialShown(activity)
            } catch (error: Exception) {
                clearInterstitialCallbacks()
                Log.w(TAG, "InMobi interstitial show failed", error)
                reloadAfterFullscreen(false)
                onUnavailable()
            }
        }
    }

    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onDismissed: () -> Unit = {},
        onUnavailable: () -> Unit = {}
    ) {
        runOnMain {
            if (!isRewardedReady || activity.isFinishing || activity.isDestroyed ||
                pendingInterstitialDismissed != null || pendingRewardedDismissed != null
            ) {
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
                clearRewardedCallbacks()
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
            val requestId = bannerRequestId.incrementAndGet()
            val completed = AtomicBoolean(false)
            fun isCurrent(ad: InMobiBanner? = null): Boolean =
                bannerRequestId.get() == requestId && (ad == null || banner === ad)
            fun failOnce() {
                if (completed.compareAndSet(false, true) && isCurrent()) onFailed()
            }
            val ad = try {
                InMobiBanner(activity, BANNER_PLACEMENT).apply {
                    setEnableAutoRefresh(true)
                    setRefreshInterval(BANNER_REFRESH_SECONDS)
                    setListener(object : BannerAdEventListener() {
                        override fun onAdLoadSucceeded(
                            ad: InMobiBanner,
                            info: com.inmobi.ads.AdMetaInfo
                        ) {
                            if (!isCurrent(ad)) return
                            Log.i(TAG, "InMobi banner loaded")
                            if (completed.compareAndSet(false, true)) onLoaded()
                        }

                        override fun onAdLoadFailed(
                            ad: InMobiBanner,
                            status: InMobiAdRequestStatus
                        ) {
                            if (!isCurrent(ad)) return
                            Log.w(TAG, "InMobi banner failed: ${status.message}")
                            failOnce()
                        }

                        override fun onAdImpression(ad: InMobiBanner) {
                            if (isCurrent(ad)) {
                                AdEventReporter.report("banner", "banner", "inmobi", "impression")
                            }
                        }
                    })
                }
            } catch (error: Exception) {
                Log.w(TAG, "InMobi banner construction failed", error)
                failOnce()
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
                if (completed.compareAndSet(false, true)) onFailed()
            }
        }
    }

    fun destroyBanner() {
        runOnMain {
            bannerRequestId.incrementAndGet()
            val oldBanner = banner
            banner = null
            try { oldBanner?.destroy() } catch (error: Exception) {
                Log.w(TAG, "InMobi banner destroy failed", error)
            }
            oldBanner?.let { (it.parent as? ViewGroup)?.removeView(it) }
        }
    }

    class NativeLoadRequest internal constructor() {
        private val cancelled = AtomicBoolean(false)
        @Volatile internal var loadedAd: InMobiNative? = null

        internal fun cancel() {
            if (!cancelled.compareAndSet(false, true)) return
            val ad = loadedAd
            loadedAd = null
            ad?.let { InMobiAdManager.destroyNative(it) }
        }

        internal fun isCancelled(): Boolean = cancelled.get()
    }

    fun loadNative(
        context: Context,
        onLoaded: (InMobiNative) -> Unit,
        onFailed: () -> Unit
    ): NativeLoadRequest {
        val request = NativeLoadRequest()
        runOnMain {
            if (request.isCancelled()) return@runOnMain
            if (!AdEligibility.canRequestAds(context) || !isInitialised) {
                onFailed()
                return@runOnMain
            }
            try {
                val ad = InMobiNative(
                    context.applicationContext,
                    NATIVE_PLACEMENT,
                    object : NativeAdEventListener() {
                        override fun onAdLoadSucceeded(
                            ad: InMobiNative,
                            info: com.inmobi.ads.AdMetaInfo
                        ) {
                            request.loadedAd = ad
                            if (request.isCancelled()) destroyNative(ad) else onLoaded(ad)
                        }

                        override fun onAdLoadFailed(
                            ad: InMobiNative,
                            status: InMobiAdRequestStatus
                        ) {
                            if (!request.isCancelled()) {
                                Log.w(TAG, "InMobi native failed: ${status.message}")
                                onFailed()
                            }
                        }

                        override fun onAdImpression(ad: InMobiNative) {
                            if (!request.isCancelled()) {
                                AdEventReporter.report("native", "native", "inmobi", "impression")
                            }
                        }
                    }
                )
                request.loadedAd = ad
                if (request.isCancelled()) destroyNative(ad) else ad.load()
            } catch (error: Exception) {
                if (!request.isCancelled()) {
                    Log.w(TAG, "InMobi native load failed", error)
                    onFailed()
                }
            }
        }
        return request
    }

    fun destroyNative(ad: InMobiNative?) {
        runOnMain {
            try {
                ad?.unTrackViews()
                ad?.destroy()
            } catch (error: Exception) {
                Log.w(TAG, "InMobi native destroy failed", error)
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }
}
