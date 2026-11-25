package com.kiduyuk.klausk.kiduyutv.ui.components

import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.inmobi.ads.InMobiNative
import com.inmobi.media.ads.nativeAd.InMobiNativeViewData
import com.kiduyuk.klausk.kiduyutv.util.AdEligibility
import com.kiduyuk.klausk.kiduyutv.util.InMobiAdManager

/** Compose bridge for an InMobi native ad with one-time SDK view registration. */
@Composable
fun InMobiNativeAdView(
    modifier: Modifier = Modifier,
    onLoadFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    var ad by remember { mutableStateOf<InMobiNative?>(null) }
    var request by remember { mutableStateOf<InMobiAdManager.NativeLoadRequest?>(null) }

    if (preview || !AdEligibility.canRequestAds(context)) {
        Box(modifier = modifier)
        return
    }

    LaunchedEffect(Unit) {
        request = InMobiAdManager.loadNative(
            context = context,
            onLoaded = { loaded -> ad = loaded },
            onFailed = onLoadFailed
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            request?.cancel()
            request = null
            ad = null
        }
    }

    ad?.let { loaded ->
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                createNativeView(viewContext, loaded).also { root ->
                    bindNativeView(root, loaded)
                }
            }
        )
    }
}

private data class NativeViews(
    val root: FrameLayout,
    val title: TextView,
    val description: TextView,
    val icon: ImageView,
    val cta: Button,
    val rating: RatingBar,
    val advertiser: TextView
)

private fun createNativeView(context: android.content.Context, ad: InMobiNative): FrameLayout {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = kotlin.math.round(value * density).toInt()

    val root = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        isClickable = true
        isFocusable = true
    }
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(context)
    row.addView(icon, LinearLayout.LayoutParams(dp(96), dp(96)))
    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
    }
    val title = TextView(context)
    val description = TextView(context)
    val advertiser = TextView(context)
    val rating = RatingBar(context, null, android.R.attr.ratingBarStyleSmall)
    textColumn.addView(title)
    textColumn.addView(description)
    textColumn.addView(advertiser)
    textColumn.addView(rating)
    row.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    val cta = Button(context)
    row.addView(cta, LinearLayout.LayoutParams(WRAP, WRAP))
    root.addView(row, FrameLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER })
    root.tag = NativeViews(root, title, description, icon, cta, rating, advertiser)
    return root
}

private const val WRAP = -2

private fun bindNativeView(root: FrameLayout, ad: InMobiNative) {
    val views = root.tag as NativeViews
    views.title.text = ad.getAdTitle().orEmpty()
    views.description.text = ad.getAdDescription().orEmpty()
    views.advertiser.text = ad.getAdvertiserName().orEmpty()
    views.cta.text = ad.getCtaText().orEmpty()
    views.rating.rating = ad.getAdRating()
    ad.getAdIcon()?.url?.let { iconUrl ->
        Glide.with(views.icon).load(iconUrl).into(views.icon)
    }
    // InMobi 11.5.x documents registerViewForTracking as the one registration
    // point and unTrackViews as its lifecycle counterpart. This function is
    // called only from AndroidView.factory, never from recomposition/update.
    val trackingData = InMobiNativeViewData.Builder(root)
        .setTitleView(views.title)
        .setDescriptionView(views.description)
        .setIconView(views.icon)
        .setCTAView(views.cta)
        .setRatingView(views.rating)
        .setAdvertiserView(views.advertiser)
        .setExtraViews(emptyList())
        .build()
    ad.registerViewForTracking(trackingData)
}
