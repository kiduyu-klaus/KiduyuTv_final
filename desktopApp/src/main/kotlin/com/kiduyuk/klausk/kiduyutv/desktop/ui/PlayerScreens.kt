package com.kiduyuk.klausk.kiduyutv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.desktop.DesktopServices
import com.kiduyuk.klausk.kiduyutv.desktop.model.*
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopRoute
import com.kiduyuk.klausk.kiduyutv.desktop.player.MpvPlayer
import com.kiduyuk.klausk.kiduyutv.desktop.player.StreamRanker
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun StreamLinksScreen(services: DesktopServices, request: PlayRequest) {
    var providers by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        loading = true
        runCatching { services.providers.enabledProviders() }
            .onSuccess { providers = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choose a server — ${request.title}", { services.navigator.pop() })
        when {
            loading -> LoadingView("Loading enabled providers…")
            error != null -> ErrorView(error.orEmpty()) { reload++ }
            else -> LazyColumn(
                contentPadding = PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ProviderButton("All Providers", "Automatic aggregate stream discovery") {
                        services.navigator.push(DesktopRoute.Player(request.copy(provider = null)))
                    }
                }
                items(providers, key = { it }) { provider ->
                    ProviderButton(provider.replaceFirstChar(Char::uppercase), "Play using $provider") {
                        services.navigator.push(DesktopRoute.Player(request.copy(provider = provider)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderButton(name: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(subtitle, color = Color.Gray)
            }
            Text("PLAY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DirectPlayerScreen(services: DesktopServices, request: PlayRequest) {
    val scope = rememberCoroutineScope()
    val player = remember { MpvPlayer(services.settings, scope) }
    val playerState by player.state.collectAsState()
    var streams by remember(request) { mutableStateOf<List<StreamItem>>(emptyList()) }
    var activeStream by remember(request) { mutableStateOf<StreamItem?>(null) }
    var loading by remember(request) { mutableStateOf(true) }
    var fetchError by remember(request) { mutableStateOf<String?>(null) }
    var attempt by remember(request) { mutableIntStateOf(0) }
    var showNoStreams by remember(request) { mutableStateOf(false) }
    var showStreams by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var subtitleUrl by remember { mutableStateOf("") }
    val resume = remember(request) { services.library.progress(request) }

    fun start(stream: StreamItem, positionMs: Long) {
        activeStream = stream
        player.play(stream, positionMs)
    }

    LaunchedEffect(request, attempt) {
        loading = true
        fetchError = null
        showNoStreams = false
        var lastFailure: Throwable? = null
        var found = emptyList<StreamItem>()
        repeat(3) { retry ->
            val result = runCatching { services.providers.streams(request, request.provider) }
            result.onSuccess { found = it }.onFailure { lastFailure = it }
            if (found.isNotEmpty()) return@repeat
            if (retry < 2) delay((retry + 1) * 1_000L)
        }
        streams = StreamRanker.sorted(found)
        if (streams.isEmpty()) {
            fetchError = lastFailure?.message ?: "No streams were found"
            showNoStreams = true
        } else {
            StreamRanker.automatic(streams)?.let { start(it, resume?.positionMs ?: 0L) }
        }
        loading = false
    }

    LaunchedEffect(playerState.positionMs, playerState.durationMs, activeStream) {
        // This effect is intentionally lightweight; the interval writer below performs storage I/O.
    }
    LaunchedEffect(request, activeStream) {
        while (true) {
            delay(15_000L)
            if (playerState.positionMs > 0L) {
                services.library.saveProgress(request.toProgress(playerState.positionMs, playerState.durationMs))
            }
        }
    }
    LaunchedEffect(playerState.running, playerState.durationMs, playerState.positionMs) {
        if (!playerState.running && playerState.durationMs > 0L &&
            playerState.positionMs >= playerState.durationMs * 0.92 &&
            request.mediaType == MediaType.SERIES
        ) {
            val next = request.copy(episode = (request.episode ?: 1) + 1, provider = request.provider)
            services.navigator.push(DesktopRoute.Player(next))
        }
    }
    DisposableEffect(request) {
        onDispose {
            if (playerState.positionMs > 0L) {
                services.library.saveProgress(request.toProgress(playerState.positionMs, playerState.durationMs))
            }
            player.close()
        }
    }

    PlayerLayout(
        title = request.title + if (request.mediaType == MediaType.SERIES) {
            "  S${request.season ?: 1} E${request.episode ?: 1}"
        } else "",
        backdrop = request.backdropPath,
        state = playerState,
        stream = activeStream,
        loading = loading,
        status = fetchError,
        onBack = {
            if (playerState.positionMs > 0L) {
                services.library.saveProgress(request.toProgress(playerState.positionMs, playerState.durationMs))
            }
            player.close()
            services.navigator.pop()
        },
        onPause = player::togglePause,
        onRewind = { player.seekBy(-30) },
        onForward = { player.seekBy(30) },
        onSeek = player::seekTo,
        onStreams = { showStreams = true },
        onTracks = { showTracks = true },
        onSubtitle = { showSubtitle = true },
        onFullscreen = player::toggleFullscreen,
        onPrevious = if (request.mediaType == MediaType.SERIES && (request.episode ?: 1) > 1) ({
            services.navigator.push(DesktopRoute.Player(request.copy(episode = (request.episode ?: 1) - 1)))
        }) else null,
        onNext = if (request.mediaType == MediaType.SERIES) ({
            services.navigator.push(DesktopRoute.Player(request.copy(episode = (request.episode ?: 1) + 1)))
        }) else null
    )

    if (showNoStreams && !playerState.playing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("No streams found") },
            text = { Text(fetchError ?: "No provider returned a stream.") },
            confirmButton = { TextButton({ attempt++ }) { Text("Retry") } },
            dismissButton = { TextButton({ services.navigator.pop() }) { Text("Exit") } }
        )
    }
    if (showStreams) {
        StreamSelectionDialog(streams, activeStream, {
            val resumeAt = playerState.positionMs
            start(it, resumeAt)
            showStreams = false
        }, { showStreams = false })
    }
    if (showTracks) {
        AlertDialog(
            onDismissRequest = { showTracks = false },
            title = { Text("Tracks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cycle through the tracks exposed by the current stream.", color = Color.LightGray)
                    TvActionButton("Next video track", player::cycleVideo, Modifier.fillMaxWidth())
                    TvActionButton("Next audio track", player::cycleAudio, Modifier.fillMaxWidth())
                    TvActionButton("Next subtitle track", player::cycleSubtitle, Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton({ showTracks = false }) { Text("Done") } }
        )
    }
    if (showSubtitle) {
        AlertDialog(
            onDismissRequest = { showSubtitle = false },
            title = { Text("Load subtitle") },
            text = {
                OutlinedTextField(
                    subtitleUrl, { subtitleUrl = it },
                    label = { Text("SRT or VTT URL/file") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton({
                    subtitleUrl.takeIf(String::isNotBlank)?.let(player::addSubtitle)
                    showSubtitle = false
                }) { Text("Load") }
            },
            dismissButton = { TextButton({ showSubtitle = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun LivePlayerScreen(services: DesktopServices, route: DesktopRoute.LivePlayer) {
    val scope = rememberCoroutineScope()
    val player = remember { MpvPlayer(services.settings, scope) }
    val state by player.state.collectAsState()
    val stream = remember(route) {
        StreamItem(name = route.name, title = route.name, url = route.url, provider = "Live TV", type = "hls", headers = route.headers)
    }
    LaunchedEffect(route) { player.play(stream) }
    DisposableEffect(route) { onDispose { player.close() } }
    PlayerLayout(
        title = route.name,
        backdrop = null,
        state = state,
        stream = stream,
        loading = state.running && !state.playing,
        status = state.error,
        onBack = { player.close(); services.navigator.pop() },
        onPause = player::togglePause,
        onRewind = { player.seekBy(-30) },
        onForward = { player.seekBy(30) },
        onSeek = player::seekTo,
        onStreams = {},
        onTracks = player::cycleAudio,
        onSubtitle = player::cycleSubtitle,
        onFullscreen = player::toggleFullscreen,
        onPrevious = null,
        onNext = null
    )
}

@Composable
private fun PlayerLayout(
    title: String,
    backdrop: String?,
    state: com.kiduyuk.klausk.kiduyutv.desktop.player.MpvState,
    stream: StreamItem?,
    loading: Boolean,
    status: String?,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onStreams: () -> Unit,
    onTracks: () -> Unit,
    onSubtitle: () -> Unit,
    onFullscreen: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RemoteImage(tmdbImage(backdrop, "original"), title, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), Color(0xEE000000)))))
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title, onBack, actions = {
                stream?.let { Text("${it.displayName} • ${it.quality}", color = Color.LightGray) }
            })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (loading && !state.playing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Buffering…")
                    }
                } else if (!status.isNullOrBlank() && !state.playing) {
                    Text(status, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Playback is running in the KiduyuTV mpv window", color = Color.LightGray)
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xD9000000)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = state.positionMs.coerceAtMost(state.durationMs.coerceAtLeast(1L)).toFloat(),
                    onValueChangeFinished = {},
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.positionMs))
                    Text("Buffered ${formatTime(state.bufferedMs)}")
                    Text(formatTime(state.durationMs))
                }
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onPrevious?.let { TvActionButton("Previous", it); Spacer(Modifier.width(8.dp)) }
                    TvActionButton("-30", onRewind)
                    Spacer(Modifier.width(8.dp))
                    TvActionButton(if (state.playing) "Pause" else "Play", onPause)
                    Spacer(Modifier.width(8.dp))
                    TvActionButton("+30", onForward)
                    Spacer(Modifier.width(8.dp))
                    TvActionButton("Fit / Full", onFullscreen)
                    Spacer(Modifier.width(8.dp))
                    TvActionButton("Subtitles", onSubtitle)
                    Spacer(Modifier.width(8.dp))
                    TvActionButton("Tracks", onTracks)
                    Spacer(Modifier.width(8.dp))
                    TvActionButton("Streams", onStreams)
                    onNext?.let { Spacer(Modifier.width(8.dp)); TvActionButton("Next", it) }
                }
            }
        }
    }
}

@Composable
private fun StreamSelectionDialog(
    streams: List<StreamItem>,
    active: StreamItem?,
    onSelect: (StreamItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose stream") },
        text = {
            LazyColumn(Modifier.widthIn(min = 520.dp, max = 760.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(streams, key = { "${it.provider}-${it.name}-${it.quality}-${it.url.hashCode()}" }) { stream ->
                    Card(
                        onClick = { onSelect(stream) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (stream == active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stream.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stream.provider, color = Color.Gray)
                            }
                            Text(stream.quality, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } }
    )
}

private fun PlayRequest.toProgress(positionMs: Long, durationMs: Long) = WatchProgress(
    tmdbId = tmdbId,
    mediaType = mediaType,
    title = title,
    posterPath = posterPath,
    backdropPath = backdropPath,
    season = season,
    episode = episode,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAt = System.currentTimeMillis()
)

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
