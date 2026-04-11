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
fun MobileMoviesScreen(
    navController: NavController,
    onMovieClick: (Int) -> Unit,
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
            if (uiState.isLoading && uiState.trendingMovies.isEmpty()) {
                LoadingContent()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        MobileHeader(
                            title = "Movies",
                            onGenresClick = { navController.navigate("genres/movie") }
                        )
                    }

                    // Trending Section
                    if (uiState.trendingMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Trending",
                                onSeeAllClick = { navController.navigate("see_all/trending_movies") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.trendingMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Latest Releases (using trendingMoviesThisWeek or latestMovies)
                    val latestReleases = if (uiState.trendingMoviesThisWeek.isNotEmpty()) uiState.trendingMoviesThisWeek else uiState.latestMovies
                    if (latestReleases.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Latest Releases",
                                onSeeAllClick = { navController.navigate("see_all/latest_movies") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(latestReleases) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Box Office (using topRatedMovies or popularMovies)
                    if (uiState.latestMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Box Office",
                                onSeeAllClick = { navController.navigate("see_all/box_office") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.latestMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
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
fun MobileHeader(
    title: String,
    onGenresClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Genres",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF4285F4), // Blue color from design
            modifier = Modifier.clickable { onGenresClick() }
        )
    }
}

@Composable
fun MobileSectionHeader(
    title: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "See All",
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryRed,
            modifier = Modifier.clickable { onSeeAllClick() }
        )
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
