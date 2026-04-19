package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback

/**
 * Manages GAM (Google Ad Manager) interstitial ads for the TV flavour.
 * TV interstitials use AdManagerInterstitialAd and are D-pad navigable by default.
 */
object TvInterstitialManager {

    private const val TAG = "TvInterstitialManager"
    
    @Volatile private var interstitialAd: AdManagerInterstitialAd? = null

    /**
     * Pre-loads a GAM interstitial ad for TV.
     */
    fun preload(context: Context) {
        val adRequest = AdManagerAdRequest.Builder().build()
        AdManagerInterstitialAd.load(
            context,
            AdUnitIds.TV_INTERSTITIAL,
            adRequest,
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                    interstitialAd = ad
                    Log.i(TAG, "TV interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "TV interstitial failed: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the interstitial ad and then launches [onDismissed] callback.
     * Used before PlayerActivity launches on TV.
     */
    fun showAndThenLaunch(activity: Activity, onDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            Log.i(TAG, "No TV interstitial ready — proceeding to launch")
            onDismissed()
            preload(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preload(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                onDismissed()
            }
        }
        ad.show(activity)
    }

    val isReady: Boolean get() = interstitialAd != null
}
