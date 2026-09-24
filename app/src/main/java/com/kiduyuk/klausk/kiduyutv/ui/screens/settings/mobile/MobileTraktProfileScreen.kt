package com.kiduyuk.klausk.kiduyutv.ui.screens.settings.mobile

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktUser
import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktUserStats
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiClient
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.rememberPhoneInterstitialBackClick
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextTertiary
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mobile Trakt Profile Screen - displays user profile and tabs for Collection, Watchlist and Recommendations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTraktProfileScreen(
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Collection", "Watchlist", "Recommended", "Stats")
    val handleBackClick = rememberPhoneInterstitialBackClick(onBackClick)

    BackHandler(onBack = handleBackClick)

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
                    Log.i("MobileTraktProfile", "Profile loaded: $profile")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trakt Profile", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .padding(bottom = innerPadding.calculateBottomPadding(), top = innerPadding.calculateTopPadding())
        ) {
            // Profile Header
            if (profile != null) {
                MobileProfileHeader(profile = profile!!, avatarUrl = avatarUrl)
            } else if (isLoadingProfile) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryRed, modifier = Modifier.size(24.dp))
                }
            } else if (profileError != null) {
                Text(
                    text = profileError!!,
                    color = Color.Red,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BackgroundDark,
                contentColor = PrimaryRed,
                divider = { HorizontalDivider(color = TextTertiary.copy(alpha = 0.1f)) },
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryRed
                    )
                },
                modifier = Modifier.height(48.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) PrimaryRed else TextSecondary,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> MobileTraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "collection",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    1 -> MobileTraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "watchlist",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    2 -> MobileTraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "recommendations",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    3 -> MobileTraktStatsTabContent()
                }
            }
        }
    }
}

@Composable
private fun MobileTraktStatsTabContent() {
    var stats by remember { mutableStateOf<TraktUserStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        Log.i("MobileTraktProfile", "Stats tab opened; requesting Trakt user stats")
        try {
            val token = TraktAuthManager.getValidAccessToken()
                ?: throw IllegalStateException("Not authenticated with Trakt.tv")
            val authorization = "Bearer $token"
            val profileResponse = TraktApiClient.apiService.getUserProfile(authorization)
            if (!profileResponse.isSuccessful) {
                error = "Failed to identify Trakt profile: ${profileResponse.code()}"
                Log.w(
                    "MobileTraktProfile",
                    "Stats profile request failed: HTTP ${profileResponse.code()} ${profileResponse.message()}"
                )
            } else {
                val username = profileResponse.body()?.username?.trim().orEmpty()
                if (username.isBlank()) {
                    error = "Trakt profile did not include a username"
                    Log.w("MobileTraktProfile", "Stats profile request succeeded without a username")
                } else {
                    Log.i("MobileTraktProfile", "Stats: resolved Trakt username=$username; requesting concrete stats endpoint")
                    val response = TraktApiClient.apiService.getUserStatsForUser(username, authorization)
                    if (response.isSuccessful) {
                        stats = response.body()
                        if (stats == null) {
                            error = "Trakt returned an empty stats response"
                            Log.w(
                                "MobileTraktProfile",
                                "Stats request for username=$username succeeded but response body was empty: HTTP ${response.code()}"
                            )
                        } else {
                            Log.i(
                                "MobileTraktProfile",
                                "Stats loaded: movies=${stats!!.movies.watched}, shows=${stats!!.shows.watched}, episodes=${stats!!.episodes.watched}"
                            )
                        }
                    } else {
                        error = "Failed to load stats: ${response.code()}"
                        Log.w("MobileTraktProfile", "Stats request for username=$username failed: HTTP ${response.code()} ${response.message()}")
                    }
                }
            }
        } catch (exception: Exception) {
            error = exception.message ?: "Unable to load Trakt stats"
            Log.e("MobileTraktProfile", "Stats request threw an exception", exception)
        } finally {
            isLoading = false
            Log.d("MobileTraktProfile", "Stats request finished; loading=$isLoading, hasStats=${stats != null}, error=${error != null}")
        }
    }

    when {
        isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = PrimaryRed, modifier = Modifier.size(32.dp))
                Text("Loading Trakt stats…", color = TextSecondary, fontSize = 14.sp)
            }
        }
        error != null -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(error!!, color = Color.Red, textAlign = TextAlign.Center)
        }
        stats != null -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { MobileTraktStatCard("Movies watched", stats!!.movies.watched.toString(), "${stats!!.movies.plays} plays · ${formatMobileTraktMinutes(stats!!.movies.minutes)}") }
            item { MobileTraktStatCard("Shows watched", stats!!.shows.watched.toString(), "${stats!!.shows.collected} collected") }
            item { MobileTraktStatCard("Episodes watched", stats!!.episodes.watched.toString(), "${stats!!.episodes.plays} plays · ${formatMobileTraktMinutes(stats!!.episodes.minutes)}") }
            item { MobileTraktStatCard("Ratings", stats!!.ratings.total.toString(), "Movies ${stats!!.movies.ratings} · Shows ${stats!!.shows.ratings}") }
            item { MobileTraktStatCard("Followers", stats!!.network.followers.toString(), "Following ${stats!!.network.following} · Friends ${stats!!.network.friends}") }
        }
    }
}

