package com.kiduyuk.klausk.kiduyutv.ui.screens.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.async
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

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
     * Also fetches person details to get the biography/overview.
     * @param castMember The cast member to load credits for.
     */
    fun loadCastDetails(castMember: CastMember) {
        viewModelScope.launch {
            _uiState.value = CastDetailUiState(isLoading = true, castMember = castMember)

            try {
                // Fetch person details, movie credits, and TV credits in parallel using async
                val personDetailsDeferred = async { repository.getPersonDetails(castMember.id) }
                val movieCreditsDeferred = async { repository.getPersonMovieCredits(castMember.id) }
                val tvCreditsDeferred = async { repository.getPersonTvCredits(castMember.id) }

                // Wait for all requests to complete
                val personDetails = personDetailsDeferred.await().getOrNull()
                val movieResult = movieCreditsDeferred.await()
                val tvResult = tvCreditsDeferred.await()

                // Get biography/overview from person details
                val biography = personDetails?.biography

                // Create updated cast member with overview
                val castMemberWithOverview = castMember.copy(overview = biography)

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
                    castMember = castMemberWithOverview,
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
 * Uses a single Box with header at top (fixed with profile + overview) and scrollable grid below.
 */
@Composable
private fun CastDetailContent(
    castMember: CastMember,
    mediaItems: List<MediaItem>,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val headerHeight = screenHeight * 0.35f // 35% of screen height to fit overview

    // Responsive grid calculation
    val horizontalPadding = 16.dp
    val spacing = 12.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 100.dp
    val actualColumns = maxOf(4, minOf(8, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))
    val calculatedCardWidth = (availableWidth - (spacing * (actualColumns - 1))) / actualColumns
    val calculatedCardHeight = calculatedCardWidth * 1.8f

    val profileUrl = castMember.profilePath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}h632$path"
    }

    // Single Box containing header (fixed) and scrollable content
    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed header section at the top with profile and overview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
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

            // Header content: Profile on left, info and overview on right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 50.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left side: Profile image
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(45.dp))
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
                                    .clip(RoundedCornerShape(45.dp))
                            )
                        } else {
                            Text(
                                text = castMember.name.take(1).uppercase(),
                                style = MaterialTheme.typography.displayMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Right side: Name, info, and overview
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Name
                    Text(
                        text = castMember.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Known for department
                    if (!castMember.knownForDepartment.isNullOrBlank()) {
                        Text(
                            text = "Known for: ${castMember.knownForDepartment}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Credits count
                    Text(
                        text = "${mediaItems.size} Credits",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryRed,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Biography section
                    if (!castMember.overview.isNullOrBlank()) {
                        Text(
                            text = "Biography",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = castMember.overview!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Scrollable content starting below the header
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = headerHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
        ) {
            // Section title
            Text(
                text = "Filmography",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Grid of all media items (movies and TV shows mixed)
            if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                // Create rows of items for the grid
                val rows = mediaItems.chunked(actualColumns)

                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { mediaItem ->
                                when (mediaItem) {
                                    is MediaItem.MovieItem -> {
                                        MediaGridCard(
                                            mediaItem = mediaItem,
                                            cardWidth = calculatedCardWidth,
                                            cardHeight = calculatedCardHeight,
                                            onClick = { onMovieClick(mediaItem.id) }
                                        )
                                    }
                                    is MediaItem.TvShowItem -> {
                                        MediaGridCard(
                                            mediaItem = mediaItem,
                                            cardWidth = calculatedCardWidth,
                                            cardHeight = calculatedCardHeight,
                                            onClick = { onTvShowClick(mediaItem.id) }
                                        )
                                    }
                                }
                            }
                            // Fill remaining space in row with spacers if needed
                            repeat(actualColumns - rowItems.size) {
                                Spacer(modifier = Modifier.width(calculatedCardWidth))
                            }
                        }
                    }
                }
            }

            // Bottom spacing for better scrolling experience
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Card for grid display showing both movies and TV shows with type badge.
 */
@Composable
private fun MediaGridCard(
    mediaItem: MediaItem,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val posterUrl = mediaItem.posterPath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}$path"
    }

    val isMovie = mediaItem is MediaItem.MovieItem

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .then(
                if (isFocused) {
                    Modifier.border(
                        3.dp,
                        FocusBorder,
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
    ) {
        // Poster Image
        if (mediaItem.posterPath != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = mediaItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mediaItem.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }

        // Rating badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = String.format("%.1f", mediaItem.voteAverage),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
        }

        // Type badge (Movie or TV)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(
                    if (isMovie) PrimaryRed.copy(alpha = 0.9f) else Purple40.copy(alpha = 0.9f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isMovie) "Movie" else "TV",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontSize = 8.sp
            )
        }
    }
}
