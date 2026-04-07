package com.kiduyuk.klausk.kiduyutv.ui.screens.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.MediaItem
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer

/**
 * UI State for the Cast Detail screen.
 */
data class CastDetailUiState(
    val isLoading: Boolean = true,
    val castMember: CastMember? = null,
    val mediaItems: List<MediaItem> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for the Cast Detail screen.
 * Fetches and manages movies and TV shows for a specific cast member.
 */
class CastDetailViewModel : ViewModel() {

    private val repository = TmdbRepository()

    private val _uiState = MutableStateFlow(CastDetailUiState())
    val uiState: StateFlow<CastDetailUiState> = _uiState.asStateFlow()

    /**
     * Loads movies and TV shows for a specific cast member.
     * @param castMember The cast member to load credits for.
     */
    fun loadCastDetails(castMember: CastMember) {
        viewModelScope.launch {
            _uiState.value = CastDetailUiState(isLoading = true, castMember = castMember)

            try {
                // Fetch both movie and TV credits in parallel
                val movieCreditsDeferred = launch { repository.getPersonMovieCredits(castMember.id) }
                val tvCreditsDeferred = launch { repository.getPersonTvCredits(castMember.id) }

                val movieResult = movieCreditsDeferred.join().let { repository.getPersonMovieCredits(castMember.id) }
                val tvResult = tvCreditsDeferred.join().let { repository.getPersonTvCredits(castMember.id) }

                val movies = movieResult.getOrNull()?.cast ?: emptyList()
                val tvShows = tvResult.getOrNull()?.cast ?: emptyList()

                // Convert to MediaItem and combine
                val movieItems = movies.map { movie ->
                    MediaItem.MovieItem(
                        id = movie.id,
                        title = movie.title ?: "",
                        posterPath = movie.posterPath,
                        backdropPath = movie.backdropPath,
                        voteAverage = movie.voteAverage,
                        releaseDate = movie.releaseDate,
                        overview = movie.overview,
                        popularity = movie.popularity
                    )
                }

                val tvShowItems = tvShows.map { tv ->
                    MediaItem.TvShowItem(
                        id = tv.id,
                        title = tv.name ?: "",
                        posterPath = tv.posterPath,
                        backdropPath = tv.backdropPath,
                        voteAverage = tv.voteAverage,
                        releaseDate = tv.firstAirDate,
                        overview = tv.overview,
                        popularity = tv.popularity
                    )
                }

                // Combine and sort by popularity (highest first)
                val combinedMedia = (movieItems + tvShowItems)
                    .sortedByDescending { it.voteAverage ?: 0.0 }

                _uiState.value = CastDetailUiState(
                    isLoading = false,
                    castMember = castMember,
                    mediaItems = combinedMedia
                )
            } catch (e: Exception) {
                _uiState.value = CastDetailUiState(
                    isLoading = false,
                    castMember = castMember,
                    error = e.message ?: "Failed to load cast details"
                )
            }
        }

      //  private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    }
}

/**
 * Composable function for displaying the cast member detail screen.
 * Shows all movies and TV shows the cast member has appeared in.
 *
 * @param castMember The cast member to display.
 * @param onBackClick Lambda to be invoked when the back button is clicked.
 * @param onMovieClick Lambda to be invoked when a movie is clicked.
 * @param onTvShowClick Lambda to be invoked when a TV show is clicked.
 * @param viewModel The [CastDetailViewModel] instance providing data for the screen.
 */
@Composable
fun CastDetailScreen(
    castMember: CastMember,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: CastDetailViewModel = remember { CastDetailViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(castMember) {
        viewModel.loadCastDetails(castMember)
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
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        } else {
            CastDetailContent(
                castMember = uiState.castMember ?: castMember,
                mediaItems = uiState.mediaItems,
                onBackClick = onBackClick,
                onMovieClick = onMovieClick,
                onTvShowClick = onTvShowClick
            )
        }
    }
}

/**
 * Content composable for the cast detail screen.
 */
@Composable
private fun CastDetailContent(
    castMember: CastMember,
    mediaItems: List<MediaItem>,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val profileUrl = castMember.profilePath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}h632$path"
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with backdrop and profile info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Backdrop image (blurred)
            if (profileUrl != null) {
                AsyncImage(
                    model = profileUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(30.dp)
                        .graphicsLayer { alpha = 0.6f }
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
                                BackgroundDark.copy(alpha = 0.8f),
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
                    modifier = Modifier.size(28.dp)
                )
            }

            // Profile and info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular profile image
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(60.dp))
                        .background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileUrl != null) {
                        AsyncImage(
                            model = profileUrl,
                            contentDescription = "${castMember.name} profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(60.dp))
                        )
                    } else {
                        Text(
                            text = castMember.name.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Name
                Text(
                    text = castMember.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontSize = 24.sp
                )

                // Known for department
                if (!castMember.knownForDepartment.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Known for: ${castMember.knownForDepartment}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                // Total credits count
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${mediaItems.size} Credits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryRed,
                    fontSize = 14.sp
                )
            }
        }

        // Media items grid
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No credits found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else {
            // Section title
            Text(
                text = "Filmography",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // Grid of media items
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mediaItems) { mediaItem ->
                    CastMediaCard(
                        mediaItem = mediaItem,
                        onClick = {
                            when (mediaItem) {
                                is MediaItem.MovieItem -> onMovieClick(mediaItem.id)
                                is MediaItem.TvShowItem -> onTvShowClick(mediaItem.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Card composable for displaying a media item in the cast detail grid.
 */
@Composable
private fun CastMediaCard(
    mediaItem: MediaItem,
    onClick: () -> Unit
) {
    val posterUrl = mediaItem.posterPath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}w342$path"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        // Poster image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = mediaItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Text(
                    text = mediaItem.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            // Media type badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (mediaItem.mediaType == "movie") PrimaryRed else Purple40,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = if (mediaItem.mediaType == "movie") "Movie" else "TV",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = mediaItem.title,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Year
        if (!mediaItem.releaseDate.isNullOrBlank()) {
            Text(
                text = mediaItem.releaseDate!!.take(4),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
