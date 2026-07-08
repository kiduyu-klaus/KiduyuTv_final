package com.kiduyuk.klausk.kiduyutv.ui.components.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark

@Composable
fun MobileHomeSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 6,
    cardsPerRow: Int = 4
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
        userScrollEnabled = false
    ) {
        item { MobileHeroSkeleton() }
        items(rowCount) { index ->
            if (index == 3) {
                MobileNetworkRowSkeleton(titleWidth = 170.dp, cardsPerRow = cardsPerRow)
            } else {
                MobilePosterRowSkeleton(
                    titleWidth = when (index) {
                        0 -> 170.dp
                        1 -> 120.dp
                        else -> 190.dp
                    },
                    cardsPerRow = cardsPerRow
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun MobileHeroSkeleton(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.45f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp, color = SurfaceDark)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.7f),
                            BackgroundDark
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.68f), height = 28.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(modifier = Modifier.width(92.dp).height(38.dp), cornerRadius = 20.dp)
                SkeletonBlock(modifier = Modifier.width(82.dp).height(38.dp), cornerRadius = 20.dp)
                SkeletonBlock(modifier = Modifier.width(78.dp).height(38.dp), cornerRadius = 20.dp)
            }
        }
    }
}

@Composable
fun MobilePosterRowSkeleton(
    titleWidth: Dp = 160.dp,
    modifier: Modifier = Modifier,
    cardsPerRow: Int = 4
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        SkeletonLine(
            width = titleWidth,
            height = 22.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(cardsPerRow) { MobilePosterCardSkeleton() }
        }
    }
}

@Composable
fun MobilePosterCardSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(120.dp)
            .height(180.dp)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxSize(), cornerRadius = 8.dp)
        SkeletonBlock(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .width(30.dp)
                .height(16.dp),
            cornerRadius = 4.dp,
            color = Color.Black.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun MobileNetworkRowSkeleton(
    titleWidth: Dp = 170.dp,
    modifier: Modifier = Modifier,
    cardsPerRow: Int = 4
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        SkeletonLine(
            width = titleWidth,
            height = 22.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(cardsPerRow) {
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SkeletonBlock(modifier = Modifier.size(80.dp), cornerRadius = 8.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonLine(width = 74.dp, height = 10.dp)
                }
            }
        }
    }
}

@Composable
fun MobileMovieDetailSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
        userScrollEnabled = false
    ) {
        item { MobileDetailHeroSkeleton() }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.78f), height = 30.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonLine(width = 42.dp, height = 14.dp)
                    SkeletonLine(width = 38.dp, height = 14.dp)
                    SkeletonLine(width = 48.dp, height = 14.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SkeletonBlock(modifier = Modifier.weight(1f).height(44.dp), cornerRadius = 8.dp)
                    SkeletonBlock(modifier = Modifier.weight(1f).height(44.dp), cornerRadius = 8.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SkeletonBlock(modifier = Modifier.weight(1f).height(42.dp), cornerRadius = 8.dp)
                    SkeletonBlock(modifier = Modifier.weight(1f).height(42.dp), cornerRadius = 8.dp)
                }
                SkeletonLineFill(height = 14.dp)
                SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.92f), height = 14.dp)
                SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.72f), height = 14.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonLine(width = 70.dp, height = 18.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        SkeletonBlock(modifier = Modifier.width(82.dp).height(28.dp), cornerRadius = 16.dp)
                    }
                }
            }
        }
        item { MobileCastCrewRowSkeleton(titleWidth = 180.dp) }
        item { MobilePosterRowSkeleton(titleWidth = 120.dp, cardsPerRow = 4) }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun MobileDetailHeroSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp, color = SurfaceDark)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.8f),
                            BackgroundDark
                        )
                    )
                )
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp),
            cornerRadius = 24.dp
        )
    }
}

@Composable
fun MobileCastCrewRowSkeleton(
    titleWidth: Dp = 180.dp,
    modifier: Modifier = Modifier,
    peopleCount: Int = 4
) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        SkeletonLine(
            width = titleWidth,
            height = 22.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) {
            items(peopleCount) {
                Column(
                    modifier = Modifier.width(90.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SkeletonCircle(modifier = Modifier.size(70.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonLine(width = 74.dp, height = 10.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonLine(width = 58.dp, height = 9.dp)
                }
            }
        }
    }
}

@Composable
fun MobileMediaListSkeleton(modifier: Modifier = Modifier, rows: Int = 5) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.67f),
                        cornerRadius = 8.dp
                    )
                }
            }
        }
    }
}

@Composable
fun MobileSearchGridSkeleton(modifier: Modifier = Modifier, itemCount: Int = 6) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        items(itemCount) {
            Column {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    cornerRadius = 12.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.8f), height = 14.dp)
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonLine(width = 46.dp, height = 12.dp)
            }
        }
    }
}
