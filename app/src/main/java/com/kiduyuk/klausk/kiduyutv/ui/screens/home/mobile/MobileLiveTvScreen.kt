package com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import android.content.Context
import android.widget.Toast
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.CountryPlaylist
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleChannel
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleEvent
import com.kiduyuk.klausk.kiduyutv.data.model.is18PlusChannel
import com.kiduyuk.klausk.kiduyutv.viewmodel.CategoryItem
import com.kiduyuk.klausk.kiduyutv.data.model.ScrapedChannel
import com.kiduyuk.klausk.kiduyutv.data.repository.ChannelScraper
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileBottomNavigation
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileSearchTopBar
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.IptvPlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.SchedulePlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.DarkRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.ScrapedChannelsCache
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.ScheduleUiState
import com.kiduyuk.klausk.kiduyutv.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch

@Composable
fun MobileLiveTvScreen(
    navController: NavController,
    onNavigate: (String) -> Unit = {},
    viewModel: LiveTvViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val daddyLiveScope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val uiState by viewModel.uiState.collectAsState()
    val scheduleUiState by scheduleViewModel.uiState.collectAsState()
    var scrapedChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var scrapedChannelsLoading by remember { mutableStateOf(false) }
    var scrapedChannelsError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var favoriteChannelToConfirm by remember { mutableStateOf<IptvChannel?>(null) }
    var scrapedChannelToConfirm by remember { mutableStateOf<IptvChannel?>(null) }
    val countryListState = rememberLazyListState()
    val visibleScrapedChannels = remember(scrapedChannels, uiState.hide18PlusChannels) {
        if (uiState.hide18PlusChannels) {
            scrapedChannels.filterNot(IptvChannel::is18PlusChannel)
        } else {
            scrapedChannels
        }
    }
    // DaddyLive channels the viewer saved live in the ViewModel, mirroring TV, so
    // they survive rotation and stay in sync with the My Channels badge.
    val savedScrapedChannels by viewModel.scrapedChannels.collectAsState()
    val visibleSavedScrapedChannels = remember(savedScrapedChannels, uiState.hide18PlusChannels) {
        if (uiState.hide18PlusChannels) {
            savedScrapedChannels.filterNot(IptvChannel::is18PlusChannel)
        } else {
            savedScrapedChannels
        }
    }

    // Initialize both data sources once; tabs then render their own loading states.
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadPlaylist()
        scheduleViewModel.initialize(context)
    }

    // Load cached DaddyLive channels when its tab is opened without blocking Live TV.
    // The schedule is fetched on first visit only, like TV, so opening the app does
    // not scrape the schedule the viewer may never look at.
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && scheduleUiState.scheduleDays.isEmpty()) {
            scheduleViewModel.loadSchedule()
        }
        if (selectedTab == 2 && scrapedChannels.isEmpty() && !scrapedChannelsLoading) {
            scrapedChannelsLoading = true
            val cached = ScrapedChannelsCache.loadChannels(context)
                .filter { it.watchPageUrl.startsWith("https://dlive.sx/") }
            scrapedChannels = cached.map { it.toMobileIptvChannel() }
            scrapedChannelsError = if (scrapedChannels.isEmpty()) {
                "No cached channels yet. Tap Scrape Channels to load DaddyLive."
            } else {
                null
            }
            scrapedChannelsLoading = false
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
                // Scrollable Material tabs keep all four destinations usable on phones.
                val tabItems = listOf(
                    "Live TV" to Icons.Default.Tv,
                    "Schedule" to Icons.Default.CalendarToday,
                    "DaddyLive" to Icons.Default.PlayCircle,
                    "My Channels" to Icons.Default.List
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardDark,
                    contentColor = TextPrimary,
                    edgePadding = 12.dp,
                    divider = {}
                ) {
                    tabItems.forEachIndexed { index, (title, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            selectedContentColor = Color.White,
                            unselectedContentColor = TextSecondary,
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = if (index == 3) {
                                        val count = viewModel.getFavoriteChannels()
                                            .count { !uiState.hide18PlusChannels || !it.is18PlusChannel() } +
                                            visibleSavedScrapedChannels.size
                                        if (count > 0) "$title ($count)" else title
                                    } else title,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                // Tab content
                when (selectedTab) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val isCountryDirectory = uiState.selectedCategory == null &&
                                uiState.playlistChoices.isEmpty()
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                placeholder = {
                                    Text(if (isCountryDirectory) "Search countries..." else "Search channels...")
                                },
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
                                if (isCountryDirectory) {
                                    val matchingCountries = uiState.categories.filter { country ->
                                        country.name.contains(uiState.searchQuery, ignoreCase = true) ||
                                            country.countryCode?.contains(uiState.searchQuery, ignoreCase = true) == true
                                    }
                                    if (matchingCountries.isEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "No countries match \"${uiState.searchQuery}\"",
                                                color = TextSecondary
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(12.dp)
                                        ) {
                                            items(matchingCountries) { country ->
                                                CountryCategoryRow(country) {
                                                    country.countryCode?.let(viewModel::selectCountry)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                } else if (uiState.searchResults.isEmpty()) {
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
                                            ChannelRow(
                                                channel,
                                                onLongClick = { favoriteChannelToConfirm = channel }
                                            ) { selected ->
                                                val intent = IptvPlayerActivity.createIntent(
                                                    context,
                                                    selected.name,
                                                    selected.url,
                                                    selected.logo,
                                                    selected.tvgId,
                                                    selected.tvgName,
                                                    selected.group,
                                                    playlistChannels = viewModel.getAllChannels()
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
                                    val lastSelectedCountryCode = uiState.lastSelectedCountryCode
                                    LaunchedEffect(uiState.categories, lastSelectedCountryCode) {
                                        val targetIndex = lastSelectedCountryCode
                                            ?.let { code -> uiState.categories.indexOfFirst { it.countryCode.equals(code, ignoreCase = true) } }
                                            ?.takeIf { it >= 0 }
                                            ?: 0
                                        if (uiState.categories.isNotEmpty()) {
                                            countryListState.scrollToItem(targetIndex)
                                        }
                                    }
                                    LazyColumn(
                                        state = countryListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(12.dp)
                                    ) {
                                        items(uiState.categories, key = { it.countryCode ?: it.name }) { category ->
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
                                            ChannelRow(
                                                channel,
                                                onLongClick = { favoriteChannelToConfirm = channel }
                                            ) { selected ->
                                                val intent = IptvPlayerActivity.createIntent(
                                                    context,
                                                    selected.name,
                                                    selected.url,
                                                    selected.logo,
                                                    selected.tvgId,
                                                    selected.tvgName,
                                                    selected.group,
                                                    playlistChannels = viewModel.getAllChannels()
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
                    3 -> {
                        // My Channels tab - favorites
                        LaunchedEffect(Unit) {
                            viewModel.syncFavoriteChannelsWithFirebase()
                        }
                        val favorites = viewModel.getFavoriteChannels().let { channels ->
                            if (uiState.hide18PlusChannels) {
                                channels.filterNot(IptvChannel::is18PlusChannel)
                            } else {
                                channels
                            }
                        }
                        if (favorites.isEmpty() && visibleSavedScrapedChannels.isEmpty()) {
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
                                if (visibleSavedScrapedChannels.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Saved DaddyLive",
                                            color = PrimaryRed,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    items(visibleSavedScrapedChannels, key = { "saved_scraped_${it.tvgId}" }) { channel ->
                                        ChannelRow(channel) { selected ->
                                            playScrapedChannelIntent(context, selected)
                                                ?.let { context.startActivity(it) }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    item { Spacer(modifier = Modifier.height(8.dp)) }
                                }
                                if (favorites.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Favorites",
                                            color = PrimaryRed,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                                items(favorites) { channel ->
                                    ChannelRow(channel) { selected ->
                                        val intent = IptvPlayerActivity.createIntent(
                                            context,
                                            selected.name,
                                            selected.url,
                                            selected.logo,
                                            selected.tvgId,
                                            selected.tvgName,
                                            selected.group,
                                            playlistChannels = viewModel.getAllChannels()
                                        )
                                        context.startActivity(intent)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        MobileScheduleTabContent(
                            uiState = scheduleUiState,
                            viewModel = scheduleViewModel,
                            onChannelClick = { channel, event ->
                                context.startActivity(
                                    SchedulePlayerActivity.createIntent(
                                        context = context,
                                        channelId = channel.id,
                                        channelName = channel.name,
                                        eventTitle = event.title
                                    )
                                )
                            }
                        )
                    }
                    2 -> {
                        MobileDaddyLiveContent(
                            channels = visibleScrapedChannels,
                            isLoading = scrapedChannelsLoading,
                            error = scrapedChannelsError,
                            onScrape = {
                                daddyLiveScope.launch {
                                    scrapedChannelsLoading = true
                                    scrapedChannelsError = null
                                    // Stream URLs are resolved by the player at click
                                    // time, so the scrape only collects watch pages.
                                    val result = ChannelScraper.fetchChannels(fetchStreamUrls = false)
                                    val scraped = result.getOrNull()
                                    if (scraped != null) {
                                        ScrapedChannelsCache.saveChannels(context, scraped)
                                        scrapedChannels = scraped.map { it.toMobileIptvChannel() }
                                        scrapedChannelsError = if (scrapedChannels.isEmpty()) {
                                            "No channels were found. Check the DaddyLive address and try again."
                                        } else null
                                    } else {
                                        val cachedChannels = ScrapedChannelsCache.loadChannels(context)
                                            .filter { it.watchPageUrl.startsWith("https://dlive.sx/") }
                                        scrapedChannels = cachedChannels.map { it.toMobileIptvChannel() }
                                        scrapedChannelsError = if (scrapedChannels.isEmpty()) {
                                            result.exceptionOrNull()?.message
                                                ?: "Unable to scrape DaddyLive channels."
                                        } else {
                                            null
                                        }
                                    }
                                    scrapedChannelsLoading = false
                                }
                            },
                            onChannelClick = { channel ->
                                playScrapedChannelIntent(context, channel)
                                    ?.let { context.startActivity(it) }
                            },
                            onChannelLongClick = { channel ->
                                scrapedChannelToConfirm = channel
                            }
                        )
                    }
                }
            }
    }

        favoriteChannelToConfirm?.let { channelToConfirm ->
            AlertDialog(
                onDismissRequest = { favoriteChannelToConfirm = null },
                title = { Text(text = "Add to favorites?") },
                text = { Text(text = "Add ${channelToConfirm.name} to your favorites?") },
                confirmButton = {
                    TextButton(onClick = {
                        if (viewModel.isFavorite(channelToConfirm)) {
                            Toast.makeText(context, "Already in favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addFavorite(channelToConfirm)
                            Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                        }
                        favoriteChannelToConfirm = null
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { favoriteChannelToConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        scrapedChannelToConfirm?.let { channelToConfirm ->
            AlertDialog(
                onDismissRequest = { scrapedChannelToConfirm = null },
                title = { Text(text = "Add to My Scraped Channels?") },
                text = {
                    Text(
                        text = "Add ${channelToConfirm.name} so it is available in My Channels?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (viewModel.isScrapedChannel(channelToConfirm)) {
                            Toast.makeText(context, "Already in My Scraped Channels", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addScrapedChannel(channelToConfirm)
                            Toast.makeText(context, "Added to My Scraped Channels", Toast.LENGTH_SHORT).show()
                        }
                        scrapedChannelToConfirm = null
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { scrapedChannelToConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
}

}

/**
 * Compact schedule presentation for phones. Events expand to reveal playable channels.
 */
@Composable
private fun MobileScheduleTabContent(
    uiState: ScheduleUiState,
    viewModel: ScheduleViewModel,
    onChannelClick: (ScheduleChannel, ScheduleEvent) -> Unit
) {
    when {
        uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LottieLoadingView(size = 180.dp)
        }
        uiState.error != null -> MobileScheduleEmptyState(
            message = uiState.error ?: "Unable to load schedule",
            actionLabel = "Retry",
            onAction = { viewModel.loadSchedule(forceRefresh = true) }
        )
        uiState.scheduleDays.isEmpty() -> MobileScheduleEmptyState(
            message = "No schedule is available right now.",
            actionLabel = "Refresh",
            onAction = { viewModel.loadSchedule(forceRefresh = true) }
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.scheduleDays.forEach { day ->
                item(key = day.date) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = day.dateTitle,
                                color = PrimaryRed,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            day.categories.forEach { category ->
                                Text(
                                    text = category.name,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                category.events.forEach { event ->
                                    val expanded = event.id in uiState.expandedEventIds
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleEventExpansion(event.id) },
                                        color = if (expanded) DarkRed.copy(alpha = 0.35f) else BackgroundDark,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = event.displayTime,
                                                    color = PrimaryRed,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = event.title,
                                                    color = TextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                                    tint = TextSecondary
                                                )
                                            }
                                            if (expanded) {
                                                event.channels.forEach { channel ->
                                                    TextButton(
                                                        onClick = { onChannelClick(channel, event) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(channel.name, color = Color.White, modifier = Modifier.weight(1f))
                                                        Icon(Icons.Default.PlayCircle, contentDescription = "Play")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Displays a consistent retry state for mobile schedule loading failures. */
@Composable
private fun MobileScheduleEmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EventBusy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun MobileDaddyLiveContent(
    channels: List<IptvChannel>,
    isLoading: Boolean,
    error: String?,
    onScrape: () -> Unit,
    onChannelClick: (IptvChannel) -> Unit,
    onChannelLongClick: (IptvChannel) -> Unit
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = PrimaryRed)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading DaddyLive channels…",
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
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
                            ChannelRow(
                                channel = channel,
                                onLongClick = { onChannelLongClick(channel) },
                                onPlay = onChannelClick
                            )
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
    // DaddyLive is scraped as a directory of watch pages. Keeping this source URL
    // avoids representing a channel as an empty stream while server discovery is
    // intentionally deferred to SchedulePlayerActivity at click time.
    url = watchPageUrl,
    group = category ?: "DaddyLive",
    tvgId = id,
    tvgName = name
)

/**
 * Mirrors the TV path: pass only the numeric channel ID so the player resolves the
 * watch page's current servers at click time. Handing it a baked stream URL instead
 * would make SchedulePlayerActivity skip resolution and play a stale source.
 */
private fun playScrapedChannelIntent(
    context: Context,
    channel: IptvChannel
): android.content.Intent? {
    val scrapedChannelId = channel.tvgId?.takeIf { id -> id.all(Char::isDigit) }
    if (scrapedChannelId == null) {
        Toast.makeText(context, "This DaddyLive channel has no valid playback ID.", Toast.LENGTH_SHORT).show()
        return null
    }
    return SchedulePlayerActivity.createIntent(
        context = context,
        channelId = scrapedChannelId,
        channelName = channel.name,
        eventTitle = channel.name
    )
}

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: IptvChannel,
    onLongClick: (() -> Unit)? = null,
    onPlay: (IptvChannel) -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = { onPlay(channel) },
            onLongClick = onLongClick
        )
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
