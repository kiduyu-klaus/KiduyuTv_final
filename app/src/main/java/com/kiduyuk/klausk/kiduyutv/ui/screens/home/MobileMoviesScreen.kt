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
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
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
                                title = "Trending Now",
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

                    // Latest Releases
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

                    // Oscar Winners 2026
                    if (uiState.oscarWinners2026.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Oscar Winners 2026",
                                onSeeAllClick = { navController.navigate("see_all/oscar_winners") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.oscarWinners2026) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Now Playing in Theaters
                    if (uiState.nowPlayingMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Now Playing",
                                onSeeAllClick = { navController.navigate("see_all/now_playing") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.nowPlayingMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Hallmark Movies
                    if (uiState.hallmarkMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Hallmark Movies",
                                onSeeAllClick = { navController.navigate("see_all/hallmark") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.hallmarkMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // True Story Movies
                    if (uiState.trueStoryMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Based on True Stories",
                                onSeeAllClick = { navController.navigate("see_all/true_story") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.trueStoryMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Best Classics
                    if (uiState.bestClassics.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Classic Cinema",
                                onSeeAllClick = { navController.navigate("see_all/classics") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.bestClassics) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // CIA & Mossad Spy Movies
                    if (uiState.spyMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Spy & Espionage",
                                onSeeAllClick = { navController.navigate("see_all/spy_movies") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.spyMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Jason Statham Action Movies
                    if (uiState.stathamMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Jason Statham Action",
                                onSeeAllClick = { navController.navigate("see_all/statham_movies") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.stathamMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Time Travel Movies
                    if (uiState.timeTravelMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Time Travel Adventures",
                                onSeeAllClick = { navController.navigate("see_all/time_travel_movies") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.timeTravelMovies) { movie ->
                                    MobileMovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                                }
                            }
                        }
                    }

                    // Top Rated / Box Office
                    if (uiState.latestMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Top Rated",
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

                    // Continue Watching for Movies
                    val continueWatchingMovies = uiState.continueWatching.filter { !it.isTv }
                    if (continueWatchingMovies.isNotEmpty()) {
                        item {
                            MobileSectionHeader(
                                title = "Continue Watching",
                                onSeeAllClick = { navController.navigate("see_all/continue_watching") }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(continueWatchingMovies) { historyItem ->
                                    MobileMovieCard(
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
                                        onClick = { onMovieClick(historyItem.id) }
                                    )
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
            color = Color(0xFF4285F4),
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
