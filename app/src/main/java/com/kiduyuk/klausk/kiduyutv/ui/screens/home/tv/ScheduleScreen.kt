package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleCategory
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleChannel
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleDay
import com.kiduyuk.klausk.kiduyutv.data.model.ScheduleEvent
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.SchedulePlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.viewmodel.ScheduleViewModel

/**
 * Composable function for the Schedule screen with tabs.
 * Shows both Live TV categories and Upcoming Schedule.
 *
 * @param onNavigate Lambda to handle navigation between screens
 * @param viewModel The [ScheduleViewModel] instance
 */
@Composable
fun ScheduleScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Initialize ViewModel with context
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadSchedule()
    }

    // Track selected tab
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        TabItem("Live TV", Icons.Default.Tv),
        TabItem("Schedule", Icons.Default.CalendarToday)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with tabs
            ScheduleTopBar(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                onBackClick = { onNavigate("home") },
                onSearchClick = { viewModel.activateSearch() }
            )

            // Content based on selected tab
            when (selectedTabIndex) {
                0 -> LiveTvTabContent(onNavigate = onNavigate)
                1 -> ScheduleTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onChannelClick = { channel, event ->
                        viewModel.selectChannel(channel, event)
                        val iframeHtml = viewModel.getIframeHtml(channel.id)
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

        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.loadSchedule(forceRefresh = true) }) {
                        Text("Retry", color = PrimaryRed)
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

/**
 * Tab item data class
 */
data class TabItem(
    val title: String,
    val icon: ImageVector
)

/**
 * Top bar with tab navigation
 */
@Composable
private fun ScheduleTopBar(
    tabs: List<TabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDark)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // App title
        Text(
            text = "DLHD Schedule",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        // Tabs
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedTabIndex

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTabSelected(index) },
                color = if (isSelected) PrimaryRed else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            if (index < tabs.size - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Search button
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = TextPrimary
            )
        }
    }
}

/**
 * Live TV tab content - shows IPTV categories (delegates to existing LiveTvScreen)
 */
@Composable
private fun LiveTvTabContent(
    onNavigate: (String) -> Unit
) {
    // This could integrate with the existing LiveTvScreen or show a simplified view
    // For now, we'll show a placeholder that can navigate to Live TV
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Live TV Categories",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Browse your IPTV channels by category",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onNavigate("live_tv") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Live TV")
            }
        }
    }
}

/**
 * Schedule tab content - shows upcoming events
 */
@Composable
private fun ScheduleTabContent(
    uiState: com.kiduyuk.klausk.kiduyutv.viewmodel.ScheduleUiState,
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
                        .padding(16.dp),
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
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
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
    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusable()
            .focusRequester(focusRequester)
            .onFocusChanged { /* Handle focus for TV navigation */ },
        color = if (isExpanded) PrimaryRed.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
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

                // Expand icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            // Expanded channels
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

                    // Channel chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        event.channels.forEach { channel ->
                            ChannelChip(
                                channel = channel,
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
 * Clickable channel chip
 */
@Composable
private fun ChannelChip(
    channel: ScheduleChannel,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .focusable()
            .clickable(onClick = onClick),
        color = PrimaryRed,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
    }
}