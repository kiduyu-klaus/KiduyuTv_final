package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.unity3d.ads.AdExpiredListener
import com.unity3d.ads.BannerAd
import com.unity3d.ads.BannerConfiguration
import com.unity3d.ads.BannerShowListener
import com.unity3d.ads.BannerSize
import com.unity3d.ads.InitializationConfiguration
import com.unity3d.ads.InitializationListener
import com.unity3d.ads.InterstitialAd
import com.unity3d.ads.InterstitialShowListener
import com.unity3d.ads.LoadConfiguration
import com.unity3d.ads.LoadListener
import com.unity3d.ads.RewardedAd
import com.unity3d.ads.RewardedShowListener
import com.unity3d.ads.ShowConfiguration
import com.unity3d.ads.ShowFinishState
import com.unity3d.ads.UnityAdsError
import com.unity3d.ads.UnityAdsExperimental
import com.unity3d.ads.UnityAds
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unity Ads Manager — singleton.
 *
 * Handles banner, interstitial, and rewarded video ads from Unity Ads.
 * Safe to call even if the SDK failed to initialise: methods no-op and
 * invoke callbacks so the app flow continues.
 *
 * Interstitial frequency is guarded by [FullscreenAdPolicy] (3 min globally).
 */
@OptIn(UnityAdsExperimental::class)
object UnityAdManager {

    private const val TAG = "UnityAdManager"

    private val gameId: String
        get() = BuildConfig.UNITY_ADS_GAME_ID
    private val testMode: Boolean
        get() = BuildConfig.UNITY_ADS_TEST_MODE
    private val rewardedPlacementId: String
        get() = BuildConfig.UNITY_ADS_REWARDED_ID
    private val interstitialPlacementId: String
        get() = BuildConfig.UNITY_ADS_INTERSTITIAL_ID
    private val bannerPlacementId: String
        get() = BuildConfig.UNITY_ADS_BANNER_ID

    private val initializationInProgress = AtomicBoolean(false)
    private val interstitialLoadInProgress = AtomicBoolean(false)
    private val rewardedLoadInProgress = AtomicBoolean(false)
    private val interstitialShowing = AtomicBoolean(false)
    private val rewardedShowing = AtomicBoolean(false)
    private val bannerGeneration = AtomicInteger(0)
    @Volatile private var pendingUserConsent: Boolean? = null

    @Volatile
    var isInitialised = false
        private set

    @Volatile
    private var currentBannerAd: BannerAd? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var rewardedAd: RewardedAd? = null

    val isInterstitialReady: Boolean
        get() = interstitialAd != null

    val isRewardedReady: Boolean
        get() = rewardedAd != null

    private fun shouldShowAds(context: Context): Boolean = AdEligibility.canRequestAds(context)

    /**
     * Applies UMP's opt-in signal once Unity is initialized, or retains it until initialization.
     * A null value means no opt-in framework applies and no Unity signal should be sent.
     */
    fun updateUserConsent(userConsent: Boolean?) {
        pendingUserConsent = userConsent
        if (isInitialised) applyPendingUserConsent()
    }

    private fun applyPendingUserConsent() {
        pendingUserConsent?.let { consent ->
            try {
                UnityAds.userConsent = consent
                Log.i(TAG, "Unity opt-in consent updated: $consent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update Unity opt-in consent", e)
            }
        }
    }

    /**
     * Initialize and pre-load Unity Ads after the app's consent flow completes.
     */
    fun preloadAds(context: Context) {
        if (!shouldShowAds(context)) {
            Log.i(TAG, "Ads disabled - skipping Unity preload")
            return
        }
        try {
            if (isInitialised) {
                initializationInProgress.set(false)
                loadFullscreenPlacements()
                return
            }

            if (gameId.isBlank()) {
                Log.w(TAG, "Unity game id missing - skipping preload")
                return
            }

            if (!initializationInProgress.compareAndSet(false, true)) {
                Log.d(TAG, "Unity Ads initialization already in progress")
                return
            }

            val configuration = InitializationConfiguration.Builder(gameId)
                .withTestMode(testMode)
                .build()
            UnityAds.initialize(configuration, InitializationListener { error ->
                if (error == null) {
                    initializationInProgress.set(false)
                    isInitialised = true
                    Log.i(TAG, "Unity Ads initialized")
                    applyPendingUserConsent()
                    loadFullscreenPlacements()
                } else {
                    initializationInProgress.set(false)
                    isInitialised = false
                    interstitialAd = null
                    rewardedAd = null
                    Log.w(TAG, "Unity initialization failed: $error")
                }
            })
            Log.i(TAG, "Unity Ads initialization requested")
        } catch (e: Exception) {
            initializationInProgress.set(false)
            Log.e(TAG, "Unity preload failed", e)
        }
    }

