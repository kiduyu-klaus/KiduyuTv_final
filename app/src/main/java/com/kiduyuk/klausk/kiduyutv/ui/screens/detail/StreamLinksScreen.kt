package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
        IconButton(
            onClick = onBackClick,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = "Select a Stream Provider",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.isLoading) {
                LottieLoadingView(size = 200.dp)
            } else if (uiState.streamProviders.isEmpty()) {
                Text(
                    text = "No streaming providers available.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(uiState.streamProviders) { provider ->
                        StreamProviderItem(
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

                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamProviderItem(
    provider: StreamProvider,
    onProviderClick: (StreamProvider) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) PrimaryRed.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onProviderClick(provider) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = provider.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                if (provider.name.contains("VidLink")) {
                    Surface(
                        color = Color(0xFF4CAF50), // Green color for "Fast"
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "FAST",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFFFC107), // Amber/Gold color for "Best"
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BEST FOR MOVIES",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (provider.name.contains("Videasy")) {
                    Surface(
                        color = Color(0xFF4CAF50), // Green color for "Fast"
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "FAST",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFFFC107), // Amber/Gold color for "Best"
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BEST FOR TV",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (provider.name.contains("VidFast")) {
                    Surface(
                        color = Color(0xFF4CAF50), // Green color for "Fast"
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "FAST",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFFFC107), // Amber/Gold color for "Best"
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BEST FOR BOTH MOVIES AND TV",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
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
            } else {
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
