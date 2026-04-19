package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds

/**
 * Composable banner ad for the phone flavour.
 * Renders a standard AdMob adaptive banner anchored to the bottom of the screen.
 *
 * Usage: place inside a Column/Box where you want the banner to appear.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(Color(0xFF141414))
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdUnitIds.PHONE_BANNER
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}