package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

/**
 * Composable function for the TV Shows screen, displaying various rows of TV show content.
 * It fetches TV show data from [HomeViewModel] and displays them using [ContentRow] and [TvShowCard].
 *
 * @param onTvShowClick Lambda to be invoked when a TV show card is clicked, typically navigating to TV show details.
 * @param onNavigate Lambda to handle navigation between top-level screens.
 * @param onSearchClick Lambda to navigate to the search screen.
 * @param viewModel The [HomeViewModel] instance providing data for the screen.
 */
@Composable
fun TvShowsScreen(
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadHomeContent(context)
    }

    val selectedTvShow by remember(uiState.selectedItem) {
        derivedStateOf {
            when (val item = uiState.selectedItem) {
                is TvShow -> item
                is WatchHistoryItem -> if (item.isTv) TvShow(
                    id = item.id,
                    name = item.title,
                    overview = item.overview ?: "",
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteAverage = item.voteAverage,
                    firstAirDate = item.releaseDate ?: "",
                    genreIds = emptyList(),
                    popularity = 0.0
                ) else null
                else -> null
            }
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.trendingTvShows.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
            // Also ensure the first item is selected in the ViewModel so the HeroSection displays it
            viewModel.onItemSelected(uiState.trendingTvShows.first())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading && uiState.trendingTvShows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LottieLoadingView(size = 300.dp)
            }
        } else { // Display TV show content once data is loaded.
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HeroSection(
                    movie = null,
                    tvShow = selectedTvShow
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // Content Row for Trending TV Shows.
                    ContentRow(
                        title = "Trending TV Shows",
                        items = uiState.trendingTvShows,
                        initialFocusRequester = firstItemFocusRequester,
                        onItemFocus = { tvShow -> viewModel.onItemSelected(tvShow) },
                        onItemClick = { tvShow -> onTvShowClick(tvShow.id) } // Handle TV show click.
                    ) { tvShow, isSelected, onClick ->
                        TvShowCard(
                            tvShow = tvShow,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }



                    // Content Row for Top Rated TV Shows.
                    ContentRow(
                        title = "Top Rated TV Shows",
                        items = uiState.topTvShows,
                        onItemFocus = { tvShow -> viewModel.onItemSelected(tvShow) },
                        onItemClick = { tvShow -> onTvShowClick(tvShow.id) } // Handle TV show click.
                    ) { tvShow, isSelected, onClick ->
                        TvShowCard(
                            tvShow = tvShow,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }

                    // Content Row for Best Sitcoms Ever
                    if (uiState.bestSitcoms.isNotEmpty()) {
                        ContentRow(
                            title = "Best Sitcoms Ever",
                            items = uiState.bestSitcoms,
                            onItemFocus = { tvShow -> viewModel.onItemSelected(tvShow) },
                            onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                        ) { tvShow, isFocused, onClick ->
                            TvShowCard(
                                tvShow = tvShow,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Time Travel TV Shows
                    if (uiState.timeTravelTvShows.isNotEmpty()) {
                        ContentRow(
                            title = "Time Travel TV Shows",
                            items = uiState.timeTravelTvShows,
                            onItemFocus = { tvShow -> viewModel.onItemSelected(tvShow) },
                            onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                        ) { tvShow, isFocused, onClick ->
                            TvShowCard(
                                tvShow = tvShow,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Christian TV Shows
                    if (uiState.christianTvShows.isNotEmpty()) {
                        ContentRow(
                            title = "Christian TV Shows",
                            items = uiState.christianTvShows,
                            onItemFocus = { tvShow -> viewModel.onItemSelected(tvShow) },
                            onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                        ) { tvShow, isFocused, onClick ->
                            TvShowCard(
                                tvShow = tvShow,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Continue Watching TV Shows, only shown if not empty.
                    if (uiState.continueWatching.isNotEmpty()) {
                        val tvHistory = uiState.continueWatching.filter { it.isTv }
                        if (tvHistory.isNotEmpty()) {
                            ContentRow(
                                title = "Continue Watching",
                                items = tvHistory,
                                onItemFocus = { historyItem -> viewModel.onItemSelected(historyItem) },
                                onItemClick = { historyItem -> onTvShowClick(historyItem.id) }
                            ) { historyItem, isSelected, onClick ->
                                TvShowCard(
                                    tvShow = TvShow(
                                        id = historyItem.id,
                                        name = historyItem.title,
                                        overview = historyItem.overview ?: "",
                                        posterPath = historyItem.posterPath,
                                        backdropPath = historyItem.backdropPath,
                                        voteAverage = historyItem.voteAverage,
                                        firstAirDate = historyItem.releaseDate ?: "",
                                        genreIds = emptyList(),
                                        popularity = 0.0
                                    ),
                                    isSelected = isSelected,
                                    onClick = onClick
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(15.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BackgroundDark, Color.Transparent)
                    )
                )
        )

        // Top navigation bar for the TV Shows screen.
        TopBar(
            selectedRoute = "tv_shows",
            onNavItemClick = { route -> onNavigate(route) }, // Handle navigation clicks.
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )
    }
}


/**
 * Preview for the [TvShowsScreen] composable.
 */
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun TvShowsScreenPreview() {
    KiduyuTvTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            // Header for the preview.
            Text(
                text = "TV Shows",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(48.dp)
            )

            // Grid of TV show cards for the preview.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(9) { index ->
                    TvShowCard(
                        tvShow = TvShow(
                            id = index + 1,
                            name = "TV Show ${index + 1}",
                            overview = "TV show description",
                            posterPath = null,
                            backdropPath = null,
                            voteAverage = 7.5 + index * 0.3,
                            firstAirDate = "2023",
                            genreIds = emptyList(),
                            popularity = 90.0
                        ),
                        isSelected = index == 0,
                        onClick = { }
                    )
                }
            }
        }
    }
}
