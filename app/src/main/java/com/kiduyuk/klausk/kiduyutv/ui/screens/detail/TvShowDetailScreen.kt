package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.data.model.Season
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel

@Composable
fun TvShowDetailScreen(
    tvId: Int,
    onBackClick: () -> Unit,
    onTvShowClick: (Int) -> Unit,
    onEpisodesClick: (tvId: Int, tvShowName: String, totalSeasons: Int) -> Unit = { _, _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(tvId) {
        viewModel.loadTvShowDetail(tvId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else if (uiState.tvShowDetail != null) {
            val tvShow = uiState.tvShowDetail!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Hero Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                ) {
                    // Background Image
                    if (tvShow.backdropPath != null) {
                        AsyncImage(
                            model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${tvShow.backdropPath}",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(10.dp)
                        )
                    }

                    // Gradient Overlay
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

                    // Back Button
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = tvShow.name,
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = PrimaryRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = String.format("%.1f", tvShow.voteAverage),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }

                            Text(
                                text = tvShow.firstAirDate?.take(4) ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )

                            if (tvShow.numberOfSeasons != null) {
                                Text(
                                    text = "${tvShow.numberOfSeasons} Seasons",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary
                                )
                            }

                            if (tvShow.numberOfEpisodes != null) {
                                Text(
                                    text = "${tvShow.numberOfEpisodes} Episodes",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Genres
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tvShow.genres?.take(3)?.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = GenrePill
                                ) {
                                    Text(
                                        text = genre.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Overview
                        Text(
                            text = tvShow.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { /* Play */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryRed
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Play Now",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onEpisodesClick(
                                        tvId,
                                        tvShow.name,
                                        tvShow.numberOfSeasons ?: 1
                                    )
                                },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Episodes",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.toggleMyList() },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isInMyList) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Seasons Section
                if (uiState.seasons.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Seasons",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.seasons) { season ->
                                SeasonCard(
                                    season = season,
                                    onClick = {
                                        viewModel.loadSeasonEpisodes(tvId, season.seasonNumber)
                                    }
                                )
                            }
                        }
                    }
                }

                // Episodes Section
                if (uiState.episodes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        uiState.episodes.forEach { episode ->
                            EpisodeCard(episode = episode)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Similar TV Shows
                if (uiState.similarTvShows.isNotEmpty()) {
                    ContentRow(
                        title = "Others Also Watched",
                        items = uiState.similarTvShows,
                        onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                    ) { tvShow, isSelected, onClick ->
                        TvShowCard(
                            tvShow = tvShow,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "Failed to load TV show details",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SeasonCard(
    season: Season,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(80.dp)
            .background(
                color = CardDark,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = season.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            if (season.episodeCount != null) {
                Text(
                    text = "${season.episodeCount} Episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(
                color = CardDark,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Episode thumbnail
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(76.dp)
                .background(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            if (episode.stillPath != null) {
                AsyncImage(
                    model = "${TmdbApiService.IMAGE_BASE_URL}w300${episode.stillPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${episode.episodeNumber}. ${episode.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (episode.voteAverage != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = PrimaryRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = String.format("%.1f", episode.voteAverage),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = episode.overview ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2
            )
        }
    }
}
