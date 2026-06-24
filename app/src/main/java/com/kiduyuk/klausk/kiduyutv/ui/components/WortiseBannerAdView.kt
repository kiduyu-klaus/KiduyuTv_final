package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * Wortise banner ad wrapped for Jetpack Compose.
 *
 * Renders a Wortise banner inside an [AndroidView].
 * Ads are automatically skipped when the user has disabled them.
 *
 * **Only show ONE banner network per screen.** Do not mix with
 * [BannerAdView], [StartAppBannerAdView], or [UnityBannerAdView]
 * on the same screen.
 *
 * @param modifier Modifier applied to the banner container.
 */
@Composable
fun WortiseBannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .padding(vertical = 4.dp)
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    // Skip rendering if ads are disabled
    if (SettingsManager(context).isAdsDisabled()) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.widget.FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                AdFallbackDispatcher.loadBanner(activity, this, BannerNetwork.WORTISE)
            }
        }
    )
}
