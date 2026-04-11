package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@Composable
fun MobileTvShowsScreen(
    navController: NavController,
    onTvShowClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(Unit) {
        viewModel.loadHomeContent(context)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        MobileHeader(
                            title = "TV Shows",
                            onGenresClick = { navController.navigate("genres/tv") }
                        )
                    }

                    // Trending Section
                    if (uiState.trendingTvShows.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Trending",
                                onSeeAllClick = { navController.navigate("see_all/trending_tv") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.trendingTvShows) { tvShow ->
                                    MobileTvShowCard(tvShow = tvShow, onClick = { onTvShowClick(tvShow.id) })
                                }
                            }
                        }
                    }

                    // Watched (using continueWatching or topTvShows)
                    val watchedShows = uiState.continueWatching.filter { it.isTv }
                    if (watchedShows.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Watched",
                                onSeeAllClick = { navController.navigate("see_all/watched_tv") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(watchedShows) { historyItem ->
                                    MobileTvShowCard(
                                        tvShow = com.kiduyuk.klausk.kiduyutv.data.model.TvShow(
                                            id = historyItem.id,
                                            name = historyItem.title,
                                            overview = historyItem.overview ?: "",
                                            posterPath = historyItem.posterPath,
                                            backdropPath = historyItem.backdropPath,
                                            voteAverage = historyItem.voteAverage,
                                            firstAirDate = historyItem.releaseDate ?: "",
                                            genreIds = emptyList(),
                                            popularity = 0.0
                                        ),
                                        onClick = { onTvShowClick(historyItem.id) }
                                    )
                                }
                            }
                        }
                    }

                    // IMDB: Popular TV Shows (using topTvShows)
                    if (uiState.topTvShows.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "IMDB: Popular TV Shows",
                                onSeeAllClick = { navController.navigate("see_all/popular_tv") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.topTvShows) { tvShow ->
                                    MobileTvShowCard(tvShow = tvShow, onClick = { onTvShowClick(tvShow.id) })
                                }
                            }
                        }
                    }

                    // All Time Favorite TV Shows (using bestSitcoms)
                    if (uiState.bestSitcoms.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "All Time Favorite TV Shows",
                                onSeeAllClick = { navController.navigate("see_all/favorite_tv") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.bestSitcoms) { tvShow ->
                                    MobileTvShowCard(tvShow = tvShow, onClick = { onTvShowClick(tvShow.id) })
                                }
                            }
                        }
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
