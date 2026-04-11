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
fun GenreContentScreen(
    mediaType: String,
    genreId: Int,
    genreName: String,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Filter items based on genreId
    val filteredItems = remember(uiState, genreId, mediaType) {
        if (mediaType == "movie") {
            // Collect all movies from different categories and filter
            val allMovies = (uiState.trendingMovies + uiState.latestMovies + uiState.trendingMoviesThisWeek + 
                            uiState.oscarWinners2026 + uiState.hallmarkMovies + uiState.trueStoryMovies + 
                            uiState.spyMovies + uiState.stathamMovies + uiState.timeTravelMovies).distinctBy { it.id }
            allMovies.filter { it.genreIds?.contains(genreId) == true }
        } else {
            // Collect all TV shows from different categories and filter
            val allTvShows = (uiState.trendingTvShows + uiState.topTvShows + uiState.bestSitcoms + 
                             uiState.timeTravelTvShows).distinctBy { it.id }
            allTvShows.filter { it.genreIds?.contains(genreId) == true }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = genreName, color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No $mediaType content found for $genreName", color = TextPrimary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems) { item ->
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
