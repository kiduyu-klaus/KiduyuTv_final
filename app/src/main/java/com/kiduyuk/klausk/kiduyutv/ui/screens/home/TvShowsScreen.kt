package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@Composable
fun TvShowsScreen(
    onTvShowClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopBar(
            selectedRoute = "tv_shows",
            onNavItemClick = { /* Handle navigation */ }
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "TV Shows",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(48.dp)
                )

                // Trending TV Shows
                ContentRow(
                    title = "Trending TV Shows",
                    items = uiState.trendingTvShows,
                    onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                ) { tvShow, isSelected, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }

                // Top Rated TV Shows
                ContentRow(
                    title = "Top Rated TV Shows",
                    items = uiState.topTvShows,
                    onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                ) { tvShow, isSelected, onClick ->
                    TvShowCard(
                        tvShow = tvShow,
                        isSelected = isSelected,
                        onClick = onClick
                    )
                }
            }
        }
    }
}
