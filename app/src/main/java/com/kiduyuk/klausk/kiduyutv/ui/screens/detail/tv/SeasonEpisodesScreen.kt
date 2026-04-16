package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.data.model.Season
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel

/**
 * Composable function for the Season and Episodes screen.
 * Redesigned to match the reference layout with two-column design:
 * Left column shows show info and seasons list.
 * Right column shows episode list with thumbnails and play buttons.
 *
 * @param tvShowId The ID of the TV show.
 * @param tvShowName The name of the TV show.
 * @param tvShowYear The release year of the TV show.
 * @param totalSeasons The total number of seasons for the TV show.
 * @param onPlayClick Lambda to navigate to stream links.
 * @param viewModel The [DetailViewModel] instance providing data for the screen.
 */
@Composable
fun SeasonEpisodesScreen(
    tvShowId: Int,
    tvShowName: String,
    tvShowYear: String = "",
    totalSeasons: Int,
    onPlayClick: (String) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var seasonsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(tvShowId) {
        if (!seasonsLoaded || uiState.seasons.isEmpty()) {
            viewModel.loadSeasons(tvShowId, totalSeasons)
            seasonsLoaded = true
        }
        viewModel.loadSeasonEpisodes(tvShowId, 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading && uiState.seasons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LottieLoadingView(size = 300.dp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 40.dp)
            ) {
                // Left Column: Show info and Seasons list (30% width)
                Column(
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                ) {
                    // Header: Show title (bold, uppercase)
                    Text(
                        text = tvShowName.uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Year and seasons count
                    Text(
                        text = if (tvShowYear.isNotEmpty()) "$tvShowYear • $totalSeasons seasons" else "$totalSeasons seasons",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Seasons label
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Seasons list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(uiState.seasons) { index, season ->
                            SeasonListItem(
                                season = season,
                                isSelected = index == selectedSeasonIndex,
                                onSeasonSelected = {
                                    if (selectedSeasonIndex != index) {
                                        selectedSeasonIndex = index
                                        viewModel.loadSeasonEpisodes(tvShowId, season.seasonNumber)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(48.dp))

                // Right Column: Episodes list (70% width)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (uiState.isLoading && uiState.episodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LottieLoadingView(size = 200.dp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            itemsIndexed(uiState.episodes) { index, episode ->
                                EpisodeListItem(
                                    episode = episode,
                                    seasonNumber = selectedSeasonIndex + 1,
                                    episodeIndex = index,
                                    onEpisodeClick = { sNum, eNum ->
                                        val defaultProvider = SettingsManager(context).getDefaultProvider()
                                        val directUrl = if (defaultProvider != SettingsManager.AUTO) {
                                            StreamLinksViewModel.resolveProviderUrl(
                                                providerName = defaultProvider,
                                                tmdbId = tvShowId,
                                                isTv = true,
                                                season = sNum,
                                                episode = eNum
                                            )
                                        } else null

                                        if (directUrl != null) {
                                            val intent = Intent(
                                                context,
                                                PlayerActivity::class.java
                                            ).apply {
                                                putExtra("STREAM_URL", directUrl)
                                                putExtra("TMDB_ID", tvShowId)
                                                putExtra("IS_TV", true)
                                                putExtra("TITLE", tvShowName)
                                                putExtra("OVERVIEW", uiState.tvShowDetail?.overview)
                                                putExtra("POSTER_PATH", episode.stillPath)
                                                putExtra("BACKDROP_PATH", uiState.tvShowDetail?.backdropPath)
                                                putExtra("VOTE_AVERAGE", uiState.tvShowDetail?.voteAverage ?: 0.0)
                                                putExtra("RELEASE_DATE", uiState.tvShowDetail?.firstAirDate)
                                                putExtra("SEASON_NUMBER", sNum)
                                                putExtra("EPISODE_NUMBER", eNum)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            onPlayClick(
                                                Screen.StreamLinks.createRoute(
                                                    tmdbId = tvShowId,
                                                    isTv = true,
                                                    title = tvShowName,
                                                    overview = uiState.tvShowDetail?.overview,
                                                    posterPath = episode.stillPath,
                                                    backdropPath = uiState.tvShowDetail?.backdropPath,
                                                    voteAverage = uiState.tvShowDetail?.voteAverage,
                                                    releaseDate = uiState.tvShowDetail?.firstAirDate,
                                                    season = sNum,
                                                    episode = eNum
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable function for a single item in the seasons list.
 * Clean text-based list with season name and episode count.
 *
 * @param season The [Season] object to display.
 * @param isSelected Whether this season is currently selected.
 * @param onSeasonSelected Lambda to be invoked when the season item is selected.
 */
@Composable
private fun SeasonListItem(
    season: Season,
    isSelected: Boolean,
    onSeasonSelected: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onSeasonSelected()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onSeasonSelected()
                }
            }
            .then(
                if (isSelected || isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = PrimaryRed,
                        shape = RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(4.dp))
            .background(
                color = if (isSelected || isFocused) PrimaryRed.copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSeasonSelected()
            }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = season.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected || isFocused) PrimaryRed else TextPrimary,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = "${season.episodeCount} Episodes",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * Composable function for a single item in the episodes list.
 * Displays episode thumbnail, play overlay, and episode details.
 *
 * @param episode The [Episode] object to display.
 * @param seasonNumber The season number this episode belongs to.
 * @param episodeIndex The index of the episode in the list.
 * @param onEpisodeClick Lambda to be invoked when the episode is clicked.
 */
@Composable
private fun EpisodeListItem(
    episode: Episode,
    seasonNumber: Int,
    episodeIndex: Int,
    onEpisodeClick: (Int, Int) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = PrimaryRed,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = if (isFocused) CardDark else SurfaceDark,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onEpisodeClick(seasonNumber, episode.episodeNumber)
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Episode Thumbnail with Play overlay
        Box(
            modifier = Modifier
                .width(240.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
        ) {
            if (episode.stillPath != null) {
                AsyncImage(
                    model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.STILL_SIZE}${episode.stillPath}",
                    contentDescription = "Episode ${episode.episodeNumber} still",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Play button overlay (white circle with red triangle)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PrimaryRed,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer(translationX = 2.dp) // Offset for play arrow centering
                )
            }
        }

        // Episode Details
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Episode title with S/E numbering
            Text(
                text = "${episode.name.uppercase()}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "S$seasonNumber, E${episode.episodeNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryRed,
                fontWeight = FontWeight.Medium
            )

            // Air date
            if (!episode.airDate.isNullOrEmpty()) {
                Text(
                    text = episode.airDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Episode overview/synopsis
            Text(
                text = episode.overview ?: "No description available.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}