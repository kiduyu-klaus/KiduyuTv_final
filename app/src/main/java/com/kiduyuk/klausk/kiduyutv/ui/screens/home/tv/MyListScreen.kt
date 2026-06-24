package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiClient
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

// ── SharedPreferences cache helpers ──────────────────────────────────────────

private const val PREFS_NAME = "trakt_watched_cache"
private const val KEY_WATCHED_ITEMS = "watched_items_json"
private const val KEY_CACHE_TIMESTAMP = "cache_timestamp_ms"
private const val KEY_CACHED_PAGE = "cached_watched_page"
private const val KEY_CACHED_HAS_MORE = "cached_watched_has_more"

// Number of items to fetch per page from Trakt
private const val WATCHED_PAGE_SIZE = 20

// How many items from the end of the grid trigger a "load more" request
private const val WATCHED_END_THRESHOLD = 4

/**
 * Serializable mirror of [MyListItem] used purely for JSON persistence.
 * We keep it here so we don't need to annotate the ViewModel model class.
 */
@Serializable
private data class CachedWatchedItem(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val type: String,
    val voteAverage: Double = 0.0
)

private val cacheJson = Json { ignoreUnknownKeys = true }

/** Write the full watched list to SharedPreferences as JSON. */
private fun saveWatchedCache(
    context: Context,
    items: List<MyListItem>,
    lastLoadedPage: Int = 0,
    hasMore: Boolean = true
) {
    try {
        val cached = items.map {
            CachedWatchedItem(it.id, it.title, it.posterPath, it.type, it.voteAverage)
        }
        val json = cacheJson.encodeToString(cached)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCHED_ITEMS, json)
            .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            .putInt(KEY_CACHED_PAGE, lastLoadedPage)
            .putBoolean(KEY_CACHED_HAS_MORE, hasMore)
            .apply()
        Log.i("MyListScreen", "Saved ${items.size} watched items to cache (page=$lastLoadedPage, hasMore=$hasMore)")
    } catch (e: Exception) {
        Log.e("MyListScreen", "Failed to save watched cache: ${e.message}")
    }
}

/** Read the cached watched list from SharedPreferences. Returns null if empty or missing. */
private data class WatchedCache(
    val items: List<MyListItem>,
    val lastLoadedPage: Int,
    val hasMore: Boolean
)

private fun loadWatchedCache(context: Context): WatchedCache? {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_WATCHED_ITEMS, null) ?: return null
        val cached = cacheJson.decodeFromString<List<CachedWatchedItem>>(json)
        if (cached.isEmpty()) null
        else {
            val items = cached.map {
                MyListItem(
                    id = it.id,
                    title = it.title,
                    posterPath = it.posterPath,
                    type = it.type,
                    voteAverage = it.voteAverage
                )
            }
            val lastPage = prefs.getInt(KEY_CACHED_PAGE, 0)
            val hasMore = prefs.getBoolean(KEY_CACHED_HAS_MORE, true)
            Log.i("MyListScreen", "Loaded ${items.size} watched items from cache (page=$lastPage, hasMore=$hasMore)")
            WatchedCache(items, lastPage, hasMore)
        }
    } catch (e: Exception) {
        Log.e("MyListScreen", "Failed to load watched cache: ${e.message}")
        null
    }
}

