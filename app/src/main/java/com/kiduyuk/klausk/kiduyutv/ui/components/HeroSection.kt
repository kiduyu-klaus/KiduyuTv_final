package com.kiduyuk.klausk.kiduyutv.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.ui.theme.*

@Composable
fun HeroSection(
    movie: Movie?,
    tvShow: TvShow?,
    modifier: Modifier = Modifier,
    onInfoClick: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    // Logging for image loading debugging
    Log.i("HeroSection", "HeroSection composed: movie=${movie?.title}, tvShow=${tvShow?.name}")

    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.55f).dp

    val isMovie = movie != null

    // Fallback: Use posterPath if backdropPath is missing
    val backdropPath = if (isMovie) {
        movie?.backdropPath ?: movie?.posterPath
    } else {
        tvShow?.backdropPath ?: tvShow?.posterPath
    }
    val title = (if (isMovie) movie?.title else tvShow?.name) ?: ""
    val overview = (if (isMovie) movie?.overview else tvShow?.overview) ?: ""
    val rating = (if (isMovie) movie?.voteAverage else tvShow?.voteAverage) ?: 0.0
    val year = (if (isMovie) movie?.releaseDate else tvShow?.firstAirDate)?.take(4) ?: ""

    // Build the full backdrop image URL for logging
    val backdropUrl = if (!backdropPath.isNullOrEmpty()) {
        "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.BACKDROP_SIZE}$backdropPath"
    } else {
        null
    }
    Log.i("HeroSection", "backdropPath=${backdropPath}, backdropUrl=${backdropUrl}")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {

        // 🔹 Background Image (safe for preview)
        if (!backdropPath.isNullOrEmpty()) {
            Log.i("HeroSection", "Rendering backdrop AsyncImage: ${title}")
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(10.dp),
                onError = { errorState ->
                    Log.e("HeroSection", "Backdrop AsyncImage error for ${title}: ${errorState.result}")
                },
                onLoading = {
                    Log.i("HeroSection", "Backdrop AsyncImage loading for ${title}")
                },
                onSuccess = { metadata ->
                    Log.i("HeroSection", "Backdrop AsyncImage loaded successfully for ${title} from: ${metadata.result.dataSource}")
                }
            )
        } else {
            // Fallback background (for preview / errors)
            Log.w("HeroSection", "No backdropPath for ${title}, showing fallback background")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            )
        }

        // 🔹 Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.5f),
                            BackgroundDark
                        )
                    )
                )
        )

        // 🔹 Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(top = 30.dp), // 🔽 reduced padding

            verticalArrangement = Arrangement.Bottom
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge, // 🔽 smaller than displayLarge
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 32.sp // 🔽 reduced
            )

            Spacer(modifier = Modifier.height(8.dp)) // 🔽 tighter

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Star icon — change tint
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = DarkRed,          // ← was PrimaryRed
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = String.format("%.1f", rating),
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }

                Text("-", color = TextSecondary)

                Text(
                    text = year,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = overview,
                color = TextSecondary,
                maxLines = 3, // 🔽 reduce lines to fit
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 600.dp),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // Play button — add focus
                val playInteraction = remember { MutableInteractionSource() }
                val playFocused by playInteraction.collectIsFocusedAsState()

                Button(
                    onClick = onPlayClick,
                    interactionSource = playInteraction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playFocused) DarkRed else Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (playFocused) DarkRed else TextPrimary),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.focusable(interactionSource = playInteraction)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontSize = 10.sp, color = TextPrimary)
                }

                // Info button — add focus
                val infoInteraction = remember { MutableInteractionSource() }
                val infoFocused by infoInteraction.collectIsFocusedAsState()

                OutlinedButton(
                    onClick = onInfoClick,
                    interactionSource = infoInteraction,
                    border = BorderStroke(1.dp, if (infoFocused) DarkRed else TextPrimary),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.focusable(interactionSource = infoInteraction),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (infoFocused) DarkRed.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Info", fontSize = 10.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HeroSectionMoviePreview() {
    val sampleMovie = Movie(
        id = 1,
        title = "Lucky Luke",
        overview = "Lucky Luke, the lone gunslinger, must team up with Louise, a fearless young woman searching for her missing mother. Together, they face the dangers of the Wild West, ally with old enemies like the Daltons or Billy the Kid - and learn to trust each other.",
        posterPath = "/path/to/poster.jpg",
        backdropPath = "/path/to/backdrop.jpg",
        voteAverage = 6.7,
        releaseDate = "2026-01-01",
        genreIds = listOf(28, 35, 80),
        popularity = 100.0
    )
    HeroSection(movie = sampleMovie, tvShow = null)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HeroSectionTvShowPreview() {
    val sampleTvShow = TvShow(
        id = 2,
        name = "The Mandalorian",
        overview = "The travels of a lone bounty hunter in the outer reaches of the galaxy, far from the authority of the New Republic.",
        posterPath = "/path/to/poster.jpg",
        backdropPath = "/path/to/backdrop.jpg",
        voteAverage = 8.5,
        firstAirDate = "2019-11-12",
        genreIds = listOf(10765, 10759),
        popularity = 200.0
    )
    HeroSection(movie = null, tvShow = sampleTvShow)
}