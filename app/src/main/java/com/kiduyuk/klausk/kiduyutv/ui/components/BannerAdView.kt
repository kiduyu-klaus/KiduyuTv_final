package com.kiduyuk.klausk.kiduyutv.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val isPreviewMode = LocalInspectionMode.current
    val context: Context = LocalContext.current
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalResources.current.displayMetrics.density
    val screenWidthDp = (screenWidthPx / density).toInt().takeIf { it > 0 } ?: 360

    if (SettingsManager(context).isAdsDisabled()) {
        Box(modifier = modifier)
        return
    }

    if (isPreviewMode) {
        Box(modifier = modifier) {
            Text(
                text = "Google Mobile Ads preview banner.",
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    val adView = remember(context, screenWidthDp) {
        AdView(context).apply {
            adUnitId = AdUnitIds.PHONE_BANNER
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.i(TAG, "Phone banner ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Phone banner ad failed to load: ${error.message}")
                }

                override fun onAdImpression() {
                    Log.i(TAG, "Phone banner ad impression")
                }

                override fun onAdClicked() {
                    Log.i(TAG, "Phone banner ad clicked")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    AndroidView(modifier = modifier.wrapContentSize(), factory = { adView })

    LifecycleResumeEffect(adView) {
        adView.resume()
        onPauseOrDispose { adView.pause() }
    }

    // Keyed on Unit so this only fires when the composable leaves the
    // composition (not on every recomposition).
    DisposableEffect(Unit) {
        onDispose {
            adView.destroy()
        }
    }
}

private const val TAG = "BannerAdView"
