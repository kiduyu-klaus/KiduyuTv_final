package com.kiduyuk.klausk.kiduyutv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.MediaListViewModel

/**
 * Screen that displays a grid of movies or TV shows filtered by a company or network.
 *
 * @param type The type of list to show ("company" or "network").
 * @param id The TMDB ID of the company or network.
 * @param name The name of the company or network to display as title.
 * @param onBackClick Callback for back button.
 * @param onMovieClick Callback when a movie is clicked.
 * @param onTvShowClick Callback when a TV show is clicked.
 * @param viewModel The MediaListViewModel instance.
 */
@Composable
fun MediaListScreen(
    type: String,
    id: Int,
    name: String,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: MediaListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load content when parameters change.
    LaunchedEffect(type, id, name) {
        if (type == "company") {
            viewModel.loadMoviesByCompany(id, name)
        } else {
            viewModel.loadTvShowsByNetwork(id, name)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            // Main Content: Grid of items
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
                    Text(text = uiState.error!!, color = TextPrimary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (type == "company") {
                        items(uiState.movies) { movie ->
                            MovieCard(
                                movie = movie,
                                isSelected = false, // Selection is handled by focus in TV apps.
                                onClick = { onMovieClick(movie.id) }
                            )
                        }
                    } else {
                        items(uiState.tvShows) { tvShow ->
                            TvShowCard(
                                tvShow = tvShow,
                                isSelected = false,
                                onClick = { onTvShowClick(tvShow.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
