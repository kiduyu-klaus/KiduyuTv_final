package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.mobile

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.CastRow
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.youtube.YouTubePlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile.MobileCategoryRow
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel

@Composable
fun MobileTvShowDetailScreen(
    tvId: Int,
    onBackClick: () -> Unit,
    onTvShowClick: (Int) -> Unit,
    onEpisodesClick: (tvId: Int, tvShowName: String, totalSeasons: Int) -> Unit,
    onPlayClick: (String) -> Unit,
    onCastClick: (Int, String, String?, String?, String?) -> Unit,
    onNavigateToCastDetail: (String) -> Unit,
    onNetworkClick: (Int, String) -> Unit = { _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(tvId) {
        viewModel.loadTvShowDetail(context, tvId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LottieLoadingView(size = 200.dp)
            }
        } else if (uiState.tvShowDetail != null) {
            val tvShow = uiState.tvShowDetail!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Backdrop with overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${tvShow.backdropPath}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.8f), BackgroundDark)
                                )
                            )
                    )
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = tvShow.name ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("%.1f", tvShow.voteAverage), color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = tvShow.firstAirDate?.take(4) ?: "", color = TextSecondary, fontSize = 14.sp)
                        if (tvShow.numberOfSeasons != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "${tvShow.numberOfSeasons} Seasons", color = TextSecondary, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    val route = Screen.MobileStreamLinks.createRoute(
                                        tmdbId = tvShow.id,
                                        isTv = true,
                                        title = tvShow.name ?: "",
                                        overview = tvShow.overview,
                                        posterPath = tvShow.posterPath,
                                        backdropPath = tvShow.backdropPath,
                                        voteAverage = tvShow.voteAverage,
                                        releaseDate = tvShow.firstAirDate
                                    )
                                    onPlayClick(route)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Now")
                        }

                        OutlinedButton(
                            onClick = {
                                val route = Screen.MobileSeasonEpisodes.createRoute(
                                    tvId = tvShow.id,
                                    tvShowName = tvShow.name ?: "",
                                    totalSeasons = tvShow.numberOfSeasons ?: 1
                                )
                                onEpisodesClick(tvShow.id, tvShow.name ?: "", tvShow.numberOfSeasons ?: 1)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TextSecondary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Episodes")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.toggleMyList(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isInMyList) CardDark else SurfaceDark
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            if (uiState.isInMyList) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (uiState.isInMyList) PrimaryRed else Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.isInMyList) "In My List" else "Add to My List",
                            color = if (uiState.isInMyList) PrimaryRed else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Watch Trailer Button
                    if (uiState.trailerKey != null) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, YouTubePlayerActivity::class.java).apply {
                                    putExtra("VIDEO_ID", uiState.trailerKey)
                                    putExtra("TITLE", tvShow.name ?: "")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TextSecondary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = PrimaryRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Watch Trailer")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = tvShow.overview ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (tvShow.genres != null) {
                        Text(text = "Genres", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tvShow.genres.take(3).forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = CardDark,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = genre.name,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (tvShow.networks != null && tvShow.networks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Networks", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tvShow.networks.take(5).forEach { network ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = CardDark,
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .clickable { onNetworkClick(network.id, network.name) }
                                ) {
                                    Text(
                                        text = network.name,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.cast.isNotEmpty()) {
                        CastRow(
                            title = "Cast",
                            cast = uiState.cast,
                            onCastClick = { castMember ->
                                val route = Screen.MobileCastDetail.createRoute(
                                    castId = castMember.id,
                                    castName = castMember.name,
                                    character = castMember.character,
                                    profilePath = castMember.profilePath,
                                    knownForDepartment = castMember.knownForDepartment
                                )
                                onNavigateToCastDetail(route)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.similarTvShows.isNotEmpty()) {
                        MobileCategoryRow(
                            title = "Similar TV Shows",
                            items = uiState.similarTvShows,
                            onItemClick = { onTvShowClick(it.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
