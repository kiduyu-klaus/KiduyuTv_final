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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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