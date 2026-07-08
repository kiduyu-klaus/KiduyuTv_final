package com.kiduyuk.klausk.kiduyutv.ui.components.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.valentinilk.shimmer.shimmer

private val SkeletonBase = CardDark.copy(alpha = 0.86f)
private val SkeletonMuted = SurfaceDark.copy(alpha = 0.72f)

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    color: Color = SkeletonBase
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .clip(RoundedCornerShape(cornerRadius))
            .shimmer()
            .background(color)
    )
}

@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    color: Color = SkeletonBase
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .clip(CircleShape)
            .shimmer()
            .background(color)
    )
}

@Composable
fun SkeletonLine(
    width: Dp,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
    color: Color = SkeletonMuted
) {
    SkeletonBlock(
        modifier = modifier
            .width(width)
            .height(height),
        cornerRadius = height / 2,
        color = color
    )
}

@Composable
fun SkeletonLineFill(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    color: Color = SkeletonMuted
) {
    SkeletonBlock(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        cornerRadius = height / 2,
        color = color
    )
}