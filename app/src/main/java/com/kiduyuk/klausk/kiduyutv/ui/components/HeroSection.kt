package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.theme.GenrePill
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

/**
 * Composable function to display a hero section, typically at the top of the home screen.
 * It shows details of either a movie or a TV show, prioritizing the movie if both are provided.
 * The hero section includes a backdrop image, title, overview, rating, year, and action buttons.
 *
 * @param movie The [Movie] object to display in the hero section. Can be null.
 * @param tvShow The [TvShow] object to display in the hero section. Can be null.
 * @param modifier The modifier to be applied to the hero section.
 */
@Composable
fun HeroSection(
    movie: Movie?,
    tvShow: TvShow?,
    modifier: Modifier = Modifier
) {
    // Determine if a movie is available and should be prioritized over a TV show.
    val isMovie = movie != null
    
    // Extract relevant data, prioritizing movie data if available.
    val backdropPath = if (isMovie) movie?.backdropPath else tvShow?.backdropPath
    val title = (if (isMovie) movie?.title else tvShow?.name) ?: ""
    val overview = (if (isMovie) movie?.overview else tvShow?.overview) ?: ""
    val rating = (if (isMovie) movie?.voteAverage else tvShow?.voteAverage) ?: 0.0
    val year = (if (isMovie) movie?.releaseDate else tvShow?.firstAirDate)?.take(4) ?: ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp) // Fixed height for the hero section.
    ) {
        // Background Image: Display the backdrop image if available.
        if (backdropPath != null) {
            AsyncImage(
                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$backdropPath",
                contentDescription = null, // Content description for accessibility.
                contentScale = ContentScale.Crop, // Crop to fill the bounds.
                modifier = Modifier
                    .fillMaxSize()
                    .blur(10.dp) // Apply a blur effect to the background image.
            )
        }

        // Gradient Overlay: Add a vertical gradient to make text more readable over the background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF141414).copy(alpha = 0.7f), // Semi-transparent dark color.
                            Color(0xFF141414) // Solid dark color at the bottom.
                        )
                    )
                )
        )

        // Content: Arrange title, rating, overview, and action buttons vertically.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp), // Padding around the content.
            verticalArrangement = Arrangement.Bottom // Align content to the bottom.
        ) {
            // Title of the movie or TV show.
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

            // Rating and Year: Display rating with a star icon and the release/first air year.
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
                        text = String.format("%.1f", rating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }

                Text(
                    text = year,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp)) // Vertical spacing.

            // Overview: Display a brief description.
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 600.dp) // Limit width for readability.
            )

            Spacer(modifier = Modifier.height(24.dp)) // Vertical spacing.

            // Action Buttons: Play Now, Info, and Add to List buttons.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { /* Play */ }, // Placeholder for play action.
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

                OutlinedButton(
                    onClick = { /* Info */ }, // Placeholder for info action.
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Info",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = { /* Add to List */ }, // Placeholder for add to list action.
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
