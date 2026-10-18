package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.util.Log
import android.view.ViewGroup

/**
 * Backward-compatible facade for older callers.
 * New code should use the dispatcher dedicated to its ad format:
 * BannerFallbackDispatcher, InterstitialFallbackDispatcher,
 * RewardedFallbackDispatcher, or AppOpenFallbackDispatcher.
 */
@Deprecated("Use the ad-format-specific fallback dispatcher")
object AdFallbackDispatcher {
    private const val TAG = "AdFallbackDispatcher"

    enum class BannerNetwork { STARTAPP, ADMOB, WORTISE, UNITY }
    enum class AdPlacement { CONTENT_TRANSITION, PLAYER_LAUNCH, TV_PLAYER_LAUNCH }

    fun loadBanner(activity: Activity, container: ViewGroup, preferred: BannerNetwork? = null) {
        if (!AdEligibility.canRequestAds(activity)) {
            container.removeAllViews()
            return
        }
        when (preferred) {
            BannerNetwork.STARTAPP -> StartAppAdManager.loadBanner(activity, container)
            BannerNetwork.UNITY -> UnityAdManager.loadBanner(activity, container)
            BannerNetwork.WORTISE -> WortiseAdManager.loadBanner(activity, container)
            BannerNetwork.ADMOB, null -> AdManager.loadBanner(activity, container)
        }
        Log.i(TAG, "Legacy banner request routed through ${preferred ?: BannerNetwork.ADMOB}")
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) =
        showInterstitial(activity, AdPlacement.CONTENT_TRANSITION, onDismissed)

    fun showInterstitial(
        activity: Activity,
        placement: AdPlacement,
        onDismissed: () -> Unit
    ) {
        InterstitialFallbackDispatcher.show(
            activity = activity,
            placement = placement.toDedicatedPlacement(),
            onDismissed = onDismissed
        )
    }

    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) = RewardedFallbackDispatcher.show(activity, onRewarded, onDismissed)

    fun showRewardedInterstitial(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit
    ) = RewardedFallbackDispatcher.showInterstitial(activity, onRewarded, onDismissed)

    private fun AdPlacement.toDedicatedPlacement(): InterstitialFallbackDispatcher.Placement =
        when (this) {
            AdPlacement.CONTENT_TRANSITION -> InterstitialFallbackDispatcher.Placement.CONTENT_TRANSITION
            AdPlacement.PLAYER_LAUNCH -> InterstitialFallbackDispatcher.Placement.PLAYER_LAUNCH
            AdPlacement.TV_PLAYER_LAUNCH -> InterstitialFallbackDispatcher.Placement.TV_PLAYER_LAUNCH
        }
}
