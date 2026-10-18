package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.AdEligibility
import com.kiduyuk.klausk.kiduyutv.util.WortiseAdManager
import com.wortise.ads.AdError
import com.wortise.ads.RevenueData
import com.wortise.ads.natives.NativeAd
import com.wortise.ads.natives.NativeAdLoader
import com.wortise.ads.natives.NativeAdView
import com.wortise.ads.natives.NativeAdViewBinder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun WortiseNativeAdView(
    modifier: Modifier = Modifier,
    fallbackToAdMob: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isPreviewMode = LocalInspectionMode.current
    val isWortiseInitialized by WortiseAdManager.isInitialisedState.collectAsState()
    var loadFailed by remember(activity) { mutableStateOf(false) }

    if (isPreviewMode || activity == null || !AdEligibility.canRequestAds(context)) {
        Box(modifier)
        return
    }
    if (!isWortiseInitialized) {
        if (fallbackToAdMob) {
            AdMobNativeAdView(modifier)
            return
        }
        Box(modifier)
        return
    }
    if (loadFailed && fallbackToAdMob) {
        AdMobNativeAdView(modifier)
        return
    }

    val loaderRef = remember(activity) { AtomicReference<NativeAdLoader?>() }
    val nativeViewRef = remember(activity) { AtomicReference<NativeAdView?>() }
    val disposed = remember(activity) { AtomicBoolean(false) }

    DisposableEffect(activity) {
        disposed.set(false)
        onDispose {
            disposed.set(true)
            loaderRef.getAndSet(null)?.destroy()
            nativeViewRef.getAndSet(null)?.let { adView ->
                (adView.parent as? ViewGroup)?.removeView(adView)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                val binder = NativeAdViewBinder.Builder(R.layout.wortise_native_ad)
                    .setHeadlineTextViewId(R.id.wortise_native_headline)
                    .setBodyTextViewId(R.id.wortise_native_body)
                    .setCallToActionButtonId(R.id.wortise_native_call_to_action)
                    .setMediaContentViewId(R.id.wortise_native_media)
                    .build()
                val loader = NativeAdLoader(
                    viewContext,
                    WortiseAdManager.nativeAdUnitId(),
                    object : NativeAdLoader.Listener {
                        override fun onNativeClicked(ad: NativeAd) {
                            Log.i("WortiseNativeAdView", "Native ad clicked")
                        }

                        override fun onNativeFailedToLoad(error: AdError) {
                            Log.w("WortiseNativeAdView", "Native ad failed to load: ${error.message}")
                            if (!disposed.get()) loadFailed = true
                        }

                        override fun onNativeImpression(ad: NativeAd) {
                            Log.i("WortiseNativeAdView", "Native ad impression")
                        }

                        override fun onNativeLoaded(ad: NativeAd) {
                            if (disposed.get()) return
                            post {
                                if (disposed.get()) return@post
                                val adView = NativeAdView(viewContext, binder)
                                if (!loaderRef.get().let { it?.renderAd(adView, ad) == true }) {
                                    Log.w("WortiseNativeAdView", "Wortise native ad could not be rendered")
                                    loadFailed = true
                                    return@post
                                }
                                removeAllViews()
                                addView(
                                    adView,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT
                                    )
                                )
                                nativeViewRef.set(adView)
                            }
                        }

                        override fun onNativeRevenuePaid(ad: NativeAd, data: RevenueData) {
                            Log.i("WortiseNativeAdView", "Native ad revenue paid")
                        }
                    }
                )
                loaderRef.set(loader)
                loader.loadAd()
            }
        }
    )
}
