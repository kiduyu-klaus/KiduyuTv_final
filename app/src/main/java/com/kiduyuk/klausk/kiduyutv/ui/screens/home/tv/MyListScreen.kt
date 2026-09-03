package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiClient
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── SharedPreferences cache helpers ──────────────────────────────────────────

private const val TAG = "MyListScreen"
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
        Log.d(TAG, "Saved watched cache: items=${items.size}, page=$lastLoadedPage, hasMore=$hasMore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watched cache: ${e.message}", e)
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
            Log.d(TAG, "Loaded watched cache payload: items=${items.size}")
            val lastPage = prefs.getInt(KEY_CACHED_PAGE, 0)
            val hasMore = prefs.getBoolean(KEY_CACHED_HAS_MORE, true)
            Log.d(TAG, "Loaded watched cache metadata: page=$lastPage, hasMore=$hasMore")
            WatchedCache(items, lastPage, hasMore)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load watched cache: ${e.message}", e)
        null
    }
}

/** Clear the watched cache (e.g. on logout). */
private fun clearWatchedCache(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    Log.i(TAG, "Watched cache cleared")
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
    var watchedHistoryTotal by remember { mutableStateOf<Int?>(null) }
    var watchedHistoryLoaded by remember { mutableIntStateOf(0) }
    val processedTmdbIds = remember { mutableSetOf<String>() }

    // Tracks how many items we requested this page so we can show a footer spinner
    val gridState = rememberLazyGridState()

    // ── Enrichment helper: append basic records immediately, then enrich them ──
    suspend fun enrichHistoryPage(
        history: List<TraktHistoryItem>,
        onItemProcessed: suspend () -> Unit,
        onItemAdded: suspend (MyListItem) -> Unit,
        onItemUpdated: suspend (MyListItem) -> Unit
    ): List<MyListItem> = withContext(Dispatchers.IO) {
        Log.d(TAG, "[enrichHistoryPage] Starting on thread=${Thread.currentThread().name}, records=${history.size}")
        val pageItems = mutableListOf<MyListItem>()

        history.forEach { item ->
            Log.d(TAG, "[enrichHistoryPage] Processing Trakt history record: type=${item.type}, watchedAt=${item.watchedAt}")
            val tmdbId: Int?
            val type: String
            val title: String
            val traktRating: Double

            when (item.type) {
                "movie" -> {
                    val movie = item.movie
                    tmdbId = movie?.ids?.tmdb
                    type = "movie"
                    title = movie?.title.orEmpty()
                    traktRating = movie?.rating ?: 0.0
                }
                "episode", "show" -> {
                    val show = item.show
                    tmdbId = show?.ids?.tmdb
                    type = "tv"
                    title = show?.title.orEmpty()
                    traktRating = show?.rating ?: 0.0
                }
                else -> {
                    Log.w(TAG, "[enrichHistoryPage] Skipping unsupported Trakt history type=${item.type}")
                    onItemProcessed()
                    return@forEach
                }
            }

            if (tmdbId != null && title.isNotBlank()) {
                val cacheKey = "$type-$tmdbId"
                if (processedTmdbIds.add(cacheKey)) {
                    Log.d(TAG, "[enrichHistoryPage] New unique item: $type/$tmdbId, title='$title'")
                    // Add a usable card before the slower TMDB detail request.
                    // The UI can render the title immediately and update the poster/rating later.
                    val baseItem = MyListItem(
                        id = tmdbId,
                        title = title,
                        posterPath = null,
                        type = type,
                        voteAverage = traktRating
                    )
                    pageItems.add(baseItem)
                    Log.d(TAG, "[enrichHistoryPage] About to call onItemAdded for $type/$tmdbId")
                    onItemAdded(baseItem)
                    Log.d(TAG, "[enrichHistoryPage] Returned from onItemAdded for $type/$tmdbId, pageItems size=${pageItems.size}")

                    var enrichedItem = baseItem
                    try {
                        Log.d(TAG, "[enrichHistoryPage] Before TMDB $type detail call for $tmdbId")
                        enrichedItem = if (type == "movie") {
                            val detail = tmdbApiService.getMovieDetail(tmdbId)
                            Log.d(TAG, "[enrichHistoryPage] After TMDB movie call for $tmdbId: poster=${detail.posterPath}")
                            baseItem.copy(
                                posterPath = detail.posterPath,
                                voteAverage = if (traktRating == 0.0) detail.voteAverage else traktRating
                            )
                        } else {
                            val detail = tmdbApiService.getTvShowDetail(tmdbId)
                            Log.d(TAG, "[enrichHistoryPage] After TMDB TV call for $tmdbId: poster=${detail.posterPath}")
                            baseItem.copy(
                                posterPath = detail.posterPath,
                                voteAverage = if (traktRating == 0.0) detail.voteAverage else traktRating
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[enrichHistoryPage] TMDB detail failed for watched $type $tmdbId: ${e.message}", e)
                    }

                    if (enrichedItem != baseItem) {
                        Log.d(TAG, "[enrichHistoryPage] Item was enriched (posterPath changed or rating filled); calling onItemUpdated for $type/$tmdbId")
                        onItemUpdated(enrichedItem)
                        Log.d(TAG, "[enrichHistoryPage] Returned from onItemUpdated for $type/$tmdbId")
                    } else {
                        Log.d(TAG, "[enrichHistoryPage] No enrichment delta for $type/$tmdbId; skipping onItemUpdated")
                    }
                } else {
                    Log.d(TAG, "[enrichHistoryPage] Skipping duplicate item already in processedTmdbIds: $type/$tmdbId")
                }
            } else {
                Log.w(TAG, "[enrichHistoryPage] Skipping item with null tmdbId or blank title: type=$type, tmdbId=$tmdbId, title='$title'")
            }
            onItemProcessed()
        }
        Log.d(TAG, "[enrichHistoryPage] Completed: uniqueItems=${pageItems.size}")
        pageItems
    }

    // ── Core "load next page" function shared by initial fetch + infinite scroll ──
    fun loadNextWatchedPage() {
        if (!isTraktConnected) {
            Log.d(TAG, "Skipping watched page load: Trakt is not connected")
            return
        }
        if (isLoadingMore || isInitialLoading) {
            Log.d(TAG, "Skipping watched page load: another watched load is active")
            return
        }
        if (!hasMoreWatched) {
            Log.d(TAG, "Skipping watched page load: no more watched pages")
            return
        }

        val nextPage = currentWatchedPage + 1
        isLoadingMore = true
        Log.i(
            TAG,
            "Loading watched history page=$nextPage, loaded=$watchedHistoryLoaded, " +
                "total=${watchedHistoryTotal ?: "unknown"}"
        )

        coroutineScope.launch {
            try {
                Log.d(TAG, "Collecting Trakt watched history flow on thread=${Thread.currentThread().name}")
                val result = traktRepository
                    .getTraktWatchHistoryPage(page = nextPage, limit = WATCHED_PAGE_SIZE)
                    .flowOn(Dispatchers.IO)
                    .first()
                Log.d(TAG, "Got Trakt flow result: isSuccess=${result.isSuccess}")

                val historyPage = result.getOrNull()
                if (historyPage != null) {
                    val history = historyPage.items
                    historyPage.totalItemCount?.let {
                        watchedHistoryTotal = it
                        Log.i(TAG, "Trakt watched total item count=$it")
                    }
                    Log.i(
                        TAG,
                        "Received watched page=$nextPage: records=${history.size}, " +
                            "pageCount=${historyPage.pageCount ?: "unknown"}"
                    )
                    if (history.isEmpty()) {
                        Log.i(TAG, "Watched page=$nextPage is empty; reached end of Trakt history")
                        hasMoreWatched = false
                    } else {
                        val newItems = enrichHistoryPage(
                            history = history,
                            onItemProcessed = {
                                withContext(Dispatchers.Main) {
                                    watchedHistoryLoaded += 1
                                    Log.d(
                                        TAG,
                                        "[onItemProcessed] watchedHistoryLoaded=$watchedHistoryLoaded/" +
                                            "${watchedHistoryTotal ?: "?"} on thread=${Thread.currentThread().name}"
                                    )
                                }
                            },
                            onItemAdded = { item ->
                                withContext(Dispatchers.Main) {
                                    Log.d(
                                        TAG,
                                        "[onItemAdded] invoked for ${item.type}/${item.id} on thread=${Thread.currentThread().name}"
                                    )
                                    if (watchedItems.none { it.id == item.id && it.type == item.type }) {
                                        watchedItems = watchedItems + item
                                        Log.d(
                                            TAG,
                                            "[onItemAdded] Appended watched item: " +
                                                "${item.type}/${item.id}, displayed=${watchedItems.size}"
                                        )
                                    } else {
                                        Log.d(TAG, "[onItemAdded] Item already in list, skipping: ${item.type}/${item.id}")
                                    }
                                }
                            },
                            onItemUpdated = { item ->
                                withContext(Dispatchers.Main) {
                                    Log.d(
                                        TAG,
                                        "[onItemUpdated] invoked for ${item.type}/${item.id} on thread=${Thread.currentThread().name}"
                                    )
                                    val before = watchedItems.size
                                    watchedItems = watchedItems.map { existing ->
                                        if (existing.id == item.id && existing.type == item.type) item else existing
                                    }
                                    Log.d(
                                        TAG,
                                        "[onItemUpdated] State map applied: size $before -> ${watchedItems.size}, " +
                                            "item ${item.type}/${item.id} now has posterPath=${item.posterPath}, " +
                                            "voteAverage=${item.voteAverage}"
                                    )
                                }
                            }
                        )

                        withContext(Dispatchers.Main) {
                            // Items were appended as soon as each TMDB enrichment
                            // completed. Only advance pagination here; keep the
                            // deduplicated list intact.
                            currentWatchedPage = nextPage
                            Log.i(
                                TAG,
                                "Appended watched page=$nextPage: added=${newItems.size}, " +
                                    "displayed=${watchedItems.size}, " +
                                    "processed=$watchedHistoryLoaded/${watchedHistoryTotal ?: "?"}"
                            )
                            // Trakt's page-count response header is authoritative.
                            // Falling back to page size preserves compatibility if a
                            // proxy removes the pagination header.
                            hasMoreWatched = historyPage.pageCount?.let {
                                nextPage < it
                            } ?: (history.size == WATCHED_PAGE_SIZE)
                            // Persist the merged list (best-effort)
                            saveWatchedCache(
                                context,
                                watchedItems,
                                currentWatchedPage,
                                hasMoreWatched
                            )
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Watched page=$nextPage fetch failed: ${error?.message}", error)
                    hasMoreWatched = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during watched pagination: ${e.message}", e)
                hasMoreWatched = false
            } finally {
                isLoadingMore = false
                Log.d(
                    TAG,
                    "Watched page load finished: page=$nextPage, loaded=$watchedHistoryLoaded, " +
                        "total=${watchedHistoryTotal ?: "unknown"}, hasMore=$hasMoreWatched, " +
                        "watchedItems.size=${watchedItems.size}"
                )
                isInitialLoading = false
            }
        }
    }

    // ── Reset / initial-load orchestration ──────────────────────────────────
    LaunchedEffect(isTraktConnected) {
        Log.i(TAG, "Watched tab connection state changed: connected=$isTraktConnected")

        if (!isTraktConnected) {
            Log.d(TAG, "Trakt disconnected; clearing watched history state")
            watchedItems = emptyList()
            hasMoreWatched = true
            currentWatchedPage = 0
            watchedHistoryTotal = null
            watchedHistoryLoaded = 0
            processedTmdbIds.clear()
            clearWatchedCache(context)
            Log.i(TAG, "Cleared watched tab state and cache because Trakt is disconnected")
            return@LaunchedEffect
        }

        // 1) Try cache first for instant display
        val cached = loadWatchedCache(context)
        if (cached != null && cached.items.isNotEmpty()) {
            Log.i(
                TAG,
                "Watched cache hit: items=${cached.items.size}, page=${cached.lastLoadedPage}, " +
                    "hasMore=${cached.hasMore}"
            )
            watchedItems = cached.items
            currentWatchedPage = cached.lastLoadedPage
            watchedHistoryLoaded = cached.items.size
            watchedHistoryTotal = null
            hasMoreWatched = cached.hasMore
            cached.items.forEach { item ->
                processedTmdbIds.add("${item.type}-${item.id}")
            }

            // Check for items with missing posterPath or rating and enrich them
            coroutineScope.launch {
                val itemsToEnrich = watchedItems.filter { it.posterPath == null || it.voteAverage == 0.0 }
                if (itemsToEnrich.isNotEmpty()) {
                    Log.i(TAG, "Enriching ${itemsToEnrich.size} cached watched items with missing TMDB metadata")
                    val enrichedItems = watchedItems.map { item ->
                        if (item.posterPath == null || item.voteAverage == 0.0) {
                            try {
                                if (item.type == "movie") {
                                    val detail = tmdbApiService.getMovieDetail(item.id)
                                    item.copy(
                                        posterPath = item.posterPath ?: detail.posterPath,
                                        voteAverage = if (item.voteAverage == 0.0) detail.voteAverage else item.voteAverage
                                    )
                                } else {
                                    val detail = tmdbApiService.getTvShowDetail(item.id)
                                    item.copy(
                                        posterPath = item.posterPath ?: detail.posterPath,
                                        voteAverage = if (item.voteAverage == 0.0) detail.voteAverage else item.voteAverage
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to enrich cached watched item ${item.id}: ${e.message}", e)
                                item
                            }
                        } else {
                            item
                        }
                    }
                    withContext(Dispatchers.Main) {
                        watchedItems = enrichedItems
                        saveWatchedCache(context, watchedItems, currentWatchedPage, hasMoreWatched)
                        Log.i(TAG, "Finished cached watched-item enrichment: attempted=${itemsToEnrich.size}")
                    }
                }
            }

            // Always continue loading from the next page so that items added since
            // the last save appear as the user scrolls. (Silent background append.)
            if (hasMoreWatched) {
                loadNextWatchedPage()
            }
            return@LaunchedEffect
        }

        // 2) No cache — fetch the first page and show a spinner
        Log.i(TAG, "No watched cache found; starting Trakt watched-history fetch")
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
                Log.d(TAG, "Watched grid reached load threshold: lastVisible=$lastVisible, total=$total")
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
                        WatchedLoadingStatus(
                            loadedItems = watchedHistoryLoaded,
                            totalItems = watchedHistoryTotal
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
                                    watchedHistoryTotal = null
                                    watchedHistoryLoaded = 0
                                    isInitialLoading = true
                                    Log.i(TAG, "Manual watched-history refresh requested by user")
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
                                loadedHistoryItems = watchedHistoryLoaded,
                                totalHistoryItems = watchedHistoryTotal,
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
    loadedHistoryItems: Int,
    totalHistoryItems: Int?,
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
                    WatchedLoadingStatus(
                        loadedItems = loadedHistoryItems,
                        totalItems = totalHistoryItems
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

@Composable
private fun WatchedLoadingStatus(
    loadedItems: Int,
    totalItems: Int?
) {
    Text(
        text = if (totalItems != null && totalItems > 0) {
            "Loading watched history\nLoaded $loadedItems/$totalItems total watched items"
        } else {
            "Loading watched history\nLoaded $loadedItems watched items (total unavailable)"
        },
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
    )
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
