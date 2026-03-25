package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
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
    // Collect UI state from the ViewModel.
    val uiState by viewModel.uiState.collectAsState()

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
                .padding(48.dp) // Padding for the content area.
        ) {
            // Screen title.
            Text(
                text = "My List",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(32.dp)) // Vertical spacing.

            // Display a message if the list is empty, otherwise show the list.
            if (uiState.myList.isEmpty()) {
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
                // LazyColumn to efficiently display a scrollable list of MyListItemCard.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp) // Spacing between list items.
                ) {
                    items(uiState.myList) { item ->
                        MyListItemCard(
                            item = item,
                            onClick = { // Handle click on a list item.
                                when (item.type) {
                                    "movie" -> onMovieClick(item.id)
                                    "tv" -> onTvShowClick(item.id)
                                }
                            },
                            onRemove = { viewModel.removeFromMyList(item.id) } // Handle item removal.
                        )
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
            .padding(16.dp), // Padding inside the card.
        horizontalArrangement = Arrangement.spacedBy(16.dp) // Spacing between elements in the row.
    ) {
        // Placeholder for the poster thumbnail.
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(88.dp)
                .background(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(4.dp) // Rounded corners for the placeholder.
                )
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
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from list",
                tint = TextPrimary
            )
        }
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
            Text(
                text = "My List",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(48.dp)
            )

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
