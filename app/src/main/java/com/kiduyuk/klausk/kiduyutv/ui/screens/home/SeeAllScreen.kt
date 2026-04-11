package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllScreen(
    category: String,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val title = when (category) {
        "trending_movies" -> "Trending Movies"
        "latest_movies" -> "Latest Releases"
        "box_office" -> "Box Office"
        "trending_tv" -> "Trending TV Shows"
        "watched_tv" -> "Watched TV Shows"
        "popular_tv" -> "Popular TV Shows"
        "favorite_tv" -> "All Time Favorite TV Shows"
        else -> "All Content"
    }

    val items = when (category) {
        "trending_movies" -> uiState.trendingMovies
        "latest_movies" -> if (uiState.trendingMoviesThisWeek.isNotEmpty()) uiState.trendingMoviesThisWeek else uiState.latestMovies
        "box_office" -> uiState.latestMovies
        "trending_tv" -> uiState.trendingTvShows
        "watched_tv" -> uiState.continueWatching.filter { it.isTv }.map {
            TvShow(id = it.id, name = it.title, overview = it.overview ?: "", posterPath = it.posterPath, backdropPath = it.backdropPath, voteAverage = it.voteAverage, firstAirDate = it.releaseDate ?: "", genreIds = emptyList(), popularity = 0.0)
        }
        "popular_tv" -> uiState.topTvShows
        "favorite_tv" -> uiState.bestSitcoms
        else -> emptyList<Any>()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No content available", color = TextPrimary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items) { item ->
                        when (item) {
                            is Movie -> MobileMovieCard(movie = item, onClick = { onMovieClick(item.id) })
                            is TvShow -> MobileTvShowCard(tvShow = item, onClick = { onTvShowClick(item.id) })
                        }
                    }
                }
            }
        }
    }
}
