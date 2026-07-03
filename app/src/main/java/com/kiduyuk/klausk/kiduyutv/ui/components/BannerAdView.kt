package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager


/**
 * AdMob banner ad.
 *
 * Critical: [adViewRef] is stored in [remember] so it survives recomposition.
 * The previous implementation used a plain `var` which was reset to `null`
 * on every recomposition, causing [DisposableEffect]'s key to change from
 * `AdView(...)` to `null` and destroying the live AdView immediately after
 * creation. That is the primary reason the bottom-nav banner never showed.
 *
 * The [DisposableEffect] now uses `Unit` as its key and references the
 * AdView through the remembered state holder, so [AdView.destroy] only runs
 * when the composable leaves the composition.
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {

    // Hold the AdView across recompositions.
    val adViewRef = remember { mutableStateOf<AdView?>(null) }

    // Compute the right adaptive banner size for the current screen width.
    val context: Context = LocalContext.current
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalResources.current.displayMetrics.density
    val screenWidthDp = (screenWidthPx / density).toInt()
    val activity = context.findActivity()
    val bannerAdSize = remember(screenWidthDp) {
        activity?.let {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(it, screenWidthDp)
        }
    }

    // Respect user preference: render an empty box if ads are disabled.
    if (SettingsManager(context).isAdsDisabled()) {
        Box(modifier = modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(bannerAdSize ?: AdSize.BANNER)
                adUnitId = AdUnitIds.PHONE_BANNER
                val adRequest = AdRequest.Builder().build()
                loadAd(adRequest)
                // Stash the AdView in the remembered holder so it lives
                // across recompositions and the dispose callback can reach it.
                adViewRef.value = this
            }
        }
    )

    // Keyed on Unit so this only fires when the composable leaves the
    // composition (not on every recomposition).
    DisposableEffect(Unit) {
        onDispose {
            adViewRef.value?.destroy()
            adViewRef.value = null
        }
    }
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}