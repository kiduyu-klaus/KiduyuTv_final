package com.kiduyuk.klausk.kiduyutv.ui.screens.detail.mobile

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
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
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import android.widget.Toast
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.StreamLinksViewModel
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.util.AdManager
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import androidx.activity.compose.BackHandler
import android.app.Activity

@Composable
fun MobileMovieDetailScreen(
    movieId: Int,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onPlayClick: (String) -> Unit,
    onCastClick: (Int, String, String?, String?, String?) -> Unit,
    onNavigateToCastDetail: (String) -> Unit,
    onCompanyClick: (Int, String) -> Unit = { _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(movieId) {
        viewModel.loadMovieDetail(context, movieId)
        Toast.makeText(
            context,
            "You can create or select companies in My List to see curated movie collections",
            Toast.LENGTH_LONG
        ).show()
    }

    // Override back navigation to show an interstitial (phone flavour only)
    val activity = context as? Activity
    BackHandler {
        if (BuildConfig.FLAVOR == "phone" && activity != null) {
            AdManager.showInterstitial(activity) {
                onBackClick()
            }
        } else {
            onBackClick()
        }
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
        } else if (uiState.movieDetail != null) {
            val movie = uiState.movieDetail!!

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
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${movie.backdropPath}",
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
                        text = movie.title ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("%.1f", movie.voteAverage), color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = movie.releaseDate?.take(4) ?: "", color = TextSecondary, fontSize = 14.sp)
                        if (movie.runtime != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "${movie.runtime}m", color = TextSecondary, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val defaultProvider = SettingsManager(context).getDefaultProvider()
                                val directUrl = if (defaultProvider != SettingsManager.AUTO) {
                                    StreamLinksViewModel.resolveProviderUrl(
                                        providerName = defaultProvider,
                                        tmdbId = movie.id,
                                        isTv = false,
                                        season = null,
                                        episode = null,
                                        timestamp = uiState.watchHistoryItem?.playbackPosition ?: 0L
                                    )
                                } else null

                                val iframeHtml = StreamProviderManager.generateIframeHtml(
                                    providerName = defaultProvider,
                                    tmdbId = movie.id,
                                    isTv = false,
                                    season = null,
                                    episode = null,
                                    timestamp = uiState.watchHistoryItem?.playbackPosition ?: 0L
                                )

                                if (directUrl != null) {
                                    val intent = Intent(context, PlayerActivity::class.java).apply {
                                        putExtra("IFRAME_HTML", iframeHtml)
                                        putExtra("STREAM_URL", directUrl)
                                        putExtra("TMDB_ID", movie.id)
                                        putExtra("IS_TV", false)
                                        putExtra("TITLE", movie.title ?: "")
                                        putExtra("OVERVIEW", movie.overview)
                                        putExtra("POSTER_PATH", movie.posterPath)
                                        putExtra("BACKDROP_PATH", movie.backdropPath)
                                        putExtra("VOTE_AVERAGE", movie.voteAverage)
                                        putExtra("RELEASE_DATE", movie.releaseDate)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val route = Screen.MobileStreamLinks.createRoute(
                                        tmdbId = movie.id,
                                        isTv = false,
                                        title = movie.title ?: "",
                                        overview = movie.overview,
                                        posterPath = movie.posterPath,
                                        backdropPath = movie.backdropPath,
                                        voteAverage = movie.voteAverage,
                                        releaseDate = movie.releaseDate
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

                        Button(
                            onClick = { viewModel.toggleMyList(context) },
                            modifier = Modifier.weight(1f),
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
                    }

                    // Watch Trailer Button below the primary buttons
                    if (uiState.trailerKey != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, YouTubePlayerActivity::class.java).apply {
                                    putExtra("VIDEO_ID", uiState.trailerKey)
                                    putExtra("TITLE", movie.title ?: "")
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
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = movie.overview ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (movie.genres != null) {
                        Text(text = "Genres", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            movie.genres.take(3).forEach { genre ->
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

                    if (movie.productionCompanies != null && movie.productionCompanies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Production Companies", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            movie.productionCompanies.take(3).forEach { company ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = CardDark,
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .clickable { onCompanyClick(company.id, company.name) }
                                ) {
                                    Text(
                                        text = company.name,
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

                    if (uiState.similarMovies.isNotEmpty()) {
                        MobileCategoryRow(
                            title = "Similar Movies",
                            items = uiState.similarMovies,
                            onItemClick = { onMovieClick(it.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

