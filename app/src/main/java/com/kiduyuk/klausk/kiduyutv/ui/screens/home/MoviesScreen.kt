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
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@Composable
fun MoviesScreen(
    onMovieClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopBar(
            selectedRoute = "movies",
            onNavItemClick = { /* Handle navigation */ }
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Movies",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(48.dp)
                )

                // Trending Movies
                ContentRow(
                    title = "Trending Movies",
                    items = uiState.trendingMovies,
                    onItemClick = { movie -> onMovieClick(movie.id) }
                ) { movie, isSelected, onClick ->
                    MovieCard(
                        movie = movie,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // Popular Movies
                ContentRow(
                    title = "Popular Movies",
                    items = uiState.latestMovies,
                    onItemClick = { movie -> onMovieClick(movie.id) }
                ) { movie, isSelected, onClick ->
                    MovieCard(
                        movie = movie,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // Continue Watching Movies
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
            }
        }
    }
}


// Preview for MoviesScreen
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MoviesScreenPreview() {
    KiduyuTvTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            // Header
            Text(
                text = "Movies",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(48.dp)
            )

            // Movies Grid
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
