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
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem

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

    val selectedMovie by remember(uiState.selectedItem) {
        derivedStateOf { uiState.selectedItem as? Movie }
    }
    val selectedTvShow by remember(uiState.selectedItem) {
        derivedStateOf { uiState.selectedItem as? TvShow }
    }

    val trendingTvShows = uiState.trendingTvShows
    val trendingMovies = uiState.trendingMovies
    val continueWatching = uiState.continueWatching
    val popularNetworks = uiState.popularNetworks
    val popularCompanies = uiState.popularCompanies
    val latestMovies = uiState.latestMovies
    val topTvShows = uiState.topTvShows
    val oscarMovies = uiState.oscarMovies
    val oscarWinners2026 = uiState.oscarWinners2026
    val hallmarkMovies = uiState.hallmarkMovies
    val myList = uiState.myList

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading) {
            LoadingContent()
        } else if (uiState.error != null) {
            ErrorContent(error = uiState.error!!)
        } else {
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
                oscarWinners2026 = oscarWinners2026,
                hallmarkMovies = hallmarkMovies,
                myList = myList,
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
                onSelectItem = { viewModel.selectItem(it) },
                onSetLastClickedItemId = { viewModel.setLastClickedItemId(it) },
                lastClickedItemId = uiState.lastClickedItemId
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
    oscarWinners2026: List<Movie>,
    hallmarkMovies: List<Movie>,
    myList: List<MyListItem>,
    scrollState: androidx.compose.foundation.ScrollState,
    selectedRoute: String,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavItemClick: (String) -> Unit,
    onSelectItem: (Any) -> Unit,
    onSetLastClickedItemId: (Int?) -> Unit = {},
    lastClickedItemId: Int? = null
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
                tvShow = selectedTvShow
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                ContentRow(
                    title = "TV Shows Trending Today",
                    items = trendingTvShows,
                    initialFocusRequester = firstItemFocusRequester,
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

                ContentRow(
                    title = "Movies Trending Today",
                    items = trendingMovies,
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

                if (continueWatching.isNotEmpty()) {
                    ContentRow(
                        title = "Continue Watching",
                        items = continueWatching,
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

                if (popularNetworks.isNotEmpty()) {
                    NetworkRow(
                        title = "Popular Networks",
                        items = popularNetworks,
                        onItemClick = { network ->
                            onSetLastClickedItemId(network.id)
                        }
                    )
                }

                if (popularCompanies.isNotEmpty()) {
                    NetworkRow(
                        title = "Popular Companies",
                        items = popularCompanies,
                        onItemClick = { company ->
                            onSetLastClickedItemId(company.id)
                        }
                    )
                }

                ContentRow(
                    title = "Latest Movies",
                    items = latestMovies,
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
                    items = topTvShows,
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

                if (oscarWinners2026.isNotEmpty()) {
                    ContentRow(
                        title = "2026 Oscar winners",
                        items = oscarWinners2026,
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

                if (hallmarkMovies.isNotEmpty()) {
                    ContentRow(
                        title = "Hallmark Movies",
                        items = hallmarkMovies,
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

                if (oscarMovies.isNotEmpty()) {
                    ContentRow(
                        title = "Oscar Movies",
                        items = oscarMovies,
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

                Spacer(modifier = Modifier.height(32.dp))
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

        TopBar(
            selectedRoute = selectedRoute,
            onNavItemClick = onNavItemClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    KiduyuTvTheme {
        HomeScreen(
            onMovieClick = {},
            onTvShowClick = {},
            onNavigate = {},
            onSearchClick = {},
            onSettingsClick = {}
        )
    }
}
