hhpackage com.kiduyuk.klausk.kiduyutv.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager

/**
 * Composable banner ad for the phone flavour.
 * Renders a standard AdMob adaptive banner anchored to the bottom of the screen.
 * Respects the ads disabled setting from SettingsManager.
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
    val context = LocalContext.current

    // Check if ads are disabled - return empty box if disabled
    if (SettingsManager(context).isAdsDisabled()) {
        Box(modifier = modifier)
        return
    }

    val adView = remember(context) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AdUnitIds.PHONE_BANNER
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.i("BannerAdView", "AdMob banner loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("BannerAdView", "AdMob banner failed: ${error.message}")
                }

                override fun onAdOpened() {
                    Log.i("BannerAdView", "AdMob banner opened")
                }

                override fun onAdClosed() {
                    Log.i("BannerAdView", "AdMob banner closed")
                }

                override fun onAdClicked() {
                    Log.i("BannerAdView", "AdMob banner clicked")
                }

                override fun onAdImpression() {
                    Log.i("BannerAdView", "AdMob banner impression")
                }
            }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            Log.i("BannerAdView", "Loading mobile banner ad — unit: ${AdUnitIds.PHONE_BANNER}")
            adView.loadAd(AdRequest.Builder().build())
            adView
        }
    )
}
