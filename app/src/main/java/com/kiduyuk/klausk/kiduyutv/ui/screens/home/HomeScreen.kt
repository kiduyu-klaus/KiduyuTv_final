package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var selectedRoute by remember { mutableStateOf("home") }
    val context = androidx.compose.ui.platform.LocalContext.current

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
    val selectedTvShow by remember(uiState.selectedItem) {
        derivedStateOf {
            when (val item = uiState.selectedItem) {
                is TvShow -> item
                is WatchHistoryItem -> if (item.isTv) TvShow(
                    id = item.id,
                    name = item.title,
                    overview = item.overview ?: "",
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    voteAverage = item.voteAverage,
                    firstAirDate = item.releaseDate ?: "",
                    genreIds = emptyList(),
                    popularity = 0.0
                ) else null
                else -> null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading && uiState.trendingTvShows.isEmpty()) {
            LoadingContent()
        } else if (uiState.error != null && uiState.trendingTvShows.isEmpty()) {
            ErrorContent(error = uiState.error!!)
        } else {
            HomeContent(
                uiState = uiState,
                selectedMovie = selectedMovie,
                selectedTvShow = selectedTvShow,
                scrollState = scrollState,
                selectedRoute = selectedRoute,
                onMovieClick = onMovieClick,
                onTvShowClick = onTvShowClick,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onNavItemClick = { route ->
                    selectedRoute = route
                    onNavigate(route)
                },
                onNavigate = onNavigate,
                onSelectItem = { viewModel.onItemSelected(it) },
                onSetLastClickedItemId = { viewModel.onItemClicked(it ?: 0) },
                lastClickedItemId = uiState.lastClickedItemId,
                onHeroInfoClick = {
                    selectedMovie?.let { onMovieClick(it.id) }
                        ?: selectedTvShow?.let { onTvShowClick(it.id) }
                },
                onHeroPlayClick = {
                    // Navigate to StreamLinksScreen to choose a stream provider
                    selectedMovie?.let { movie ->
                        movie.title?.let {
                            onNavigate(
                                Screen.StreamLinks.createRoute(
                                    tmdbId = movie.id,
                                    isTv = false,
                                    title = it,
                                    overview = movie.overview,
                                    posterPath = movie.posterPath,
                                    backdropPath = movie.backdropPath,
                                    voteAverage = movie.voteAverage,
                                    releaseDate = movie.releaseDate,
                                    timestamp = 0L
                                )
                            )
                        }
                    }
                    selectedTvShow?.let { tvShow ->
                        tvShow.name?.let {
                            onNavigate(
                                Screen.StreamLinks.createRoute(
                                    tmdbId = tvShow.id,
                                    isTv = true,
                                    title = it,
                                    overview = tvShow.overview,
                                    posterPath = tvShow.posterPath,
                                    backdropPath = tvShow.backdropPath,
                                    voteAverage = tvShow.voteAverage,
                                    releaseDate = tvShow.firstAirDate,
                                    timestamp = 0L
                                )
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LottieLoadingView(size = 300.dp)
    }
}

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

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    selectedMovie: Movie?,
    selectedTvShow: TvShow?,
    scrollState: androidx.compose.foundation.ScrollState,
    selectedRoute: String,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavItemClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSelectItem: (Any?) -> Unit,
    onSetLastClickedItemId: (Int?) -> Unit = {},
    lastClickedItemId: Int? = null,
    onHeroInfoClick: () -> Unit = {},
    onHeroPlayClick: () -> Unit = {}
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (lastClickedItemId == null) {
            firstItemFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HeroSection(
                movie = selectedMovie,
                tvShow = selectedTvShow,
                onInfoClick = onHeroInfoClick,
                onPlayClick = onHeroPlayClick
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Spacer to push content down slightly if needed,
                    // or just start the rows.
                    // Spacer(modifier = Modifier.height(5.dp))

                    // Now Playing Row - First row, gets initial focus
                    if (uiState.nowPlayingMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Now Playing",
                            items = uiState.nowPlayingMovies,
                            initialFocusRequester = firstItemFocusRequester,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSelectItem(movie)
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    ContentRow(
                        title = "TV Shows Trending Today",
                        items = uiState.trendingTvShows,
                        restoreFocusItemId = lastClickedItemId,
                        getItemId = { it.id },
                        onItemFocus = { tvShow -> onSelectItem(tvShow) },
                        onItemClick = { tvShow ->
                            onSelectItem(tvShow)
                            onSetLastClickedItemId(tvShow.id)
                            onTvShowClick(tvShow.id)
                        }
                    ) { tvShow, isFocused, onClick ->
                        TvShowCard(
                            tvShow = tvShow,
                            isSelected = isFocused,
                            onClick = onClick
                        )
                    }
                    // Content Row for Movies Trending This Week.
                    if (uiState.trendingMoviesThisWeek.isNotEmpty()) {
                        ContentRow(
                            title = "Movies Trending This Week",
                            items = uiState.trendingMoviesThisWeek,
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie -> onMovieClick(movie.id) }
                        ) { movie, isSelected, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isSelected,
                                onClick = onClick
                            )
                        }
                    }

                    ContentRow(
                        title = "Movies Trending Today",
                        items = uiState.trendingMovies,
                        restoreFocusItemId = lastClickedItemId,
                        getItemId = { it.id },
                        onItemFocus = { movie -> onSelectItem(movie) },
                        onItemClick = { movie ->
                            onSelectItem(movie)
                            onSetLastClickedItemId(movie.id)
                            onMovieClick(movie.id)
                        }
                    ) { movie, isFocused, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isFocused,
                            onClick = onClick
                        )
                    }

                    if (uiState.continueWatching.isNotEmpty()) {
                        ContentRow(
                            title = "Continue Watching",
                            items = uiState.continueWatching,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { item -> onSelectItem(item) },
                            onItemClick = { item ->
                                onSetLastClickedItemId(item.id)
                                if (item.isTv) onTvShowClick(item.id) else onMovieClick(item.id)
                            }
                        ) { item, isFocused, onClick ->
                            if (item.isTv) {
                                TvShowCard(
                                    tvShow = TvShow(
                                        id = item.id,
                                        name = item.title,
                                        overview = item.overview ?: "",
                                        posterPath = item.posterPath,
                                        backdropPath = item.backdropPath,
                                        voteAverage = item.voteAverage,
                                        firstAirDate = item.releaseDate ?: "",
                                        genreIds = emptyList(),
                                        popularity = 0.0
                                    ),
                                    isSelected = isFocused,
                                    onClick = onClick
                                )
                            } else {
                                MovieCard(
                                    movie = Movie(
                                        id = item.id,
                                        title = item.title,
                                        overview = item.overview ?: "",
                                        posterPath = item.posterPath,
                                        backdropPath = item.backdropPath,
                                        voteAverage = item.voteAverage,
                                        releaseDate = item.releaseDate ?: "",
                                        genreIds = emptyList(),
                                        popularity = 0.0
                                    ),
                                    isSelected = isFocused,
                                    onClick = onClick
                                )
                            }
                        }
                    }

                    if (uiState.popularNetworks.isNotEmpty()) {
                        NetworkRow(
                            title = "Popular Networks",
                            items = uiState.popularNetworks,
                            restoreFocusItemId = lastClickedItemId,  // Added
                            onItemClick = { network ->
                                onSetLastClickedItemId(network.id)
                                onNavigate("media_list/network/${network.id}/${network.name}")
                            }
                        )
                    }

                    if (uiState.popularCompanies.isNotEmpty()) {
                        NetworkRow(
                            title = "Popular Companies",
                            items = uiState.popularCompanies,
                            restoreFocusItemId = lastClickedItemId,  // Added
                            onItemClick = { company ->
                                onSetLastClickedItemId(company.id)
                                onNavigate("media_list/company/${company.id}/${company.name}")
                            }
                        )
                    }

                    ContentRow(
                        title = "Top Rated Movies",
                        items = uiState.latestMovies,
                        restoreFocusItemId = lastClickedItemId,
                        getItemId = { it.id },
                        onItemFocus = { movie -> onSelectItem(movie) },
                        onItemClick = { movie ->
                            onSetLastClickedItemId(movie.id)
                            onMovieClick(movie.id)
                        }
                    ) { movie, isFocused, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isFocused,
                            onClick = onClick
                        )
                    }

                    ContentRow(
                        title = "Top Rated TV Shows",
                        items = uiState.topTvShows,
                        restoreFocusItemId = lastClickedItemId,
                        getItemId = { it.id },
                        onItemFocus = { tvShow -> onSelectItem(tvShow) },
                        onItemClick = { tvShow ->
                            onSetLastClickedItemId(tvShow.id)
                            onTvShowClick(tvShow.id)
                        }
                    ) { tvShow, isFocused, onClick ->
                        TvShowCard(
                            tvShow = tvShow,
                            isSelected = isFocused,
                            onClick = onClick
                        )
                    }

                    if (uiState.oscarWinners2026.isNotEmpty()) {
                        ContentRow(
                            title = "2026 Oscar winners",
                            items = uiState.oscarWinners2026,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.hallmarkMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Hallmark Movies",
                            items = uiState.hallmarkMovies,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.trueStoryMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Movies Based on True Stories",
                            items = uiState.trueStoryMovies,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.bestSitcoms.isNotEmpty()) {
                        ContentRow(
                            title = "Best Sitcoms Ever",
                            items = uiState.bestSitcoms,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { tvShow -> onSelectItem(tvShow) },
                            onItemClick = { tvShow ->
                                onSetLastClickedItemId(tvShow.id)
                                onTvShowClick(tvShow.id)
                            }
                        ) { tvShow, isFocused, onClick ->
                            TvShowCard(
                                tvShow = tvShow,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.bestClassics.isNotEmpty()) {
                        ContentRow(
                            title = "Best movie classics",
                            items = uiState.bestClassics,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.spyMovies.isNotEmpty()) {
                        ContentRow(
                            title = "CIA & Mossad Spies",
                            items = uiState.spyMovies,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.stathamMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Jason Statham Movies",
                            items = uiState.stathamMovies,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
                        ) { movie, isFocused, onClick ->
                            MovieCard(
                                movie = movie,
                                isSelected = isFocused,
                                onClick = onClick
                            )
                        }
                    }

                    if (uiState.timeTravelMovies.isNotEmpty()) {
                        ContentRow(
                            title = "Time Travel Movies",
                            items = uiState.timeTravelMovies,
                            restoreFocusItemId = lastClickedItemId,
                            getItemId = { it.id },
                            onItemFocus = { movie -> onSelectItem(movie) },
                            onItemClick = { movie ->
                                onSetLastClickedItemId(movie.id)
                                onMovieClick(movie.id)
                            }
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

                // Top gradient overlay to blend with HeroSection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    BackgroundDark,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        TopBar(
            selectedRoute = selectedRoute,
            onNavItemClick = onNavItemClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )
    }
}
