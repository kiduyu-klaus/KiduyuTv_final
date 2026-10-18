package com.kiduyuk.klausk.kiduyutv.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.util.concurrent.atomic.AtomicInteger

/**
 * Selects one native-ad network per placement and advances through the other
 * supported networks only when the selected network fails to load.
 *
 * Unity Ads does not expose a native-ad format through the SDK currently used
 * by the app, so it is intentionally not part of this dispatcher.
 */
object NativeAdFallbackDispatcher {

    private const val TAG = "NativeAdFallbackDispatcher"
    private val nextNetwork = AtomicInteger(0)
    private val networks = listOf(
        NativeAdNetwork.WORTISE,
        NativeAdNetwork.START_IO,
        NativeAdNetwork.ADMOB
    )

    @Composable
    fun Render(
        modifier: Modifier = Modifier
    ) {
        val initialIndex = remember { nextNetwork.getAndIncrement().floorMod(networks.size) }
        var networkIndex by remember { mutableIntStateOf(initialIndex) }
        var attempt by remember { mutableIntStateOf(0) }
        val network = networks[networkIndex]

        fun onLoadFailed() {
            if (attempt >= networks.lastIndex) {
                Log.w(TAG, "All native networks failed for placement")
                return
            }
            attempt += 1
            networkIndex = (initialIndex + attempt).floorMod(networks.size)
            Log.i(TAG, "Native ${network.displayName} failed; trying ${networks[networkIndex].displayName}")
        }

        // If a provider SDK becomes ready after this placement first composes,
        // force the selected component to restart its request cleanly.
        key(network) {
            when (network) {
                NativeAdNetwork.WORTISE -> WortiseNativeAdView(
                    modifier = modifier,
                    fallbackToAdMob = false,
                    onLoadFailed = ::onLoadFailed
                )

                NativeAdNetwork.START_IO -> StartAppNativeAdView(
                    modifier = modifier,
                    fallbackToAdMob = false,
                    onLoadFailed = ::onLoadFailed
                )

                NativeAdNetwork.ADMOB -> AdMobNativeAdView(
                    modifier = modifier,
                    onLoadFailed = ::onLoadFailed
                )
            }
        }

        LaunchedEffect(Unit) {
            Log.i(TAG, "Native placement started with ${network.displayName}")
        }
    }

    private fun Int.floorMod(divisor: Int): Int = Math.floorMod(this, divisor)

    private enum class NativeAdNetwork(val displayName: String) {
        WORTISE("Wortise"),
        START_IO("Start.io"),
        ADMOB("AdMob")
    }
}
