package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

/**
 * Composable function for the TV Shows screen, displaying various rows of TV show content.
 * It fetches TV show data from [HomeViewModel] and displays them using [ContentRow] and [TvShowCard].
 *
 * @param onTvShowClick Lambda to be invoked when a TV show card is clicked, typically navigating to TV show details.
 * @param onNavigate Lambda to handle navigation between top-level screens.
 * @param viewModel The [HomeViewModel] instance providing data for the screen.
 */
@Composable
fun TvShowsScreen(
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Top navigation bar for the TV Shows screen.
        TopBar(
            selectedRoute = "tv_shows",
            onNavItemClick = { route -> onNavigate(route) } // Handle navigation clicks.
        )

        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else { // Display TV show content once data is loaded.
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Screen title.
                Text(
                    text = "TV Shows",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(48.dp)
                )

                // Content Row for Trending TV Shows.
                ContentRow(
                    title = "Trending TV Shows",
                    items = uiState.trendingTvShows,
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
                    onItemClick = { tvShow -> onTvShowClick(tvShow.id) } // Handle TV show click.
                ) { tvShow, isSelected, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }
            }
        }
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
