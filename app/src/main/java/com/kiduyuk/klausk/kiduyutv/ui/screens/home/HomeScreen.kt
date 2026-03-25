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
import androidx.compose.ui.graphics.Brush
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
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeUiState
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem

/**
 * The main home screen of the KiduyuTv application.
 * Displays a hero section, various content rows for movies and TV shows, and navigation.
 * It observes the [HomeViewModel] for UI state updates and handles user interactions.
 * Performance optimized with proper state management to minimize recompositions.
 *
 * @param onMovieClick Lambda to navigate to the detail screen of a movie.
 * @param onTvShowClick Lambda to navigate to the detail screen of a TV show.
 * @param onNavigate Lambda to handle general navigation events.
 * @param onSearchClick Lambda to navigate to the search screen.
 * @param viewModel The [HomeViewModel] instance providing data for the screen.
 */
@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()

    // Remember scroll state for the main content column.
    val scrollState = rememberScrollState()

    // State to keep track of the currently selected navigation route.
    var selectedRoute by remember { mutableStateOf("home") }

    // Memoize selected item derivations to prevent unnecessary recompositions
    val selectedMovie by remember(uiState.selectedItem) {
        derivedStateOf { uiState.selectedItem as? Movie }
    }
    val selectedTvShow by remember(uiState.selectedItem) {
        derivedStateOf { uiState.selectedItem as? TvShow }
    }

    // Use uiState values directly - they're already immutable data
    // This avoids unnecessary recompositions when isLoading or error changes
    val trendingTvShows = uiState.trendingTvShows
    val trendingMovies = uiState.trendingMovies
    val continueWatching = uiState.continueWatching
    val popularNetworks = uiState.popularNetworks
    val popularCompanies = uiState.popularCompanies
    val latestMovies = uiState.latestMovies
    val topTvShows = uiState.topTvShows
    val oscarMovies = uiState.oscarMovies
    val myList = uiState.myList

    // Main container for the home screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading) {
            LoadingContent()
        } else if (uiState.error != null) { // Display an error message if an error occurred.
            ErrorContent(error = uiState.error!!)
        } else { // Display the main content once data is loaded.
            HomeContent(
                selectedMovie = selectedMovie,
                selectedTvShow = selectedTvShow,
                trendingTvShows = trendingTvShows,
                trendingMovies = trendingMovies,
                continueWatching = continueWatching,
                popularNetworks = popularNetworks,
                popularCompanies = popularCompanies,
                latestMovies = latestMovies,
                topTvShows = topTvShows,
                oscarMovies = oscarMovies,
                myList = myList,
                scrollState = scrollState,
                selectedRoute = selectedRoute,
                onMovieClick = onMovieClick,
                onTvShowClick = onTvShowClick,
                onNavigate = onNavigate,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onRouteChange = { selectedRoute = it },
                onSelectItem = { viewModel.selectItem(it) }
            )
        }
    }
}

/**
 * Loading content composable - displays progress indicator.
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = PrimaryRed)
    }
}

/**
 * Error content composable - displays error message.
 */
@Composable
private fun ErrorContent(error: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

/**
 * Home content composable - displays all home screen content.
 * Extracted to separate composable to enable proper memoization.
 */
@Composable
private fun HomeContent(
    selectedMovie: Movie?,
    selectedTvShow: TvShow?,
    trendingTvShows: List<TvShow>,
    trendingMovies: List<Movie>,
    continueWatching: List<Movie>,
    popularNetworks: List<com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem>,
    popularCompanies: List<com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem>,
    latestMovies: List<Movie>,
    topTvShows: List<TvShow>,
    oscarMovies: List<Movie>,
    myList: List<MyListItem>,
    scrollState: androidx.compose.foundation.ScrollState,
    selectedRoute: String,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRouteChange: (String) -> Unit,
    onSelectItem: (Any) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HeroSection(
                movie = selectedMovie,
                tvShow = selectedTvShow
            )

            // Scrollable content area — takes all remaining vertical space.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                // Content Row for TV Shows Trending Today.
                ContentRow(
                    title = "TV Shows Trending Today",
                    items = trendingTvShows,
                    onItemFocus = { tvShow -> onSelectItem(tvShow) },
                    onItemClick = { tvShow ->
                        onSelectItem(tvShow)
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
                    items = trendingMovies,
                    onItemFocus = { movie -> onSelectItem(movie) },
                    onItemClick = { movie ->
                        onSelectItem(movie)
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
                if (continueWatching.isNotEmpty()) {
                    ContentRow(
                        title = "Continue Watching",
                        items = continueWatching,
                        onItemFocus = { movie -> onSelectItem(movie) },
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
                    items = popularNetworks,
                    onItemClick = { network ->
                        onNavigate("media_list/network/${network.id}/${network.name}")
                    }
                )

                // Content Row for Popular Companies.
                NetworkRow(
                    title = "Popular Companies",
                    items = popularCompanies,
                    onItemClick = { company ->
                        onNavigate("media_list/company/${company.id}/${company.name}")
                    }
                )

                // Content Row for Latest Movies Last Week.
                ContentRow(
                    title = "Latest Movies Last Week",
                    items = latestMovies,
                    onItemFocus = { movie -> onSelectItem(movie) },
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
                    items = topTvShows,
                    onItemFocus = { tvShow -> onSelectItem(tvShow) },
                    onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                ) { tvShow, isFocused, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isFocused,
                        onClick = onClick
                    )
                }

                // Content Row for Oscar Movies (only shown if not empty).
                if (oscarMovies.isNotEmpty()) {
                    ContentRow(
                        title = "Oscar Movies",
                        items = oscarMovies,
                        onItemFocus = { movie -> onSelectItem(movie) },
                        onItemClick = { movie -> onMovieClick(movie.id) }
                    ) { movie, isFocused, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isFocused,
                            onClick = onClick
                        )
                    }
                }

                // My List section, only shown if not empty.
                if (myList.isNotEmpty()) {
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
                        myList.forEach { item ->
                            // TODO: Implement display for my list items.
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp)) // Bottom spacing.
            } // end scrollable Column
        } // end content Column

        // Transparent TopBar overlaid on top of all content
        // A subtle gradient scrim ensures text stays readable over the hero image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        ) {
            TopBar(
                selectedRoute = selectedRoute,
                onNavItemClick = { route ->
                    onRouteChange(route)
                    onNavigate(route)
                },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick
            )
        }
    } // end outer Box
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
