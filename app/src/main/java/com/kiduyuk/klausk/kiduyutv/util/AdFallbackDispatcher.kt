package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup

/**
 * Unified fallback ad dispatcher.
 *
 * Provides a single entry-point for showing ads across all networks.
 * The fallback chain follows the priority:
 *
 *   StartApp → AdMob → Wortise → Unity Ads
 *
 * For banners, a specific network can be requested; otherwise StartApp is used.
 * For interstitials and rewarded videos, the dispatcher walks the chain
 * until it finds a ready ad.
 *
 * All methods are safe to call from any thread and always invoke the
 * callback so the app flow never stalls.
 */
object AdFallbackDispatcher {

    private const val TAG = "AdFallbackDispatcher"

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Shows an interstitial ad using the first ready network in the chain:
     * StartApp → AdMob → Wortise → Unity Ads.
     * Always calls [onDismissed] when the ad closes (or immediately if none ready).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        when {
            StartAppAdManager.isInitialised -> {
                Log.i(TAG, "Showing StartApp interstitial")
                StartAppAdManager.showInterstitial(activity, onDismissed)
            }

            AdManager.isInterstitialReady -> {
                Log.i(TAG, "Falling back to AdMob interstitial")
                AdManager.showInterstitial(activity, onDismissed)
            }

            WortiseAdManager.isInterstitialReady -> {
                Log.i(TAG, "Falling back to Wortise interstitial")
                WortiseAdManager.showInterstitial(activity, onDismissed)
            }

            UnityAdManager.isInterstitialReady -> {
                Log.i(TAG, "Falling back to Unity interstitial")
                UnityAdManager.showInterstitial(activity, onDismissed)
            }

            else -> {
                Log.i(TAG, "No interstitial ready from any network")
                onDismissed()
            }
        }
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Shows a rewarded video ad using the first ready network in the chain:
     * StartApp → AdMob → Wortise → Unity Ads.
     *
     * [onRewarded] fires only when the user fully watches the ad.
     * [onDismissed] always fires when the ad closes.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) {
        when {
            StartAppAdManager.isInitialised -> {
                Log.i(TAG, "Showing StartApp rewarded")
                StartAppAdManager.showRewarded(activity, onRewarded, onDismissed)
            }

            AdManager.isRewardedReady -> {
                Log.i(TAG, "Falling back to AdMob rewarded")
                AdManager.showRewarded(activity, onRewarded, onDismissed)
            }

            WortiseAdManager.isRewardedReady -> {
                Log.i(TAG, "Falling back to Wortise rewarded")
                WortiseAdManager.showRewarded(activity, onRewarded, onDismissed)
            }

            UnityAdManager.isRewardedReady -> {
                Log.i(TAG, "Falling back to Unity rewarded")
                UnityAdManager.showRewarded(activity, onRewarded, onDismissed)
            }

            else -> {
                Log.i(TAG, "No rewarded ready from any network")
                onDismissed()
            }
        }
    }

    // ── Banner (choose ONE network per screen) ────────────────────────────

    /**
     * Loads a banner from the requested [preferred] network into [container].
     * Only ONE banner network should be active per screen.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        preferred: BannerNetwork = BannerNetwork.STARTAPP
    ) {
        Log.i(TAG, "Loading banner from: $preferred")
        when (preferred) {
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
