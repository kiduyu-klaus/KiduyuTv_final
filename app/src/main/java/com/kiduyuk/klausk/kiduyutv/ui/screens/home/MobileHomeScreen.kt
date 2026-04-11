package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kiduyuk.klausk.kiduyutv.ui.components.*
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

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
                    item {
                        // Minimal Hero Section for Mobile
                        Box(modifier = Modifier.height(250.dp)) {
                            // Reuse existing HeroSection or a simplified one
                            Text("Welcome to Kiduyu TV", 
                                 style = MaterialTheme.typography.headlineMedium,
                                 color = TextPrimary,
                                 modifier = Modifier.padding(16.dp))
                        }
                    }

                    item {
                        MobileCategoryRow("Now Playing", uiState.nowPlayingMovies) { movie ->
                            onMovieClick(movie.id)
                        }
                    }

                    item {
                        MobileCategoryRow("Trending TV Shows", uiState.trendingTvShows) { tvShow ->
                            onTvShowClick(tvShow.id)
                        }
                    }

                    item {
                        MobileCategoryRow("Popular Movies", uiState.trendingMovies) { movie ->
                            onMovieClick(movie.id)
                        }
                    }
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
                    is com.kiduyuk.klausk.kiduyutv.data.model.Movie -> {
                        MobileMovieCard(movie = item, onClick = { onItemClick(item) })
                    }
                    is com.kiduyuk.klausk.kiduyutv.data.model.TvShow -> {
                        MobileTvShowCard(tvShow = item, onClick = { onItemClick(item) })
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
