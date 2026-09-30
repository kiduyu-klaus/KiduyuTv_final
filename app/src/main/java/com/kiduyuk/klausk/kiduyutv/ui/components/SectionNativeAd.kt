package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Native in-feed placement shared by TV and mobile catalog screens.
 *
 * This is placed after two normal content sections. [AdMobNativeAdView]
 * handles consent and the user's ads-disabled preference, and remains empty
 * until a native ad is available.
 */
@Composable
fun SectionNativeAd(modifier: Modifier = Modifier) {
    AdMobNativeAdView(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}
