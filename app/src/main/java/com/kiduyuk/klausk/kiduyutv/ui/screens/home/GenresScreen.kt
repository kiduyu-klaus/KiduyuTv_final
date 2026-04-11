package com.kiduyuk.klausk.kiduyutv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    mediaType: String, // "movie" or "tv"
    onBackClick: () -> Unit,
    onGenreClick: (Int, String) -> Unit
) {
    // Standard TMDB Genres
    val movieGenres = listOf(
        Genre(28, "Action"), Genre(12, "Adventure"), Genre(16, "Animation"),
        Genre(35, "Comedy"), Genre(80, "Crime"), Genre(99, "Documentary"),
        Genre(18, "Drama"), Genre(10751, "Family"), Genre(14, "Fantasy"),
        Genre(36, "History"), Genre(27, "Horror"), Genre(10402, "Music"),
        Genre(9648, "Mystery"), Genre(10749, "Romance"), Genre(878, "Sci-Fi"),
        Genre(10770, "TV Movie"), Genre(53, "Thriller"), Genre(10752, "War"),
        Genre(37, "Western")
    )

    val tvGenres = listOf(
        Genre(10759, "Action & Adventure"), Genre(16, "Animation"), Genre(35, "Comedy"),
        Genre(80, "Crime"), Genre(99, "Documentary"), Genre(18, "Drama"),
        Genre(10751, "Family"), Genre(10762, "Kids"), Genre(9648, "Mystery"),
        Genre(10763, "News"), Genre(10764, "Reality"), Genre(10765, "Sci-Fi & Fantasy"),
        Genre(10766, "Soap"), Genre(10767, "Talk"), Genre(10768, "War & Politics"),
        Genre(37, "Western")
    )

    val genres = if (mediaType == "movie") movieGenres else tvGenres

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Genres", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(genres) { genre ->
                    GenreCard(genre = genre, onClick = { onGenreClick(genre.id, genre.name) })
                }
            }
        }
    }
}

@Composable
fun GenreCard(
    genre: Genre,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class Genre(val id: Int, val name: String)
