package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Mobile-optimized screen that displays movies or TV shows filtered by a specific genre.
 * Fetches content from TMDB API based on the selected genre.
 *
 * @param mediaType The type of media ("movie" or "tv")
 * @param genreId The TMDB genre ID
 * @param genreName The name of the genre to display as title
 * @param onBackClick Callback for back button navigation
 * @param onMovieClick Callback when a movie is clicked
 * @param onTvShowClick Callback when a TV show is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileGenreContentScreen(
    mediaType: String,
    genreId: Int,
    genreName: String,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TmdbRepository() }
    val scope = rememberCoroutineScope()

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var tvShows by remember { mutableStateOf<List<TvShow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }

    // Get screen configuration to calculate responsive grid
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val horizontalPadding = 16.dp
    val spacing = 10.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 120.dp
    val actualColumns = maxOf(3, minOf(5, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))
    val calculatedCardWidth = (availableWidth - (spacing * (actualColumns - 1))) / actualColumns
    val calculatedCardHeight = calculatedCardWidth * 1.5f

    // Remember grid state for scroll detection
    val gridState = rememberLazyGridState()

    // Load initial content
    LaunchedEffect(mediaType, genreId) {
        isLoading = true
        error = null
        currentPage = 1
        hasMorePages = true

        if (mediaType == "movie") {
            repository.getMoviesByGenre(genreId, 1)
                .onSuccess { response ->
                    movies = response.results
                    hasMorePages = response.page < response.totalPages
                    isLoading = false
                }
                .onFailure { e ->
                    error = e.message ?: "Failed to load content"
                    isLoading = false
                }
        } else {
            repository.getTvShowsByGenre(genreId, 1)
                .onSuccess { response ->
                    tvShows = response.results
                    hasMorePages = response.page < response.totalPages
                    isLoading = false
                }
                .onFailure { e ->
                    error = e.message ?: "Failed to load content"
                    isLoading = false
                }
        }
    }

    // Detect when user scrolls to the end and trigger pagination
    LaunchedEffect(gridState, currentPage, hasMorePages) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1

            // Check if we're near the end
            val visibleRows = layoutInfo.visibleItemsInfo.size
            val threshold = maxOf(1, visibleRows / 2)

            lastVisibleItemIndex >= (totalItemsNumber - (actualColumns * threshold)) && totalItemsNumber > 0
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !isLoading && !isLoadingMore && hasMorePages) {
                isLoadingMore = true
                currentPage++

                if (mediaType == "movie") {
                    repository.getMoviesByGenre(genreId, currentPage)
                        .onSuccess { response ->
                            movies = movies + response.results
                            hasMorePages = response.page < response.totalPages
                            isLoadingMore = false
                        }
                        .onFailure {
                            isLoadingMore = false
                        }
                } else {
                    repository.getTvShowsByGenre(genreId, currentPage)
                        .onSuccess { response ->
                            tvShows = tvShows + response.results
                            hasMorePages = response.page < response.totalPages
                            isLoadingMore = false
                        }
                        .onFailure {
                            isLoadingMore = false
                        }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = genreName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
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
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieLoadingView(size = 150.dp)
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = error!!,
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    isLoading = true
                                    error = null
                                    scope.launch {
                                        if (mediaType == "movie") {
                                            repository.getMoviesByGenre(genreId, 1)
                                                .onSuccess { response ->
                                                    movies = response.results
                                                    hasMorePages = response.page < response.totalPages
                                                    isLoading = false
                                                }
                                                .onFailure { e ->
                                                    error = e.message ?: "Failed to load content"
                                                    isLoading = false
                                                }
                                        } else {
                                            repository.getTvShowsByGenre(genreId, 1)
                                                .onSuccess { response ->
                                                    tvShows = response.results
                                                    hasMorePages = response.page < response.totalPages
                                                    isLoading = false
                                                }
                                                .onFailure { e ->
                                                    error = e.message ?: "Failed to load content"
                                                    isLoading = false
                                                }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                else -> {
                    val items = if (mediaType == "movie") {
                        @Suppress("UNCHECKED_CAST")
                        (movies as List<Any>)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (tvShows as List<Any>)
                    }

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No $mediaType found for $genreName",
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(actualColumns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                top = 8.dp,
                                bottom = 80.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            items(items) { item ->
                                when (item) {
                                    is Movie -> {
                                        MobileMovieCard(
                                            movie = item,
                                            onClick = { onMovieClick(item.id) },
                                            modifier = Modifier
                                                .width(calculatedCardWidth)
                                                .height(calculatedCardHeight)
                                        )
                                    }
                                    is TvShow -> {
                                        MobileTvShowCard(
                                            tvShow = item,
                                            onClick = { onTvShowClick(item.id) },
                                            modifier = Modifier
                                                .width(calculatedCardWidth)
                                                .height(calculatedCardHeight)
                                        )
                                    }
                                }
                            }
                        }

                        // Loading more indicator
                        if (isLoadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LottieLoadingView(size = 40.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}