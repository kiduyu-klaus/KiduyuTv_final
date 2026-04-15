package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.mobile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel
import androidx.compose.material3.CircularProgressIndicator
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv.StreamProvider

/**
 * Mobile version of StreamLinksScreen.
 * Displays available streaming providers in a mobile-friendly vertical layout.
 *
 * @param tmdbId The TMDB ID of the media.
 * @param isTv Whether the media is a TV show.
 * @param title The title of the media.
 * @param overview The overview/description of the media.
 * @param posterPath The poster image path.
 * @param backdropPath The backdrop image path.
 * @param voteAverage The vote average rating.
 * @param releaseDate The release date.
 * @param season The season number (for TV shows).
 * @param episode The episode number (for TV shows).
 * @param timestamp The playback timestamp.
 * @param onBackClick Callback when back button is clicked.
 * @param onProviderClick Callback when a provider is selected.
 * @param viewModel The StreamLinksViewModel instance.
 */
@Composable
fun MobileStreamLinksScreen(
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
    onProviderClick: (String) -> Unit,
    viewModel: StreamLinksViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId, isTv, season, episode) {
        viewModel.loadStreamProviders(tmdbId, isTv, season, episode, context)
    }

    // Auto-launch if a default provider is set and providers have loaded
    val defaultProvider = remember { SettingsManager(context).getDefaultProvider() }
    LaunchedEffect(uiState.streamProviders) {
        if (defaultProvider != SettingsManager.AUTO && uiState.streamProviders.isNotEmpty()) {
            val match = uiState.streamProviders.find { it.name == defaultProvider }
            if (match != null) {
                val url = when (match.name) {
                    "VidLink" -> "${match.urlTemplate}&startAt=$timestamp"
                    "VidKing" -> "${match.urlTemplate}&progress=$timestamp"
                    "Videasy" -> "${match.urlTemplate}&progress=$timestamp"
                    "VidFast" -> "${match.urlTemplate}&startAt=$timestamp"
                    else -> match.urlTemplate
                }
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("url", url)
                    putExtra("title", title)
                    putExtra("tmdbId", tmdbId)
                    putExtra("isTv", isTv)
                    putExtra("season", season ?: 0)
                    putExtra("episode", episode ?: 0)
                    putExtra("timestamp", timestamp)
                }
                context.startActivity(intent)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (backdropPath != null) {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${backdropPath}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = String.format("%.1f", voteAverage),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (releaseDate != null) {
                            Text(
                                text = releaseDate.take(4),
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        if (isTv && season != null && episode != null) {
                            Text(
                                text = "S${season}E${episode}",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Select a streaming provider:",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

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
                } else {
                    items(uiState.streamProviders) { provider ->
                        MobileStreamProviderCard(
                            provider = provider,
                            onProviderClick = {
                                // Launch PlayerActivity for webview links
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
                                onProviderClick(provider.urlTemplate)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
 * Mobile-optimized stream provider card.
 *
 * @param provider The StreamProvider to display.
 * @param onProviderClick Callback when the provider is clicked.
 */
@Composable
private fun MobileStreamProviderCard(
    provider: StreamProvider,
    onProviderClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onProviderClick
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Provider badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "FAST",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            fontSize = 11.sp
                        )
                    }

                    when {
                        provider.name.contains("VidLink") -> {
                            Surface(
                                color = Color(0xFFFFC107),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "BEST FOR MOVIES",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        provider.name.contains("Videasy") -> {
                            Surface(
                                color = Color(0xFFFFC107),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "BEST FOR TV",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        provider.name.contains("VidFast") -> {
                            Surface(
                                color = Color(0xFFFFC107),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "BEST FOR BOTH",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            if (provider.isAvailable) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Available",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
