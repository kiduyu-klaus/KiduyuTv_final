package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.CastRow
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.youtube.YouTubePlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel
import com.kiduyuk.klausk.kiduyutv.util.TvInterstitialManager
import com.kiduyuk.klausk.kiduyutv.BuildConfig

/**
 * Composable function for displaying the detailed information of a TV show.
 * It fetches TV show details, recommendations, and similar TV shows using [DetailViewModel].
 * Uses LazyColumn for better TV focus navigation and to prevent autoscroll on focus.
 *
 * @param tvId The ID of the TV show to display.
 * @param onBackClick Lambda to be invoked when the back button is clicked.
 * @param onTvShowClick Lambda to be invoked when a recommended or similar TV show is clicked.
 * @param onEpisodesClick Lambda to be invoked when the "Episodes" button is clicked.
 * @param onNetworkClick Lambda to be invoked when a network is clicked.
 * @param viewModel The [DetailViewModel] instance providing data for the screen.
 */
@Composable
fun TvShowDetailScreen(
    tvId: Int,
    onBackClick: () -> Unit,
    onTvShowClick: (Int) -> Unit,
    onEpisodesClick: (tvId: Int, tvShowName: String, totalSeasons: Int) -> Unit = { _, _, _ -> },
    onNetworkClick: (id: Int, name: String) -> Unit = { _, _ -> },
    onPlayClick: (String) -> Unit,
    onCastClick: (Int, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current

    // Button interaction sources for focus tracking
    val playFocusRequester = remember { FocusRequester() }
    val playInteraction = remember { MutableInteractionSource() }
    val playFocused by playInteraction.collectIsFocusedAsState()

    val trailerInteraction = remember { MutableInteractionSource() }
    val trailerFocused by trailerInteraction.collectIsFocusedAsState()

    val episodesInteraction = remember { MutableInteractionSource() }
    val episodesFocused by episodesInteraction.collectIsFocusedAsState()

    val myListInteraction = remember { MutableInteractionSource() }
    val myListFocused by myListInteraction.collectIsFocusedAsState()

    LaunchedEffect(tvId) {
        viewModel.loadTvShowDetail(context, tvId)
    }

    // Request focus on the play button after content loads
    // LazyColumn handles focus-scroll interaction better than verticalScroll
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.tvShowDetail != null) {
            // Small delay to allow LazyColumn to settle before requesting focus
            kotlinx.coroutines.delay(50)
            playFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LottieLoadingView(size = 300.dp)
            }
        } else if (uiState.tvShowDetail != null) {
            val tvShow = uiState.tvShowDetail!!

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Hero Section ─────────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        // Backdrop image
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

                        // Gradient overlay
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

                        // Back button
                        val activity = context as? android.app.Activity
                        IconButton(
                            onClick = {
                                if (BuildConfig.FLAVOR == "tv" && activity != null) {
                                    TvInterstitialManager.showAndThen(activity) {
                                        onBackClick()
                                    }
                                } else {
                                    onBackClick()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Hero content pinned to bottom
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = 20.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Title
                            Text(
                                text = tvShow.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                fontSize = 22.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Metadata row: rating · year · seasons · episodes
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = PrimaryRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = String.format("%.1f", tvShow.voteAverage),
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text("·", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = tvShow.firstAirDate?.take(4) ?: "",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                if (tvShow.numberOfSeasons != null) {
                                    Text("·", color = TextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = "${tvShow.numberOfSeasons}S",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                if (tvShow.numberOfEpisodes != null) {
                                    Text("·", color = TextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = "${tvShow.numberOfEpisodes}Ep",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                if (tvShow.status != null) {
                                    Text("·", color = TextSecondary, fontSize = 12.sp)
                                    Surface(
                                        color = PrimaryRed.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = tvShow.status,
                                            color = PrimaryRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Genres + Networks on same row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tvShow.genres?.take(5)?.forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = GenrePill
                                    ) {
                                        Text(
                                            text = genre.name,
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                tvShow.networks?.take(5)?.forEach { network ->
                                    val networkInteraction = remember { MutableInteractionSource() }
                                    val networkFocused by networkInteraction.collectIsFocusedAsState()

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (networkFocused) DarkRed else Color.DarkGray,
                                        modifier = Modifier.clickable(
                                            interactionSource = networkInteraction,
                                            indication = null
                                        ) { onNetworkClick(network.id, network.name) }
                                    ) {
                                        Text(
                                            text = network.name,
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Overview — 2 lines max
                            Text(
                                text = tvShow.overview ?: "",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Play Now
                                Button(
                                    onClick = {
                                        val seasonNumber = uiState.watchHistoryItem?.seasonNumber ?: 1
                                        val episodeNumber = uiState.watchHistoryItem?.episodeNumber ?: 1
                                        val timestamp = uiState.watchHistoryItem?.playbackPosition ?: 0L
                                        val defaultProvider = SettingsManager(context).getDefaultProvider()
                                        val directUrl = if (defaultProvider != SettingsManager.AUTO) {
                                            StreamLinksViewModel.resolveProviderUrl(
                                                providerName = defaultProvider,
                                                tmdbId = tvShow.id,
                                                isTv = true,
                                                season = seasonNumber,
                                                episode = episodeNumber,
                                                timestamp = timestamp
                                            )
                                        } else null

                                        if (directUrl != null) {
                                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                                putExtra("STREAM_URL", directUrl)
                                                putExtra("TMDB_ID", tvShow.id)
                                                putExtra("IS_TV", true)
                                                putExtra("TITLE", tvShow.name ?: "")
                                                putExtra("OVERVIEW", tvShow.overview)
                                                putExtra("POSTER_PATH", tvShow.posterPath)
                                                putExtra("BACKDROP_PATH", tvShow.backdropPath)
                                                putExtra("VOTE_AVERAGE", tvShow.voteAverage)
                                                putExtra("RELEASE_DATE", tvShow.firstAirDate)
                                                putExtra("SEASON_NUMBER", seasonNumber)
                                                putExtra("EPISODE_NUMBER", episodeNumber)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            onPlayClick(
                                                Screen.StreamLinks.createRoute(
                                                    tmdbId = tvShow.id,
                                                    isTv = true,
                                                    title = tvShow.name ?: "",
                                                    overview = tvShow.overview,
                                                    posterPath = tvShow.posterPath,
                                                    backdropPath = tvShow.backdropPath,
                                                    voteAverage = tvShow.voteAverage,
                                                    releaseDate = tvShow.firstAirDate,
                                                    season = seasonNumber,
                                                    episode = episodeNumber,
                                                    timestamp = timestamp
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.focusRequester(playFocusRequester),
                                    interactionSource = playInteraction,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (playFocused) DarkRed else PrimaryRed
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (uiState.watchHistoryItem != null) "Continue" else "Play", fontSize = 12.sp)
                                }

                                // Watch Trailer
                                if (uiState.trailerKey != null) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(context, YouTubePlayerActivity::class.java).apply {
                                                putExtra("VIDEO_ID", uiState.trailerKey)
                                                putExtra("TITLE", tvShow.name ?: "Trailer")
                                            }
                                            context.startActivity(intent)
                                        },
                                        interactionSource = trailerInteraction,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (trailerFocused) DarkRed else Color.DarkGray
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Movie, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Trailer", fontSize = 12.sp)
                                    }
                                }

                                // Episodes
                                OutlinedButton(
                                    onClick = { onEpisodesClick(tvShow.id, tvShow.name ?: tvShow.name ?: "", tvShow.numberOfSeasons ?: 1) },
                                    interactionSource = episodesInteraction,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (episodesFocused) DarkRed else Color.Transparent,
                                        contentColor = TextPrimary
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Episodes", fontSize = 12.sp)
                                }

                                // My List toggle
                                OutlinedButton(
                                    onClick = { viewModel.toggleMyList(context) },
                                    interactionSource = myListInteraction,
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (myListFocused) DarkRed else Color.Transparent,
                                        contentColor = TextPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isInMyList) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // ── End Hero Section ─────────────────────────────────────────

                // Cast Row
                if (uiState.cast.isNotEmpty()) {
                    item {
                        CastRow(
                            title = "Cast",
                            cast = uiState.cast,
                            onCastClick = { castMember ->
                                onCastClick(
                                    castMember.id,
                                    castMember.name,
                                    castMember.character,
                                    castMember.profilePath,
                                    castMember.knownForDepartment
                                )
                            }
                        )
                    }
                }

                // Similar TV Shows
                if (uiState.similarTvShows.isNotEmpty()) {
                    item {
                        ContentRow(
                            title = "Others Also Watched",
                            items = uiState.similarTvShows,
                            onItemClick = { tvShow -> onTvShowClick(tvShow.id) }
                        ) { tvShow, isFocused, onClick ->
                            TvShowCard(tvShow = tvShow, isSelected = isFocused, onClick = onClick)
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun TvShowDetailScreenPreview() {
    KiduyuTvTheme {
        TvShowDetailScreen(
            tvId = 1,
            onBackClick = {},
            onTvShowClick = {},
            onPlayClick = {}
        )
    }
}