@Composable
private fun MobileTraktStatCard(label: String, value: String, detail: String) {
    Column(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(CardDark)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = TextSecondary, fontSize = 11.sp)
    }
}

private fun formatMobileTraktMinutes(minutes: Int): String {
    val hours = minutes / 60
    return if (hours >= 24) "${hours / 24}d ${hours % 24}h" else "${hours}h"
}

@Composable
private fun MobileProfileHeader(profile: TraktUser, avatarUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CardDark)
                .border(2.dp, PrimaryRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Profile Avatar",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.username,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!profile.name.isNullOrBlank()) {
                Text(
                    text = profile.name!!,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!profile.about.isNullOrBlank()) {
                Text(
                    text = profile.about!!,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (profile.vip) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "VIP",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileTraktMediaTabContent(
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
                        Log.e("MobileTraktProfile", "Failed to fetch $tabType $type: ${e.message}")
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
                        } catch (e: Exception) { }
                    } else if (item.posterPath == null && cachedPath != null) {
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LottieLoadingView(size = 150.dp)
        }
    } else if (error != null && mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text = "Error: $error", color = Color.Red, textAlign = TextAlign.Center)
        }
    } else if (mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text = "No items found in your $tabType.", color = TextSecondary, textAlign = TextAlign.Center)
        }
    } else {
        MobileTraktMediaGrid(
            items = mediaItems,
            onMovieClick = onMovieClick,
            onTvShowClick = onTvShowClick
        )
    }
}

@Composable
private fun MobileTraktMediaGrid(
    items: List<MyListItem>,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val horizontalPadding = 16.dp
    val spacing = 10.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 110.dp
    val actualColumns = maxOf(3, minOf(5, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))
    val calculatedCardWidth = (availableWidth - (spacing * (actualColumns - 1))) / actualColumns
    val calculatedCardHeight = calculatedCardWidth * 1.5f

    LazyVerticalGrid(
        columns = GridCells.Fixed(actualColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 12.dp,
            bottom = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(items) { item ->
            if (item.type == "movie") {
                MobileMovieCard(
                    movie = Movie(
                        id = item.id,
                        title = item.title,
                        overview = "",
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage,
                        releaseDate = "",
                        genreIds = emptyList(),
                        popularity = 0.0
                    ),
                    onClick = { onMovieClick(item.id) },
                    modifier = Modifier
                        .width(calculatedCardWidth)
                        .height(calculatedCardHeight)
                )
            } else {
                MobileTvShowCard(
                    tvShow = TvShow(
                        id = item.id,
                        name = item.title,
                        overview = "",
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage,
                        firstAirDate = "",
                        genreIds = emptyList(),
                        popularity = 0.0
                    ),
                    onClick = { onTvShowClick(item.id) },
                    modifier = Modifier
                        .width(calculatedCardWidth)
                        .height(calculatedCardHeight)
                )
            }
        }
    }
}
