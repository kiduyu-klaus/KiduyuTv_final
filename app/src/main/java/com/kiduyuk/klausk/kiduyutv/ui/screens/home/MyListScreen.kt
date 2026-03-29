package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.TmdbApiService
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem

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
    viewModel: HomeViewModel = viewModel()
) {
    // Collect My List from the global manager.
    val myList by MyListManager.myList.collectAsState()
    val context = LocalContext.current

    // Get screen configuration to calculate responsive grid
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val horizontalPadding = 48.dp
    val spacing = 16.dp
    val availableWidth = screenWidth - (horizontalPadding * 2)
    val minCardWidth = 120.dp
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


            Spacer(modifier = Modifier.height(32.dp)) // Vertical spacing.

            // Display a message if the list is empty, otherwise show the list.
            if (myList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your list is empty. Start adding movies and TV shows!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            } else {
                // LazyVerticalGrid to efficiently display a scrollable grid of items.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(actualColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(myList) { item ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .width(calculatedCardWidth)
                                .height(calculatedCardHeight)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    when (item.type) {
                                        "movie" -> onMovieClick(item.id)
                                        "tv" -> onTvShowClick(item.id)
                                    }
                                }
                        ) {
                            if (item.type == "movie") {
                                MovieCard(
                                    movie = Movie(
                                        id = item.id,
                                        title = item.title,
                                        overview = "",
                                        posterPath = item.posterPath,
                                        backdropPath = null,
                                        voteAverage = 0.0,
                                        releaseDate = null,
                                        genreIds = null,
                                        popularity = 0.0
                                    ),
                                    isSelected = isFocused,
                                    onClick = { onMovieClick(item.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                TvShowCard(
                                    tvShow = TvShow(
                                        id = item.id,
                                        name = item.title,
                                        overview = "",
                                        posterPath = item.posterPath,
                                        backdropPath = null,
                                        voteAverage = 0.0,
                                        firstAirDate = null,
                                        genreIds = null,
                                        popularity = 0.0
                                    ),
                                    isSelected = isFocused,
                                    onClick = { onTvShowClick(item.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Remove button overlay
                            IconButton(
                                onClick = { MyListManager.removeItem(item.id, item.type, context) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from list",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
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
            .padding(16.dp), // Padding inside the card.
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
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
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
