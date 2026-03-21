package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var selectedRoute by remember { mutableStateOf("home") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Top Navigation Bar
                TopBar(
                    selectedRoute = selectedRoute,
                    onNavItemClick = { route ->
                        selectedRoute = route
                        onNavigate(route)
                    }
                )

                // Hero Section with selected item
                val selectedMovie = uiState.selectedItem as? Movie
                val selectedTvShow = uiState.selectedItem as? TvShow

                HeroSection(
                    movie = if (selectedMovie != null) selectedMovie else uiState.trendingMovies.firstOrNull(),
                    tvShow = if (selectedTvShow != null) selectedTvShow else uiState.trendingTvShows.firstOrNull()
                )

                // TV Shows Trending Today
                ContentRow(
                    title = "TV Shows Trending Today",
                    items = uiState.trendingTvShows,
                    onItemFocus = { tvShow -> viewModel.selectItem(tvShow) },
                    onItemClick = { tvShow ->
                        viewModel.selectItem(tvShow)
                        onTvShowClick(tvShow.id)
                    }
                ) { tvShow, isSelected, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // Movies Trending Today
                ContentRow(
                    title = "Movies Trending Today",
                    items = uiState.trendingMovies,
                    onItemFocus = { movie -> viewModel.selectItem(movie) },
                    onItemClick = { movie ->
                        viewModel.selectItem(movie)
                        onMovieClick(movie.id)
                    }
                ) { movie, isSelected, onClick ->
                    MovieCard(
                        movie = movie,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // Continue Watching
                if (uiState.continueWatching.isNotEmpty()) {
                    ContentRow(
                        title = "Continue Watching",
                        items = uiState.continueWatching,
                        onItemClick = { movie -> onMovieClick(movie.id) }
                    ) { movie, isSelected, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }
                }

                // Popular Networks
                NetworkRow(
                    title = "Popular Networks",
                    items = uiState.popularNetworks,
                    onItemClick = { /* Navigate to network content */ }
                )

                // Popular Companies
                NetworkRow(
                    title = "Popular Companies",
                    items = uiState.popularCompanies,
                    onItemClick = { /* Navigate to company content */ }
                )

                // Latest Movies Last Week
                ContentRow(
                    title = "Latest Movies Last Week",
                    items = uiState.latestMovies,
                    onItemClick = { movie -> onMovieClick(movie.id) }
                ) { movie, isSelected, onClick ->
                    MovieCard(
                        movie = movie,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // Top TV Shows Last Week
                ContentRow(
                    title = "Top TV Shows Last Week",
                    items = uiState.topTvShows,
                    onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                ) { tvShow, isSelected, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // My List
                if (uiState.myList.isNotEmpty()) {
                    Text(
                        text = "My List",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        uiState.myList.forEach { item ->
                            // Display my list items
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// Preview for HomeScreen
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun HomeScreenPreview() {
    KiduyuTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Navigation Bar
                TopBar(
                    selectedRoute = "home",
                    onNavItemClick = {}
                )

                // Hero Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Trending Now
                Text(
                    text = "Trending Now",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(5) { index ->
                        MovieCard(
                            movie = Movie(
                                id = index + 1,
                                title = "Movie ${index + 1}",
                                overview = "Movie description",
                                posterPath = null,
                                backdropPath = null,
                                voteAverage = 8.0 + index * 0.2,
                                releaseDate = "2023",
                                genreIds = emptyList(),
                                popularity = 100.0
                            ),
                            isSelected = index == 0,
                            onClick = { }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // TV Shows
                Text(
                    text = "TV Shows",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(5) { index ->
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

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}