    private fun loadFullscreenPlacements() {
        requestInterstitial()
        requestRewarded()
        Log.i(TAG, "Unity ad preload requested")
    }

    private fun requestInterstitial() {
        if (!isInitialised || interstitialShowing.get() || interstitialAd != null ||
            !interstitialLoadInProgress.compareAndSet(false, true)
        ) return
        try {
            val config = LoadConfiguration.Builder(interstitialPlacementId).build()
            InterstitialAd.load(config, object : LoadListener<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd?, error: UnityAdsError?) {
                    interstitialLoadInProgress.set(false)
                    if (ad == null) {
                        Log.w(TAG, "Unity interstitial failed to load: $error")
                        return
                    }
                    interstitialAd = ad
                    ad.onAdExpired = AdExpiredListener {
                        if (interstitialAd === ad) {
                            interstitialAd = null
                            Log.i(TAG, "Unity interstitial expired; reloading")
                            requestInterstitial()
                        }
                    }
                    Log.i(TAG, "Unity interstitial loaded")
                }
            })
        } catch (e: Exception) {
            interstitialLoadInProgress.set(false)
            Log.w(TAG, "Unable to request Unity interstitial", e)
        }
    }

    private fun requestRewarded() {
        if (!isInitialised || rewardedShowing.get() || rewardedAd != null ||
            !rewardedLoadInProgress.compareAndSet(false, true)
        ) return
        try {
            val config = LoadConfiguration.Builder(rewardedPlacementId).build()
            RewardedAd.load(config, object : LoadListener<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd?, error: UnityAdsError?) {
                    rewardedLoadInProgress.set(false)
                    if (ad == null) {
                        Log.w(TAG, "Unity rewarded ad failed to load: $error")
                        return
                    }
                    rewardedAd = ad
                    ad.onAdExpired = AdExpiredListener {
                        if (rewardedAd === ad) {
                            rewardedAd = null
                            Log.i(TAG, "Unity rewarded ad expired; reloading")
                            requestRewarded()
                        }
                    }
                    Log.i(TAG, "Unity rewarded ad loaded")
                }
            })
        } catch (e: Exception) {
            rewardedLoadInProgress.set(false)
            Log.w(TAG, "Unable to request Unity rewarded ad", e)
        }
    }

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * Loads a Unity banner into the supplied [container].
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        onFailed: () -> Unit = {}
    ) {
        if (!shouldShowAds(activity)) return
        if (!isInitialised) {
            Log.w(TAG, "Unity banner skipped - SDK not initialized")
            preloadAds(activity)
            onFailed()
            return
        }
        try {
            destroyBanner()
            container.removeAllViews()
            val generation = bannerGeneration.get()
            val showListener = object : BannerShowListener {
                override fun onImpression(ad: BannerAd) {
                    Log.i(TAG, "Unity banner impression")
                }

                override fun onClicked(ad: BannerAd) {
                    Log.i(TAG, "Unity banner clicked")
                }

                override fun onFailedToShow(ad: BannerAd, error: UnityAdsError) {
                    Log.w(TAG, "Unity banner failed to show: $error")
                    onFailed()
                }
            }
            val config = BannerConfiguration.Builder(
                bannerPlacementId,
                BannerSize(320, 50),
                showListener
            )
                .build()
            BannerAd.load(config, object : LoadListener<BannerAd> {
                override fun onAdLoaded(ad: BannerAd?, error: UnityAdsError?) {
                    if (ad == null) {
                        Log.w(TAG, "Unity banner failed to load: $error")
                        onFailed()
                        return
                    }
                    val adView = ad.view
                    if (adView == null) {
                        Log.w(TAG, "Unity banner loaded without a view")
                        onFailed()
                        return
                    }
                    container.post {
                        if (generation != bannerGeneration.get()) return@post
                        currentBannerAd = ad
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        container.removeAllViews()
                        container.addView(
                            adView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                        Log.i(TAG, "Unity banner loaded")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Unity banner", e)
            onFailed()
        }
    }

    /**
     * Destroys the current Unity banner and removes it from its parent.
     * Call when the banner is no longer needed (e.g. Composable disposal).
     */
    fun destroyBanner() {
        bannerGeneration.incrementAndGet()
        currentBannerAd?.view?.let { bannerView ->
            (bannerView.parent as? ViewGroup)?.removeView(bannerView)
        }
        currentBannerAd = null
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows a Unity interstitial if one is ready and the cooldown has elapsed.
     * Always calls [onDismissed] when done.
     */
    fun showInterstitial(
        activity: Activity,
        onDismissed: () -> Unit = {},
        onUnavailable: (() -> Unit)? = null
    ) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        if (!isInitialised) {
            Log.i(TAG, "Unity interstitial skipped - SDK not initialized")
            onUnavailable?.invoke() ?: onDismissed()
            preloadAds(activity)
            return
        }
        if (!FullscreenAdPolicy.canShowInterstitial(activity)) {
            onDismissed()
            return
        }
        val ad = synchronized(this) {
            interstitialAd.also { interstitialAd = null }
        }
        if (ad == null) {
            Log.i(TAG, "Unity interstitial not ready")
            onUnavailable?.invoke() ?: onDismissed()
            requestInterstitial()
            return
        }
        if (!interstitialShowing.compareAndSet(false, true)) {
            synchronized(this) {
                if (interstitialAd == null) interstitialAd = ad
            }
            onUnavailable?.invoke() ?: onDismissed()
            return
        }
        try {
            ad.show(
                activity,
                ShowConfiguration.Builder().build(),
                object : InterstitialShowListener {
                    override fun onStarted(ad: InterstitialAd) {
                        Log.i(TAG, "Unity interstitial started")
                    }

                    override fun onClicked(ad: InterstitialAd) {
                        Log.i(TAG, "Unity interstitial clicked")
                    }

                    override fun onCompleted(ad: InterstitialAd, state: ShowFinishState) {
                        Log.i(TAG, "Unity interstitial complete: $state")
                        FullscreenAdPolicy.recordInterstitialShown(activity)
                        interstitialShowing.set(false)
                        requestInterstitial()
                        onDismissed()
                    }

                    override fun onFailed(ad: InterstitialAd, error: UnityAdsError) {
                        Log.w(TAG, "Unity interstitial show failed: $error")
                        interstitialShowing.set(false)
                        requestInterstitial()
                        onUnavailable?.invoke() ?: onDismissed()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Unity interstitial", e)
            interstitialShowing.set(false)
            requestInterstitial()
            onUnavailable?.invoke() ?: onDismissed()
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
        onDismissed: () -> Unit = {},
        onUnavailable: (() -> Unit)? = null
    ) {
        if (!shouldShowAds(activity)) {
            onDismissed()
            return
        }
        if (!isInitialised) {
            Log.i(TAG, "Unity rewarded skipped - SDK not initialized")
            onUnavailable?.invoke() ?: onDismissed()
            preloadAds(activity)
            return
        }
        val ad = synchronized(this) {
            rewardedAd.also { rewardedAd = null }
        }
        if (ad == null) {
            Log.i(TAG, "Unity rewarded not ready")
            onUnavailable?.invoke() ?: onDismissed()
            requestRewarded()
            return
        }
        if (!rewardedShowing.compareAndSet(false, true)) {
            synchronized(this) {
                if (rewardedAd == null) rewardedAd = ad
            }
            onUnavailable?.invoke() ?: onDismissed()
            return
        }
        try {
            val rewardDelivered = AtomicBoolean(false)
            ad.show(
                activity,
                ShowConfiguration.Builder().build(),
                object : RewardedShowListener {
                    override fun onStarted(ad: RewardedAd) {
                        Log.i(TAG, "Unity rewarded ad started")
                    }

                    override fun onClicked(ad: RewardedAd) {
                        Log.i(TAG, "Unity rewarded ad clicked")
                    }

                    override fun onRewarded(ad: RewardedAd) {
                        if (rewardDelivered.compareAndSet(false, true)) {
                            onRewarded()
                        }
                    }

                    override fun onCompleted(ad: RewardedAd, state: ShowFinishState) {
                        Log.i(TAG, "Unity rewarded ad complete: $state")
                        rewardedShowing.set(false)
                        requestRewarded()
                        onDismissed()
                    }

                    override fun onFailed(ad: RewardedAd, error: UnityAdsError) {
                        Log.w(TAG, "Unity rewarded ad show failed: $error")
                        rewardedShowing.set(false)
                        requestRewarded()
                        onUnavailable?.invoke() ?: onDismissed()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Unity rewarded", e)
            rewardedShowing.set(false)
            requestRewarded()
            onUnavailable?.invoke() ?: onDismissed()
        }
    }
}
