package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
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
import com.kiduyuk.klausk.kiduyutv.util.StartAppAdManager

@Composable
fun TvBannerAdView(modifier: Modifier = Modifier) {
    val isPreviewMode = LocalInspectionMode.current
    val focusManager = LocalFocusManager.current
    val context: Context = LocalContext.current
    val activity = context as? Activity
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

    val containerRef = remember { mutableStateOf<FrameLayout?>(null) }
    val adView = remember(context, screenWidthDp) {
        AdView(context).apply {
            adUnitId = AdUnitIds.TV_BANNER
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.i(TAG, "TV banner ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "TV banner ad failed to load: ${error.message}")
                    val container = containerRef.value
                    if (activity != null && container != null) {
                        Log.i(TAG, "Loading Start.io TV banner fallback")
                        StartAppAdManager.loadBanner(activity, container)
                    }
                }

                override fun onAdImpression() {
                    Log.i(TAG, "TV banner ad impression")
                }

                override fun onAdClicked() {
                    Log.i(TAG, "TV banner ad clicked")
                }
            }
        }
    }

    AndroidView(
        modifier = modifier
            .wrapContentSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) {
                    false
                } else {
                    // The banner is rendered as a bottom overlay, so Compose's normal
                    // spatial search can stop on a nearby lower row. Continue moving
                    // upward until the topmost reachable control is focused.
                    var moved = false
                    for (step in 0 until 64) {
                        if (!focusManager.moveFocus(FocusDirection.Up)) break
                        moved = true
                    }
                    moved
                }
            },
        factory = { ctx ->
            FrameLayout(ctx).apply {
                containerRef.value = this
                addView(adView)
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    )

    LifecycleResumeEffect(adView) {
        adView.resume()
        onPauseOrDispose { adView.pause() }
    }

    DisposableEffect(adView) {
        onDispose {
            containerRef.value?.removeAllViews()
            containerRef.value = null
            adView.destroy()
        }
    }
}

private const val TAG = "TvBannerAdView"
