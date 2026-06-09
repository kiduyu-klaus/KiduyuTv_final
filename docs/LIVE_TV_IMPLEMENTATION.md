# Live TV Implementation Guide - KiduyuTV

## Overview

This document provides a complete implementation guide for adding Live TV functionality to the KiduyuTV Android application. The implementation uses an IPTV playlist (M3U format) and ExoPlayer for video playback. The flow consists of three main screens:

1. **Categories Screen** - Displays TV channel categories from the playlist
2. **Channels Screen** - Displays channels within a selected category
3. **Player Activity** - ExoPlayer-based video player for live streaming

---

## IPTV Playlist URL

```
https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u
```

---

## Table of Contents

1. [Data Models](#1-data-models)
2. [IPTV Repository](#2-iptv-repository)
3. [Live TV ViewModel](#3-live-tv-viewmodel)
4. [Live TV Screen (Categories)](#4-live-tv-screen-categories)
5. [Channels Screen](#5-channels-screen)
6. [ExoPlayer Activity](#6-exoplayer-activity)
7. [Navigation Updates](#7-navigation-updates)
8. [Gradle Dependencies](#8-gradle-dependencies)
9. [Android Manifest Updates](#9-android-manifest-updates)

---

## 1. Data Models

Create a new file `app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/model/IptvModels.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a TV channel category from the IPTV playlist.
 *
 * @param name The name of the category (e.g., "Sports", "Movies", "News")
 * @param channels The list of channels belonging to this category
 */
data class IptvCategory(
    val name: String,
    val channels: List<IptvChannel> = emptyList()
)

/**
 * Represents a single TV channel from the IPTV playlist.
 *
 * @param name The name of the channel
 * @param logo The URL of the channel's logo image
 * @param url The streaming URL for the channel
 * @param group The category/group the channel belongs to
 * @param tvgId The TVG ID for EPG integration
 * @param tvgName The TVG name for EPG integration
 */
data class IptvChannel(
    val name: String,
    val logo: String?,
    val url: String,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null
)

/**
 * Represents the parsed IPTV playlist data.
 *
 * @param categories Map of category names to their channels
 * @param allChannels Flat list of all channels (useful for "All" category)
 */
data class IptvPlaylist(
    val categories: Map<String, List<IptvChannel>>,
    val allChannels: List<IptvChannel>
)
```

---

## 2. IPTV Repository

Create a new file `app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/repository/IptvRepository.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.data.repository

import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository for fetching and parsing IPTV playlists.
 * Handles M3U format parsing and channel categorization.
 */
class IptvRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        // IPTV Playlist URL
        const val PLAYLIST_URL = "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/main/combined-playlist.m3u"
        
        // Singleton instance
        @Volatile
        private var instance: IptvRepository? = null
        
        fun getInstance(): IptvRepository {
            return instance ?: synchronized(this) {
                instance ?: IptvRepository().also { instance = it }
            }
        }
    }
    
    /**
     * Fetches and parses the IPTV playlist from the remote URL.
     *
     * @return Result containing either the parsed IptvPlaylist or an error
     */
    suspend fun fetchPlaylist(): Result<IptvPlaylist> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(PLAYLIST_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to fetch playlist: ${response.code}")
                )
            }
            
            val m3uContent = response.body?.string() 
                ?: return@withContext Result.failure(
                    Exception("Empty response from server")
                )
            
            val playlist = parseM3uPlaylist(m3uContent)
            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parses M3U playlist content into structured data.
     *
     * @param content The raw M3U playlist content
     * @return Parsed IptvPlaylist with categorized channels
     */
    private fun parseM3uPlaylist(content: String): IptvPlaylist {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines().iterator()
        
        var currentChannel: IptvChannel? = null
        
        while (lines.hasNext()) {
            val line = lines.next().trim()
            
            when {
                // Channel info line (EXTINF)
                line.startsWith("#EXTINF:") -> {
                    currentChannel = parseExtInfLine(line)
                }
                // URL line - completes the channel
                line.isNotEmpty() && !line.startsWith("#") && (currentChannel != null) -> {
                    val channel = currentChannel?.copy(url = line)
                    if (channel != null) {
                        channels.add(channel)
                    }
                    currentChannel = null
                }
                // Skip other #EXT lines (EXTM3U, EXTGRP, etc.)
                line.startsWith("#") && !line.startsWith("#EXTINF:") -> {
                    currentChannel = null
                }
            }
        }
        
        // Group channels by category
        val categories = channels
            .filter { !it.group.isNullOrBlank() }
            .groupBy { it.group!! }
        
        return IptvPlaylist(
            categories = categories,
            allChannels = channels
        )
    }
    
    /**
     * Parses the #EXTINF line to extract channel metadata.
     *
     * @param line The #EXTINF line from M3U
     * @return IptvChannel with parsed metadata
     */
    private fun parseExtInfLine(line: String): IptvChannel? {
        try {
            // Extract attributes from #EXTINF:-1 tvg-id="..." tvg-name="..." tvg-logo="..." group-title="...",Channel Name
            val attributesPart = line.substringAfter("#EXTINF:").substringBefore(",")
            val name = line.substringAfterLast(",").trim()
            
            // Parse tvg-logo
            val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(attributesPart)
            val logo = logoMatch?.groupValues?.get(1)
            
            // Parse group-title
            val groupMatch = Regex("group-title=\"([^\"]+)\"").find(attributesPart)
            val group = groupMatch?.groupValues?.get(1)
            
            // Parse tvg-id
            val tvgIdMatch = Regex("tvg-id=\"([^\"]+)\"").find(attributesPart)
            val tvgId = tvgIdMatch?.groupValues?.get(1)
            
            // Parse tvg-name
            val tvgNameMatch = Regex("tvg-name=\"([^\"]+)\"").find(attributesPart)
            val tvgName = tvgNameMatch?.groupValues?.get(1)
            
            return IptvChannel(
                name = name,
                logo = logo,
                url = "", // Will be set from next line
                group = group,
                tvgId = tvgId,
                tvgName = tvgName
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Searches channels by name across all categories.
     *
     * @param query Search query string
     * @param channels List of channels to search in
     * @return Filtered list of matching channels
     */
    fun searchChannels(query: String, channels: List<IptvChannel>): List<IptvChannel> {
        if (query.isBlank()) return channels
        return channels.filter { 
            it.name.contains(query, ignoreCase = true)
        }
    }
}
```

---

## 3. Live TV ViewModel

Create a new file `app/src/main/java/com/kiduyuk/klausk/kiduyutv/viewmodel/LiveTvViewModel.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import com.kiduyuk.klausk.kiduyutv.data.repository.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Live TV screen.
 *
 * @param isLoading Loading state for initial playlist fetch
 * @param categories List of available categories
 * @param selectedCategory Currently selected category
 * @param channels Channels in the selected category
 * @param selectedChannel Currently selected channel for playback
 * @param error Error message if playlist fetch failed
 */
data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: String? = null,
    val channels: List<IptvChannel> = emptyList(),
    val selectedChannel: IptvChannel? = null,
    val error: String? = null
)

/**
 * Represents a category item for display in the UI.
 *
 * @param name Category name
 * @param channelCount Number of channels in this category
 */
data class CategoryItem(
    val name: String,
    val channelCount: Int
)

/**
 * ViewModel for the Live TV screen.
 * Manages playlist fetching, category selection, and channel browsing.
 */
class LiveTvViewModel : ViewModel() {
    
    private val repository = IptvRepository.getInstance()
    
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()
    
    private var cachedPlaylist: IptvPlaylist? = null
    
    init {
        loadPlaylist()
    }
    
    /**
     * Loads the IPTV playlist from the remote server.
     */
    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.fetchPlaylist().fold(
                onSuccess = { playlist ->
                    cachedPlaylist = playlist
                    val categoryItems = playlist.categories.keys.map { name ->
                        CategoryItem(
                            name = name,
                            channelCount = playlist.categories[name]?.size ?: 0
                        )
                    }.sortedBy { it.name }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        categories = categoryItems,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load playlist"
                    )
                }
            )
        }
    }
    
    /**
     * Selects a category and loads its channels.
     *
     * @param categoryName The name of the category to select
     */
    fun selectCategory(categoryName: String) {
        cachedPlaylist?.let { playlist ->
            val channels = playlist.categories[categoryName] ?: emptyList()
            _uiState.value = _uiState.value.copy(
                selectedCategory = categoryName,
                channels = channels,
                selectedChannel = null
            )
        }
    }
    
    /**
     * Clears the category selection and returns to categories view.
     */
    fun clearCategorySelection() {
        _uiState.value = _uiState.value.copy(
            selectedCategory = null,
            channels = emptyList(),
            selectedChannel = null
        )
    }
    
    /**
     * Selects a channel for playback.
     *
     * @param channel The channel to play
     */
    fun selectChannel(channel: IptvChannel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
    }
    
    /**
     * Clears the selected channel.
     */
    fun clearSelectedChannel() {
        _uiState.value = _uiState.value.copy(selectedChannel = null)
    }
    
    /**
     * Gets all channels (for "All Channels" view).
     *
     * @return List of all channels in the playlist
     */
    fun getAllChannels(): List<IptvChannel> {
        return cachedPlaylist?.allChannels ?: emptyList()
    }
}
```

---

## 4. Live TV Screen (Categories)

Update the existing `app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/screens/home/tv/LiveTvScreen.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kiduyuk.klausk.kiduyutv.data.model.CategoryItem
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.DarkRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.SurfaceDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel

/**
 * Composable function for the Live TV screen.
 * Displays categories and channels based on the IPTV playlist.
 *
 * @param onChannelPlay Callback when a channel is selected for playback
 * @param onNavigate Lambda to handle navigation between top-level screens
 * @param onSearchClick Lambda to navigate to the search screen
 * @param onSettingsClick Lambda to navigate to the settings screen
 * @param viewModel The [LiveTvViewModel] instance
 */
@Composable
fun LiveTvScreen(
    onChannelPlay: (IptvChannel) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationClick: (id: Int, type: String) -> Unit = { _, _ -> },
    viewModel: LiveTvViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val firstItemFocusRequester = remember { FocusRequester() }
    
    // Handle channel selection for playback
    LaunchedEffect(uiState.selectedChannel) {
        uiState.selectedChannel?.let { channel ->
            onChannelPlay(channel)
            viewModel.clearSelectedChannel()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top Bar
            TopBar(
                selectedRoute = "live_tv",
                onNavItemClick = onNavigate,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onNotificationClick = onNotificationClick
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when {
                // Loading state
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieLoadingView(size = 300.dp)
                    }
                }
                
                // Error state
                uiState.error != null -> {
                    ErrorContent(
                        errorMessage = uiState.error!!,
                        onRetry = { viewModel.loadPlaylist() }
                    )
                }
                
                // Categories view
                uiState.selectedCategory == null -> {
                    CategoriesContent(
                        categories = uiState.categories,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onCategoryClick = { category ->
                            viewModel.selectCategory(category.name)
                        },
                        onBackClick = { viewModel.clearCategorySelection() }
                    )
                }
                
                // Channels view
                else -> {
                    ChannelsContent(
                        categoryName = uiState.selectedCategory!!,
                        channels = uiState.channels,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onChannelClick = { channel ->
                            viewModel.selectChannel(channel)
                        },
                        onBackClick = { viewModel.clearCategorySelection() }
                    )
                }
            }
        }
    }
}

/**
 * Content displaying all available categories.
 */
@Composable
private fun CategoriesContent(
    categories: List<CategoryItem>,
    firstItemFocusRequester: FocusRequester,
    onCategoryClick: (CategoryItem) -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Live TV Categories",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No categories available",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    CategoryCard(
                        category = category,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

/**
 * Card component for displaying a category.
 */
@Composable
private fun CategoryCard(
    category: CategoryItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) DarkRed else CardDark)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = category.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${category.channelCount} channels",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Content displaying channels within a selected category.
 */
@Composable
private fun ChannelsContent(
    categoryName: String,
    channels: List<IptvChannel>,
    firstItemFocusRequester: FocusRequester,
    onChannelClick: (IptvChannel) -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Back button and category title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val isBackFocused by backInteractionSource.collectIsFocusedAsState()
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBackFocused) DarkRed else CardDark)
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = onBackClick
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "← Back",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = categoryName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No channels in this category",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(channels) { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
    }
}

/**
 * Card component for displaying a channel.
 */
@Composable
private fun ChannelCard(
    channel: IptvChannel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) DarkRed else CardDark)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Channel logo
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Error content with retry button.
 */
@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error loading playlist",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val interactionSource = remember { MutableInteractionSource() }
            val isRetryFocused by interactionSource.collectIsFocusedAsState()
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRetryFocused) PrimaryRed else DarkRed)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onRetry
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Retry",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
```

---

## 5. ExoPlayer Activity

Create a new file `app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/player/iptv/IptvPlayerActivity.kt`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

/**
 * Activity for playing IPTV live streams using ExoPlayer.
 * Supports live TV streaming with proper buffering and error handling.
 */
@OptIn(UnstableApi::class)
class IptvPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "IptvPlayerActivity"
        
        // Intent extras keys
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"
        
        /**
         * Creates an Intent to start the IPTV player activity.
         *
         * @param context Application context
         * @param channelName Name of the channel being played
         * @param streamUrl URL of the stream to play
         * @param channelLogo URL of the channel logo
         * @return Intent to start the activity
         */
        fun createIntent(
            context: Context,
            channelName: String,
            streamUrl: String,
            channelLogo: String? = null
        ): Intent {
            return Intent(context, IptvPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_STREAM_URL, streamUrl)
                putExtra(EXTRA_CHANNEL_LOGO, channelLogo)
            }
        }
    }
    
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var channelName: String = ""
    private var streamUrl: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get intent extras
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
        
        if (streamUrl.isBlank()) {
            finish()
            return
        }
        
        setupPlayer()
    }
    
    /**
     * Sets up the ExoPlayer with the stream URL.
     */
    private fun setupPlayer() {
        // Create track selector for adaptive streaming
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSizeSd()
                .setForceLowestBitrate(false)
        )
        
        // Create renderers factory
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        
        // Create ExoPlayer
        player = ExoPlayer.Builder(this, renderersFactory, trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        // Create PlayerView
        playerView = PlayerView(this).apply {
            player = this@IptvPlayerActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Create root layout
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(playerView)
        }
        
        setContentView(rootLayout)
        
        // Prepare and play
        player?.apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            setMediaItem(mediaItem)
            playWhenReady = true
            addListener(playerListener)
            prepare()
        }
    }
    
    /**
     * Player listener for handling playback events.
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    // Show buffering indicator if needed
                }
                Player.STATE_READY -> {
                    // Player is ready to play
                }
                Player.STATE_ENDED -> {
                    // Live stream ended (rare for live TV)
                }
                Player.STATE_IDLE -> {
                    // Player is idle
                }
            }
        }
        
        override fun onPlayerError(error: PlaybackException) {
            // Handle playback error
            showErrorDialog(error.message ?: "Playback error occurred")
        }
    }
    
    /**
     * Shows an error dialog when playback fails.
     */
    private fun showErrorDialog(message: String) {
        QuitDialog(
            context = this,
            title = "Playback Error",
            message = message,
            positiveButtonText = "Retry",
            negativeButtonText = "Exit",
            lottieAnimRes = R.raw.exit,
            onNo = { finish() },
            onYes = { 
                player?.prepare()
                player?.play()
            }
        ).show()
    }
    
    override fun onStart() {
        super.onStart()
        player?.play()
    }
    
    override fun onResume() {
        super.onResume()
        player?.play()
    }
    
    override fun onPause() {
        super.onPause()
        player?.pause()
    }
    
    override fun onStop() {
        super.onStop()
        player?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        playerView = null
    }
    
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }
    
    /**
     * Shows exit confirmation dialog.
     */
    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Stop Playback?",
            message = "Are you sure you want to stop watching $channelName?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { },
            onYes = { finish() }
        ).show()
    }
}
```

---

## 6. Navigation Updates

Update `NavGraph.kt` to handle the Live TV navigation and channel playback:

```kotlin
// Add import for IptvPlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.IptvPlayerActivity

// Add import for LiveTvViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel

// Update the LiveTvScreen composable to handle playback
composable(Screen.LiveTv.route) {
    LiveTvScreen(
        onChannelPlay = { channel ->
            // Start the IPTV player activity
            val intent = IptvPlayerActivity.createIntent(
                context = context,
                channelName = channel.name,
                streamUrl = channel.url,
                channelLogo = channel.logo
            )
            startActivity(intent)
        },
        onNavigate = { route ->
            if (route != Screen.LiveTv.route) {
                navController.navigate(route)
            }
        },
        onSearchClick = {
            navController.navigate(Screen.Search.route)
        },
        onSettingsClick = {
            navController.navigate(Screen.Settings.route)
        }
    )
}
```

---

## 7. Gradle Dependencies

Add the following dependencies to `app/build.gradle`:

```gradle
dependencies {
    // ... existing dependencies ...
    
    // ExoPlayer / Media3
    implementation 'androidx.media3:media3-exoplayer:1.2.1'
    implementation 'androidx.media3:media3-exoplayer-hls:1.2.1'
    implementation 'androidx.media3:media3-exoplayer-dash:1.2.1'
    implementation 'androidx.media3:media3-ui:1.2.1'
    
    // OkHttp (for IPTV repository)
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // Coil for image loading (already exists)
    implementation 'io.coil-kt:coil-compose:2.5.0'
}
```

---

## 8. Android Manifest Updates

Update `app/src/main/AndroidManifest.xml` to add the IPTV player activity:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.kiduyuk.klausk.kiduyutv">
    
    <!-- Permissions for IPTV streaming -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <application
        android:name=".KiduyuTvApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.KiduyuTv"
        android:usesCleartextTraffic="true">
        
        <!-- Main Activity -->
        <activity
            android:name=".activity.mainactivity.MainActivity"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout|uiMode"
            android:theme="@style/Theme.KiduyuTv">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <!-- IPTV Player Activity -->
        <activity
            android:name=".ui.player.iptv.IptvPlayerActivity"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout|uiMode"
            android:theme="@style/Theme.KiduyuTv.Fullscreen" />
        
        <!-- Other existing activities... -->
        
    </application>
</manifest>
```

---

## 9. Screen Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Live TV Screen                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    TopBar (NavBar)                       │   │
│  │  [Logo] Movies | TV Shows | My List | Live TV | 🔔 | 🔍 | ⚙️ │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Live TV Categories                          │   │
│  │                                                          │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│  │  │ Sports   │ │ Movies   │ │ News     │ │ Kids     │   │   │
│  │  │ 25 ch    │ │ 15 ch    │ │ 12 ch    │ │ 10 ch    │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│  │                                                          │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│  │  │ Music    │ │ Entertainment│ Entertainment │ More  │   │   │
│  │  │ 18 ch    │ │ 20 ch    │ │ 8 ch     │ │ 5 ch     │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

                           │
                           ▼ (User clicks "Sports")
                           │
┌─────────────────────────────────────────────────────────────────┐
│                     Sports Channels Screen                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ [← Back]              Sports                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │  🏀 ESPN │ │  ⚽ BeIN │ │  🏈 NFL  │ │  🎾 TENN │           │
│  │          │ │          │ │          │ │          │           │
│  │  [logo]  │ │  [logo]  │ │  [logo]  │ │  [logo]  │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │  ⚽ Sky  │ │  🏀 ESPN │ │  🎯 Darts │ │  🥊 Box  │           │
│  │          │ │          │ │          │ │          │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
└─────────────────────────────────────────────────────────────────┘

                           │
                           ▼ (User clicks "ESPN")
                           │
┌─────────────────────────────────────────────────────────────────┐
│                   ExoPlayer Activity                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │                                                          │   │
│  │                    LIVE STREAM                          │   │
│  │                                                          │   │
│  │                      ESPN                               │   │
│  │                                                          │   │
│  │  ◀◀  ▶/❚❚  ▶▶    ──────●─────────────    🔊    ⛶    │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. Additional Considerations

### 10.1 Error Handling
- The implementation includes error handling for network failures and playback errors
- Users can retry failed operations through the UI

### 10.2 Performance Optimization
- Lazy loading for categories and channels to handle large playlists
- Image caching with Coil for channel logos
- Connection pooling in OkHttp for efficient network requests

### 10.3 M3U Playlist Parsing
The parser handles standard M3U8 format:
```
#EXTM3U
#EXTINF:-1 tvg-id="espn.us" tvg-name="ESPN" tvg-logo="https://example.com/espn.png" group-title="Sports",ESPN
https://stream.example.com/espn.m3u8
```

### 10.4 Stream URL Formats
The implementation supports:
- HLS streams (.m3u8)
- MPEG-DASH streams (.mpd)
- Direct MP4 streams

### 10.5 Future Enhancements
- EPG (Electronic Program Guide) integration
- Channel favoriting/bookmarking
- Picture-in-Picture mode
- Channel recording capabilities

---

## Summary

This implementation provides a complete Live TV solution with:

1. **Data Layer**: IPTV playlist parsing and channel management
2. **Repository Layer**: Network requests with caching
3. **ViewModel Layer**: State management and business logic
4. **UI Layer**: Jetpack Compose screens for categories and channels
5. **Player Layer**: ExoPlayer-based video playback activity

All code snippets are ready for integration into the KiduyuTV Android application.