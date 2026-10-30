package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.util.AdEligibility
import com.kiduyuk.klausk.kiduyutv.util.StartAppAdManager
import com.startapp.sdk.ads.banner.BannerFormat

/** Start.io MRec (300x250) placement. Usable from phone and Android TV Compose screens. */
@Composable
fun StartAppMrecAdView(
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp),
    adTag: String? = null,
    tvFocusable: Boolean = false
) {
    StartAppInlineAdView(
        format = BannerFormat.MREC,
        modifier = modifier,
        adTag = adTag,
        tvFocusable = tvFocusable
    )
}

/** Start.io Cover (300x157dp) placement. Usable from phone and Android TV Compose screens. */
@Composable
fun StartAppCoverAdView(
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 157.dp),
    adTag: String? = null,
    tvFocusable: Boolean = false
) {
    StartAppInlineAdView(
        format = BannerFormat.COVER,
        modifier = modifier,
        adTag = adTag,
        tvFocusable = tvFocusable
    )
}

@Composable
private fun StartAppInlineAdView(
    format: BannerFormat,
    modifier: Modifier,
    adTag: String?,
    tvFocusable: Boolean
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val canRequestAds = AdEligibility.canRequestAds(context)
    val containerRef = remember { mutableStateOf<FrameLayout?>(null) }

    if (!canRequestAds) return

    AndroidView(
        modifier = if (tvFocusable) modifier.focusable() else modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                isFocusable = tvFocusable
                isClickable = tvFocusable
                containerRef.value = this
            }
        }
    )

    val container = containerRef.value
    LaunchedEffect(container, format, adTag, canRequestAds) {
        if (container != null && canRequestAds) {
            StartAppAdManager.loadInlineAd(
                activity = activity,
                container = container,
                format = format,
                adTag = adTag
            )
        }
    }

    DisposableEffect(container) {
        onDispose {
            container?.removeAllViews()
            containerRef.value = null
        }
    }
}
