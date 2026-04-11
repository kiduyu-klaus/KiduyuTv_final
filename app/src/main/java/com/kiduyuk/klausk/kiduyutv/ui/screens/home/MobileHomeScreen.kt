package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import android.net.Uri

@Composable
fun MobileHomeScreen(
    navController: NavController,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(Unit) {
        viewModel.loadHomeContent(context)
    }

    // Get selected item for HeroSection
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

    Scaffold(
        bottomBar = { MobileBottomNavigation(navController, currentRoute) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            if (uiState.isLoading && uiState.trendingTvShows.isEmpty()) {
                LoadingContent()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Hero Section
                    item {
                        MobileHeroSection(
                            movie = selectedMovie,
                            tvShow = selectedTvShow,
                            onPlayClick = {
                                val selectedItem = uiState.selectedItem
                                when (selectedItem) {
                                    is Movie -> navController.navigate(
                                        Screen.StreamLinks.createRoute(
                                            tmdbId = selectedItem.id,
                                            isTv = false,
                                            title = selectedItem.title ?: "",
                                            overview = selectedItem.overview,
                                            posterPath = selectedItem.posterPath,
                                            backdropPath = selectedItem.backdropPath,
                                            voteAverage = selectedItem.voteAverage,
                                            releaseDate = selectedItem.releaseDate
                                        )
                                    )
                                    is TvShow -> navController.navigate(
                                        Screen.StreamLinks.createRoute(
                                            tmdbId = selectedItem.id,
                                            isTv = true,
                                            title = selectedItem.name ?: "",
                                            overview = selectedItem.overview,
                                            posterPath = selectedItem.posterPath,
                                            backdropPath = selectedItem.backdropPath,
                                            voteAverage = selectedItem.voteAverage,
                                            releaseDate = selectedItem.firstAirDate
                                        )
                                    )
                                    is WatchHistoryItem -> navController.navigate(
                                        Screen.StreamLinks.createRoute(
                                            tmdbId = selectedItem.id,
                                            isTv = selectedItem.isTv,
                                            title = selectedItem.title,
                                            overview = selectedItem.overview,
                                            posterPath = selectedItem.posterPath,
                                            backdropPath = selectedItem.backdropPath,
                                            voteAverage = selectedItem.voteAverage,
                                            releaseDate = selectedItem.releaseDate,
                                            season = selectedItem.seasonNumber,
                                            episode = selectedItem.episodeNumber
                                        )
                                    )
                                }
                            },
                            onInfoClick = {
                                val selectedItem = uiState.selectedItem
                                when (selectedItem) {
                                    is Movie -> onMovieClick(selectedItem.id)
                                    is TvShow -> onTvShowClick(selectedItem.id)
                                    is WatchHistoryItem -> {
                                        if (selectedItem.isTv) onTvShowClick(selectedItem.id)
                                        else onMovieClick(selectedItem.id)
                                    }
                                }
                            }
                        )
                    }

                    // Continue Watching
                    if (uiState.continueWatching.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Continue Watching", uiState.continueWatching) { historyItem ->
                                if (historyItem.isTv) onTvShowClick(historyItem.id)
                                else onMovieClick(historyItem.id)
                            }
                        }
                    }

                    // Now Playing Movies
                    if (uiState.nowPlayingMovies.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Now Playing", uiState.nowPlayingMovies) { movie ->
                                onMovieClick(movie.id)
                            }
                        }
                    }

                    // Trending TV Shows
                    if (uiState.trendingTvShows.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Trending TV Shows", uiState.trendingTvShows) { tvShow ->
                                onTvShowClick(tvShow.id)
                            }
                        }
                    }

                    // Popular Movies
                    if (uiState.trendingMovies.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Popular Movies", uiState.trendingMovies) { movie ->
                                onMovieClick(movie.id)
                            }
                        }
                    }

                    // Top Rated Movies
                    if (uiState.latestMovies.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Top Rated Movies", uiState.latestMovies) { movie ->
                                onMovieClick(movie.id)
                            }
                        }
                    }

                    // Oscar Winners
                    if (uiState.oscarWinners2026.isNotEmpty()) {
                        item {
                            MobileCategoryRow("2026 Oscar Winners", uiState.oscarWinners2026) { movie ->
                                onMovieClick(movie.id)
                            }
                        }
                    }

                    // Time Travel Movies
                    if (uiState.timeTravelMovies.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Time Travel Movies", uiState.timeTravelMovies) { movie ->
                                onMovieClick(movie.id)
                            }
                        }
                    }

                    // Time Travel TV Shows
                    if (uiState.timeTravelTvShows.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Time Travel TV Shows", uiState.timeTravelTvShows) { tvShow ->
                                onTvShowClick(tvShow.id)
                            }
                        }
                    }

                    // Popular Networks
                    if (uiState.popularNetworks.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Popular Networks", uiState.popularNetworks) { network ->
                                onNavigate("media_list/${network.type}/${network.id}/${Uri.encode(network.name)}")
                            }
                        }
                    }

                    // Popular Companies
                    if (uiState.popularCompanies.isNotEmpty()) {
                        item {
                            MobileCategoryRow("Production Companies", uiState.popularCompanies) { company ->
                                onNavigate("media_list/${company.type}/${company.id}/${Uri.encode(company.name)}")
                            }
                        }
                    }

                    //Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun MobileHeroSection(
    movie: Movie?,
    tvShow: TvShow?,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.45f).dp

    val isMovie = movie != null
    val backdropPath = if (isMovie) {
        movie?.backdropPath ?: movie?.posterPath
    } else {
        tvShow?.backdropPath ?: tvShow?.posterPath
    }
    val title = (if (isMovie) movie?.title else tvShow?.name) ?: ""
    val backdropUrl = if (!backdropPath.isNullOrEmpty()) {
        "${com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService.IMAGE_BASE_URL}${com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService.BACKDROP_SIZE}$backdropPath"
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // Background Image
        if (!backdropPath.isNullOrEmpty()) {
            coil.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark))
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            BackgroundDark.copy(alpha = 0.7f),
                            BackgroundDark
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            androidx.compose.material3.Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.Button(
                    onClick = onPlayClick,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
                    )
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    androidx.compose.material3.Text("Play")
                }

                androidx.compose.material3.OutlinedButton(onClick = onInfoClick) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Info,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    androidx.compose.material3.Text("Info")
                }
            }
        }
    }
}

@Composable
fun <T> MobileCategoryRow(
    title: String,
    items: List<T>,
    onItemClick: (T) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        androidx.compose.material3.Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is com.kiduyuk.klausk.kiduyutv.data.model.Movie -> {
                        MobileMovieCard(movie = item, onClick = { onItemClick(item) })
                    }
                    is com.kiduyuk.klausk.kiduyutv.data.model.TvShow -> {
                        MobileTvShowCard(tvShow = item, onClick = { onItemClick(item) })
                    }
                    is WatchHistoryItem -> {
                        MobileMovieCard(
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
                            onClick = { onItemClick(item) }
                        )
                    }
                    is com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem -> {
                        MobileNetworkCard(networkItem = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        LottieLoadingView(size = 200.dp)
    }
}
