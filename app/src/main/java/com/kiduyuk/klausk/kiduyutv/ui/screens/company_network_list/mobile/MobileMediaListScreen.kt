package com.kiduyuk.klausk.kiduyutv.ui.screens.company_network_list.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.MediaListViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Mobile-optimized screen that displays movies or TV shows filtered by a company or network.
 * Uses a vertical scrolling layout with 2-column grid for optimal mobile viewing.
 *
 * @param type The type of list to show ("company" or "network").
 * @param id The TMDB ID of the company or network.
 * @param name The name of the company or network to display as title.
 * @param onBackClick Callback for back button.
 * @param onMovieClick Callback when a movie is clicked.
 * @param onTvShowClick Callback when a TV show is clicked.
 * @param viewModel The MediaListViewModel instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMediaListScreen(
    type: String,
    id: Int,
    name: String,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: MediaListViewModel = viewModel()
) {
    // Observe view-model state so the screen recomposes on updates.
    val uiState by viewModel.uiState.collectAsState()
    // Needed for persistence actions (save/unsave list).
    val context = LocalContext.current
    // Tracks scroll position and powers infinite-scroll pagination.
    val listState = rememberLazyListState()

    // Load content when parameters change
    LaunchedEffect(type, id, name) {
        if (type == "company") {
            viewModel.loadMoviesByCompany(id, name)
        } else {
            viewModel.loadTvShowsByNetwork(id, name)
        }
    }

    // Detect when user scrolls to the bottom and trigger pagination
    LaunchedEffect(listState, uiState, type) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleRowIndex ->
                if (lastVisibleRowIndex != null) {
                    // Calculate total rows (items are chunked into 3-column rows)
                    val totalItems = if (type == "company") uiState.movies.size else uiState.tvShows.size
                    val totalRows = (totalItems + 2) / 3  // Ceiling division for 3-column layout
                    // Load more when user is within 2 rows from the end
                    if (lastVisibleRowIndex >= totalRows - 2 && !uiState.isLoading && !uiState.isLoadingMore) {
                        if (type == "company") {
                            viewModel.loadMoreMovies()
                        } else {
                            viewModel.loadMoreTvShows()
                        }
                    }
                }
            }
    }

    Scaffold(
        // App bar with list identity, item count, and save toggle.
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        if (type == "company") {
                            Text(
                                text = "${uiState.movies.size} movies",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "${uiState.tvShows.size} shows",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.toggleSave(context, type, id, name) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isSaved) MaterialTheme.colorScheme.secondary else SurfaceDark
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isSaved) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (uiState.isSaved) "Saved" else "Save",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.isSaved) "Saved" else "Save",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            // Render state machine: initial loading -> error -> empty -> content.
            when {
                uiState.isLoading && (uiState.movies.isEmpty() && uiState.tvShows.isEmpty()) -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieLoadingView(size = 300.dp)
                    }
                }

                uiState.error != null && uiState.movies.isEmpty() && uiState.tvShows.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Error loading content",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                uiState.movies.isEmpty() && uiState.tvShows.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No content found",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                    }
                }

                else -> {
                    // Main media feed rendered as manual 3-column rows.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (type == "company") {
                            // Display movies in 3-column grid rows
                            val movieRows = uiState.movies.chunked(3)
                            items(movieRows) { rowMovies ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowMovies.forEach { movie ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.67f)
                                        ) {
                                            MobileMovieCard(
                                                movie = movie,
                                                onClick = { onMovieClick(movie.id) },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    // Add spacers if row has fewer than 3 items
                                    repeat(3 - rowMovies.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            // Display TV shows in 3-column grid rows
                            val tvShowRows = uiState.tvShows.chunked(3)
                            items(tvShowRows) { rowShows ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowShows.forEach { tvShow ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.67f)
                                        ) {
                                            MobileTvShowCard(
                                                tvShow = tvShow,
                                                onClick = { onTvShowClick(tvShow.id) },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    // Add spacers if row has fewer than 3 items
                                    repeat(3 - rowShows.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Loading more indicator
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LottieLoadingView(size = 80.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
