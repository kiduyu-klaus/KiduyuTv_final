package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsEvent
import com.kiduyuk.klausk.kiduyutv.data.model.LiveSportsSource
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveSportsUiState
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveSportsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Shared PPV/TIMST browser for the phone and TV Live TV surfaces. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LiveSportsTabContent(
    onEventClick: (LiveSportsEvent) -> Unit,
    onEventLongClick: (LiveSportsEvent) -> Unit = {},
    columns: Int = 2,
    useGrid: Boolean = true,
    viewModel: LiveSportsViewModel = viewModel()
) {
    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    val source = LiveSportsSource.entries[selectedSourceIndex]
    val ppvUiState by viewModel.ppvUiState.collectAsState()
    val timstUiState by viewModel.timstUiState.collectAsState()
    val uiState = if (source == LiveSportsSource.PPV) ppvUiState else timstUiState

    LaunchedEffect(source) { viewModel.load(source) }

    Column(Modifier.fillMaxSize().background(BackgroundDark)) {
        ScrollableTabRow(
            selectedTabIndex = selectedSourceIndex,
            containerColor = CardDark,
            contentColor = TextPrimary,
            edgePadding = 12.dp,
            divider = {}
        ) {
            LiveSportsSource.entries.forEachIndexed { index, item ->
                Tab(
                    selected = selectedSourceIndex == index,
                    onClick = { selectedSourceIndex = index },
                    text = { Text(item.tabLabel) }
                )
            }
        }
        SportsEventsBody(
            source = source,
            uiState = uiState,
            onRetry = { viewModel.load(source, forceRefresh = true) },
            onEventClick = onEventClick,
            onEventLongClick = onEventLongClick,
            columns = columns,
            useGrid = useGrid
        )
    }
}

@Composable
private fun SportsEventsBody(
    source: LiveSportsSource,
    uiState: LiveSportsUiState,
    onRetry: () -> Unit,
    onEventClick: (LiveSportsEvent) -> Unit,
    onEventLongClick: (LiveSportsEvent) -> Unit,
    columns: Int,
    useGrid: Boolean
) {
    when {
        uiState.isLoading && uiState.events.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PrimaryRed)
        }
        uiState.error != null && uiState.events.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(uiState.error, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
        uiState.events.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.SportsSoccer, null, tint = PrimaryRed, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No ${source.tabLabel} events are available right now.", color = TextSecondary)
        }
        else -> {
            val contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            if (useGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns.coerceAtLeast(1)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.events, key = { it.id }) { event ->
                        LiveSportsEventCard(
                            event,
                            onClick = { onEventClick(event) },
                            onLongClick = { onEventLongClick(event) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.events, key = { it.id }) { event ->
                        LiveSportsEventCard(
                            event,
                            onClick = { onEventClick(event) },
                            onLongClick = { onEventLongClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LiveSportsEventCard(
    event: LiveSportsEvent,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val canPlay = event.playbackUrls.isNotEmpty()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    var isConfirmHeld by remember { mutableStateOf(false) }
    var heldLongEnough by remember { mutableStateOf(false) }
    var suppressNextClick by remember { mutableStateOf(false) }
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    LaunchedEffect(isConfirmHeld, longPressTimeoutMs) {
        if (isConfirmHeld) {
            delay(longPressTimeoutMs)
            heldLongEnough = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .focusable(enabled = canPlay, interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                val isConfirmKey = keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter

                when {
                    isConfirmKey && keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.nativeKeyEvent.repeatCount == 0 -> {
                        isConfirmHeld = true
                        heldLongEnough = false
                        suppressNextClick = false
                        false
                    }

                    isConfirmKey && keyEvent.type == KeyEventType.KeyUp && heldLongEnough -> {
                        isConfirmHeld = false
                        heldLongEnough = false
                        suppressNextClick = true
                        currentOnLongClick()
                        false
                    }

                    isConfirmKey && keyEvent.type == KeyEventType.KeyUp -> {
                        isConfirmHeld = false
                        heldLongEnough = false
                        false
                    }

                    else -> false
                }
            }
            .combinedClickable(
                enabled = true,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (suppressNextClick) {
                        suppressNextClick = false
                    } else if (canPlay) {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) PrimaryRed.copy(alpha = 0.6f) else CardDark
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            if (event.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = event.posterUrl,
                    contentDescription = "Poster for ${event.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else Box(Modifier.fillMaxSize().background(Color(0xFF35111A)))
            Surface(
                modifier = Modifier.padding(12.dp),
                color = if (event.isLive) PrimaryRed else Color(0xFF52525B),
                shape = RoundedCornerShape(5.dp)
            ) {
                Text(
                    if (event.isLive) "LIVE" else "UPCOMING",
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                (event.league.ifBlank { event.category }).uppercase(Locale.getDefault()),
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                event.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    tint = if (canPlay) PrimaryRed else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        !canPlay -> "No stream available"
                        event.isLive -> "Live now • ${event.playbackUrls.size} server${if (event.playbackUrls.size == 1) "" else "s"}"
                        else -> event.startsAtMillis?.let(::formatStartTime) ?: "Upcoming"
                    },
                    color = if (canPlay) TextPrimary else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun formatStartTime(timeMillis: Long): String =
    SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(Date(timeMillis))
