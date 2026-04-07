package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import android.content.Intent
import android.net.Uri
import android.net.Uri.encode
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.CastRow
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.DetailViewModel

/**
 * Composable function for displaying the detailed information of a movie.
 * It fetches movie details and similar movies using [DetailViewModel].
 *
 * @param movieId The ID of the movie to display.
 * @param onBackClick Lambda to be invoked when the back button is clicked.
 * @param onMovieClick Lambda to be invoked when a similar movie is clicked.
 * @param onCompanyClick Lambda to be invoked when a production company is clicked.
 * @param viewModel The [DetailViewModel] instance providing data for the screen.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MovieDetailScreen(
    movieId: Int,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onCompanyClick: (id: Int, name: String) -> Unit = { _, _ -> },
    onPlayClick: (String) -> Unit,
    onCastClick: (Int, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Button interaction sources for focus tracking
    val playFocusRequester = remember { FocusRequester() }
    val playInteraction = remember { MutableInteractionSource() }
    val playFocused by playInteraction.collectIsFocusedAsState()

    val trailerInteraction = remember { MutableInteractionSource() }
    val trailerFocused by trailerInteraction.collectIsFocusedAsState()

    val myListInteraction = remember { MutableInteractionSource() }
    val myListFocused by myListInteraction.collectIsFocusedAsState()

    LaunchedEffect(movieId) {
        viewModel.loadMovieDetail(context, movieId)
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.movieDetail != null) {
            playFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LottieLoadingView(size = 300.dp)
            }
        } else if (uiState.movieDetail != null) {
            val movie = uiState.movieDetail!!


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // ── Hero Section ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    // Backdrop image
                    if (movie.backdropPath != null) {
                        AsyncImage(
                            model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${movie.backdropPath}",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(10.dp)
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

                    // Hero content pinned to bottom
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .padding(top = 15.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Title
                        Text(
                            text = movie.title ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            fontSize = 22.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Metadata row: rating · year · runtime
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = PrimaryRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = String.format("%.1f", movie.voteAverage),
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                            Text("·", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = movie.releaseDate?.take(4) ?: "",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            if (movie.runtime != null) {
                                Text("·", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = "${movie.runtime}m",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Genres + Production Companies on same row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            movie.genres?.take(5)?.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GenrePill
                                ) {
                                    Text(
                                        text = genre.name,
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            movie.productionCompanies?.take(5)?.forEach { company ->
                                val companyInteraction = remember { MutableInteractionSource() }
                                val companyFocused by companyInteraction.collectIsFocusedAsState()

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (companyFocused) DarkRed else Color.DarkGray,
                                    modifier = Modifier.clickable(
                                        interactionSource = companyInteraction,
                                        indication = null
                                    ) { onCompanyClick(company.id, company.name) }
                                ) {
                                    Text(
                                        text = company.name,
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Overview — 2 lines max
                        Text(
                            text = movie.overview ?: "",
                            color = TextSecondary,
                            //maxLines = 2,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play Now
                            Button(
                                onClick = {
                                    onPlayClick(
                                        Screen.StreamLinks.createRoute(
                                            tmdbId = movie.id,
                                            isTv = false,
                                            title = movie.title ?: "",
                                            overview = movie.overview,
                                            posterPath = movie.posterPath,
                                            backdropPath = movie.backdropPath,
                                            voteAverage = movie.voteAverage,
                                            releaseDate = movie.releaseDate,
                                            timestamp = uiState.watchHistoryItem?.playbackPosition ?: 0L
                                        )
                                    )
                                },
                                modifier = Modifier.focusRequester(playFocusRequester),
                                interactionSource = playInteraction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (playFocused) DarkRed else PrimaryRed
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (uiState.watchHistoryItem != null) "Continue" else "Play", fontSize = 12.sp)
                            }

                            // Watch Trailer
                            if (uiState.trailerKey != null) {
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.youtube.com/watch?v=${uiState.trailerKey}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    interactionSource = trailerInteraction,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (trailerFocused) DarkRed else Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Movie, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Trailer", fontSize = 12.sp)
                                }
                            }

                            // My List toggle
                            OutlinedButton(
                                onClick = { viewModel.toggleMyList(context) },
                                interactionSource = myListInteraction,
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (myListFocused) DarkRed else Color.Transparent,
                                    contentColor = TextPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isInMyList) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                // ── End Hero Section ─────────────────────────────────────────
                // Collection Movies
                if (uiState.collectionDetail != null) {
                    val collection = uiState.collectionDetail!!
                    ContentRow(
                        title = collection.name.uppercase(),
                        items = collection.parts,
                        onItemClick = { movie -> onMovieClick(movie.id) }
                    ) { movie, isSelected, onClick ->
                        MovieCard(movie = movie, isSelected = isSelected, onClick = onClick)
                    }
                }
                // Cast Row
                if (uiState.cast.isNotEmpty()) {
                    CastRow(
                        title = "Cast",
                        cast = uiState.cast,
                        onCastClick = { castMember ->
                            onCastClick(
                                castMember.id,
                                castMember.name,
                                castMember.character,
                                castMember.profilePath,
                                castMember.knownForDepartment
                            )
                        }
                    )
                }



                // Similar Movies
                if (uiState.similarMovies.isNotEmpty()) {
                    ContentRow(
                        title = "Others Also Watched",
                        items = uiState.similarMovies,
                        onItemClick = { movie -> onMovieClick(movie.id) }
                    ) { movie, isSelected, onClick ->
                        MovieCard(movie = movie, isSelected = isSelected, onClick = onClick)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun MovieDetailScreenPreview() {
    KiduyuTvTheme {
        MovieDetailScreen(
            movieId = 1,
            onBackClick = {},
            onMovieClick = {},
            onCompanyClick = { _, _ -> },
            onPlayClick = {}
        )
    }
}