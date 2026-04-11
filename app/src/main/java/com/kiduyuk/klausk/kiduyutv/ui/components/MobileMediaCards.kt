package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary

@Composable
fun MobileMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = if (movie.posterPath != null) {
        "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${movie.posterPath}"
    } else null

    Box(
        modifier = modifier
            .width(120.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardDark)
            )
        }

        // Rating badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = String.format("%.1f", movie.voteAverage),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun MobileTvShowCard(
    tvShow: TvShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = if (tvShow.posterPath != null) {
        "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${tvShow.posterPath}"
    } else null

    Box(
        modifier = modifier
            .width(120.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = tvShow.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardDark)
            )
        }

        // Rating badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = String.format("%.1f", tvShow.voteAverage),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
        }
    }
}
