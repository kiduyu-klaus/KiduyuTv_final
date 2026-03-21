package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.FocusBorder
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary

/**
 * Composable function to display a movie card.
 * This card is designed to be displayed within a [ContentRow] and handles its own click events.
 *
 * @param movie The [Movie] data to display.
 * @param isSelected A boolean indicating if the card is currently selected (focused).
 * @param onClick Lambda to be invoked when the card is clicked.
 * @param modifier The modifier to be applied to the card.
 */
@Composable
fun MovieCard(
    movie: Movie,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Box to hold the movie card content, applying width and conditional border for focus indication.
    Box(
        modifier = modifier
            .width(160.dp)
            .then(
                // Apply a border if the card is selected (focused).
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = FocusBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(8.dp)) // Clip content to rounded corners.
            .clickable { // Handle click events for the card.
                onClick()
            }
    ) {
        Column { // Arrange content vertically.
            // Box for the movie poster and rating badge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        color = CardDark,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                // Display movie poster if available.
                if (movie.posterPath != null) {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${movie.posterPath}",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                // Rating badge displayed at the top-end of the poster.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1f", movie.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // Vertical space.

            // Movie title.
            Text(
                text = movie.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Movie release year.
            Text(
                text = movie.releaseDate?.take(4) ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * Composable function to display a TV show card.
 * This card is designed to be displayed within a [ContentRow] and handles its own click events.
 *
 * @param tvShow The [TvShow] data to display.
 * @param isSelected A boolean indicating if the card is currently selected (focused).
 * @param onClick Lambda to be invoked when the card is clicked.
 * @param modifier The modifier to be applied to the card.
 */
@Composable
fun TvShowCard(
    tvShow: TvShow,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Box to hold the TV show card content, applying width and conditional border for focus indication.
    Box(
        modifier = modifier
            .width(160.dp)
            .then(
                // Apply a border if the card is selected (focused).
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = FocusBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(8.dp)) // Clip content to rounded corners.
            .clickable { // Handle click events for the card.
                onClick()
            }
    ) {
        Column { // Arrange content vertically.
            // Box for the TV show poster and rating badge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        color = CardDark,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                // Display TV show poster if available.
                if (tvShow.posterPath != null) {
                    AsyncImage(
                        model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${tvShow.posterPath}",
                        contentDescription = tvShow.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                // Rating badge displayed at the top-end of the poster.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1f", tvShow.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // Vertical space.

            // TV show name.
            Text(
                text = tvShow.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // TV show first air date year.
            Text(
                text = tvShow.firstAirDate?.take(4) ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
