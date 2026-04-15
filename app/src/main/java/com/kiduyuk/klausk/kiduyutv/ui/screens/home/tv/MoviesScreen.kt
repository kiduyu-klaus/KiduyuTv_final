package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
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
    val scrollState = rememberScrollState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadHomeContent(context)
    }

    val selectedMovie by remember(uiState.selectedItem) {
        derivedStateOf {
            when (val item = uiState.selectedItem) {
                is Movie -> item
                is WatchHistoryItem -> if (!item.isTv) Movie(
                    id = item.id,
                    title = item.title,
                    overview = item.overview ?: "",
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteAverage = item.voteAverage,
                    releaseDate = item.releaseDate ?: "",
                    genreIds = emptyList(),
                    popularity = 0.0
                ) else null
                else -> null
            }
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.trendingMovies.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
            // Also ensure the first item is selected in the ViewModel so the HeroSection displays it
            viewModel.onItemSelected(uiState.trendingMovies.first())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading && uiState.trendingMovies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LottieLoadingView(size = 300.dp)
            }
        } else { // Display movie content once data is loaded.
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HeroSection(
                    movie = selectedMovie,
                    tvShow = null
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {

                    // Content Row for Now Playing Movies.
                    if (uiState.nowPlayingMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Now Playing",
                            items = uiState.nowPlayingMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isSelected, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isSelected,
                                onClick = onClick
                            )
                        }
                    }


                    // Content Row for Continue Watching Movies.
                    if (uiState.continueWatching.isNotEmpty()) {
                        val movieHistory = uiState.continueWatching.filter { !it.isTv }
                        if (movieHistory.isNotEmpty()) {
                            ContentRow(
                                title = "Continue Watching",
                                items = movieHistory,
                                onItemFocus = { historyItem -> viewModel.onItemSelected(historyItem) },
                                onItemClick = { historyItem -> onMovieClick(historyItem.id) }
                            ) { historyItem, isSelected, onClick ->
                                MovieCard(
                                    movie = Movie(
                                        id = historyItem.id,
                                        title = historyItem.title,
                                        overview = historyItem.overview ?: "",
                                        posterPath = historyItem.posterPath,
                                        backdropPath = historyItem.backdropPath,
                                        voteAverage = historyItem.voteAverage,
                                        releaseDate = historyItem.releaseDate ?: "",
                                        genreIds = emptyList(),
                                        popularity = 0.0
                                    ),
                                    isSelected = isSelected,
                                    onClick = onClick
                                )
                            }
                        }
                    }
                    // Content Row for Trending Movies.
//                    ContentRow(
//                        title = "Trending Movies",
//                        items = uiState.trendingMovies,
//                        initialFocusRequester = firstItemFocusRequester,
//                        onItemFocus = { movie -> viewModel.onItemSelected(movie) },
//                        onItemClick = { movie -> onMovieClick(movie.id) } // Handle movie click.
//                    ) { movie, isSelected, onClick ->
//                        MovieCard(
//                            movie = movie,
//                            isSelected = isSelected,
//                            onClick = onClick
//                        )
//                    }

                    // Content Row for Movies Trending This Week.
                    if (uiState.trendingMoviesThisWeek.isNotEmpty()) {
                        ContentRow(
                            title = "Movies Trending This Week",
                            items = uiState.trendingMoviesThisWeek,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isSelected, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isSelected,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Top Rated Movies
                    if (uiState.latestMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Top Rated Movies",
                            items = uiState.latestMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for 2026 Oscar winners
                    if (uiState.oscarWinners2026.isNotEmpty()) {
                        ContentRow(
                            title = "2026 Oscar winners",
                            items = uiState.oscarWinners2026,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Hallmark Movies
                    if (uiState.hallmarkMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Hallmark Movies",
                            items = uiState.hallmarkMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Movies Based on True Stories
                    if (uiState.trueStoryMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Movies Based on True Stories",
                            items = uiState.trueStoryMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Best movie classics
                    if (uiState.bestClassics.isNotEmpty()) {
                        ContentRow(
                            title = "Best movie classics",
                            items = uiState.bestClassics,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for CIA & Mossad Spies
                    if (uiState.spyMovies.isNotEmpty()) {
                        ContentRow(
                            title = "CIA & Mossad Spies",
                            items = uiState.spyMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Jason Statham Movies
                    if (uiState.stathamMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Jason Statham Movies",
                            items = uiState.stathamMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Time Travel Movies
                    if (uiState.timeTravelMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Time Travel Movies",
                            items = uiState.timeTravelMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Christian Movies
                    if (uiState.christianMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Christian Movies",
                            items = uiState.christianMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    // Content Row for Movies from the Bible
                    if (uiState.bibleMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Movies from the Bible",
                            items = uiState.bibleMovies,
                            onItemFocus = { movie -> viewModel.onItemSelected(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(15.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BackgroundDark, Color.Transparent)
                    )
                )
        )

        // Top navigation bar for the Movies screen.
        TopBar(
            selectedRoute = "movies",
            onNavItemClick = { route -> onNavigate(route) }, // Handle navigation clicks.
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )
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