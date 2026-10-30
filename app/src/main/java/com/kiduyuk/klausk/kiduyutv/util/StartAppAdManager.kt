package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.startapp.sdk.ads.banner.BannerCreator
import com.startapp.sdk.ads.banner.BannerFormat
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.BannerRequest
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.VideoListener
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.model.AdPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** Start.io manager using the SDK 5.3.1 initialization and inline-ad APIs. */
object StartAppAdManager {
    private const val TAG = "StartAppAdManager"
    private const val FULLSCREEN_LOAD_TIMEOUT_MS = 8_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val CONSENT_PREFERENCES = "startapp_consent"
    private const val CONSENT_TIMESTAMP_KEY = "consent_timestamp"
    private const val IAB_US_PRIVACY_KEY = "IABUSPrivacy_String"

    @Volatile var isInitialised = false
        private set
    @Volatile private var initializationStarted = false
    private val pendingInitializationActions = mutableListOf<() -> Unit>()
    private val _isInitialisedState = MutableStateFlow(false)
    val isInitialisedState: StateFlow<Boolean> = _isInitialisedState.asStateFlow()

    @Synchronized
    fun preloadAds(context: Context) {
        if (isInitialised || initializationStarted) return
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads not eligible yet - deferring Start.io initialization")
            return
        }
        initializationStarted = true
        try {
            // The official demo enables this only during development/testing.
            if (BuildConfig.START_IO_TEST_ADS) StartAppSDK.setTestAdsEnabled(true)
            StartAppSDK.initParams(context.applicationContext, BuildConfig.START_IO_APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    runOnMain {
                        if (!isInitialised) {
                            isInitialised = true
                            _isInitialisedState.value = true
                            applyConsent(context)
                            Log.i(TAG, "Start.io initialization completed")
                            val pending = synchronized(pendingInitializationActions) {
                                pendingInitializationActions.toList().also { pendingInitializationActions.clear() }
                            }
                            pending.forEach { it.invoke() }
                        }
                    }
                }
                .init()
        } catch (error: Exception) {
            initializationStarted = false
            Log.e(TAG, "Start.io initialization failed", error)
        }
    }

    fun applyConsent(context: Context) {
        try {
            val preferences = context.getSharedPreferences(CONSENT_PREFERENCES, Context.MODE_PRIVATE)
            val timestamp = preferences.getLong(CONSENT_TIMESTAMP_KEY, System.currentTimeMillis())
                .also { if (!preferences.contains(CONSENT_TIMESTAMP_KEY)) preferences.edit().putLong(CONSENT_TIMESTAMP_KEY, it).apply() }
            StartAppSDK.setUserConsent(context, "pas", timestamp, ConsentManager.canShowStartIoPersonalizedAds(context))
            val umpPreferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val privacyString = umpPreferences.getString(IAB_US_PRIVACY_KEY, null)
            val extras = StartAppSDK.getExtras(context).edit()
            if (privacyString.isNullOrBlank()) extras.remove(IAB_US_PRIVACY_KEY) else extras.putString(IAB_US_PRIVACY_KEY, privacyString)
            extras.apply()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to apply consent to Start.io", error)
        }
    }

    fun recordConsentUpdate(context: Context) {
        context.getSharedPreferences(CONSENT_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putLong(CONSENT_TIMESTAMP_KEY, System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun retryInitIfEligible(context: Context) {
        if (!isInitialised) {
            if (shouldShowAds(context)) preloadAds(context)
        } else applyConsent(context)
    }

    private fun shouldShowAds(context: Context) = AdEligibility.canRequestAds(context)

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    fun loadBanner(activity: Activity, container: ViewGroup, onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) =
        loadInlineAd(activity, container, BannerFormat.BANNER, null, onLoaded, onFailed)

    fun loadMrec(activity: Activity, container: ViewGroup, adTag: String? = null, onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) =
        loadInlineAd(activity, container, BannerFormat.MREC, adTag, onLoaded, onFailed)

    fun loadCover(activity: Activity, container: ViewGroup, adTag: String? = null, onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) =
        loadInlineAd(activity, container, BannerFormat.COVER, adTag, onLoaded, onFailed)

    /** Shared SDK-5.3.1 BannerRequest path for BANNER, MREC, and COVER. */
    fun loadInlineAd(
        activity: Activity,
        container: ViewGroup,
        format: BannerFormat,
        adTag: String? = null,
        onLoaded: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) { runOnMain(onFailed); return }
        if (!isInitialised) preloadAds(activity)
        if (!isInitialised) {
            synchronized(pendingInitializationActions) {
                pendingInitializationActions += {
                    loadInlineAd(activity, container, format, adTag, onLoaded, onFailed)
                }
            }
            return
        }
        try {
            val preferences = AdPreferences().apply { if (!adTag.isNullOrBlank()) setAdTag(adTag) }
            BannerRequest(activity.applicationContext)
                .setAdFormat(format)
                .setAdPreferences(preferences)
                .load { creator: BannerCreator?, error: String? ->
                    runOnMain {
                        if (creator == null || activity.isFinishing) {
                            Log.w(TAG, "Start.io $format failed: ${error ?: "no creator"}")
                            onFailed()
                            return@runOnMain
                        }
                        try {
                            val adView = creator.create(activity.applicationContext, bannerListener(format))
                            container.removeAllViews()
                            container.addView(adView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                            onLoaded()
                        } catch (error: Exception) {
                            Log.e(TAG, "Failed to create Start.io $format view", error)
                            onFailed()
                        }
                    }
                }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to request Start.io $format", error)
            runOnMain(onFailed)
        }
    }

    private fun bannerListener(format: BannerFormat) = object : BannerListener {
        override fun onReceiveAd(view: View) = Log.i(TAG, "Start.io $format received")
        override fun onFailedToReceiveAd(view: View) = Log.w(TAG, "Start.io $format failed after creation")
        override fun onClick(view: View) = Log.i(TAG, "Start.io $format clicked")
        override fun onImpression(view: View) = Log.i(TAG, "Start.io $format impression")
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
        if (!shouldShowAds(activity)) { onUnavailable(); return }
        if (!isInitialised) preloadAds(activity)
        if (!isInitialised) { onUnavailable(); return }
        if (!FullscreenAdPolicy.canShowInterstitial(activity)) { onDismissed(); return }
        val pending = AtomicBoolean(true)
        val settled = AtomicBoolean(false)
        val timeout = Runnable { if (pending.compareAndSet(true, false)) onUnavailable() }
        try {
            val ad = StartAppAd(activity)
            mainHandler.postDelayed(timeout, FULLSCREEN_LOAD_TIMEOUT_MS)
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(loaded: Ad) {
                    if (!pending.compareAndSet(true, false)) return
                    mainHandler.removeCallbacks(timeout)
                    runOnMain {
                        if (activity.isFinishing) { if (settled.compareAndSet(false, true)) onUnavailable(); return@runOnMain }
                        try {
                            ad.showAd(object : AdDisplayListener {
                                override fun adDisplayed(ad: Ad?) = FullscreenAdPolicy.recordInterstitialShown(activity)
                                override fun adHidden(ad: Ad?) { if (settled.compareAndSet(false, true)) onDismissed() }
                                override fun adClicked(ad: Ad?) = Unit
                                override fun adNotDisplayed(ad: Ad?) { if (settled.compareAndSet(false, true)) onUnavailable() }
                            })
                        } catch (error: Exception) {
                            Log.w(TAG, "Start.io interstitial show failed", error)
                            if (settled.compareAndSet(false, true)) onUnavailable()
                        }
                    }
                }
                override fun onFailedToReceiveAd(ad: Ad?) {
                    if (pending.compareAndSet(true, false)) { mainHandler.removeCallbacks(timeout); runOnMain(onUnavailable) }
                }
            })
        } catch (error: Exception) {
            Log.e(TAG, "Failed to request Start.io interstitial", error)
            if (pending.compareAndSet(true, false)) { mainHandler.removeCallbacks(timeout); runOnMain(onUnavailable) }
        }
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit = {}, onDismissed: () -> Unit = {}, onUnavailable: (() -> Unit)? = null) {
        if (!shouldShowAds(activity)) { onDismissed(); return }
        if (!isInitialised) preloadAds(activity)
        if (!isInitialised) { onUnavailable?.invoke() ?: onDismissed(); return }
        val pending = AtomicBoolean(true)
        val settled = AtomicBoolean(false)
        val rewardDelivered = AtomicBoolean(false)
        val timeout = Runnable { if (pending.compareAndSet(true, false)) runOnMain { onUnavailable?.invoke() ?: onDismissed() } }
        try {
            val ad = StartAppAd(activity)
            mainHandler.postDelayed(timeout, FULLSCREEN_LOAD_TIMEOUT_MS)
            ad.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    if (!settled.get() && rewardDelivered.compareAndSet(false, true)) runOnMain { if (!settled.get()) onRewarded() }
                }
            })
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(loaded: Ad) {
                    if (!pending.compareAndSet(true, false)) return
                    mainHandler.removeCallbacks(timeout)
                    runOnMain {
                        if (activity.isFinishing) { if (settled.compareAndSet(false, true)) onUnavailable?.invoke() ?: onDismissed(); return@runOnMain }
                        try {
                            ad.showAd(object : AdDisplayListener {
                                override fun adDisplayed(ad: Ad?) = Unit
                                override fun adHidden(ad: Ad?) { if (settled.compareAndSet(false, true)) onDismissed() }
                                override fun adClicked(ad: Ad?) = Unit
                                override fun adNotDisplayed(ad: Ad?) { if (settled.compareAndSet(false, true)) onUnavailable?.invoke() ?: onDismissed() }
                            })
                        } catch (error: Exception) {
                            Log.w(TAG, "Start.io rewarded show failed", error)
                            if (settled.compareAndSet(false, true)) onUnavailable?.invoke() ?: onDismissed()
                        }
                    }
                }
                override fun onFailedToReceiveAd(ad: Ad?) {
                    if (pending.compareAndSet(true, false)) { mainHandler.removeCallbacks(timeout); runOnMain { onUnavailable?.invoke() ?: onDismissed() } }
                }
            })
        } catch (error: Exception) {
            Log.e(TAG, "Failed to request Start.io rewarded ad", error)
            if (pending.compareAndSet(true, false)) { mainHandler.removeCallbacks(timeout); runOnMain { onUnavailable?.invoke() ?: onDismissed() } }
        }
    }

    fun showSplash(activity: Activity, savedInstanceState: Bundle? = null) {
        if (!shouldShowAds(activity) || !isInitialised) return
        try { StartAppAd.showSplash(activity, savedInstanceState) } catch (error: Exception) { Log.e(TAG, "Start.io splash failed", error) }
    }

    fun showExitAd(activity: Activity) {
        if (!shouldShowAds(activity) || !isInitialised) return
        try { StartAppAd.onBackPressed(activity) } catch (error: Exception) { Log.e(TAG, "Start.io exit ad failed", error) }
    }
}
