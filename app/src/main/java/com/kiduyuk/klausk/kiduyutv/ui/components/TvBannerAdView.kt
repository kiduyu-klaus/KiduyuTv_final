package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds

/**
 * GAM banner for the TV flavour.
 * Uses a 728×90 leaderboard — appropriate for a 1080p TV canvas.
 * Fire TV and Android TV compatible.
 */
@Composable
fun TvBannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
        .background(Color(0xFF0F0F0F))
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdManagerAdView(context).apply {
                setAdSizes(AdSize.LEADERBOARD) // 728×90
                adUnitId = AdUnitIds.TV_BANNER
                loadAd(AdManagerAdRequest.Builder().build())
            }
        }
    )
}
