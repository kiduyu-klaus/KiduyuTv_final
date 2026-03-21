package com.kiduyuk.klausk.kiduyutv.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.ui.components.ContentRow
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
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
@Composable
fun MovieDetailScreen(
    movieId: Int,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onCompanyClick: (id: Int, name: String) -> Unit = { _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()
    // Remember scroll state for the main content column.
    val scrollState = rememberScrollState()
    // Get the current context for launching intents.
    val context = LocalContext.current

    // Load movie details when the movieId changes.
    LaunchedEffect(movieId) {
        viewModel.loadMovieDetail(movieId)
    }

    // Main container for the movie detail screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Display a loading indicator if data is being fetched.
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
            }
        } else if (uiState.movieDetail != null) { // Display movie details if available.
            val movie = uiState.movieDetail!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState) // Make the column vertically scrollable.
            ) {
                // Hero Section for the movie details.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp) // Fixed height for the hero section.
                ) {
                    // Background Image: Display the backdrop image if available.
                    if (movie.backdropPath != null) {
                        AsyncImage(
                            model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}${movie.backdropPath}",
                            contentDescription = null, // Content description for accessibility.
                            contentScale = ContentScale.Crop, // Crop to fill the bounds.
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(10.dp) // Apply a blur effect to the background image.
                        )
                    }

                    // Gradient Overlay: Add a vertical gradient to make text more readable.
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

                    // Back Button.
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Content: Arrange title, metadata, genres, overview, and action buttons vertically.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp), // Padding around the content.
                        verticalArrangement = Arrangement.Bottom // Align content to the bottom.
                    ) {
                        // Movie title.
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

                        // Metadata: Rating, release year, and runtime.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = PrimaryRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = String.format("%.1f", movie.voteAverage),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }

                            Text(
                                text = movie.releaseDate?.take(4) ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )

                            if (movie.runtime != null) {
                                Text(
                                    text = "${movie.runtime} min",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

                        // Genres: Display genre pills.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            movie.genres?.take(3)?.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = GenrePill
                                ) {
                                    Text(
                                        text = genre.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

                        // Overview: Display a brief description.
                        Text(
                            text = movie.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

                        // Production Companies: Clickable pills for production companies.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            movie.productionCompanies?.take(3)?.forEach { company ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.DarkGray,
                                    modifier = Modifier.clickable { onCompanyClick(company.id, company.name) }
                                ) {
                                    Text(
                                        text = company.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing.

                        // Action Buttons: Play Now, Watch Trailer, and Add to List buttons.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { /* Play movie action */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryRed
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Play Now",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            // Watch Trailer Button: Only shown if a trailer key is available.
                            if (uiState.trailerKey != null) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${uiState.trailerKey}"))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Watch Trailer",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.toggleMyList() }, // Toggle item in My List.
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isInMyList) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Similar Movies section.
                if (uiState.similarMovies.isNotEmpty()) {
                    ContentRow(
                        title = "Others Also Watched",
                        items = uiState.similarMovies,
                        onItemClick = { movie -> onMovieClick(movie.id) }
                    ) { movie, isSelected, onClick ->
                        MovieCard(
                            movie = movie,
                            isSelected = isSelected,
                            onClick = onClick
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp)) // Bottom spacing.
            }
        } else if (uiState.error != null) { // Display an error message if an error occurred.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Preview for the [MovieDetailScreen] composable.
 */
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MovieDetailScreenPreview() {
    KiduyuTvTheme {
        MovieDetailScreen(
            movieId = 1,
            onBackClick = {},
            onMovieClick = {}
        )
    }
}
