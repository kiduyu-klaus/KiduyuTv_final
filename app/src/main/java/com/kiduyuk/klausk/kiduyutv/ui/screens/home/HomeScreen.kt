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

/**
 * The main home screen of the KiduyuTv application.
 * Displays a hero section, various content rows for movies and TV shows, and navigation.
 * It observes the [HomeViewModel] for UI state updates and handles user interactions.
 *
 * @param onMovieClick Lambda to navigate to the detail screen of a movie.
 * @param onTvShowClick Lambda to navigate to the detail screen of a TV show.
 * @param onNavigate Lambda to handle general navigation events.
 * @param viewModel The [HomeViewModel] instance providing data for the screen.
 */
@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()
    // Remember scroll state for the main content column.
    val scrollState = rememberScrollState()
    // State to keep track of the currently selected navigation route.
    var selectedRoute by remember { mutableStateOf("home") }

    // Main container for the home screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else if (uiState.error != null) { // Display an error message if an error occurred.
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
        } else { // Display the main content once data is loaded.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState) // Make the column vertically scrollable.
            ) {
                // Top Navigation Bar component.
                TopBar(
                    selectedRoute = selectedRoute,
                    onNavItemClick = { route ->
                        selectedRoute = route
                        onNavigate(route)
                    }
                )

                // Hero Section, displaying details of the currently selected item.
                val selectedMovie = uiState.selectedItem as? Movie
                val selectedTvShow = uiState.selectedItem as? TvShow

                HeroSection(
                    movie = selectedMovie,
                    tvShow = selectedTvShow
                )

                // Content Row for TV Shows Trending Today.
                ContentRow(
                    title = "TV Shows Trending Today",
                    items = uiState.trendingTvShows,
                    onItemFocus = { tvShow -> viewModel.selectItem(tvShow) }, // Update selected item on focus.
                    onItemClick = { tvShow ->
                        viewModel.selectItem(tvShow)
                        onTvShowClick(tvShow.id)
                    }
                ) { tvShow, isFocused, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isFocused,
                        onClick = onClick
                    )
                }

                // Content Row for Movies Trending Today.
                ContentRow(
                    title = "Movies Trending Today",
                    items = uiState.trendingMovies,
                    onItemFocus = { movie -> viewModel.selectItem(movie) }, // Update selected item on focus.
                    onItemClick = { movie ->
                        viewModel.selectItem(movie)
                        onMovieClick(movie.id)
                    }
                ) { movie, isFocused, onClick ->
                    MovieCard(
                        movie = movie,
                        isSelected = isFocused,
                        onClick = onClick
                    )
                }

                // Content Row for Continue Watching, only shown if not empty.
                if (uiState.continueWatching.isNotEmpty()) {
                    ContentRow(
                        title = "Continue Watching",
                        items = uiState.continueWatching,
                        onItemFocus = { movie -> viewModel.selectItem(movie) }, // Update selected item on focus.
                        onItemClick = { movie -> onMovieClick(movie.id) }
                    ) { movie, isFocused, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isFocused,
                            onClick = onClick
                        )
                    }
                }

                // Content Row for Popular Networks.
                NetworkRow(
                    title = "Popular Networks",
                    items = uiState.popularNetworks,
                    onItemClick = { network ->
                        onNavigate("media_list/network/${network.id}/${network.name}")
                    }
                )

                // Content Row for Popular Companies.
                NetworkRow(
                    title = "Popular Companies",
                    items = uiState.popularCompanies,
                    onItemClick = { company ->
                        onNavigate("media_list/company/${company.id}/${company.name}")
                    }
                )

                // Content Row for Latest Movies Last Week.
                ContentRow(
                    title = "Latest Movies Last Week",
                    items = uiState.latestMovies,
                    onItemFocus = { movie -> viewModel.selectItem(movie) }, // Update selected item on focus.
                    onItemClick = { movie -> onMovieClick(movie.id) }
                ) { movie, isFocused, onClick ->
                    MovieCard(
                        movie = movie,
                        isSelected = isFocused,
                        onClick = onClick
                    )
                }

                // Content Row for Top TV Shows Last Week.
                ContentRow(
                    title = "Top TV Shows Last Week",
                    items = uiState.topTvShows,
                    onItemFocus = { tvShow -> viewModel.selectItem(tvShow) }, // Update selected item on focus.
                    onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                ) { tvShow, isFocused, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isFocused,
                        onClick = onClick
                    )
                }

                // My List section, only shown if not empty.
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
                            // TODO: Implement display for my list items.
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp)) // Bottom spacing.
            }
        }
    }
}

/**
 * Preview for the [HomeScreen] composable.
 */
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
                // Top Navigation Bar for preview.
                TopBar(
                    selectedRoute = "home",
                    onNavItemClick = {}
                )

                // Hero Section placeholder for preview.
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

                Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing.

                // Trending Now section for preview.
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

                Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing.

                // TV Shows section for preview.
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

                Spacer(modifier = Modifier.height(32.dp)) // Bottom spacing.
            }
        }
    }
}
