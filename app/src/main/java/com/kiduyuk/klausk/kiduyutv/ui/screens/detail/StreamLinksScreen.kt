package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.* // ktlint-disable no-wildcard-imports
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class StreamProvider(
    val name: String,
    val urlTemplate: String,
    var isAvailable: Boolean = false,
    val type: String // "movie" or "tv"
)

@Composable
fun StreamLinksScreen(
    tmdbId: Int,
    isTv: Boolean,
    title: String,
    posterPath: String?,
    backdropPath: String?,
    season: Int? = null,
    episode: Int? = null,
    onBackClick: () -> Unit,
    viewModel: StreamLinksViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId, isTv, season, episode) {
        viewModel.loadStreamProviders(tmdbId, isTv, season, episode)
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
                                val intent = Intent(context, PlayerActivity::class.java).apply {
                                    putExtra("TMDB_ID", tmdbId)
                                    putExtra("IS_TV", isTv)
                                    putExtra("SEASON_NUMBER", season ?: 0)
                                    putExtra("EPISODE_NUMBER", episode ?: 0)
                                    putExtra("TITLE", title)
                                    putExtra("POSTER_PATH", posterPath)
                                    putExtra("BACKDROP_PATH", backdropPath)
                                    putExtra("STREAM_URL", it.urlTemplate)
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
            Text(
                text = provider.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            if (provider.isAvailable) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Available",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Unavailable",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
