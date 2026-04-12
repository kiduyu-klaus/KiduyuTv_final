package com.kiduyuk.klausk.kiduyutv.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.SavedMediaItem
import com.kiduyuk.klausk.kiduyutv.data.repository.MyListManager
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileMovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.MobileTvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit = {},
    onTvShowClick: (Int) -> Unit = {},
    onCompanyClick: (Int, String) -> Unit = { _, _ -> },
    onNetworkClick: (Int, String) -> Unit = { _, _ -> },
    onCastClick: (Int, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val myList by MyListManager.myList.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCacheSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // My List Section
            SettingsGroup(title = "My List") {
                if (myList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items in your list yet",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(myList) { item ->
                            when (item.type) {
                                "movie" -> {
                                    MobileMovieCard(
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
                                        onClick = { onMovieClick(item.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                "tv" -> {
                                    MobileTvShowCard(
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
                                        onClick = { onTvShowClick(item.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                "company" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onCompanyClick(item.id, item.title) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.posterPath != null) {
                                                AsyncImage(
                                                    model = "https://image.tmdb.org/t/p/w200${item.posterPath}",
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = item.title.take(1).uppercase(),
                                                    color = TextPrimary,
                                                    fontSize = 20.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.title,
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                "network" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNetworkClick(item.id, item.title) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.posterPath != null) {
                                                AsyncImage(
                                                    model = "https://image.tmdb.org/t/p/w200${item.posterPath}",
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = item.title.take(1).uppercase(),
                                                    color = TextPrimary,
                                                    fontSize = 20.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.title,
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                "cast" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onCastClick(
                                                    item.id,
                                                    item.title,
                                                    item.character,
                                                    item.posterPath,
                                                    item.knownForDepartment
                                                )
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.posterPath != null) {
                                                AsyncImage(
                                                    model = "https://image.tmdb.org/t/p/w200${item.posterPath}",
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = item.title.take(1).uppercase(),
                                                    color = TextPrimary,
                                                    fontSize = 20.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.title,
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsGroup(title = "App Settings") {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear Cache",
                    subtitle = "Current cache: ${uiState.cacheSize}",
                    onClick = { viewModel.clearCache(context) },
                    isLoading = uiState.isClearingCache,
                    isSuccess = uiState.cacheClearSuccess
                )
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Clear Watch History",
                    subtitle = "Remove all previously watched content",
                    onClick = { viewModel.clearWatchHistory() },
                    isLoading = uiState.isClearingWatchHistory,
                    isSuccess = uiState.watchHistoryClearSuccess
                )
                SettingsItem(
                    icon = Icons.Default.PlaylistRemove,
                    title = "Clear My List",
                    subtitle = "Remove all items from your favorites",
                    onClick = { viewModel.clearMyList() },
                    isLoading = uiState.isClearingMyList,
                    isSuccess = uiState.myListClearSuccess
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsGroup(title = "App Information") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About KiduyuTV",
                    subtitle = "Version 1.2.1",
                    onClick = { /* Could show a dialog */ }
                )
                SettingsItem(
                    icon = Icons.Default.Public,
                    title = "Visit Website",
                    subtitle = "https://kiduyu-klaus.github.io/KiduyuTv_final/",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://kiduyu-klaus.github.io/KiduyuTv_final/".toUri())
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsGroup(title = "Updates") {
                SettingsItem(
                    icon = Icons.Default.Update,
                    title = "Check for Updates",
                    subtitle = uiState.updateCheckResult ?: "Stay on the latest version",
                    onClick = { viewModel.checkForUpdates(context) },
                    isLoading = uiState.isCheckingForUpdates
                )
                if (uiState.updateAvailable) {
                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = "Download Update",
                        subtitle = "New version is available",
                        onClick = { viewModel.downloadAndInstallUpdate(context) },
                        isLoading = uiState.isDownloadingUpdate
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "KiduyuTV v1.2.1",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = PrimaryRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSuccess: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryRed, strokeWidth = 2.dp)
        } else if (isSuccess) {
            Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.Green, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
