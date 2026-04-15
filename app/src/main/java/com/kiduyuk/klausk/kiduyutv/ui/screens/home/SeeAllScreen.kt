package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllScreen(
    category: String,
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val repository = remember { TmdbRepository() }

    // Always trigger a load — SeeAllScreen gets its own fresh ViewModel instance
    // scoped to the NavBackStackEntry, so uiState starts empty.
    LaunchedEffect(Unit) {
        viewModel.loadHomeContent(context)
    }

    val title = when (category) {
        "trending_movies"  -> "Trending Movies"
        "latest_movies"    -> "Latest Releases"
        "box_office"       -> "Box Office"
        "trending_tv"      -> "Trending TV Shows"
        "watched_tv"       -> "Watched TV Shows"
        "popular_tv"       -> "Popular TV Shows"
        "favorite_tv"      -> "All Time Favorite TV Shows"
        "continue_watching"-> "Continue Watching"
        "now_playing"      -> "Now Playing"
        "time_travel_movies" -> "Time Travel Movies"
        "time_travel_tv"   -> "Time Travel TV Shows"
        "oscar_winners"    -> "2026 Oscar Winners"
        "hallmark"         -> "Hallmark Movies"
        "true_story"       -> "True Story Movies"
        "classics"         -> "Best Classics"
        "spy_movies"       -> "Spy Movies"
        "statham_movies"   -> "Jason Statham Movies"
        "networks"         -> "Popular Networks"
        "companies"        -> "Production Companies"
        "christian_movies" -> "Christian Movies"
        "bible_movies"     -> "Movies from the Bible"
        "christian_tv"     -> "Christian TV Shows"
        else               -> "All Content"
    }

    // ── JSON-backed categories ────────────────────────────────────────────────
    // Derived directly from uiState so they update as soon as loadHomeContent
    // finishes — no separate async fetch needed.
    val jsonItems: List<Any>? = when (category) {
        "oscar_winners"      -> uiState.oscarWinners2026
        "hallmark"           -> uiState.hallmarkMovies
        "true_story"         -> uiState.trueStoryMovies
        "classics"           -> uiState.bestClassics
        "spy_movies"         -> uiState.spyMovies
        "statham_movies"     -> uiState.stathamMovies
        "time_travel_movies" -> uiState.timeTravelMovies
        "time_travel_tv"     -> uiState.timeTravelTvShows
        "favorite_tv"        -> uiState.bestSitcoms
        "christian_movies" -> uiState.christianMovies
        "bible_movies"     -> uiState.bibleMovies
        "christian_tv"     -> uiState.christianTvShows
        else                 -> null  // null = not a JSON category, use async fetch
    }

    // ── TMDB-backed categories ────────────────────────────────────────────────
    // Fetched once via repository (with uiState as a warm cache).
    var asyncItems by remember { mutableStateOf<List<Any>>(emptyList()) }
    var isAsyncLoading by remember { mutableStateOf(false) }

    LaunchedEffect(category) {
        if (jsonItems != null) return@LaunchedEffect  // handled reactively above
        isAsyncLoading = true
        asyncItems = when (category) {
            "trending_movies" -> uiState.trendingMovies.ifEmpty {
                repository.getTrendingMoviesToday().getOrNull() ?: emptyList()
            }
            "latest_movies" -> (uiState.trendingMoviesThisWeek.ifEmpty { uiState.latestMovies }).ifEmpty {
                repository.getTrendingMoviesThisWeek().getOrNull() ?: emptyList()
            }
            "box_office" -> uiState.latestMovies.ifEmpty {
                repository.getTopRatedMovies().getOrNull() ?: emptyList()
            }
            "trending_tv" -> uiState.trendingTvShows.ifEmpty {
                repository.getTrendingTvToday().getOrNull() ?: emptyList()
            }
            "watched_tv" -> uiState.continueWatching.filter { it.isTv }.map {
                TvShow(id = it.id, name = it.title, overview = it.overview ?: "", posterPath = it.posterPath, backdropPath = it.backdropPath, voteAverage = it.voteAverage, firstAirDate = it.releaseDate ?: "", genreIds = emptyList(), popularity = 0.0)
            }
            "popular_tv" -> uiState.topTvShows.ifEmpty {
                repository.getPopularTvShows().getOrNull() ?: emptyList()
            }
            "continue_watching" -> uiState.continueWatching.map { history ->
                if (history.isTv) {
                    TvShow(id = history.id, name = history.title, overview = history.overview ?: "", posterPath = history.posterPath, backdropPath = history.backdropPath, voteAverage = history.voteAverage, firstAirDate = history.releaseDate ?: "", genreIds = emptyList(), popularity = 0.0)
                } else {
                    Movie(id = history.id, title = history.title, overview = history.overview ?: "", posterPath = history.posterPath, backdropPath = history.backdropPath, voteAverage = history.voteAverage, releaseDate = history.releaseDate ?: "", genreIds = emptyList(), popularity = 0.0)
                }
            }
            "now_playing" -> uiState.nowPlayingMovies.ifEmpty {
                repository.getNowPlayingMovies().getOrNull() ?: emptyList()
            }
            else -> emptyList()
        }
        isAsyncLoading = false
    }

    // Final list shown in the grid — JSON categories track uiState live;
    // TMDB categories use the async result.
    val items: List<Any> = jsonItems ?: asyncItems
    val isLoading = uiState.isLoading || isAsyncLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No content available", color = TextPrimary)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items) { item ->
                            when (item) {
                                is Movie -> MobileMovieCard(movie = item, onClick = { onMovieClick(item.id) })
                                is TvShow -> MobileTvShowCard(tvShow = item, onClick = { onTvShowClick(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
