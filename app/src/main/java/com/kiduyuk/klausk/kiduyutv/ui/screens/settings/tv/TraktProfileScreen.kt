package com.kiduyuk.klausk.kiduyutv.ui.screens.settings.tv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktUser
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiClient
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trakt Profile Screen - displays user profile information from Trakt.tv
 * Now with tabs for Collection, Watchlist and Recommendations.
 */
@Composable
fun TraktProfileScreen(
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit = {},
    onTvShowClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Collection", "Watchlist", "Recommended")

    var isLoadingProfile by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<TraktUser?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }

    val traktAuthManager = remember(context) {
        com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager.getInstance(context)
    }
    val avatarUrl by traktAuthManager.userAvatarUrl.collectAsState()

    val traktRepository = remember {
        TraktRepository(TraktApiClient.apiService, TraktAuthManager)
    }

    // Fetch profile on load
    LaunchedEffect(Unit) {
        isLoadingProfile = true
        profileError = null
        try {
            val token = TraktAuthManager.getValidAccessToken()
            if (token != null) {
                val response = TraktApiClient.apiService.getUserProfile(token = "Bearer $token")
                if (response.isSuccessful) {
                    profile = response.body()
                    Log.i("TraktProfileScreen", "Profile loaded: $profile")
                } else {
                    profileError = "Failed to load profile: ${response.code()}"
                }
            } else {
                profileError = "Not authenticated with Trakt.tv"
            }
        } catch (e: Exception) {
            profileError = "Error: ${e.message}"
        } finally {
            isLoadingProfile = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(color = CardDark, shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        color = PrimaryRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Trakt.tv",
                    color = PrimaryRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Info Header (Avatar and Username)
        if (profile != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CardDark)
                        .border(2.dp, PrimaryRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile Avatar",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Column {
                    Text(
                        text = profile!!.username,
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!profile!!.name.isNullOrBlank()) {
                        Text(
                            text = profile!!.name!!,
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else if (isLoadingProfile) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = PrimaryRed)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Loading profile...", color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = PrimaryRed,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryRed
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) PrimaryRed else TextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> TraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "collection",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    1 -> TraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "watchlist",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    2 -> TraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "recommendations",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = PrimaryRed,
                strokeWidth = 3.dp
            )
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onBackClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = message,
                color = Color(0xFFFF6B6B),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBackClick) {
                Text("Go Back")
            }
        }
    }
}

