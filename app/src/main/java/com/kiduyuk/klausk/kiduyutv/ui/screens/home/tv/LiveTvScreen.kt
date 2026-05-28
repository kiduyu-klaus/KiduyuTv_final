package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kiduyuk.klausk.kiduyutv.data.model.*
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.SchedulePlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.CategoryItem
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.ScheduleViewModel
import com.kiduyuk.klausk.kiduyutv.viewmodel.ScheduleUiState

/**
 * Composable function for the Live TV screen.
 * Displays categories, channels, and search functionality for IPTV playlist.
 * Now merged with Schedule view.
 *
 * @param onChannelPlay Callback when a channel is selected for playback
 * @param onNavigate Lambda to handle navigation between top-level screens
 * @param onSearchClick Lambda to navigate to the search screen
 * @param onSettingsClick Lambda to navigate to the settings screen
 * @param initialTab Index of the tab to show initially (0 for Live TV, 1 for Schedule)
 * @param viewModel The [LiveTvViewModel] instance
 * @param scheduleViewModel The [ScheduleViewModel] instance
 */
@Composable
fun LiveTvScreen(
    onChannelPlay: (IptvChannel) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationClick: (id: Int, type: String) -> Unit = { _, _ -> },
    initialTab: Int = 0,
    viewModel: LiveTvViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scheduleUiState by scheduleViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Initialize ViewModels with context
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadPlaylist()
        // Pre-load EPG data for program info
        viewModel.loadEpg()
        
        scheduleViewModel.initialize(context)
        scheduleViewModel.loadSchedule()
    }

    // Handle channel selection for playback
    LaunchedEffect(uiState.selectedChannel) {
        uiState.selectedChannel?.let { channel ->
            onChannelPlay(channel)
            viewModel.clearSelectedChannel()
        }
    }

    // Track selected tab
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf(
        TabItem("Live TV", Icons.Default.Tv),
        TabItem("Schedule", Icons.Default.CalendarToday)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Combined Top Bar with Navigation and Tabs
            LiveTvTopBar(
                selectedRoute = "live_tv",
                onNavItemClick = onNavigate,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onNotificationClick = onNotificationClick,
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTabIndex) {
                0 -> { // Live TV Tab
                    LiveTvTabContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
                1 -> { // Schedule Tab
                    ScheduleTabContent(
                        uiState = scheduleUiState,
                        viewModel = scheduleViewModel,
                        onChannelClick = { channel, event ->
                            scheduleViewModel.selectChannel(channel, event)
                            val iframeHtml = scheduleViewModel.getIframeHtml(channel.id)
                            val intent = Intent(context, SchedulePlayerActivity::class.java).apply {
                                putExtra("IFRAME_HTML", iframeHtml)
                                putExtra("CHANNEL_NAME", channel.name)
                                putExtra("EVENT_TITLE", event.title)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
        
        // Schedule error snackbar
        if (selectedTabIndex == 1 && scheduleUiState.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { scheduleViewModel.loadSchedule(forceRefresh = true) }) {
                        Text("Retry", color = PrimaryRed)
                    }
                }
            ) {
                Text(scheduleUiState.error!!)
            }
        }
    }
}

/**
 * Top bar with navigation items and internal tabs
 */
@Composable
private fun LiveTvTopBar(
    selectedRoute: String,
    onNavItemClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationClick: (Int, String) -> Unit,
    tabs: List<TabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(CardDark.copy(alpha = 0.5f))) {
        // Original TopBar for navigation
        TopBar(
            selectedRoute = selectedRoute,
            onNavItemClick = onNavItemClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onNotificationClick = onNotificationClick
        )
        
        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedTabIndex
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onTabSelected(index) }
                        )
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    color = if (isSelected) PrimaryRed else if (isFocused) DarkRed else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected || isFocused) Color.White else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected || isFocused) Color.White else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }

                if (index < tabs.size - 1) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
        }
    }
}

/**
 * Live TV specific content (extracted from original LiveTvScreen)
 */
@Composable
private fun LiveTvTabContent(
    uiState: com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvUiState,
    viewModel: LiveTvViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                    onRetry = { viewModel.loadPlaylist(forceRefresh = true) }
                )
            }

            // Search mode
            uiState.isSearchActive -> {
                SearchContent(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onChannelClick = { viewModel.selectChannel(it) },
                    onCloseSearch = { viewModel.deactivateSearch() },
                    onBackClick = { viewModel.deactivateSearch() }
                )
            }

            // Categories view
            uiState.selectedCategory == null -> {
                CategoriesContent(
                    categories = uiState.categories,
                    onCategoryClick = { category ->
                        viewModel.selectCategory(category.name)
                    },
                    onSearchClick = { viewModel.activateSearch() },
                    totalChannels = viewModel.getTotalChannelCount()
                )
            }

            // Channels view
            else -> {
                ChannelsContent(
                    categoryName = uiState.selectedCategory!!,
                    channels = uiState.channels,
                    onChannelClick = { channel ->
                        viewModel.selectChannel(channel)
                    },
                    onBackClick = { viewModel.clearCategorySelection() }
                )
            }
        }
    }
}

