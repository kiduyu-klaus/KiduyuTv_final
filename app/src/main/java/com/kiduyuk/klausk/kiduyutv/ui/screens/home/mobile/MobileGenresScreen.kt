package com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileSearchTopBar
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.rememberPhoneInterstitialBackClick
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.viewmodel.HomeViewModel

/**
 * Mobile-optimized screen that displays a grid of genre chips for movie or TV show selection.
 * When a genre is clicked, it navigates to the genre content screen.
 *
 * @param mediaType The type of media ("movie" or "tv")
 * @param onGenreClick Callback for genre selection
 * @param onBackClick Callback for back button navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileGenresScreen(
    mediaType: String,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onGenreClick: (Int, String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    val handleBackClick = rememberPhoneInterstitialBackClick(onBackClick)

    BackHandler(onBack = handleBackClick)

    // Movie genres
    val movieGenres = listOf(
        Genre(28, "Action"),
        Genre(12, "Adventure"),
        Genre(16, "Animation"),
        Genre(35, "Comedy"),
        Genre(80, "Crime"),
        Genre(99, "Documentary"),
        Genre(18, "Drama"),
        Genre(10751, "Family"),
        Genre(14, "Fantasy"),
        Genre(36, "History"),
        Genre(27, "Horror"),
        Genre(10402, "Music"),
        Genre(9648, "Mystery"),
        Genre(10749, "Romance"),
        Genre(878, "Sci-Fi"),
        Genre(10770, "TV Movie"),
        Genre(53, "Thriller"),
        Genre(10752, "War"),
        Genre(37, "Western")
    )

    // TV genres
    val tvGenres = listOf(
        Genre(10759, "Action & Adventure"),
        Genre(16, "Animation"),
        Genre(35, "Comedy"),
        Genre(80, "Crime"),
        Genre(99, "Documentary"),
        Genre(18, "Drama"),
        Genre(10751, "Family"),
        Genre(10762, "Kids"),
        Genre(9648, "Mystery"),
        Genre(10763, "News"),
        Genre(10764, "Reality"),
        Genre(10765, "Sci-Fi & Fantasy"),
        Genre(10766, "Soap"),
        Genre(10767, "Talk"),
        Genre(10768, "War & Politics"),
        Genre(37, "Western")
    )

            val genres = if (mediaType == "movie") movieGenres else tvGenres
    val title = if (mediaType == "movie") "Movie Genres" else "TV Show Genres"

    LaunchedEffect(Unit) {
        isLoading = false
    }

    Scaffold(
        topBar = {
            MobileSearchTopBar(
                onSearchClick = { onNavigate(Screen.Search.route) },
                onSettingsClick = { onNavigate(Screen.Settings.route) },
                title = title,
                onBackClick = handleBackClick
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LottieLoadingView(size = 150.dp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(genres) { genre ->
                        MobileGenreChip(
                            genre = genre,
                            onClick = {
                                onGenreClick(genre.id, Uri.encode(genre.name))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MobileGenreChip(
    genre: Genre,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = genre.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class Genre(val id: Int, val name: String)
