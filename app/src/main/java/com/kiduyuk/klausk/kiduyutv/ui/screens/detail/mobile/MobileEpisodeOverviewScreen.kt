package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kiduyuk.klausk.kiduyutv.ui.components.CastRow
import com.kiduyuk.klausk.kiduyutv.ui.components.CrewRow
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.rememberPhoneInterstitialBackClick
import com.kiduyuk.klausk.kiduyutv.ui.components.shimmer.MobileMovieDetailSkeleton
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.EpisodeOverviewViewModel

@Composable
fun MobileEpisodeOverviewScreen(
    tvId: Int,
    seasonNumber: Int,
    episodeNumber: Int,
    tvShowName: String,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    viewModel: EpisodeOverviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val handleBackClick = rememberPhoneInterstitialBackClick(onBackClick)
    BackHandler(onBack = handleBackClick)
    LaunchedEffect(tvId, seasonNumber, episodeNumber) {
        viewModel.loadEpisode(tvId, seasonNumber, episodeNumber)
    }

    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
        when {
            state.isLoading -> MobileMovieDetailSkeleton()
            state.episode != null -> MobileEpisodeOverviewContent(
                episode = state.episode!!,
                tvShowName = state.tvShow?.name ?: tvShowName,
                backdropPath = state.tvShow?.backdropPath,
                genres = state.tvShow?.genres.orEmpty().map { it.name },
                networks = state.tvShow?.networks.orEmpty().map { it.name },
                crew = state.writingAndProductionCrew,
                cast = state.cast,
                onBackClick = handleBackClick,
                onPlayClick = {
                    onPlayClick(
                        Screen.MobileStreamLinks.createRoute(
                            tmdbId = tvId,
                            isTv = true,
                            title = "${state.tvShow?.name ?: tvShowName} • ${state.episode!!.name}",
                            overview = state.episode!!.overview,
                            posterPath = state.episode!!.stillPath,
                            backdropPath = state.tvShow?.backdropPath,
                            voteAverage = state.episode!!.voteAverage,
                            releaseDate = state.episode!!.airDate,
                            season = seasonNumber,
                            episode = episodeNumber
                        )
                    )
                }
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Episode details unavailable", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun MobileEpisodeOverviewContent(
    episode: Episode,
    tvShowName: String,
    backdropPath: String?,
    genres: List<String>,
    networks: List<String>,
    crew: List<com.kiduyuk.klausk.kiduyutv.data.model.CrewMember>,
    cast: List<com.kiduyuk.klausk.kiduyutv.data.model.CastMember>,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                backdropPath?.let {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$it",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.8f), BackgroundDark))
                    )
                )
                IconButton(onClick = onBackClick, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(tvShowName, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(episode.name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = PrimaryRed, modifier = Modifier.size(18.dp))
                    Text(String.format("%.1f", episode.voteAverage ?: 0.0), color = TextPrimary)
                    Text("S${episode.seasonNumber}E${episode.episodeNumber}", color = TextSecondary)
                    episode.airDate?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary) }
                    episode.runtime?.takeIf { it > 0 }?.let { Text("${it}m", color = TextSecondary) }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
                Spacer(Modifier.height(18.dp))
                Text(episode.overview?.takeIf { it.isNotBlank() } ?: "No description available.", color = TextSecondary, lineHeight = 20.sp)
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text("Genres", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.take(5).forEach { label ->
                            Surface(shape = RoundedCornerShape(16.dp), color = CardDark) {
                                Text(label, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
                if (networks.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Networks", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        networks.take(5).forEach { label ->
                            Surface(shape = RoundedCornerShape(16.dp), color = CardDark) {
                                Text(label, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }
        if (crew.isNotEmpty()) item { CrewRow(title = "Writing & Production", crew = crew, onCrewClick = {}) }
        if (cast.isNotEmpty()) item { CastRow(title = "Cast", cast = cast, onCastClick = {}) }
    }
}
