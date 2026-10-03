package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv

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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.CrewMember
import com.kiduyuk.klausk.kiduyutv.ui.components.CastRow
import com.kiduyuk.klausk.kiduyutv.ui.components.CrewRow
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.GenrePill
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.EpisodeOverviewViewModel

@Composable
fun EpisodeOverviewScreen(
    tvId: Int,
    seasonNumber: Int,
    episodeNumber: Int,
    tvShowName: String,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    onCastClick: (CastMember) -> Unit,
    onCrewClick: (CrewMember) -> Unit,
    onImagesClick: () -> Unit,
    viewModel: EpisodeOverviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(tvId, seasonNumber, episodeNumber) {
        viewModel.loadEpisode(tvId, seasonNumber, episodeNumber)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LottieLoadingView(size = 260.dp)
            }
            state.episode != null -> EpisodeOverviewContent(
                episode = state.episode!!,
                tvShowName = state.tvShow?.name ?: tvShowName,
                backdropPath = state.tvShow?.backdropPath,
                genres = state.tvShow?.genres.orEmpty().map { it.name },
                networks = state.tvShow?.networks.orEmpty().map { it.name },
                crew = state.writingAndProductionCrew,
                cast = state.cast,
                onBackClick = onBackClick,
                onPlayClick = {
                    onPlayClick(
                        Screen.StreamLinks.createRoute(
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
                },
                onCastClick = onCastClick,
                onCrewClick = onCrewClick,
                onImagesClick = onImagesClick
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Episode details unavailable", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun EpisodeOverviewContent(
    episode: Episode,
    tvShowName: String,
    backdropPath: String?,
    genres: List<String>,
    networks: List<String>,
    crew: List<CrewMember>,
    cast: List<CastMember>,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onCastClick: (CastMember) -> Unit,
    onCrewClick: (CrewMember) -> Unit,
    onImagesClick: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(310.dp)) {
                backdropPath?.let {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$it",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(8.dp)
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.65f), BackgroundDark)
                        )
                    )
                )
                androidx.compose.material3.IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 18.dp)
                ) {
                    Text(tvShowName, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(episode.name, color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = PrimaryRed, modifier = Modifier.size(16.dp))
                        Text(String.format("%.1f", episode.voteAverage ?: 0.0), color = TextPrimary)
                        Text("S${episode.seasonNumber}E${episode.episodeNumber}", color = TextSecondary)
                        episode.airDate?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary) }
                        episode.runtime?.takeIf { it > 0 }?.let { Text("${it}m", color = TextSecondary) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 28.dp)) {
                if (genres.isNotEmpty() || networks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (genres.take(5) + networks.take(5)).forEach { label ->
                            Surface(shape = RoundedCornerShape(12.dp), color = GenrePill) {
                                Text(label, color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(episode.overview?.takeIf { it.isNotBlank() } ?: "No description available.", color = TextSecondary, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                        shape = RoundedCornerShape(5.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    Button(
                        onClick = onImagesClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A)),
                        shape = RoundedCornerShape(5.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Image, "Posters", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Posters")
                    }
                }
            }
        }
        if (crew.isNotEmpty()) {
            item { CrewRow(title = "Writing & Production", crew = crew, onCrewClick = onCrewClick) }
        }
        if (cast.isNotEmpty()) {
            item { CastRow(title = "Cast", cast = cast, onCastClick = onCastClick) }
        }
    }
}