/**
 * Tab item data class
 */
private data class TabItem(
    val title: String,
    val icon: ImageVector
)

// --- Schedule components below ---

/**
 * Schedule tab content - shows upcoming events
 */
@Composable
private fun ScheduleTabContent(
    uiState: ScheduleUiState,
    viewModel: ScheduleViewModel,
    onChannelClick: (ScheduleChannel, ScheduleEvent) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                LottieLoadingView(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.scheduleDays.isEmpty() -> {
                EmptyScheduleView(
                    onRetry = { viewModel.loadSchedule(forceRefresh = true) }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Show each day
                    items(uiState.scheduleDays) { scheduleDay ->
                        ScheduleDayCard(
                            scheduleDay = scheduleDay,
                            expandedEventIds = uiState.expandedEventIds,
                            onEventClick = { eventId -> viewModel.toggleEventExpansion(eventId) },
                            onChannelClick = { channel, event -> onChannelClick(channel, event) }
                        )
                    }
                    
                    // Bottom padding
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        // Refresh indicator
        if (uiState.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = PrimaryRed
            )
        }
    }
}

/**
 * Empty state view for schedule
 */
@Composable
private fun EmptyScheduleView(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EventBusy,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Schedule Available",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Unable to load the schedule. Please check your connection.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFocused) Color.White else PrimaryRed,
                contentColor = if (isFocused) PrimaryRed else Color.White
            ),
            modifier = Modifier.focusable(interactionSource = interactionSource)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

/**
 * Card showing a single schedule day with categories and events
 */
@Composable
private fun ScheduleDayCard(
    scheduleDay: ScheduleDay,
    expandedEventIds: Set<String>,
    onEventClick: (String) -> Unit,
    onChannelClick: (ScheduleChannel, ScheduleEvent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Day title
            Text(
                text = scheduleDay.dateTitle,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryRed,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Categories
            scheduleDay.categories.forEach { category ->
                CategorySection(
                    category = category,
                    expandedEventIds = expandedEventIds,
                    onEventClick = onEventClick,
                    onChannelClick = onChannelClick
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Section showing a category with its events
 */
@Composable
private fun CategorySection(
    category: ScheduleCategory,
    expandedEventIds: Set<String>,
    onEventClick: (String) -> Unit,
    onChannelClick: (ScheduleChannel, ScheduleEvent) -> Unit
) {
    Column {
        // Category header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkRed.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${category.events.size} events",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Events
        category.events.forEach { event ->
            EventItem(
                event = event,
                isExpanded = expandedEventIds.contains(event.id),
                onClick = { onEventClick(event.id) },
                onChannelClick = { channel -> onChannelClick(channel, event) }
            )
        }
    }
}

/**
 * Individual event item that can be expanded to show channels
 */
@Composable
private fun EventItem(
    event: ScheduleEvent,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onChannelClick: (ScheduleChannel) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Focus requester for the first channel chip when expanded
    val firstChannelFocusRequester = remember { FocusRequester() }

    // Request focus on first channel when event is expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded && event.channels.isNotEmpty()) {
            firstChannelFocusRequester.requestFocus()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = if (isFocused) DarkRed.copy(alpha = 0.5f) else if (isExpanded) PrimaryRed.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = if (isFocused) BorderStroke(1.dp, Color.White) else null
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
        ) {
            // Event header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Time badge
                Surface(
                    color = if (isExpanded) PrimaryRed else DarkRed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = event.displayTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Event title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Expand icon with channel count badge
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.channels.size > 1) {
                        Surface(
                            color = if (isExpanded) PrimaryRed else DarkRed,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${event.channels.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextSecondary
                    )
                }
            }

            // Expanded channels - always focusable even with single channel
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Available Channels:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Channel chips - use LazyRow for better focus management
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(event.channels.size) { index ->
                            val channel = event.channels[index]
                            ChannelChip(
                                channel = channel,
                                isFirst = index == 0,
                                focusRequester = if (index == 0) firstChannelFocusRequester else null,
                                onClick = { onChannelClick(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clickable channel chip - always focusable for D-pad navigation
 */
@Composable
private fun ChannelChip(
    channel: ScheduleChannel,
    isFirst: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = if (isFocused) Color.White else PrimaryRed,
        shape = RoundedCornerShape(16.dp),
        border = if (isFocused) BorderStroke(2.dp, PrimaryRed) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = if (isFocused) PrimaryRed else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) PrimaryRed else Color.White,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Content displaying all available categories.
 */
@Composable
private fun CategoriesContent(
    categories: List<CategoryItem>,
    onCategoryClick: (CategoryItem) -> Unit,
    onSearchClick: () -> Unit,
    totalChannels: Int
) {
    // Focus requester for D-pad navigation
    val firstFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // Request focus on first item when categories load
    LaunchedEffect(categories) {
        if (categories.isNotEmpty()) {
            firstFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Header row with title and search button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live TV Categories",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$totalChannels channels available",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            SearchButton(onClick = onSearchClick)
        }

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
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                itemsIndexed(categories) { index, category ->
                    val modifier = if (index == 0) {
                        Modifier.focusRequester(firstFocusRequester)
                    } else {
                        Modifier
                    }
                    CategoryCard(
                        category = category,
                        modifier = modifier,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

/**
 * Search button component for TV navigation.
 */
@Composable
private fun SearchButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) PrimaryRed else DarkRed)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.White
            )
            Text(
                text = "Search Channels",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Content displaying search interface and results.
 */
@Composable
private fun SearchContent(
    searchQuery: String,
    searchResults: List<IptvChannel>,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (IptvChannel) -> Unit,
    onCloseSearch: () -> Unit,
    onBackClick: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current

    // Track current IME action based on keyboard type
    var currentImeAction by remember { mutableStateOf(ImeAction.Search) }

    // Request focus on search field when screen opens
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    // Handle IME action submission
    val handleImeAction: (String) -> Unit = { query ->
        // Trigger search when IME action is pressed
        if (query.isNotBlank()) {
            // Already searched via onValueChange, just clear focus
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // Keep focus on search field when clicking outside
                    searchFocusRequester.requestFocus()
                }
            )
    ) {
        // Back button and search header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val isBackFocused by backInteractionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .focusRequester(backFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBackFocused) PrimaryRed else DarkRed)
                    .border(
                        width = if (isBackFocused) 2.dp else 0.dp,
                        color = if (isBackFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = {
                            focusManager.clearFocus()
                            onBackClick()
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "\u2190 Back",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Search Channels",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Search input field
        SearchInputField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = {
                onSearchQueryChange("")
                // Keep focus after clearing
                searchFocusRequester.requestFocus()
            },
            onImeAction = handleImeAction,
            focusRequester = searchFocusRequester,
            imeAction = currentImeAction
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search results count
        if (searchQuery.isNotBlank()) {
            Text(
                text = "${searchResults.size} channel(s) found",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Results grid or empty state
        if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No channels found for \"$searchQuery\"",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Try a different search term",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        } else if (searchResults.isNotEmpty()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                itemsIndexed(searchResults) { index, channel ->
                    ChannelCard(
                        channel = channel,
                        modifier = Modifier,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
    }
}

/**
 * Search input field with TV navigation support.
 */
@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onImeAction: (String) -> Unit,
    focusRequester: FocusRequester,
    imeAction: ImeAction = ImeAction.Search
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) PrimaryRed else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isFocused) PrimaryRed else TextSecondary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(PrimaryRed),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = imeAction,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onImeAction(query) },
                    onNext = { onImeAction(query) },
                    onDone = { onImeAction(query) }
                ),
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Type channel name to search...",
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = TextSecondary
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
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
    onChannelClick: (IptvChannel) -> Unit,
    onBackClick: () -> Unit
) {
    // Focus requesters for D-pad navigation
    val backFocusRequester = remember { FocusRequester() }
    val firstChannelFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // Request focus on back button when screen opens
    LaunchedEffect(categoryName) {
        backFocusRequester.requestFocus()
    }

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
                    .focusRequester(backFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBackFocused) PrimaryRed else DarkRed)
                    .border(
                        width = if (isBackFocused) 2.dp else 0.dp,
                        color = if (isBackFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = onBackClick
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "\u2190 Back",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = categoryName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${channels.size} channels",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
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
            // Request focus on first channel when grid is ready
            LaunchedEffect(channels) {
                if (channels.isNotEmpty()) {
                    firstChannelFocusRequester.requestFocus()
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                itemsIndexed(channels) { index, channel ->
                    val modifier = if (index == 0) {
                        Modifier.focusRequester(firstChannelFocusRequester)
                    } else {
                        Modifier
                    }
                    ChannelCard(
                        channel = channel,
                        modifier = modifier,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current

    Box(
        modifier = modifier
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