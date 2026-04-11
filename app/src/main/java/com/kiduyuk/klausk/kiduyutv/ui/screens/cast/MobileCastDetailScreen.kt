package com.kiduyuk.klausk.kiduyutv.ui.screens.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.MediaItem
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Mobile Cast Detail screen.
 */
data class MobileCastDetailUiState(
    val isLoading: Boolean = true,
    val castMember: CastMember? = null,
    val mediaItems: List<MediaItem> = emptyList(),
    val isSaved: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the Mobile Cast Detail screen.
 */
class MobileCastDetailViewModel : ViewModel() {
    private val repository = TmdbRepository()
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(MobileCastDetailUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<MobileCastDetailUiState> = _uiState.asStateFlow()

    /**
     * Loads movies and TV shows for a specific cast member.
     */
    fun loadCastDetails(castMember: CastMember) {
        viewModelScope.launch {
            val isSaved = MyListManager.isInList(castMember.id, "cast")
            _uiState.value = MobileCastDetailUiState(isLoading = true, castMember = castMember, isSaved = isSaved)

            try {
                val personDetailsDeferred = async { repository.getPersonDetails(castMember.id) }
                val movieCreditsDeferred = async { repository.getPersonMovieCredits(castMember.id) }
                val tvCreditsDeferred = async { repository.getPersonTvCredits(castMember.id) }

                val personDetails = personDetailsDeferred.await().getOrNull()
                val movieResult = movieCreditsDeferred.await()
                val tvResult = tvCreditsDeferred.await()

                val biography = personDetails?.biography
                val castMemberWithOverview = castMember.copy(overview = biography)

                val movies = movieResult.getOrNull()?.cast ?: emptyList()
                val tvShows = tvResult.getOrNull()?.cast ?: emptyList()

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

                val combinedMedia = (movieItems + tvShowItems)
                    .sortedByDescending { it.voteAverage ?: 0.0 }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    castMember = castMemberWithOverview,
                    mediaItems = combinedMedia
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    castMember = castMember,
                    error = e.message ?: "Failed to load cast details"
                )
            }
        }
    }

    /**
     * Toggles the current cast member in/out of My List.
     */
    fun toggleSave(context: android.content.Context, castMember: CastMember) {
        val isSaved = MyListManager.isInList(castMember.id, "cast")
        if (isSaved) {
            MyListManager.removeItem(castMember.id, "cast", context)
            _uiState.value = _uiState.value.copy(isSaved = false)
        } else {
            MyListManager.addItem(
                MyListItem(
                    id = castMember.id,
                    title = castMember.name,
                    posterPath = castMember.profilePath,
                    type = "cast",
                    character = castMember.character,
                    knownForDepartment = castMember.knownForDepartment
                ),
                context
            )
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}

/**
 * Mobile-optimized Cast Detail Screen with touch-friendly interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileCastDetailScreen(
    castMember: CastMember,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: MobileCastDetailViewModel = remember { MobileCastDetailViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(castMember) {
        viewModel.loadCastDetails(castMember)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(castMember.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LottieLoadingView(size = 200.dp)
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: "An error occurred", color = TextPrimary)
                    }
                }
                else -> {
                    MobileCastDetailContent(
                        castMember = uiState.castMember ?: castMember,
                        mediaItems = uiState.mediaItems,
                        isSaved = uiState.isSaved,
                        onSaveClick = { viewModel.toggleSave(context, uiState.castMember ?: castMember) },
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                }
            }
        }
    }
}

/**
 * Mobile-optimized content for cast detail screen.
 */
@Composable
private fun MobileCastDetailContent(
    castMember: CastMember,
    mediaItems: List<MediaItem>,
    isSaved: Boolean,
    onSaveClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val context = LocalContext.current

    // Responsive grid: 3 columns on phones, more on tablets
    val columns = when {
        screenWidth >= 600.dp -> 5
        screenWidth >= 400.dp -> 4
        else -> 3
    }

    val cardSpacing = 12.dp
val horizontalPadding = 16.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val cardWidth = (availableWidth - (cardSpacing * (columns - 1))) / columns
    val cardHeight = cardWidth * 1.5f

    val profileUrl = castMember.profilePath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}h632$path"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Image
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(CardDark),
                contentAlignment = Alignment.Center
            ) {
                if (profileUrl != null) {
                    AsyncImage(
                        model = profileUrl,
                        contentDescription = "${castMember.name} profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = castMember.name.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary
                    )
                }
            }

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = castMember.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!castMember.knownForDepartment.isNullOrBlank()) {
                    Text(
                        text = "Known for: ${castMember.knownForDepartment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "${mediaItems.size} Credits",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryRed,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Save Button
                Button(
                    onClick = onSaveClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSaved) SurfaceDark else PrimaryRed
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSaved) "Saved" else "Save to List",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Biography Section
        if (!castMember.overview.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontalPadding, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Biography",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = castMember.overview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Filmography Section
        Text(
            text = "Filmography",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontalPadding, vertical = 16.dp)
        )

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
            // Grid layout using LazyVerticalGrid
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                verticalArrangement = Arrangement.spacedBy(cardSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((cardHeight + cardSpacing) * ((mediaItems.size + columns - 1) / columns) + 80.dp)
            ) {
                items(mediaItems) { mediaItem ->
                    MobileCastMediaCard(
                        mediaItem = mediaItem,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Touch-friendly media card for mobile cast detail screen.
 */
@Composable
private fun MobileCastMediaCard(
    mediaItem: MediaItem,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val posterUrl = mediaItem.posterPath?.let { path ->
        "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}$path"
    }

    val isMovie = mediaItem is MediaItem.MovieItem

    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable {
                if (isMovie) onMovieClick(mediaItem.id)
                else onTvShowClick(mediaItem.id)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (posterUrl != null) {
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
                    Icon(
                        imageVector = if (isMovie) Icons.Default.Movie else Icons.Default.Tv,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Type Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
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
                    fontSize = 10.sp
                )
            }

            // Rating Badge
            if ((mediaItem.voteAverage ?: 0.0) > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1f", mediaItem.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = mediaItem.title,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (mediaItem.releaseDate != null && mediaItem.releaseDate!!.isNotBlank()) {
            Text(
                text = mediaItem.releaseDate!!.take(4),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
