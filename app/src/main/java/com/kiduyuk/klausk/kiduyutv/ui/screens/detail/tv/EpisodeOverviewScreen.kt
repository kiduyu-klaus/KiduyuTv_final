package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.CrewMember
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.ui.components.CastRow
import com.kiduyuk.klausk.kiduyutv.ui.components.CrewRow
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.GenrePill
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
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

    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
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
            else -> Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        state.error ?: "Episode details unavailable",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(28.dp)
                    )
                }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                backdropPath?.let {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$it",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(10.dp)
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = .08f), BackgroundDark.copy(alpha = .62f), BackgroundDark)
                        )
                    )
                )
                FocusableIconButton(
                    onClick = onBackClick,
                    contentDescription = "Back",
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tvShowName, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(episode.name, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    EpisodeMetaRow(episode)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = .96f))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (genres.isNotEmpty() || networks.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((genres + networks).distinct().take(8)) { label ->
                                Surface(shape = RoundedCornerShape(50.dp), color = GenrePill) {
                                    Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FocusableActionButton(onClick = onPlayClick, primary = true) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play episode")
                        }
                        FocusableActionButton(onClick = onImagesClick, primary = false) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View images")
                        }
                    }
                    Text(
                        episode.overview?.takeIf { it.isNotBlank() } ?: "No description available.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (crew.isNotEmpty()) item { CrewRow(title = "Writing & production", crew = crew, onCrewClick = onCrewClick) }
        if (cast.isNotEmpty()) item { CastRow(title = "Guest cast", cast = cast, onCastClick = onCastClick) }
    }
}

@Composable
private fun EpisodeMetaRow(episode: Episode) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = PrimaryRed, modifier = Modifier.size(14.dp))
            Text(String.format("%.1f", episode.voteAverage ?: 0.0), color = TextPrimary, fontSize = 12.sp)
        }
        Text("S${episode.seasonNumber}E${episode.episodeNumber}", color = TextSecondary, fontSize = 12.sp)
        episode.airDate?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
        episode.runtime?.takeIf { it > 0 }?.let { Text("${it} min", color = TextSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun FocusableActionButton(
    onClick: () -> Unit,
    primary: Boolean,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val modifier = Modifier.onFocusChanged { focused = it.isFocused }.then(
        if (focused) Modifier.border(2.dp, FocusBorder, RoundedCornerShape(14.dp)) else Modifier
    )
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed, contentColor = TextPrimary)
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) { content() }
    }
}

@Composable
private fun FocusableIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, FocusBorder, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = .45f))
    ) { content() }
}
