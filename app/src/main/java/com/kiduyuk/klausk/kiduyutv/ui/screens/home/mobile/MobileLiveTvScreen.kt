package com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.CountryPlaylist
import com.kiduyuk.klausk.kiduyutv.viewmodel.CategoryItem
import com.kiduyuk.klausk.kiduyutv.data.model.ScrapedChannel
import com.kiduyuk.klausk.kiduyutv.data.repository.ChannelScraper
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileBottomNavigation
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileSearchTopBar
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.IptvPlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.SchedulePlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.ScrapedChannelsCache
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel
import kotlinx.coroutines.launch

@Composable
fun MobileLiveTvScreen(
    navController: NavController,
    onNavigate: (String) -> Unit = {},
    viewModel: LiveTvViewModel = viewModel()
) {
    val context = LocalContext.current
    val daddyLiveScope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val uiState by viewModel.uiState.collectAsState()
    val daddyLiveEnabled = remember(context) {
        SettingsManager(context).isDaddyLiveEnabled()
    }
    var scrapedChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var scrapedChannelsLoading by remember { mutableStateOf(daddyLiveEnabled) }
    var scrapedChannelsError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(daddyLiveEnabled) {
        viewModel.initialize(context)
        if (daddyLiveEnabled) {
            scrapedChannelsLoading = true
            val cached = ScrapedChannelsCache.loadChannels(context)
            scrapedChannels = cached.map { it.toMobileIptvChannel() }
            scrapedChannelsError = if (scrapedChannels.isEmpty()) {
                "No scraped channels are cached. Scrape channels from Settings first."
            } else {
                null
            }
            scrapedChannelsLoading = false
        } else {
            viewModel.loadPlaylist()
        }
    }

    Scaffold(
        topBar = {
            MobileSearchTopBar(
                onSearchClick = { onNavigate(Screen.Search.route) },
                onSettingsClick = { onNavigate(Screen.Settings.route) },
                title = "Live TV"
            )
        },
        bottomBar = { MobileBottomNavigation(navController, currentRoute) }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {

            if (daddyLiveEnabled) {
                MobileDaddyLiveContent(
                    channels = scrapedChannels,
                    isLoading = scrapedChannelsLoading,
                    error = scrapedChannelsError,
                    onScrape = {
                        daddyLiveScope.launch {
                            scrapedChannelsLoading = true
                            scrapedChannelsError = null
                            val result = ChannelScraper.fetchChannels(fetchStreamUrls = true)
                            val scraped = result.getOrNull()
                            if (scraped != null) {
                                ScrapedChannelsCache.saveChannels(context, scraped)
                                scrapedChannels = scraped.map { it.toMobileIptvChannel() }
                                scrapedChannelsError = if (scrapedChannels.isEmpty()) {
                                    "No channels were found. Check the DaddyLive address and try again."
                                } else {
                                    null
                                }
                            } else {
                                scrapedChannelsError = result.exceptionOrNull()?.message
                                    ?: "Unable to scrape DaddyLive channels."
                            }
                            scrapedChannelsLoading = false
                        }
                    },
                    onChannelClick = { channel ->
                        context.startActivity(
                            SchedulePlayerActivity.createIntent(
                                context = context,
                                channelId = channel.id,
                                channelName = channel.name,
                                eventTitle = channel.name,
                                iframeUrls = channel.url
                                    .takeIf { it.isNotBlank() }
                                    ?.let { listOf(it) }
                                    .orEmpty()
                            )
                        )
                    }
                )
                return@Box
            }

            if (uiState.isLoading && selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    LottieLoadingView(size = 200.dp)
                }
                return@Box
            }

            if (uiState.error != null && selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = "${uiState.error}")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadPlaylist(forceRefresh = true) }) {
                        Text("Retry")
                    }
                }
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Black,
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Live TV") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val favCount = viewModel.getFavoriteChannels().size
                            Text(
                                text = if (favCount > 0) "My Channels ($favCount)" else "My Channels"
                            )
                        }
                    )
                }

                // Tab content
                when (selectedTab) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                placeholder = { Text("Search channels...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF121212),
                                    unfocusedContainerColor = Color(0xFF121212),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedPlaceholderColor = Color.LightGray,
                                    unfocusedPlaceholderColor = Color.LightGray,
                                    focusedBorderColor = PrimaryRed,
                                    unfocusedBorderColor = TextSecondary
                                ),
                                singleLine = true
                            )

                            if (uiState.searchQuery.isNotBlank()) {
                                if (uiState.searchResults.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "No channels match \"${uiState.searchQuery}\"", color = TextSecondary)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                        items(uiState.searchResults) { channel ->
                                            ChannelRow(channel) { selected ->
                                                val intent = IptvPlayerActivity.createIntent(
                                                    context,
                                                    selected.name,
                                                    selected.url,
                                                    selected.logo,
                                                    selected.tvgId,
                                                    selected.tvgName,
                                                    selected.group
                                                )
                                                context.startActivity(intent)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            } else {
                                if (uiState.playlistChoices.isNotEmpty()) {
                                    CountryPlaylistList(
                                        playlists = uiState.playlistChoices,
                                        onPlaylistClick = viewModel::selectCountryPlaylist,
                                        onBackClick = viewModel::clearCategorySelection
                                    )
                                } else if (uiState.selectedCategory == null) {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                        items(uiState.categories) { category ->
                                            CountryCategoryRow(category) {
                                                category.countryCode?.let(viewModel::selectCountry)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                } else {
                                    // Channels list for selected category
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                        items(uiState.channels) { channel ->
                                            ChannelRow(channel) { selected ->
                                                val intent = IptvPlayerActivity.createIntent(
                                                    context,
                                                    selected.name,
                                                    selected.url,
                                                    selected.logo,
                                                    selected.tvgId,
                                                    selected.tvgName,
                                                    selected.group
                                                )
                                                context.startActivity(intent)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // My Channels tab - favorites
                        val favorites = viewModel.getFavoriteChannels()
                        if (favorites.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "No favorite channels yet")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Long-press a channel to add it to favorites")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                items(favorites) { channel ->
                                    ChannelRow(channel) { selected ->
                                        val intent = IptvPlayerActivity.createIntent(
                                            context,
                                            selected.name,
                                            selected.url,
                                            selected.logo,
                                            selected.tvgId,
                                            selected.tvgName,
                                            selected.group
                                        )
                                        context.startActivity(intent)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileDaddyLiveContent(
    channels: List<IptvChannel>,
    isLoading: Boolean,
    error: String?,
    onScrape: () -> Unit,
    onChannelClick: (IptvChannel) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredChannels = remember(channels, query) {
        if (query.isBlank()) {
            channels
        } else {
            channels.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LottieLoadingView(size = 200.dp)
            }
        }

        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = error, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onScrape) {
                    Text("Scrape Channels")
                }
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    placeholder = { Text("Search DaddyLive channels...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.LightGray,
                        unfocusedPlaceholderColor = Color.LightGray,
                        focusedBorderColor = PrimaryRed,
                        unfocusedBorderColor = TextSecondary
                    ),
                    singleLine = true
                )

                if (filteredChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No DaddyLive channels found", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        items(filteredChannels, key = { "daddylive_${it.id}" }) { channel ->
                            ChannelRow(channel = channel, onPlay = onChannelClick)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun ScrapedChannel.toMobileIptvChannel() = IptvChannel(
    name = name,
    logo = thumbnailUrl,
    url = primaryStreamUrl.orEmpty(),
    group = category ?: "DaddyLive",
    tvgId = id,
    tvgName = name
)

@Composable
private fun CountryCategoryRow(category: CategoryItem, onClick: () -> Unit) {
    val fallbackFlagUrl = category.countryCode?.let { countryCode ->
        "https://cdn.jsdelivr.net/gh/hampusborgos/country-flags@main/svg/$countryCode.svg"
    }
    var flagUrl by remember(category.countryCode, category.flagUrl) {
        mutableStateOf(category.flagUrl)
    }
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = flagUrl,
            contentDescription = "${category.name} flag",
            modifier = Modifier.size(32.dp),
            onError = {
                if (!fallbackFlagUrl.isNullOrBlank() && flagUrl != fallbackFlagUrl) {
                    flagUrl = fallbackFlagUrl
                }
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = category.name, modifier = Modifier.weight(1f))
        Text(text = if (category.channelCount == 1) "1 playlist" else "${category.channelCount} playlists")
    }
}

@Composable
private fun CountryPlaylistList(
    playlists: List<CountryPlaylist>,
    onPlaylistClick: (CountryPlaylist) -> Unit,
    onBackClick: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            Button(onClick = onBackClick) { Text("← Countries") }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Choose a playlist")
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(playlists, key = { it.url }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = playlist.displayName, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChannelRow(channel: IptvChannel, onPlay: (IptvChannel) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onPlay(channel) }
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = channel.name)
            Text(text = channel.group ?: "", modifier = Modifier.padding(top = 4.dp))
        }
        Button(onClick = { onPlay(channel) }) { Text("Play") }
    }
}
