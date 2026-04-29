package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * GAM banner for the TV flavour.
 * Uses a 728×90 leaderboard — appropriate for a 1080p TV canvas.
 * Fire TV and Android TV compatible.
 * Respects the ads disabled setting from SettingsManager.
 */
@Composable
fun TvBannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
        .background(Color(0xFF0F0F0F))
) {
    val context = LocalContext.current

    // Check if ads are disabled - return empty box if disabled
    if (SettingsManager(context).isAdsDisabled()) {
        Box(modifier = modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdManagerAdView(ctx).apply {
                setAdSizes(AdSize.LEADERBOARD) // 728×90
                adUnitId = AdUnitIds.TV_BANNER
                loadAd(AdManagerAdRequest.Builder().build())
            }
        }
    )
}
