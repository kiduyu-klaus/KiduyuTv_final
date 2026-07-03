package com.kiduyuk.klausk.kiduyutv.ui.components

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.AdManager
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

@Composable
fun AdMobNativeAdView(
    modifier: Modifier = Modifier,
    startMuted: Boolean = true
) {
    val context = LocalContext.current
    val isPreviewMode = LocalInspectionMode.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    if (isPreviewMode || SettingsManager(context).isAdsDisabled()) {
        Box(modifier = modifier)
        return
    }

    DisposableEffect(context, startMuted) {
        var disposed = false
        AdManager.loadNativeAd(
            context = context,
            startMuted = startMuted,
            onLoaded = { ad ->
                if (disposed) {
                    ad.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = ad
                }
            }
        )

        onDispose {
            disposed = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                LayoutInflater.from(viewContext)
                    .inflate(R.layout.ad_native, null) as NativeAdView
            },
            update = { adView ->
                populateNativeAdView(ad, adView)
            }
        )
    }
}

private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
    val mediaView = nativeAdView.findViewById<MediaView>(R.id.native_ad_media)
    val headlineView = nativeAdView.findViewById<TextView>(R.id.native_ad_headline)
    val bodyView = nativeAdView.findViewById<TextView>(R.id.native_ad_body)
    val callToActionView = nativeAdView.findViewById<Button>(R.id.native_ad_call_to_action)
    val iconView = nativeAdView.findViewById<ImageView>(R.id.native_ad_icon)
    val priceView = nativeAdView.findViewById<TextView>(R.id.native_ad_price)
    val starRatingView = nativeAdView.findViewById<RatingBar>(R.id.native_ad_stars)
    val storeView = nativeAdView.findViewById<TextView>(R.id.native_ad_store)
    val advertiserView = nativeAdView.findViewById<TextView>(R.id.native_ad_advertiser)

    nativeAdView.mediaView = mediaView
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = callToActionView
    nativeAdView.iconView = iconView
    nativeAdView.priceView = priceView
    nativeAdView.starRatingView = starRatingView
    nativeAdView.storeView = storeView
    nativeAdView.advertiserView = advertiserView

    headlineView.text = nativeAd.headline
    nativeAd.mediaContent?.let { mediaView.setMediaContent(it) }

    setTextAsset(bodyView, nativeAd.body)
    setTextAsset(callToActionView, nativeAd.callToAction)
    setTextAsset(priceView, nativeAd.price)
    setTextAsset(storeView, nativeAd.store)
    setTextAsset(advertiserView, nativeAd.advertiser)

    if (nativeAd.icon == null) {
        iconView.visibility = View.GONE
    } else {
        iconView.setImageDrawable(nativeAd.icon?.drawable)
        iconView.visibility = View.VISIBLE
    }

    if (nativeAd.starRating == null) {
        starRatingView.visibility = View.GONE
    } else {
        starRatingView.rating = nativeAd.starRating!!.toFloat()
        starRatingView.visibility = View.VISIBLE
    }

    nativeAd.mediaContent?.videoController?.videoLifecycleCallbacks =
        object : com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks() {
            override fun onVideoEnd() {
                super.onVideoEnd()
            }
        }

    nativeAdView.setNativeAd(nativeAd)
}

private fun setTextAsset(view: TextView, value: String?) {
    if (value.isNullOrBlank()) {
        view.visibility = View.GONE
    } else {
        view.text = value
        view.visibility = View.VISIBLE
    }
}
