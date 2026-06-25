package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher.BannerNetwork
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * TV banner ad — loaded via the unified [AdFallbackDispatcher].
 *
 * The dispatcher picks the requested banner network (Wortise for the TV flavour)
 * and injects the banner into a [FrameLayout] container. A 728×90 leaderboard
 * container height keeps the layout stable while the ad loads.
 *
 * No-op when the user has disabled ads in settings.
 */
@Composable
fun TvBannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
        .background(Color(0xFF0F0F0F))
) {
    val context = LocalContext.current
    val containerRef = remember { mutableStateOf<FrameLayout?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            containerRef.value?.let { container ->
                destroyAdMobChildren(container)
                container.removeAllViews()
            }
            containerRef.value = null
        }
    }

    // Respect the user's "disable ads" preference.
    if (SettingsManager(context).isAdsDisabled()) {
        Box(modifier = modifier)
        return
    }
    val activity = context.findActivity() ?: return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // FrameLayout acts as the ViewGroup host for the dispatcher.
            val container = FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            containerRef.value = container

            Log.i(TAG, "Loading TV banner via AdFallbackDispatcher (preferred=ADMOB)")
            AdFallbackDispatcher.loadBanner(
                activity = activity,
                container = container,
                preferred = BannerNetwork.WORTISE
            )
            container
        }
    )
}

private fun destroyAdMobChildren(container: ViewGroup) {
    for (index in 0 until container.childCount) {
        (container.getChildAt(index) as? com.google.android.gms.ads.AdView)?.destroy()
    }
}

/** Walk up the context wrapper chain looking for the hosting [Activity]. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Log tag kept in one place for grep-ability. */
private const val TAG = "TvBannerAdView"
