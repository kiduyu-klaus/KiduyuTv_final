package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

/**
 * Composable function for the Movies screen, displaying various rows of movie content.
 * It fetches movie data from [HomeViewModel] and displays them using [ContentRow] and [MovieCard].
 *
 * @param onMovieClick Lambda to be invoked when a movie card is clicked, typically navigating to movie details.
 * @param onNavigate Lambda to handle navigation between top-level screens.
 * @param onSearchClick Lambda to navigate to the search screen.
 * @param viewModel The [HomeViewModel] instance providing data for the screen.
 */
@Composable
fun MoviesScreen(
    onMovieClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Top navigation bar for the Movies screen.
        TopBar(
            selectedRoute = "movies",
            onNavItemClick = { route -> onNavigate(route) }, // Handle navigation clicks.
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )

        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else { // Display movie content once data is loaded.
            // Scrollable content area
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Screen title.
                item {
                    Text(
                        text = "Movies",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(48.dp)
                    )
                }

                // Content Row for Trending Movies.
                item {
                    ContentRow(
                        title = "Trending Movies",
                        items = uiState.trendingMovies,
                        onItemClick = { movie -> onMovieClick(movie.id) } // Handle movie click.
                    ) { movie, isSelected, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }
                }

                // Content Row for Popular Movies (using latestMovies from UI state).
                item {
                    ContentRow(
                        title = "Popular Movies",
                        items = uiState.latestMovies,
                        onItemClick = { movie -> onMovieClick(movie.id) } // Handle movie click.
                    ) { movie, isSelected, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }
                }

                // Content Row for Continue Watching Movies, only shown if not empty.
                if (uiState.continueWatching.isNotEmpty()) {
                    item {
                        ContentRow(
                            title = "Continue Watching",
                            items = uiState.continueWatching,
                            onItemClick = { movie -> onMovieClick(movie.id) } // Handle movie click.
                        ) { movie, isSelected, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isSelected,
                                onClick = onClick
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Preview for the [MoviesScreen] composable.
 */
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MoviesScreenPreview() {
    KiduyuTvTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            // Header for the preview.
            Text(
                text = "Movies",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(48.dp)
            )

            // Grid of movie cards for the preview.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(9) { index ->
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
        }
    }
}
