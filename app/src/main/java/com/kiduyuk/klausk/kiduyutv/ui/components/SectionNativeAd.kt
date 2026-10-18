package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Native in-feed placement shared by TV and mobile catalog screens.
 *
 * TV callers opt into a focusable placement so the ad participates in
 * Android TV D-pad navigation. Wortise is preferred; AdMob is used if the
 * Wortise native request fails.
 */
@Composable
fun SectionNativeAd(
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusModifier = if (tvFocusable) {
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    } else {
        Modifier
    }

    NativeAdFallbackDispatcher.Render(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}
