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
import androidx.compose.foundation.layout.widthIn
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
fun TvHomeSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 6,
    cardsPerRow: Int = 9
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            item { TvHeroSkeleton() }
            items(rowCount) { index ->
                if (index == 2) {
                    TvNetworkRowSkeleton(titleWidth = 210.dp)
                } else {
                    TvPosterRowSkeleton(
                        titleWidth = when (index) {
                            0 -> 140.dp
                            1 -> 190.dp
                            else -> 230.dp
                        },
                        cardsPerRow = cardsPerRow
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BackgroundDark.copy(alpha = 0.92f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun TvHeroSkeleton(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.55f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        SkeletonBlock(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            color = SurfaceDark
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.55f),
                            BackgroundDark
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonLine(width = 360.dp, height = 34.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(width = 52.dp, height = 14.dp)
                SkeletonLine(width = 42.dp, height = 14.dp)
            }
            SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.95f), height = 12.dp)
            SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.76f), height = 12.dp)
            SkeletonLineFill(modifier = Modifier.fillMaxWidth(0.58f), height = 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(modifier = Modifier.width(92.dp).height(36.dp), cornerRadius = 4.dp)
                SkeletonBlock(modifier = Modifier.width(92.dp).height(36.dp), cornerRadius = 4.dp)
            }
        }
    }
}

@Composable
fun TvPosterRowSkeleton(
    titleWidth: Dp = 180.dp,
    modifier: Modifier = Modifier,
    cardsPerRow: Int = 9
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
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
            items(cardsPerRow) {
                TvPosterCardSkeleton()
            }
        }
    }
}

@Composable
fun TvPosterCardSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 100.dp, height = 180.dp)) {
        SkeletonBlock(modifier = Modifier.fillMaxSize(), cornerRadius = 8.dp)
        SkeletonBlock(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .width(34.dp)
                .height(18.dp),
            cornerRadius = 4.dp,
            color = Color.Black.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun TvNetworkRowSkeleton(
    titleWidth: Dp = 210.dp,
    modifier: Modifier = Modifier,
    cardsPerRow: Int = 7
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        SkeletonLine(
            width = titleWidth,
            height = 22.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(cardsPerRow) {
                SkeletonBlock(
                    modifier = Modifier.width(160.dp).height(100.dp),
                    cornerRadius = 8.dp
                )
            }
        }
    }
}

@Composable
fun TvCastCrewRowSkeleton(
    titleWidth: Dp = 110.dp,
    modifier: Modifier = Modifier,
    peopleCount: Int = 9
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
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
fun TvMovieDetailSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark),
        userScrollEnabled = false
    ) {
        item { TvDetailHeroSkeleton() }
        item { TvCastCrewRowSkeleton(titleWidth = 210.dp) }
        item { TvCastCrewRowSkeleton(titleWidth = 70.dp) }
        item { TvPosterRowSkeleton(titleWidth = 210.dp, cardsPerRow = 9) }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun TvDetailHeroSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
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
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonLine(width = 330.dp, height = 24.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonLine(width = 44.dp, height = 12.dp)
                SkeletonLine(width = 38.dp, height = 12.dp)
                SkeletonLine(width = 48.dp, height = 12.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) {
                    SkeletonBlock(modifier = Modifier.width(72.dp).height(22.dp), cornerRadius = 12.dp)
                }
            }
            SkeletonLineFill(modifier = Modifier.width(680.dp), height = 12.dp)
            SkeletonLineFill(modifier = Modifier.width(560.dp), height = 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(modifier = Modifier.width(86.dp).height(36.dp), cornerRadius = 4.dp)
                SkeletonBlock(modifier = Modifier.width(96.dp).height(36.dp), cornerRadius = 4.dp)
                SkeletonBlock(modifier = Modifier.width(42.dp).height(36.dp), cornerRadius = 4.dp)
                SkeletonBlock(modifier = Modifier.width(96.dp).height(36.dp), cornerRadius = 4.dp)
            }
        }
    }
}

@Composable
fun TvMediaGridSkeleton(
    columns: Int = 6,
    modifier: Modifier = Modifier,
    itemCount: Int = columns * 3
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 25.dp, end = 25.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(itemCount) {
            Box(Modifier.aspectRatio(0.56f)) {
                SkeletonBlock(modifier = Modifier.fillMaxSize(), cornerRadius = 8.dp)
                SkeletonBlock(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .width(34.dp)
                        .height(18.dp),
                    cornerRadius = 4.dp,
                    color = Color.Black.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
fun TvSearchResultsSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        item { SkeletonLine(width = 130.dp, height = 16.dp) }
        items(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(modifier = Modifier.width(100.dp).height(150.dp), cornerRadius = 8.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonLine(width = 260.dp, height = 22.dp)
                    SkeletonLine(width = 90.dp, height = 14.dp)
                    SkeletonLineFill(modifier = Modifier.width(620.dp), height = 12.dp)
                    SkeletonLineFill(modifier = Modifier.width(520.dp), height = 12.dp)
                }
            }
        }
    }
}
