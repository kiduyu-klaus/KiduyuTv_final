package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.kiduyuk.klausk.kiduyutv.R

/**
 * A reusable loading view that displays a Lottie animation.
 * Replaces the default CircularProgressIndicator for a more branded experience.
 *
 * @param modifier Modifier for the container Box.
 * @param size The size of the Lottie animation.
 */
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun LottieLoadingView(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(size)
        )
    }
}
