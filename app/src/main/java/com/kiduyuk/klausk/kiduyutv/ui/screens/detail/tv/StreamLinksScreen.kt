package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Activity
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
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamProviderUi
import com.kiduyuk.klausk.kiduyutv.util.TvInterstitialManager
import com.kiduyuk.klausk.kiduyutv.BuildConfig

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

        // Backdrop
        backdropPath?.let {
            AsyncImage(
                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$it",
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
                        listOf(
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
                .padding(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp)
        ) {

            Spacer(modifier = Modifier.height(60.dp))

            // 🎬 HERO HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                AsyncImage(
                    model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}$posterPath",
                    contentDescription = null,
                    modifier = Modifier
                        .height(140.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isTv) "SEASON $season • EPISODE $episode" else "MOVIE",
                        color = PrimaryRed,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "${releaseDate?.take(4)} • ⭐ $voteAverage",
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "Choose a Provider",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.isLoading) {
                LottieLoadingView(size = 200.dp)
            } else if (uiState.streamProviders.isEmpty()) {
                Text("No providers found", color = TextSecondary)
            } else {

                // 🔥 GRID
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(uiState.streamProviders) { index, provider ->
                        StreamProviderItem(
                            index = index + 1,
                            provider = provider
                        ) {
                            val finalUrl = StreamLinksViewModel.resolveProviderUrl(
                                providerName = provider.name,
                                tmdbId = tmdbId,
                                isTv = isTv,
                                season = season,
                                episode = episode,
                                timestamp = timestamp
                            ) ?: provider.urlTemplate

                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra("STREAM_URL", finalUrl)
                                putExtra("TMDB_ID", tmdbId)
                                putExtra("IS_TV", isTv)
                                putExtra("TITLE", title)
                                putExtra("OVERVIEW", overview)
                                putExtra("POSTER_PATH", posterPath)
                                putExtra("BACKDROP_PATH", backdropPath)
                                putExtra("VOTE_AVERAGE", voteAverage)
                                putExtra("RELEASE_DATE", releaseDate)
                                if (isTv) {
                                    putExtra("SEASON_NUMBER", season ?: 1)
                                    putExtra("EPISODE_NUMBER", episode ?: 1)
                                }
                            }

                            // Show TV interstitial before launching player (TV flavour only)
                            if (BuildConfig.FLAVOR == "tv") {
                                TvInterstitialManager.showAndThenLaunch(context as android.app.Activity) {
                                    context.startActivity(intent)
                                }
                            } else {
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamProviderItem(
    index: Int,
    provider: StreamProviderUi,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) PrimaryRed else Color.DarkGray.copy(0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 🔴 index circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.Red, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$index", color = Color.White)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    provider.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                    Tag("FAST", Color(0xFF4CAF50), Color.White)

                    when (provider.name) {
                        "Videasy" -> Tag("BEST FOR TV", Color(0xFFFFC107), Color.Black)
                        "VidLink" -> Tag("BEST FOR MOVIES", Color(0xFFFFC107), Color.Black)
                        "VidFast" -> Tag("MOVIES & TV", Color(0xFFFFC107), Color.Black)
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun Tag(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

