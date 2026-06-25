package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import com.kiduyuk.klausk.kiduyutv.BuildConfig

/**
 * Unified fallback ad dispatcher.
 *
 * Provides a single entry-point for showing ads across all networks.
 * The fallback chain follows the platform priority:
 *
 *   Phone: AdMob → StartApp → Wortise → Unity Ads
 *   TV:    StartApp → AdMob → Wortise → Unity Ads
 *
 * For banners, a specific network can be requested; otherwise the platform
 * default is used.
 * For interstitials and rewarded videos, the dispatcher walks the chain
 * until it finds a ready ad.
 *
 * All methods are safe to call from any thread and always invoke the
 * callback so the app flow never stalls.
 */
object AdFallbackDispatcher {

    private const val TAG = "AdFallbackDispatcher"

    private val coreFlow: List<BannerNetwork>
        get() = if (BuildConfig.FLAVOR == "phone") {
            listOf(
                BannerNetwork.ADMOB,
                BannerNetwork.STARTAPP,
                BannerNetwork.WORTISE,
                BannerNetwork.UNITY
            )
        } else {
            listOf(
                BannerNetwork.STARTAPP,
                BannerNetwork.ADMOB,
                BannerNetwork.WORTISE,
                BannerNetwork.UNITY
            )
        }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows an interstitial ad using the first ready network in the platform chain.
     * Always calls [onDismissed] when the ad closes (or immediately if none ready).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        Log.i(TAG, "Interstitial flow: ${coreFlow.joinToString(" -> ")}")
        for (network in coreFlow) {
            when (network) {
                BannerNetwork.ADMOB -> if (AdManager.isInterstitialReady) {
                    Log.i(TAG, "Showing AdMob interstitial")
                    AdManager.showInterstitial(activity, onDismissed)
                    return
                }

                BannerNetwork.STARTAPP -> if (StartAppAdManager.isInitialised) {
                    Log.i(TAG, "Showing StartApp interstitial")
                    StartAppAdManager.showInterstitial(activity, onDismissed)
                    return
                }

                BannerNetwork.WORTISE -> if (WortiseAdManager.isInterstitialReady) {
                    Log.i(TAG, "Showing Wortise interstitial")
                    WortiseAdManager.showInterstitial(activity, onDismissed)
                    return
                }

                BannerNetwork.UNITY -> if (UnityAdManager.isInterstitialReady) {
                    Log.i(TAG, "Showing Unity interstitial")
                    UnityAdManager.showInterstitial(activity, onDismissed)
                    return
                }
            }
        }

        Log.i(TAG, "No interstitial ready from any network")
        onDismissed()
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a rewarded video ad using the first ready network in the platform chain.
     *
     * [onRewarded] fires only when the user fully watches the ad.
     * [onDismissed] always fires when the ad closes.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        Log.i(TAG, "Rewarded flow: ${coreFlow.joinToString(" -> ")}")
        for (network in coreFlow) {
            when (network) {
                BannerNetwork.ADMOB -> if (AdManager.isRewardedReady) {
                    Log.i(TAG, "Showing AdMob rewarded")
                    AdManager.showRewarded(activity, onRewarded, onDismissed)
                    return
                }

                BannerNetwork.STARTAPP -> if (StartAppAdManager.isInitialised) {
                    Log.i(TAG, "Showing StartApp rewarded")
                    StartAppAdManager.showRewarded(activity, onRewarded, onDismissed)
                    return
                }

                BannerNetwork.WORTISE -> if (WortiseAdManager.isRewardedReady) {
                    Log.i(TAG, "Showing Wortise rewarded")
                    WortiseAdManager.showRewarded(activity, onRewarded, onDismissed)
                    return
                }

                BannerNetwork.UNITY -> if (UnityAdManager.isRewardedReady) {
                    Log.i(TAG, "Showing Unity rewarded")
                    UnityAdManager.showRewarded(activity, onRewarded, onDismissed)
                    return
                }
            }
        }

        Log.i(TAG, "No rewarded ready from any network")
        onDismissed()
    }

    // ── Banner (choose ONE network per screen) ────────────────────────────

    /**
     * Loads a banner from the requested [preferred] network into [container].
     * Only ONE banner network should be active per screen.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        preferred: BannerNetwork? = null
    ) {
        val selectedNetwork = preferred ?: coreFlow.first()
        Log.i(TAG, "Loading banner from: $selectedNetwork")
        when (selectedNetwork) {
            BannerNetwork.STARTAPP -> StartAppAdManager.loadBanner(activity, container)
            BannerNetwork.ADMOB -> AdManager.loadBanner(activity, container)
            BannerNetwork.WORTISE -> WortiseAdManager.loadBanner(activity, container)
            BannerNetwork.UNITY -> UnityAdManager.loadBanner(activity, container)
        }
    }

    /**
     * Which banner network to request.
     */
    enum class BannerNetwork {
        STARTAPP,
        ADMOB,
        WORTISE,
        UNITY
    }
}
