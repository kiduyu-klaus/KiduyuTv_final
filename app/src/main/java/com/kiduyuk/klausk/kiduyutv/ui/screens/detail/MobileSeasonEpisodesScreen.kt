package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel

/**
 * Mobile version of SeasonEpisodesScreen.
 * Displays episodes of a TV show season in a mobile-friendly vertical list.
 *
 * @param tvShowId The ID of the TV show.
 * @param tvShowName The name of the TV show.
 * @param totalSeasons The total number of seasons.
 * @param onBackClick Callback when back button is clicked.
 * @param onPlayClick Callback when an episode is selected to play.
 * @param viewModel The DetailViewModel instance.
 */
@Composable
fun MobileSeasonEpisodesScreen(
    tvShowId: Int,
    tvShowName: String,
    totalSeasons: Int,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedSeason by remember { mutableIntStateOf(1) }

    LaunchedEffect(tvShowId, selectedSeason) {
        viewModel.loadSeasonDetail(tvShowId, selectedSeason)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tvShowName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Season $selectedSeason",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Season selector
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Select Season",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Horizontal season selector
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(totalSeasons) { index ->
                            val seasonNum = index + 1
                            Button(
                                onClick = { selectedSeason = seasonNum },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedSeason == seasonNum) PrimaryRed else CardDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(50.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "S$seasonNum",
                                    color = if (selectedSeason == seasonNum) Color.White else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Episodes list
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryRed)
                        }
                    }
                } else if (uiState.episodes.isNotEmpty()) {
                    items(uiState.episodes) { episode ->
                        MobileEpisodeCard(
                            episode = episode,
                            seasonNumber = selectedSeason,
                            onEpisodeClick = { season, episodeNum ->
                                val route = com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen.StreamLinks.createRoute(
                                    tmdbId = tvShowId,
                                    isTv = true,
                                    title = "$tvShowName - S${season}E${episodeNum}",
                                    overview = episode.overview,
                                    posterPath = episode.stillPath,
                                    backdropPath = null,
                                    voteAverage = episode.voteAverage,
                                    releaseDate = episode.airDate,
                                    season = season,
                                    episode = episodeNum
                                )
                                onPlayClick(route)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No episodes available",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Mobile-optimized episode card.
 *
 * @param episode The Episode to display.
 * @param seasonNumber The season number.
 * @param onEpisodeClick Callback when the episode is clicked.
 */
@Composable
private fun MobileEpisodeCard(
    episode: Episode,
    seasonNumber: Int,
    onEpisodeClick: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable { onEpisodeClick(seasonNumber, episode.episodeNumber) }
            .padding(12.dp)
    ) {
        // Episode thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardDark)
        ) {
            if (episode.stillPath != null) {
                AsyncImage(
                    model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.STILL_SIZE}${episode.stillPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Play icon overlay
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Episode title
        Text(
            text = "${episode.episodeNumber}. ${episode.name}",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Episode metadata
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if ((episode.voteAverage ?: 0.0) > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = PrimaryRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = String.format("%.1f", episode.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }

            if (episode.runtime != null) {
                Text(
                    text = "${episode.runtime}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (episode.airDate != null) {
                Text(
                    text = episode.airDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Episode overview
        if (episode.overview != null) {
            Text(
                text = episode.overview,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }
    }
}
