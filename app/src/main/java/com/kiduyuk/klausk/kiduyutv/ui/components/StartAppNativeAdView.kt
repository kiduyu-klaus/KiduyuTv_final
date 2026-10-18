package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiduyuk.klausk.kiduyutv.util.AdEligibility
import com.kiduyuk.klausk.kiduyutv.util.StartAppAdManager
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "StartAppNativeAdView"

@Composable
fun StartAppNativeAdView(
    modifier: Modifier = Modifier,
    fallbackToAdMob: Boolean = true,
    onLoadFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isPreviewMode = LocalInspectionMode.current
    val isStartAppInitialized by StartAppAdManager.isInitialisedState.collectAsState()
    var nativeAd by remember(activity) { mutableStateOf<NativeAdDetails?>(null) }
    var loadFailed by remember(activity) { mutableStateOf(false) }

    if (isPreviewMode || activity == null || !AdEligibility.canRequestAds(context)) {
        Box(modifier)
        return
    }
    if (!isStartAppInitialized) {
        if (fallbackToAdMob) {
            AdMobNativeAdView(modifier)
        } else {
            Box(modifier)
        }
        return
    }
    if (loadFailed) {
        if (fallbackToAdMob) {
            AdMobNativeAdView(modifier)
        } else {
            Box(modifier)
        }
        return
    }

    DisposableEffect(activity) {
        var disposed = false
        val nativeAdLoader = StartAppNativeAd(activity.applicationContext)
        val loadedAdRef = AtomicReference<NativeAdDetails?>()
        val preferences = NativeAdPreferences().apply {
            setAdsNumber(1)
            setAutoBitmapDownload(true)
        }
        nativeAdLoader.loadAd(preferences, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                val details = nativeAdLoader.nativeAds?.firstOrNull()
                activity.runOnUiThread {
                    if (disposed) {
                        details?.unregisterView()
                    } else if (details == null) {
                        Log.w(TAG, "Start.io native request returned no ad")
                        loadFailed = true
                        onLoadFailed()
                    } else {
                        loadedAdRef.set(details)
                        nativeAd = details
                    }
                }
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                Log.w(TAG, "Start.io native ad failed to load: ${ad?.errorMessage}")
                activity.runOnUiThread {
                    if (!disposed) {
                        loadFailed = true
                        onLoadFailed()
                    }
                }
            }
        })

        onDispose {
            disposed = true
            loadedAdRef.getAndSet(null)?.unregisterView()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .background(Color(0xFF1F1F1F))
        ) {
            Row {
                ad.imageBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Advertisement image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(120.dp)
                            .height(120.dp)
                            .padding(12.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    Text(
                        text = ad.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (ad.category.isNotBlank()) {
                        Text(
                            text = ad.category,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (ad.description.isNotBlank()) {
                        Text(
                            text = ad.description,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (ad.callToAction.isNotBlank()) {
                        Text(
                            text = ad.callToAction,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                                .background(Color(0xFFE50914))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Text(
                text = "Ad",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .border(1.dp, Color.LightGray)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    View(viewContext).apply {
                        isFocusable = true
                        isClickable = true
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == KeyEvent.ACTION_UP &&
                                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == KeyEvent.KEYCODE_ENTER ||
                                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                            ) {
                                performClick()
                                true
                            } else {
                                false
                            }
                        }
                        // Start.io opens the advertiser URL from the
                        // registered interaction view through its SDK handler.
                        ad.registerViewForInteraction(this)
                    }
                }
            )
        }
    }
}
