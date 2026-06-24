package com.kiduyuk.klausk.kiduyutv.ui.components

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiduyuk.klausk.kiduyutv.util.AdFallbackDispatcher
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * Unity Ads banner wrapped for Jetpack Compose.
 *
 * Renders a Unity banner inside an [AndroidView].
 * The banner is properly destroyed when the Composable leaves composition
 * to prevent memory leaks.
 *
 * **Only show ONE banner network per screen.** Do not mix with
 * [BannerAdView], [StartAppBannerAdView], or [WortiseBannerAdView]
 * on the same screen.
 *
 * @param modifier Modifier applied to the banner container.
 */
@Composable
fun UnityBannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .padding(vertical = 4.dp)
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    // Skip rendering if ads are disabled
    if (SettingsManager(context).isAdsDisabled()) return

    // Clean up banner on disposal to avoid memory leaks
    DisposableEffect(activity) {
        onDispose {
            UnityAdManager.destroyBanner()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.widget.FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                AdFallbackDispatcher.loadBanner(activity, this, BannerNetwork.UNITY)
            }
        }
    )
}
