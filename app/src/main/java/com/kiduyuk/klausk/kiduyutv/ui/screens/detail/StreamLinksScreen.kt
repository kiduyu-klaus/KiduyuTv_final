package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel

data class StreamProvider(
    val name: String,
    val urlTemplate: String,
    var isAvailable: Boolean = false,
    val type: String // "movie" or "tv"
)

private data class ProviderBadge(val label: String, val color: Color, val textColor: Color)

private fun getBadgesForProvider(name: String): List<ProviderBadge> = when {
    name.contains("VidLink") -> listOf(
        ProviderBadge("FAST", Color(0xFF22C55E), Color.White),
        ProviderBadge("BEST FOR MOVIES", Color(0xFFF59E0B), Color(0xFF1A1A1A))
    )
    name.contains("Videasy") -> listOf(
        ProviderBadge("FAST", Color(0xFF22C55E), Color.White),
        ProviderBadge("BEST FOR TV", Color(0xFFF59E0B), Color(0xFF1A1A1A))
    )
    name.contains("VidFast") -> listOf(
        ProviderBadge("FAST", Color(0xFF22C55E), Color.White),
        ProviderBadge("MOVIES & TV", Color(0xFFF59E0B), Color(0xFF1A1A1A))
    )
    else -> emptyList()
}

@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable
fun StreamLinksScreen(
    tmdbId: Int,
    isTv: Boolean,
    title: String,
    overview: String?,
    posterPath: String?,
    backdropPath: String?,
    voteAverage: Double,
    releaseDate: String?,
    season: Int? = null,
    episode: Int? = null,
    timestamp: Long = 0L,
    onBackClick: () -> Unit,
    viewModel: StreamLinksViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Focus management for D-pad navigation
    val scrollState = rememberScrollState()
    val firstCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.streamProviders) {
        // Request focus on the first provider card when providers are loaded
        if (uiState.streamProviders.isNotEmpty()) {
            firstCardFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(tmdbId, isTv, season, episode) {
        viewModel.loadStreamProviders(tmdbId, isTv, season, episode, context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Backdrop image
        if (backdropPath != null) {
            AsyncImage(
                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${backdropPath}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradient overlay — heavier at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to BackgroundDark.copy(alpha = 0.55f),
                            0.35f to BackgroundDark.copy(alpha = 0.82f),
                            1.0f to BackgroundDark
                        )
                    )
                )
        )

        // Back button - made non-focusable to prevent focus trap
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header: poster + title/meta ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                if (posterPath != null) {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}w185${posterPath}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 72.dp, height = 108.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (isTv && season != null && episode != null) {
                        Text(
                            text = "SEASON $season  ·  EPISODE $episode",
                            color = PrimaryRed,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (releaseDate != null) {
                            Text(
                                text = releaseDate.take(4),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "·",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "★ ${String.format("%.1f", voteAverage)}",
                            color = Color(0xFFF59E0B),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(20.dp))

            // ── Section header ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Choose a Provider",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!uiState.isLoading) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "${uiState.streamProviders.size} sources",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Provider list ────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LottieLoadingView(size = 160.dp)
                }
            } else if (uiState.streamProviders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No streaming providers available.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Two-column grid
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2
                ) {
                    uiState.streamProviders.forEachIndexed { index, provider ->
                        StreamProviderCard(
                            provider = provider,
                            index = index + 1,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstCardFocusRequester)
                                    else Modifier
                                ),
                            onProviderClick = {
                                val intent = Intent(context, PlayerActivity::class.java).apply {
                                    putExtra("TMDB_ID", tmdbId)
                                    putExtra("IS_TV", isTv)
                                    putExtra("SEASON_NUMBER", season ?: 0)
                                    putExtra("EPISODE_NUMBER", episode ?: 0)
                                    putExtra("TITLE", title)
                                    putExtra("OVERVIEW", overview)
                                    putExtra("POSTER_PATH", posterPath)
                                    putExtra("BACKDROP_PATH", backdropPath)
                                    putExtra("VOTE_AVERAGE", voteAverage)
                                    putExtra("RELEASE_DATE", releaseDate)

                                    val finalUrl = if (timestamp > 0) {
                                        when (provider.name) {
                                            "VidLink" -> "${provider.urlTemplate}&startAt=$timestamp"
                                            "VidKing" -> "${provider.urlTemplate}&progress=$timestamp"
                                            "Videasy" -> "${provider.urlTemplate}&progress=$timestamp"
                                            "VidFast" -> "${provider.urlTemplate}&startAt=$timestamp"
                                            else -> provider.urlTemplate
                                        }
                                    } else {
                                        provider.urlTemplate
                                    }
                                    putExtra("STREAM_URL", finalUrl)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamProviderCard(
    provider: StreamProvider,
    index: Int,
    modifier: Modifier = Modifier,
    onProviderClick: (StreamProvider) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "cardScale"
    )

    val badges = getBadgesForProvider(provider.name)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused)
                    Brush.linearGradient(
                        colors = listOf(
                            PrimaryRed,
                            PrimaryRed.copy(alpha = 0.85f)
                        )
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) PrimaryRed else Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onProviderClick(provider) }
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Numbered index circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFocused) Color.White else PrimaryRed
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        color = if (isFocused) PrimaryRed else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = provider.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Play arrow
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isFocused) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Badges
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    badges.forEach { badge ->
                        Surface(
                            color = badge.color.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badge.label,
                                color = badge.textColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
