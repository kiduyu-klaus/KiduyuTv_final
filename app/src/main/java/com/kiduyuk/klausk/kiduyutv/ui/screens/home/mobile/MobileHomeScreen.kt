package com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileBottomNavigation
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileNetworkCard
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.NetworkItem
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel

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

    // My List state
    val myList by MyListManager.myList.collectAsState()

    val selectedItemId = uiState.selectedItem?.let {
        when (it) {
            is Movie -> it.id
            is TvShow -> it.id
            is WatchHistoryItem -> it.id
            else -> null
        }
    } ?: 0
    val selectedItemType = when (uiState.selectedItem) {
        is Movie -> "movie"
        is TvShow -> "tv"
        is WatchHistoryItem -> if ((uiState.selectedItem as WatchHistoryItem).isTv) "tv" else "movie"
        else -> ""
    }
    val isSelectedItemInMyList = myList.any { item -> item.id == selectedItemId && item.type == selectedItemType }

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
                                val defaultProvider = SettingsManager(context).getDefaultProvider()
                                when (selectedItem) {
                                    is Movie -> {
                                        val directUrl = if (defaultProvider != SettingsManager.AUTO) {
                                            StreamLinksViewModel.resolveProviderUrl(
                                                providerName = defaultProvider,
                                                tmdbId = selectedItem.id,
                                                isTv = false,
                                                season = null,
                                                episode = null,
                                                timestamp = 0L
                                            )
                                        } else null
                                        if (directUrl != null) {
                                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                                putExtra("STREAM_URL", directUrl)
                                                putExtra("TMDB_ID", selectedItem.id)
                                                putExtra("IS_TV", false)
                                                putExtra("TITLE", selectedItem.title ?: "")
                                                putExtra("OVERVIEW", selectedItem.overview)
                                                putExtra("POSTER_PATH", selectedItem.posterPath)
                                                putExtra("BACKDROP_PATH", selectedItem.backdropPath)
                                                putExtra("VOTE_AVERAGE", selectedItem.voteAverage)
                                                putExtra("RELEASE_DATE", selectedItem.releaseDate)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            navController.navigate(
                                                Screen.MobileStreamLinks.createRoute(
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
                                        }
                                    }
                                    is TvShow -> {
                                        val directUrl = if (defaultProvider != SettingsManager.AUTO) {
                                            StreamLinksViewModel.resolveProviderUrl(
                                                providerName = defaultProvider,
                                                tmdbId = selectedItem.id,
                                                isTv = true,
                                                season = 1,
                                                episode = 1,
                                                timestamp = 0L
                                            )
                                        } else null
                                        if (directUrl != null) {
                                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                                putExtra("STREAM_URL", directUrl)
                                                putExtra("TMDB_ID", selectedItem.id)
                                                putExtra("IS_TV", true)
                                                putExtra("TITLE", selectedItem.name ?: "")
                                                putExtra("OVERVIEW", selectedItem.overview)
                                                putExtra("POSTER_PATH", selectedItem.posterPath)
                                                putExtra("BACKDROP_PATH", selectedItem.backdropPath)
                                                putExtra("VOTE_AVERAGE", selectedItem.voteAverage)
                                                putExtra("RELEASE_DATE", selectedItem.firstAirDate)
                                                putExtra("SEASON_NUMBER", 1)
                                                putExtra("EPISODE_NUMBER", 1)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            navController.navigate(
                                                Screen.MobileStreamLinks.createRoute(
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
                                        }
                                    }
                                    is WatchHistoryItem -> {
                                        val seasonNumber = selectedItem.seasonNumber ?: 1
                                        val episodeNumber = selectedItem.episodeNumber ?: 1
                                        val directUrl = if (defaultProvider != SettingsManager.AUTO) {
                                            StreamLinksViewModel.resolveProviderUrl(
                                                providerName = defaultProvider,
                                                tmdbId = selectedItem.id,
                                                isTv = selectedItem.isTv,
                                                season = if (selectedItem.isTv) seasonNumber else null,
                                                episode = if (selectedItem.isTv) episodeNumber else null,
                                                timestamp = selectedItem.playbackPosition
                                            )
                                        } else null
                                        if (directUrl != null) {
                                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                                putExtra("STREAM_URL", directUrl)
                                                putExtra("TMDB_ID", selectedItem.id)
                                                putExtra("IS_TV", selectedItem.isTv)
                                                putExtra("TITLE", selectedItem.title)
                                                putExtra("OVERVIEW", selectedItem.overview)
                                                putExtra("POSTER_PATH", selectedItem.posterPath)
                                                putExtra("BACKDROP_PATH", selectedItem.backdropPath)
                                                putExtra("VOTE_AVERAGE", selectedItem.voteAverage)
                                                putExtra("RELEASE_DATE", selectedItem.releaseDate)
                                                if (selectedItem.isTv) {
                                                    putExtra("SEASON_NUMBER", seasonNumber)
                                                    putExtra("EPISODE_NUMBER", episodeNumber)
                                                }
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            navController.navigate(
                                                Screen.MobileStreamLinks.createRoute(
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
                                    }
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
                            },
                            onAddToListClick = {
                                val selectedItem = uiState.selectedItem
                                val contextRef = context
                                when (selectedItem) {
                                    is Movie -> {
                                        val item = MyListItem(
                                            id = selectedItem.id,
                                            title = selectedItem.title ?: "",
                                            posterPath = selectedItem.posterPath,
                                            type = "movie",
                                            voteAverage = selectedItem.voteAverage
                                        )
                                        MyListManager.toggleItem(item, contextRef)
                                    }
                                    is TvShow -> {
                                        val item = MyListItem(
                                            id = selectedItem.id,
                                            title = selectedItem.name ?: "",
                                            posterPath = selectedItem.posterPath,
                                            type = "tv",
                                            voteAverage = selectedItem.voteAverage
                                        )
                                        MyListManager.toggleItem(item, contextRef)
                                    }
                                    is WatchHistoryItem -> {
                                        val item = MyListItem(
                                            id = selectedItem.id,
                                            title = selectedItem.title,
                                            posterPath = selectedItem.posterPath,
                                            type = if (selectedItem.isTv) "tv" else "movie",
                                            voteAverage = selectedItem.voteAverage
                                        )
                                        MyListManager.toggleItem(item, contextRef)
                                    }
                                }
                            },
                            isInMyList = isSelectedItemInMyList
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
    onAddToListClick: () -> Unit,
    isInMyList: Boolean,
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
        "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$backdropPath"
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // Background Image
        if (!backdropPath.isNullOrEmpty()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(CardDark))
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
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
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // My List Button
                OutlinedButton(
                    onClick = onAddToListClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("My List", style = MaterialTheme.typography.labelMedium)
                }
                // Play Button
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", style = MaterialTheme.typography.labelMedium)
                }

                // Info Button
                OutlinedButton(
                    onClick = onInfoClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Info", style = MaterialTheme.typography.labelMedium)
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is Movie -> {
                        MobileMovieCard(movie = item, onClick = { onItemClick(item) })
                    }
                    is TvShow -> {
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
                    is NetworkItem -> {
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
        contentAlignment = Alignment.Center
    ) {
        LottieLoadingView(size = 200.dp)
    }
}