/** Clear the watched cache (e.g. on logout). */
private fun clearWatchedCache(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    Log.i("MyListScreen", "Watched cache cleared")
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Composable function for the "My List" screen, displaying items saved by the user.
 * It observes the [HomeViewModel] for the list of saved items and allows navigation to their details
 * or removal from the list.
 *
 * The "Watched" tab (only visible when authenticated with Trakt) supports paginated loading
 * of [WATCHED_PAGE_SIZE] items per page with infinite-scroll auto-loading.
 *
 * @param onMovieClick Lambda to navigate to the detail screen of a movie.
 * @param onTvShowClick Lambda to navigate to the detail screen of a TV show.
 * @param onNavigate Lambda to handle navigation between top-level screens.
 * @param onSearchClick Lambda to navigate to the search screen.
 * @param viewModel The [HomeViewModel] instance providing data for the screen.
 */
@Composable
fun MyListScreen(
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationClick: (id: Int, type: String) -> Unit = { _, _ -> },
    onCompanyClick: (Int, String) -> Unit = { _, _ -> },
    onNetworkClick: (Int, String) -> Unit = { _, _ -> },
    onCastClick: (CastMember) -> Unit = { _ -> },
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect My List from the global manager.
    val myList by MyListManager.myList.collectAsState()

    // Trakt integration
    val traktRepository = remember {
        TraktRepository(TraktApiClient.apiService, TraktAuthManager)
    }
    val tmdbApiService = remember { ApiClient.tmdbApiService }
    val isTraktConnected by TraktAuthManager.isTraktAuthenticated.collectAsState()

    // ── Watched tab pagination state ────────────────────────────────────────
    var watchedItems by remember { mutableStateOf<List<MyListItem>>(emptyList()) }
    var isInitialLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreWatched by remember { mutableStateOf(true) }
    var currentWatchedPage by remember { mutableIntStateOf(0) }
    val processedTmdbIds = remember { mutableSetOf<String>() }

    // Tracks how many items we requested this page so we can show a footer spinner
    val gridState = rememberLazyGridState()

    // ── Enrichment helper: process a single Trakt history page into MyListItems ──
    suspend fun enrichHistoryPage(
        history: List<TraktHistoryItem>
    ): List<MyListItem> = withContext(Dispatchers.IO) {
        val pageItems = mutableListOf<MyListItem>()

        history.forEach { item ->
            when (item.type) {
                "movie" -> {
                    val movie = item.movie
                    val tmdbId = movie?.ids?.tmdb
                    if (tmdbId != null) {
                        val cacheKey = "movie-$tmdbId"
                        if (processedTmdbIds.add(cacheKey)) {
                            var posterPath: String? = null
                            var rating = movie.rating ?: 0.0
                            try {
                                val detail = tmdbApiService.getMovieDetail(tmdbId)
                                posterPath = detail.posterPath
                                if (rating == 0.0) rating = detail.voteAverage
                            } catch (e: Exception) {
                                Log.e("MyListScreen", "TMDB movie detail failed for $tmdbId: ${e.message}")
                            }
                            pageItems.add(
                                MyListItem(
                                    id = tmdbId,
                                    title = movie.title,
                                    posterPath = posterPath,
                                    type = "movie",
                                    voteAverage = rating
                                )
                            )
                        }
                    }
                }
                "episode", "show" -> {
                    val show = item.show
                    val tmdbId = show?.ids?.tmdb
                    if (tmdbId != null) {
                        val cacheKey = "tv-$tmdbId"
                        if (processedTmdbIds.add(cacheKey)) {
                            var posterPath: String? = null
                            var rating = show.rating ?: 0.0
                            try {
                                val detail = tmdbApiService.getTvShowDetail(tmdbId)
                                posterPath = detail.posterPath
                                if (rating == 0.0) rating = detail.voteAverage
                            } catch (e: Exception) {
                                Log.e("MyListScreen", "TMDB tv detail failed for $tmdbId: ${e.message}")
                            }
                            pageItems.add(
                                MyListItem(
                                    id = tmdbId,
                                    title = show.title,
                                    posterPath = posterPath,
                                    type = "tv",
                                    voteAverage = rating
                                )
                            )
                        }
                    }
                }
            }
        }
        pageItems
    }

    // ── Core "load next page" function shared by initial fetch + infinite scroll ──
    fun loadNextWatchedPage() {
        if (!isTraktConnected) return
        if (isLoadingMore || isInitialLoading) return
        if (!hasMoreWatched) return

        val nextPage = currentWatchedPage + 1
        isLoadingMore = true

        coroutineScope.launch {
            try {
                traktRepository
                    .getTraktWatchHistory(page = nextPage, limit = WATCHED_PAGE_SIZE)
                    .collect { result ->
                        result.fold(
                            onSuccess = { history ->
                                if (history.isEmpty()) {
                                    Log.i("MyListScreen", "Page $nextPage empty — no more watched items")
                                    hasMoreWatched = false
                                } else {
                                    val newItems = enrichHistoryPage(history)
                                    Log.i(
                                        "MyListScreen",
                                        "Page $nextPage: fetched=${history.size}, newUnique=${newItems.size}"
                                    )

                                    withContext(Dispatchers.Main) {
                                        watchedItems = watchedItems + newItems
                                        currentWatchedPage = nextPage
                                        // If fewer items than the page size were returned,
                                        // we have reached the end of available history.
                                        if (history.size < WATCHED_PAGE_SIZE) {
                                            hasMoreWatched = false
                                        }
                                        // Persist the merged list (best-effort)
                                        saveWatchedCache(
                                            context,
                                            watchedItems,
                                            currentWatchedPage,
                                            hasMoreWatched
                                        )
                                    }
                                }
                            },
                            onFailure = { error ->
                                Log.e(
                                    "MyListScreen",
                                    "Page $nextPage fetch failed: ${error.message}"
                                )
                                hasMoreWatched = false
                            }
                        )
                    }
            } catch (e: Exception) {
                Log.e("MyListScreen", "Error during watched pagination: ${e.message}")
                hasMoreWatched = false
            } finally {
                isLoadingMore = false
                isInitialLoading = false
            }
        }
    }

    // ── Reset / initial-load orchestration ──────────────────────────────────
    LaunchedEffect(isTraktConnected) {
        Log.i("MyListScreen", "isTraktConnected: $isTraktConnected")

        if (!isTraktConnected) {
            Log.i("MyListScreen", "Trakt not connected, clearing history")
            watchedItems = emptyList()
            hasMoreWatched = true
            currentWatchedPage = 0
            processedTmdbIds.clear()
            clearWatchedCache(context)
            return@LaunchedEffect
        }

        // 1) Try cache first for instant display
        val cached = loadWatchedCache(context)
        if (cached != null && cached.items.isNotEmpty()) {
            Log.i(
                "MyListScreen",
                "Cache hit — displaying ${cached.items.size} items (page=${cached.lastLoadedPage}, hasMore=${cached.hasMore})"
            )
            watchedItems = cached.items
            currentWatchedPage = cached.lastLoadedPage
            hasMoreWatched = cached.hasMore
            cached.items.forEach { item ->
                processedTmdbIds.add("${item.type}-${item.id}")
            }
            // Always continue loading from the next page so that items added since
            // the last save appear as the user scrolls. (Silent background append.)
            if (hasMoreWatched) {
                loadNextWatchedPage()
            }
            return@LaunchedEffect
        }

        // 2) No cache — fetch the first page and show a spinner
        Log.i("MyListScreen", "No cache — fetching first page of Trakt watch history")
        isInitialLoading = true
        loadNextWatchedPage()
    }

    // ── Infinite scroll: when user nears the end of the grid, fetch more ───
    LaunchedEffect(gridState, isTraktConnected) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (
                isTraktConnected &&
                hasMoreWatched &&
                !isLoadingMore &&
                !isInitialLoading &&
                total > 0 &&
                lastVisible >= total - WATCHED_END_THRESHOLD
            ) {
                Log.i(
                    "MyListScreen",
                    "End-of-grid reached (last=$lastVisible, total=$total) — loading more"
                )
                loadNextWatchedPage()
            }
        }
    }

    // Categorize items
    val movies = myList.filter { it.type == "movie" }
    val tvShows = myList.filter { it.type == "tv" }
    val companies = myList.filter { it.type == "company" }
    val networks = myList.filter { it.type == "network" }
    val castMembers = myList.filter { it.type == "cast" }

    // Tab state - Watched tab is first if Trakt is connected
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val baseTabs = listOf("Movies", "TV Shows", "Companies", "Networks", "Cast")
    val tabs = if (isTraktConnected) listOf("Watched") + baseTabs else baseTabs

    // Responsive grid
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val horizontalPadding = 25.dp
    val spacing = 10.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 100.dp
    val actualColumns = maxOf(4, minOf(8, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))
    val calculatedCardWidth = (availableWidth - (spacing * (actualColumns - 1))) / actualColumns
    val calculatedCardHeight = calculatedCardWidth * 1.8f

    // Whether the currently visible tab is the Watched tab
    val isWatchedTabSelected = isTraktConnected && selectedTabIndex == 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopBar(
            selectedRoute = "my_list",
            onNavItemClick = { route -> onNavigate(route) },
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onNotificationClick = { id, type ->
                if (type == "movie") onMovieClick(id) else onTvShowClick(id)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
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
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else TextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // ── Watched tab: full-screen spinner for the very first page ────
            if (isWatchedTabSelected && isInitialLoading && watchedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Loading watched history…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                return@Column  // Don't render the grid while initial-loading
            }

            // Current list for the selected tab
            val currentList = when {
                isWatchedTabSelected -> watchedItems
                else -> {
                    val effectiveIndex = if (isTraktConnected) selectedTabIndex - 1 else selectedTabIndex
                    when (effectiveIndex) {
                        0 -> movies
                        1 -> tvShows
                        2 -> companies
                        3 -> networks
                        4 -> castMembers
                        else -> emptyList()
                    }
                }
            }

            if (currentList.isEmpty() && !(isWatchedTabSelected && isLoadingMore)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyMessage = when {
                        isWatchedTabSelected -> "No watched history from Trakt yet."
                        else -> "No ${tabs[selectedTabIndex]} saved yet."
                    }
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )

                        // Watched tab empty state: offer a manual refresh button
                        if (isWatchedTabSelected) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    // Wipe cache so the full paginated fetch runs again
                                    clearWatchedCache(context)
                                    watchedItems = emptyList()
                                    processedTmdbIds.clear()
                                    hasMoreWatched = true
                                    currentWatchedPage = 0
                                    isInitialLoading = true
                                    loadNextWatchedPage()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Fetch Watched History")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(actualColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(currentList) { item ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        when (item.type) {
                            "movie" -> {
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
                            }
                            "tv" -> {
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
                            "company", "network" -> {
                                Card(
                                    modifier = Modifier
                                        .width(calculatedCardWidth)
                                        .height(calculatedCardHeight / 2)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            if (item.type == "company") onCompanyClick(item.id, item.title)
                                            else onNetworkClick(item.id, item.title)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFocused) MaterialTheme.colorScheme.primary else CardDark
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!item.posterPath.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.LOGO_SIZE}${item.posterPath}",
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp)
                                            )
                                        } else {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary,
                                                modifier = Modifier.padding(8.dp),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            "cast" -> {
                                Column(
                                    modifier = Modifier
                                        .width(calculatedCardWidth)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            onCastClick(
                                                CastMember(
                                                    id = item.id,
                                                    name = item.title,
                                                    character = null,
                                                    profilePath = item.posterPath,
                                                    knownForDepartment = null,
                                                    popularity = null,
                                                    order = null,
                                                    overview = null
                                                )
                                            )
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .border(
                                                width = if (isFocused) 2.dp else 0.dp,
                                                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        AsyncImage(
                                            model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${item.posterPath}",
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isFocused) MaterialTheme.colorScheme.primary else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // ── Footer: "load more" spinner / status row ────────────
                    if (isWatchedTabSelected) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            WatchedFooter(
                                isLoadingMore = isLoadingMore,
                                hasMore = hasMoreWatched,
                                totalItems = watchedItems.size,
                                columns = actualColumns
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Footer for the Watched tab grid ──────────────────────────────────────────

/**
 * Footer shown at the bottom of the Watched grid. Displays a spinner while
 * the next page is loading, a "no more items" hint when the user has reached
 * the end of their Trakt history, and otherwise nothing (the user is not
 * near the end yet, so we keep the UI clean).
 */
@Composable
private fun WatchedFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    totalItems: Int,
    columns: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingMore -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Loading more…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            !hasMore && totalItems > 0 -> {
                Text(
                    text = "You've reached the end ($totalItems items)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            // Else: no spinner while user is still scrolling; only show a footer
            // when we are actively loading or definitively done.
        }
    }
}

// ── Private item card (used in preview) ──────────────────────────────────────

/**
 * Composable function to display a single item in the "My List" screen.
 */
@Composable
private fun MyListItemCard(
    item: MyListItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                color = CardDark,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncImage(
            model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${item.posterPath}",
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(80.dp)
                .height(88.dp)
                .background(color = SurfaceDark, shape = RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (item.type == "movie") "Movie" else "TV Show",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MyListScreenPreview() {
    KiduyuTvTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 25.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(5) { index ->
                    MyListItemCard(
                        item = MyListItem(
                            id = index + 1,
                            title = "My List Item ${index + 1}",
                            posterPath = null,
                            type = if (index % 2 == 0) "movie" else "tv"
                        ),
                        onClick = {},
                        onRemove = { }
                    )
                }
            }
        }
    }
}
