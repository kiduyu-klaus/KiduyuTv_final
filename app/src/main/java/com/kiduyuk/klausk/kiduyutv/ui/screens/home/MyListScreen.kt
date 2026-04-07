package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import androidx.compose.ui.text.style.TextOverflow

/**
 * Composable function for the "My List" screen, displaying items saved by the user.
 * It observes the [HomeViewModel] for the list of saved items and allows navigation to their details
 * or removal from the list.
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
    onCompanyClick: (Int, String) -> Unit = { _, _ -> },
    onNetworkClick: (Int, String) -> Unit = { _, _ -> },
    onCastClick: (CastMember) -> Unit = { _ -> },
    viewModel: HomeViewModel = viewModel()
) {
    // Collect My List from the global manager.
    val myList by MyListManager.myList.collectAsState()
    val context = LocalContext.current

    // Categorize items
    val movies = myList.filter { it.type == "movie" }
    val tvShows = myList.filter { it.type == "tv" }
    val companies = myList.filter { it.type == "company" }
    val networks = myList.filter { it.type == "network" }
    val castMembers = myList.filter { it.type == "cast" }

    // Tab state
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Movies", "TV Shows", "Companies", "Networks", "Cast")

    // Get screen configuration to calculate responsive grid
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val horizontalPadding = 25.dp
    val spacing = 10.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 100.dp
    val actualColumns = maxOf(4, minOf(8, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt()))
    val calculatedCardWidth = (availableWidth - (spacing * (actualColumns - 1))) / actualColumns
    val calculatedCardHeight = calculatedCardWidth * 1.8f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark) // Set background color.
    ) {
        // Top navigation bar for the My List screen.
        TopBar(
            selectedRoute = "my_list",
            onNavItemClick = { route -> onNavigate(route) }, // Handle navigation clicks.
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding) // Padding for the content area.
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

            Spacer(modifier = Modifier.height(15.dp)) // Vertical spacing.

            // Content based on selected tab
            val currentList = when (selectedTabIndex) {
                0 -> movies
                1 -> tvShows
                2 -> companies
                3 -> networks
                4 -> castMembers
                else -> emptyList()
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ${tabs[selectedTabIndex]} saved yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(actualColumns),
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
                                Box(
                                    modifier = Modifier
                                        .width(calculatedCardWidth)
                                        .height(calculatedCardHeight)
                                        .focusable(interactionSource = interactionSource)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            onMovieClick(item.id)
                                        }
                                ) {
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
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            "tv" -> {
                                Box(
                                    modifier = Modifier
                                        .width(calculatedCardWidth)
                                        .height(calculatedCardHeight)
                                        .focusable(interactionSource = interactionSource)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            onTvShowClick(item.id)
                                        }
                                ) {
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
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            "company", "network" -> {
                                Card(
                                    modifier = Modifier
                                        .width(calculatedCardWidth)
                                        .height(calculatedCardHeight / 2)
                                        .focusable(interactionSource = interactionSource)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            if (item.type == "company") onCompanyClick(item.id, item.title)
                                            else onNetworkClick(item.id, item.title)
                                        }
                                        .then(
                                            if (isFocused) {
                                                Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(8.dp)
                                                )
                                            } else Modifier
                                        ),
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
                                            // Show logo if available
                                            AsyncImage(
                                                model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.LOGO_SIZE}${item.posterPath}",
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(16.dp)
                                            )
                                        } else {
                                            // Show name with icon if no logo
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                // Icon indicator for company/network
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(
                                                            color = if (isFocused) CardDark.copy(alpha = 0.3f) else SurfaceDark,
                                                            shape = RoundedCornerShape(8.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (item.type == "company") "C" else "N",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = if (isFocused) MaterialTheme.colorScheme.primary else TextSecondary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isFocused) TextPrimary else TextPrimary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "cast" -> {
                                Column(
                                    modifier = Modifier
                                        .width(calculatedCardWidth)
                                        .focusable(interactionSource = interactionSource)
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
                                                width = if (isFocused) 3.dp else 0.dp,
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
                }
            }
        }
    }
}

/**
 * Composable function to display a single item in the "My List" screen.
 * It shows the item's title, type, and provides options to view details or remove it.
 *
 * @param item The [MyListItem] data to display.
 * @param onClick Lambda to be invoked when the card is clicked.
 * @param onRemove Lambda to be invoked when the remove button is clicked.
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
                shape = RoundedCornerShape(8.dp) // Rounded corners for the card background.
            )
            .clickable { onClick() }
            .padding(10.dp), // Padding inside the card.
        horizontalArrangement = Arrangement.spacedBy(16.dp) // Spacing between elements in the row.
    ) {
        // Poster thumbnail.
        AsyncImage(
            model = "${TmdbApiService.IMAGE_BASE_URL}${TmdbApiService.POSTER_SIZE}${item.posterPath}",
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(80.dp)
                .height(88.dp)
                .background(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(4.dp)
                )
                .clip(RoundedCornerShape(4.dp))
        )

        // Column for item title and type.
        Column(
            modifier = Modifier.weight(1f), // Takes available horizontal space.
            verticalArrangement = Arrangement.Center // Center content vertically.
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp)) // Vertical spacing.
            Text(
                text = if (item.type == "movie") "Movie" else "TV Show",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Remove button.
//        IconButton(onClick = onRemove) {
//            Icon(
//                imageVector = Icons.Default.Close,
//                contentDescription = "Remove from list",
//                tint = TextPrimary
//            )
//        }
    }
}


/**
 * Preview for the [MyListScreen] composable.
 */
@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MyListScreenPreview() {
    KiduyuTvTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            // Header for the preview.
//            Text(
//                text = "My List",
//                style = MaterialTheme.typography.headlineLarge,
//                color = TextPrimary,
//                modifier = Modifier.padding(48.dp)
//            )

            // Sample My List Items for the preview.
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