@Composable
private fun TraktMediaTabContent(
    traktRepository: TraktRepository,
    tabType: String,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val tmdbApiService = remember { ApiClient.tmdbApiService }
    val posterCache = remember { mutableStateMapOf<String, String?>() }
    var mediaItems by remember { mutableStateOf<List<MyListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tabType) {
        isLoading = true
        error = null
        try {
            val results = mutableListOf<MyListItem>()
            val processedIds = mutableSetOf<String>()

            // Fetch both movies and shows
            val types = listOf("movies", "shows")
            
            for (type in types) {
                val flow = when (tabType) {
                    "collection" -> traktRepository.getCollection(type)
                    "watchlist" -> traktRepository.getWatchlist(type)
                    else -> traktRepository.getRecommendations(type)
                }

                flow.collect { result ->
                    result.onSuccess { items ->
                        items.forEach { item ->
                            val movie = (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktCollectionItem)?.movie
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktWatchlistItem)?.movie
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktRecommendation)?.movie
                            
                            val show = (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktCollectionItem)?.show
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktWatchlistItem)?.show
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktRecommendation)?.show

                            if (movie != null) {
                                movie.ids.tmdb?.let { tmdbId ->
                                    val key = "movie-$tmdbId"
                                    if (processedIds.add(key)) {
                                        results.add(MyListItem(
                                            id = tmdbId,
                                            title = movie.title,
                                            posterPath = null,
                                            type = "movie",
                                            voteAverage = movie.rating ?: 0.0
                                        ))
                                    }
                                }
                            } else if (show != null) {
                                show.ids.tmdb?.let { tmdbId ->
                                    val key = "tv-$tmdbId"
                                    if (processedIds.add(key)) {
                                        results.add(MyListItem(
                                            id = tmdbId,
                                            title = show.title,
                                            posterPath = null,
                                            type = "tv",
                                            voteAverage = show.rating ?: 0.0
                                        ))
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        Log.e("TraktProfileScreen", "Failed to fetch $tabType $type: ${e.message}")
                    }
                }
            }

            mediaItems = results.toList()
            
            // Background loading for posters and ratings
            withContext(Dispatchers.IO) {
                mediaItems.forEachIndexed { index, item ->
                    val key = "${item.type}-${item.id}"
                    val cachedPath = posterCache[key]
                    val needsPoster = item.posterPath == null && cachedPath == null
                    val needsRating = item.voteAverage == 0.0

                    if (needsPoster || needsRating) {
                        try {
                            val (path, rating) = if (item.type == "movie") {
                                val detail = tmdbApiService.getMovieDetail(item.id)
                                detail.posterPath to detail.voteAverage
                            } else {
                                val detail = tmdbApiService.getTvShowDetail(item.id)
                                detail.posterPath to detail.voteAverage
                            }

                            withContext(Dispatchers.Main) {
                                if (path != null) posterCache[key] = path
                                mediaItems = mediaItems.toMutableList().apply {
                                    this[index] = this[index].copy(
                                        posterPath = path ?: cachedPath,
                                        voteAverage = if (needsRating) rating else item.voteAverage
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TraktProfileScreen", "Failed to fetch TMDB details for ${item.type}-${item.id}: ${e.message}")
                        }
                    } else if (item.posterPath == null && cachedPath != null) {
                        // Use cached poster if available
                        withContext(Dispatchers.Main) {
                            mediaItems = mediaItems.toMutableList().apply {
                                this[index] = this[index].copy(posterPath = cachedPath)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    if (isLoading && mediaItems.isEmpty()) {
        LoadingIndicator("Loading $tabType...")
    } else if (error != null && mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Error: $error", color = Color.Red)
        }
    } else if (mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No items found in your $tabType.", color = TextSecondary)
        }
    } else {
        TraktMediaGrid(
            items = mediaItems,
            onMovieClick = onMovieClick,
            onTvShowClick = onTvShowClick
        )
    }
}

@Composable
private fun TraktMediaGrid(
    items: List<MyListItem>,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val spacing = 16.dp
    val columns = 6
    val calculatedCardWidth = (screenWidth - 100.dp - (spacing * (columns - 1))) / columns
    val calculatedCardHeight = calculatedCardWidth * 1.5f

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(items) { item ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            if (item.type == "movie") {
                MovieCard(
                    movie = Movie(
                        id = item.id,
                        title = item.title,
                        overview = "",
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage,
                        releaseDate = null,
                        genreIds = null,
                        popularity = 0.0
                    ),
                    isSelected = isFocused,
                    onClick = { onMovieClick(item.id) },
                    modifier = Modifier
                        .width(calculatedCardWidth)
                        .height(calculatedCardHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onMovieClick(item.id) }
                )
            } else {
                TvShowCard(
                    tvShow = TvShow(
                        id = item.id,
                        name = item.title,
                        overview = "",
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage,
                        firstAirDate = null,
                        genreIds = null,
                        popularity = 0.0
                    ),
                    isSelected = isFocused,
                    onClick = { onTvShowClick(item.id) },
                    modifier = Modifier
                        .width(calculatedCardWidth)
                        .height(calculatedCardHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTvShowClick(item.id) }
                )
            }
        }
    }
}

private fun formatTraktDate(dateString: String): String {
    return try {
        // Trakt date format: 2010-09-01T12:34:56.000Z
        val parts = dateString.split("T")
        if (parts.isNotEmpty()) {
            parts[0] // Return just the date part
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}
