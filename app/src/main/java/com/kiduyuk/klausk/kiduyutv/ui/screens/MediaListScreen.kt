package com.kiduyuk.klausk.kiduyutv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * Uses a responsive grid layout that adapts to screen width.
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

    // Get screen configuration to calculate responsive grid
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Calculate responsive card dimensions and grid columns
    // Standard TV screen padding
    val horizontalPadding = 48.dp
    val spacing = 16.dp

    // Available width after padding
    val availableWidth = screenWidth - (horizontalPadding * 2)

    // Target card dimensions (maintaining 2:3 aspect ratio)
    // For TV, we aim for 8 items per row on wide screens
    val targetColumns = 8

    // Calculate card width: (availableWidth - (spacing * (columns - 1))) / columns
    val cardWidth = (availableWidth - (spacing * (targetColumns - 1))) / targetColumns
    val cardHeight = cardWidth * 1.8f // 2:3 aspect ratio with slight adjustment for TV

    // Calculate actual number of columns that fit
    val minCardWidth = 120.dp
    val actualColumns = maxOf(4, minOf(8, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))

    // Recalculate card size based on actual columns
    val calculatedCardWidth = (availableWidth - (spacing * (actualColumns - 1))) / actualColumns
    val calculatedCardHeight = calculatedCardWidth * 1.8f

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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
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

            // Main Content: Responsive Grid of items
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
                    columns = GridCells.Fixed(actualColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 8.dp,
                        bottom = 32.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    if (type == "company") {
                        items(uiState.movies) { movie ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .width(calculatedCardWidth)
                                    .height(calculatedCardHeight)
                                    .focusable(interactionSource = interactionSource)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        onMovieClick(movie.id)
                                    }
                            ) {
                                MovieCard(
                                    movie = movie,
                                    isSelected = isFocused,
                                    onClick = { onMovieClick(movie.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        items(uiState.tvShows) { tvShow ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .width(calculatedCardWidth)
                                    .height(calculatedCardHeight)
                                    .focusable(interactionSource = interactionSource)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        onTvShowClick(tvShow.id)
                                    }
                            ) {
                                TvShowCard(
                                    tvShow = tvShow,
                                    isSelected = isFocused,
                                    onClick = { onTvShowClick(tvShow.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
