package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.data.model.Season
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel

/**
 * Composable function for the Season and Episodes screen.
 * This screen displays a list of seasons on the left and the episodes for the selected season on the right.
 * It is accessed from the [TvShowDetailScreen].
 *
 * @param tvShowId The ID of the TV show.
 * @param tvShowName The name of the TV show.
 * @param totalSeasons The total number of seasons for the TV show.
 * @param onBackClick Lambda to navigate back to the previous screen.
 * @param viewModel The [DetailViewModel] instance providing data for the screen.
 */
@Composable
fun SeasonEpisodesScreen(
    tvShowId: Int,
    tvShowName: String,
    totalSeasons: Int,
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()
    // State to keep track of the currently selected season index.
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    // State to track if seasons have been loaded to prevent redundant loads.
    var seasonsLoaded by remember { mutableStateOf(false) }

    // Load seasons and the first season's episodes when the screen is initialized or tvShowId changes.
    LaunchedEffect(tvShowId) {
        if (!seasonsLoaded || uiState.seasons.isEmpty()) {
            viewModel.loadSeasons(tvShowId, totalSeasons)
            seasonsLoaded = true
        }
        // Initially load episodes for the first season.
        viewModel.loadSeasonEpisodes(tvShowId, 1)
    }

    // Main container for the screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading && uiState.seasons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else {
            // Main content layout: Two columns.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                // Left Column: Seasons List.
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                ) {
                    // Header: Back button and TV show title.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = tvShowName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$totalSeasons seasons",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // LazyColumn for the list of seasons.
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(uiState.seasons) { index, season ->
                            SeasonListItem(
                                season = season,
                                isSelected = index == selectedSeasonIndex,
                                onFocused = {
                                    // Update selected season and load its episodes when focused (Android TV behavior)
                                    if (selectedSeasonIndex != index) {
                                        selectedSeasonIndex = index
                                        viewModel.loadSeasonEpisodes(tvShowId, season.seasonNumber)
                                    }
                                },
                                onClick = {
                                    // Also handle click for accessibility or touch support
                                    if (selectedSeasonIndex != index) {
                                        selectedSeasonIndex = index
                                        viewModel.loadSeasonEpisodes(tvShowId, season.seasonNumber)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(32.dp)) // Horizontal spacing between columns.

                // Right Column: Episodes List.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.isLoading && uiState.episodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryRed)
                        }
                    } else {
                        // LazyColumn for the list of episodes.
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(uiState.episodes) { episode ->
                                EpisodeListItem(
                                    episode = episode,
                                    seasonNumber = selectedSeasonIndex + 1,
                                    onEpisodeClick = { sNum, eNum ->
                                        val intent = Intent(context, PlayerActivity::class.java).apply {
                                            putExtra("TMDB_ID", tvShowId)
                                            putExtra("IS_TV", true)
                                            putExtra("SEASON_NUMBER", sNum)
                                            putExtra("EPISODE_NUMBER", eNum)
                                        }
                                        context.startActivity(intent)
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
 *
 * @param season The [Season] object to display.
 * @param isSelected Whether this season is currently selected.
 * @param onFocused Lambda to be invoked when the season item gains focus.
 * @param onClick Lambda to be invoked when the season item is clicked.
 */
@Composable
private fun SeasonListItem(
    season: Season,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Trigger onFocused when focus state changes to true
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            }
            .then(
                // Apply focus border if selected or focused.
                if (isSelected || isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = FocusBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = if (isSelected || isFocused) CardDark else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = season.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected || isFocused) PrimaryRed else TextPrimary
            )
            Text(
                text = "${season.episodeCount} Episodes",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * Composable function for a single item in the episodes list.
 *
 * @param episode The [Episode] object to display.
 * @param seasonNumber The season number this episode belongs to.
 */
@Composable
private fun EpisodeListItem(
    episode: Episode,
    seasonNumber: Int,
    onEpisodeClick: (Int, Int) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // Apply focus border if focused.
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = FocusBorder,
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
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Episode Thumbnail placeholder
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
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

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if ((episode.voteAverage ?: 0.0) > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                                color = TextPrimary
                            )
                        }
                    }

                    if (episode.runtime != null) {
                        Text(
                            text = "${episode.runtime}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Text(
                        text = episode.airDate ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = episode.overview ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
