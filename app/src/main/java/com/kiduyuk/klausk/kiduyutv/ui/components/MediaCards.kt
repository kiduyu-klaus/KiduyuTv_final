package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * This card is designed to be displayed within a focusable wrapper (like in ContentRow)
 * or to be made focusable by the caller. It no longer handles its own focus or click
 * to avoid conflicts with parent focus managers.
 *
 * @param movie The [Movie] data to display.
 * @param isSelected A boolean indicating if the card is currently selected (focused).
 * @param onClick Lambda to be invoked when the card is clicked. (Note: The caller should handle the clickable modifier)
 * @param modifier The modifier to be applied to the card.
 */
@Composable
fun MovieCard(
    movie: Movie,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Root container for the card
    Box(
        modifier = modifier
            .size(width = 100.dp, height = 180.dp) // Fixed card size
            .then(
                // Apply border only when card is selected (focused)
                if (isSelected) {
                    Modifier.border(
                        3.dp,
                        FocusBorder,
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(8.dp)) // Clip content to rounded corners
    ) {

        // 🔹 Poster Image (fills entire card)
        if (movie.posterPath != null) {
            AsyncImage(
                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${movie.posterPath}",
                contentDescription = movie.title,
                contentScale = ContentScale.Crop, // Crop to fill without distortion
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 🔹 Fallback background if no image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardDark)
            )
        }

        // ⭐ Rating badge (top-right corner)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd) // Position at top-right
                .padding(6.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f), // Semi-transparent background
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = String.format("%.1f", movie.voteAverage), // Format rating
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
        }
    }
}

/**
 * Composable function to display a TV show card.
 * This card is designed to be displayed within a focusable wrapper (like in ContentRow)
 * or to be made focusable by the caller. It no longer handles its own focus or click
 * to avoid conflicts with parent focus managers.
 *
 * @param tvShow The [TvShow] data to display.
 * @param isSelected A boolean indicating if the card is currently selected (focused).
 * @param onClick Lambda to be invoked when the card is clicked. (Note: The caller should handle the clickable modifier)
 * @param modifier The modifier to be applied to the card.
 */
@Composable
fun TvShowCard(
    tvShow: TvShow,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Root container for the card
    Box(
        modifier = modifier
            .size(width = 100.dp, height = 180.dp) // Fixed card size
            .then(
                // Apply border when selected (focused)
                if (isSelected) {
                    Modifier.border(
                        3.dp,
                        FocusBorder,
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(8.dp)) // Rounded edges
    ) {

        // 🔹 Poster Image
        if (tvShow.posterPath != null) {
            AsyncImage(
                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${tvShow.posterPath}",
                contentDescription = tvShow.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 🔹 Fallback if no poster
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardDark)
            )
        }

        // ⭐ Rating badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd) // Top-right corner
                .padding(6.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(4.dp)
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
}