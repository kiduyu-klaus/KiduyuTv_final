package com.kiduyuk.klausk.kiduyutv.ui.screens.search.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.SearchResult
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.SearchUiState
import com.kiduyuk.klausk.kiduyutv.viewmodel.SearchViewModel

/**
 * Search screen composable that allows users to search for movies and TV shows.
 * Displays search results in a list layout with modern dark mode design.
 * Also shows recent searches when the search is empty.
 *
 * @param onBackClick Callback when the back button is clicked.
 * @param onMovieClick Callback when a movie result is clicked.
 * @param onTvShowClick Callback when a TV show result is clicked.
 * @param viewModel The [SearchViewModel] for managing search state.
 */
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Search header with back button and search field
        SearchHeader(
            query = uiState.query,
            onQueryChange = viewModel::onQueryChange,
            onBackClick = onBackClick,
            onClearClick = viewModel::clearSearch,
            focusRequester = focusRequester
        )

        // Search content area
        SearchContent(
            uiState = uiState,
            onMovieClick = onMovieClick,
            onTvShowClick = onTvShowClick,
            onRecentSearchClick = viewModel::setSearchQuery,
            onRemoveRecentSearch = viewModel::removeRecentSearch,
            onClearAllRecentSearches = viewModel::clearRecentSearches
        )
    }
}

/**
 * Header component with back button and search input field.
 */
@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onClearClick: () -> Unit,
    focusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark.copy(alpha = 0.95f))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back button - pill-shaped with rounded corners
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = CardDark,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Search field - wide input with rounded corners
            SearchTextField(
                query = query,
                onQueryChange = onQueryChange,
                onClearClick = onClearClick,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Custom search text field with search icon and clear button.
 */
@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .fillMaxWidth()
            .background(
                color = CardDark,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 2.dp,
                color = if (query.isNotEmpty()) DarkRed else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = TextSecondary,
                modifier = Modifier.size(28.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal
                ),
                singleLine = true,
                cursorBrush = SolidColor(DarkRed),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .focusable(),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search movies and TV shows...",
                                color = TextSecondary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = TextSecondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Content area that displays search results, recent searches, or loading/error states.
 */
@Composable
private fun SearchContent(
    uiState: SearchUiState,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearAllRecentSearches: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LottieLoadingView(size = 300.dp)
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Oops! Something went wrong",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.error,
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            !uiState.hasSearched && uiState.query.isEmpty() -> {
                // Show recent searches when no search has been performed and query is empty
                if (uiState.recentSearches.isNotEmpty()) {
                    RecentSearchesList(
                        recentSearches = uiState.recentSearches,
                        onRecentSearchClick = onRecentSearchClick,
                        onRemoveRecentSearch = onRemoveRecentSearch,
                        onClearAllRecentSearches = onClearAllRecentSearches
                    )
                } else {
                    // Show empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "Start typing to search",
                                color = TextSecondary,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Find movies and TV shows",
                                color = TextTertiary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            uiState.results.isEmpty() && uiState.hasSearched -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No results found",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try searching with different keywords",
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            else -> {
                SearchResultsList(
                    results = uiState.results,
                    onMovieClick = onMovieClick,
                    onTvShowClick = onTvShowClick
                )
            }
        }
    }
}

/**
 * Displays the list of recent searches.
 */
@Composable
private fun RecentSearchesList(
    recentSearches: List<String>,
    onRecentSearchClick: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearAllRecentSearches: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with title and clear all button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Recent Searches",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Clear All",
                color = DarkRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClearAllRecentSearches() }
                    .padding(8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recentSearches) { search ->
                RecentSearchItem(
                    query = search,
                    onClick = { onRecentSearchClick(search) },
                    onRemove = { onRemoveRecentSearch(search) }
                )
            }
        }
    }
}

/**
 * Single recent search item with query text and remove button.
 */
@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) CardDark else SurfaceDark
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) DarkRed else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = query,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * List layout for displaying search results.
 */
@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "${results.size} results found",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(results) { result ->
            SearchResultRow(
                result = result,
                onClick = {
                    when (result) {
                        is SearchResult.MovieResult -> onMovieClick(result.id)
                        is SearchResult.TvResult -> onTvShowClick(result.id)
                    }
                }
            )
        }
    }
}

/**
 * Row component for displaying a single search result with poster and details.
 */
@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) CardDark else SurfaceDark
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) DarkRed else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster image with rounded corners
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (result.posterPath != null) {
                AsyncImage(
                    model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${result.posterPath}",
                    contentDescription = result.title,
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
                        text = result.title.take(1),
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Content details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            Text(
                text = result.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Media type badge - pill-shaped
            Box(
                modifier = Modifier
                    .background(
                        color = if (result.mediaType == "movie") PrimaryRed.copy(alpha = 0.2f)
                        else DarkRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (result.mediaType == "movie") "Movie" else "TV Show",
                    color = if (result.mediaType == "movie") PrimaryRed else DarkRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Rating
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = String.format("%.1f", result.voteAverage),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "/ 10",
                    color = TextTertiary,
                    fontSize = 14.sp
                )
            }

            // Overview
            if (result.overview.isNotEmpty()) {
                Text(
                    text = result.overview,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}