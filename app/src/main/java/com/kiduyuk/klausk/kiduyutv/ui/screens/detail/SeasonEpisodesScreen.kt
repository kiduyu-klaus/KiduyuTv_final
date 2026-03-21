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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                                .focusable()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
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
                                    seasonNumber = selectedSeasonIndex + 1
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
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Season ${season.seasonNumber}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Text(
                text = "${season.episodeCount ?: 0} episodes",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/**
 * Composable function for a single item in the episodes list.
 *
 * @param episode The [Episode] object to display.
 * @param seasonNumber The number of the season this episode belongs to.
 */
@Composable
private fun EpisodeListItem(
    episode: Episode,
    seasonNumber: Int
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
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
            .background(CardDark)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                // TODO: Implement playback logic for the selected episode.
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Thumbnail: Display episode still image with a play button overlay.
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(158.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            if (episode.stillPath != null) {
                AsyncImage(
                    model = "${TmdbApiService.IMAGE_BASE_URL}w300${episode.stillPath}",
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Play button overlay.
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PrimaryRed,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Episode Info: Title, number, rating, and overview.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${episode.episodeNumber}. ${episode.name}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Episode rating.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = PrimaryRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = String.format("%.1f", episode.voteAverage ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "S${seasonNumber} E${episode.episodeNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Episode overview description.
            Text(
                text = episode.overview ?: "No description available.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Preview for the [SeasonEpisodesScreen] composable.
 */
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun SeasonEpisodesScreenPreview() {
    KiduyuTvTheme {
        SeasonEpisodesScreen(
            tvShowId = 1,
            tvShowName = "Sample TV Show",
            totalSeasons = 3,
            onBackClick = {}
        )
    }
}
