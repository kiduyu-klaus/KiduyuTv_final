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
import androidx.compose.ui.awt.SwingPanel
import com.kiduyuk.klausk.kiduyutv.desktop.DesktopServices
import com.kiduyuk.klausk.kiduyutv.desktop.data.DesktopLog
import com.kiduyuk.klausk.kiduyutv.desktop.data.logSafe
import com.kiduyuk.klausk.kiduyutv.desktop.model.*
import com.kiduyuk.klausk.kiduyutv.desktop.navigation.DesktopRoute
import com.kiduyuk.klausk.kiduyutv.desktop.player.MpvPlayer
import com.kiduyuk.klausk.kiduyutv.desktop.player.StreamRanker
import com.kiduyuk.klausk.kiduyutv.desktop.webview.StreamProviderManager
import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Canvas
import java.awt.Dimension
import java.util.concurrent.TimeUnit

@Composable
fun StreamLinksScreen(services: DesktopServices, request: PlayRequest) {
    LaunchedEffect(request) {
        DesktopLog.logger.info(
            "StreamLinksScreen opened title={} type={} tmdbId={} provider={} directStreamEnabled={}",
            request.title,
            request.mediaType,
            request.tmdbId,
            request.provider ?: "<aggregate>",
            services.settings.directStreamEnabled
        )
    }
    if (services.settings.directStreamEnabled) {
        LaunchedEffect(request) {
            DesktopLog.logger.info("Opening direct player title={} tmdbId={}", request.title, request.tmdbId)
            services.navigator.pop()
            services.navigator.push(DesktopRoute.Player(request))
        }
        LoadingView("Opening direct stream player…")
        return
    }
    val providers = StreamProviderManager.providers
    DesktopLog.logger.debug("Rendering WebView provider chooser count={}", providers.size)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choose a server — ${request.title}", { services.navigator.pop() })
        LazyColumn(
            contentPadding = PaddingValues(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(providers, key = { it.name }) { provider ->
                ProviderButton(provider.name, "Open ${provider.name} in WebView") {
                    DesktopLog.logger.info(
                        "Opening WebView provider={} title={} tmdbId={} type={}",
                        provider.name,
                        request.title,
                        request.tmdbId,
                        request.mediaType
                    )
                    services.navigator.push(DesktopRoute.WebPlayer(request, provider.name))
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
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("PLAY", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DirectPlayerScreen(services: DesktopServices, request: PlayRequest) {
    LaunchedEffect(request) {
        DesktopLog.logger.info(
            "DirectPlayerScreen opened title={} type={} tmdbId={} season={} episode={} provider={}",
            request.title,
            request.mediaType,
            request.tmdbId,
            request.season,
            request.episode,
            request.provider ?: "<aggregate>"
        )
    }
    val scope = rememberCoroutineScope()
    val player = remember { MpvPlayer(services.settings, scope) }
    val playerState by player.state.collectAsState()
    val videoCanvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
            preferredSize = Dimension(1280, 720)
        }
    }
    var videoWindowId by remember { mutableStateOf<Long?>(null) }
    var videoSurfaceError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(videoCanvas) {
        val surfaceReady = withTimeoutOrNull(5_000L) {
            while (
                isActive &&
                (!videoCanvas.isDisplayable || !videoCanvas.isShowing ||
                    videoCanvas.width <= 0 || videoCanvas.height <= 0)
            ) {
                delay(50L)
            }
            true
        }
        videoWindowId = if (surfaceReady == true) {
            runCatching { windowsMpvWindowId(videoCanvas) }
                .onFailure { DesktopLog.logger.error("Failed to obtain embedded mpv window ID", it) }
                .getOrNull()
                ?.takeIf { it != 0L }
        } else {
            null
        }
        DesktopLog.logger.info(
            "Embedded mpv surface ready={} displayable={} showing={} size={}x{} windowId={}",
            surfaceReady == true,
            videoCanvas.isDisplayable,
            videoCanvas.isShowing,
            videoCanvas.width,
            videoCanvas.height,
            videoWindowId
        )
        if (videoWindowId == null) {
            videoSurfaceError = "The embedded video surface could not be initialized."
            DesktopLog.logger.error("Embedded mpv surface initialization failed")
        }
    }
    var streams by remember(request) { mutableStateOf<List<StreamItem>>(emptyList()) }
    var activeStream by remember(request) { mutableStateOf<StreamItem?>(null) }
    var loading by remember(request) { mutableStateOf(true) }
    var fetchError by remember(request) { mutableStateOf<String?>(null) }
    var fetchStatus by remember(request) { mutableStateOf("Fetching enabled providers…") }
    var attempt by remember(request) { mutableIntStateOf(0) }
    var showNoStreams by remember(request) { mutableStateOf(false) }
    var showStreams by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var subtitleUrl by remember { mutableStateOf("") }
    val resume = remember(request) { services.library.progress(request) }

    fun start(stream: StreamItem, positionMs: Long) {
        DesktopLog.logger.info(
            "Starting direct stream name={} provider={} type={} quality={} url={} resumeMs={} windowId={}",
            stream.displayName,
            stream.provider,
            stream.type,
            stream.quality,
            stream.url.logSafe(500),
            positionMs,
            videoWindowId
        )
        activeStream = stream
        player.play(stream, positionMs, videoWindowId)
    }

    LaunchedEffect(request, attempt, videoWindowId) {
        if (videoWindowId == null) return@LaunchedEffect
        loading = true
        fetchError = null
        fetchStatus = "Fetching enabled providers…"
        showNoStreams = false
        var lastFailure: Throwable? = null
        // ProvidersClient completes only after provider discovery, every enabled-provider
        // request, and each provider retry has finished.
        DesktopLog.logger.info(
            "Starting provider stream fetch title={} tmdbId={} type={} providerMode={}",
            request.title,
            request.tmdbId,
            request.mediaType,
            request.provider ?: "aggregate-enabled-providers"
        )
        val result = runCatching {
            services.providers.streams(
                request = request,
                provider = request.provider,
                onProviderProgress = { index, total, providerName ->
                    withContext(Dispatchers.Main.immediate) {
                        fetchStatus = "Provider $index/$total enabled providers: $providerName\nfetching streams"
                    }
                },
                onProviderRetry = { index, total, providerName ->
                    withContext(Dispatchers.Main.immediate) {
                        fetchStatus = "Provider $index/$total enabled providers: $providerName\nretrying streams"
                    }
                }
            )
        }
        val found = result.getOrElse {
            lastFailure = it
            DesktopLog.logger.error("Provider stream fetch failed", it)
            emptyList()
        }
        DesktopLog.logger.info("Provider stream fetch completed totalStreams={}", found.size)
        streams = StreamRanker.sorted(found)
        if (streams.isEmpty()) {
            fetchError = lastFailure?.message ?: "No streams were found"
            DesktopLog.logger.warn("No playable streams found error={}", fetchError)
            showNoStreams = true
        } else {
            val automatic = StreamRanker.automatic(streams)
            DesktopLog.logger.info(
                "Playable streams sorted count={} automaticSelection={}",
                streams.size,
                automatic?.displayName ?: "<none>"
            )
            automatic?.let { start(it, resume?.positionMs ?: 0L) }
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
    LaunchedEffect(playerState.running, playerState.playing, playerState.error) {
        DesktopLog.logger.info(
            "Direct player state running={} playing={} positionMs={} durationMs={} error={}",
            playerState.running,
            playerState.playing,
            playerState.positionMs,
            playerState.durationMs,
            playerState.error ?: "<none>"
        )
    }
    LaunchedEffect(playerState.running, playerState.durationMs, playerState.positionMs) {
        if (!playerState.running && playerState.durationMs > 0L &&
            playerState.positionMs >= playerState.durationMs * 0.92 &&
            request.mediaType == MediaType.SERIES
        ) {
            val next = request.copy(episode = (request.episode ?: 1) + 1, provider = request.provider)
            DesktopLog.logger.info("Auto-advancing to next episode season={} episode={}", next.season, next.episode)
            services.navigator.push(DesktopRoute.Player(next))
        }
    }
    DisposableEffect(request) {
        onDispose {
            DesktopLog.logger.info(
                "DirectPlayerScreen disposed title={} tmdbId={} positionMs={} durationMs={}",
                request.title,
                request.tmdbId,
                playerState.positionMs,
                playerState.durationMs
            )
            if (playerState.positionMs > 0L) {
                services.library.saveProgress(request.toProgress(playerState.positionMs, playerState.durationMs))
            }
            player.close()
        }
    }

    val playerLoading = videoSurfaceError == null &&
        (loading || (playerState.running && !playerState.playing && playerState.error == null))
    val playerStatus = videoSurfaceError ?: playerState.error ?: fetchError
    val dialogVisible = showNoStreams || showStreams || showTracks || showSubtitle
    PlayerLayout(
        title = request.title + if (request.mediaType == MediaType.SERIES) {
            "  S${request.season ?: 1} E${request.episode ?: 1}"
        } else "",
        backdrop = request.backdropPath,
        videoCanvas = videoCanvas,
        state = playerState,
        stream = activeStream,
        loading = playerLoading,
        loadingMessage = if (activeStream == null) fetchStatus else "Buffering…",
        status = playerStatus,
        videoObscured =
            dialogVisible ||
                (playerLoading && !playerState.playing) ||
                (!playerStatus.isNullOrBlank() && !playerState.playing),
        onBack = {
            DesktopLog.logger.info("Direct player back pressed title={} tmdbId={}", request.title, request.tmdbId)
            if (playerState.positionMs > 0L) {
                services.library.saveProgress(request.toProgress(playerState.positionMs, playerState.durationMs))
            }
            player.close()
            services.navigator.pop()
        },
        onPause = {
            DesktopLog.logger.info("Direct player pause toggle")
            player.togglePause()
        },
        onRewind = {
            DesktopLog.logger.info("Direct player rewind seconds=30")
            player.seekBy(-30)
        },
        onForward = {
            DesktopLog.logger.info("Direct player forward seconds=30")
            player.seekBy(30)
        },
        onSeek = {
            DesktopLog.logger.info("Direct player seek positionMs={}", it)
            player.seekTo(it)
        },
        onStreams = {
            DesktopLog.logger.info("Opening direct stream selection count={}", streams.size)
            showStreams = true
        },
        onTracks = {
            DesktopLog.logger.info("Opening direct track dialog")
            showTracks = true
        },
        onSubtitle = {
            DesktopLog.logger.info("Opening direct subtitle dialog")
            showSubtitle = true
        },
        onFullscreen = {
            DesktopLog.logger.info("Direct player fullscreen toggle")
            player.toggleFullscreen()
        },
        onPrevious = if (request.mediaType == MediaType.SERIES && (request.episode ?: 1) > 1) ({
            DesktopLog.logger.info("Navigating to previous episode episode={}", (request.episode ?: 1) - 1)
            services.navigator.push(DesktopRoute.Player(request.copy(episode = (request.episode ?: 1) - 1)))
        }) else null,
        onNext = if (request.mediaType == MediaType.SERIES) ({
            DesktopLog.logger.info("Navigating to next episode episode={}", (request.episode ?: 1) + 1)
            services.navigator.push(DesktopRoute.Player(request.copy(episode = (request.episode ?: 1) + 1)))
        }) else null
    )

    if (showNoStreams && !playerState.playing) {
        DesktopLog.logger.info("Showing no-streams dialog error={}", fetchError ?: "<none>")
        AlertDialog(
            onDismissRequest = {},
            title = { Text("No streams found") },
            text = { Text(fetchError ?: "No provider returned a stream.") },
            confirmButton = {
                TextButton({
                    DesktopLog.logger.info("Retrying direct provider stream fetch")
                    attempt++
                }) { Text("Retry") }
            },
            dismissButton = {
                TextButton({
                    DesktopLog.logger.info("Exiting after no direct streams")
                    services.navigator.pop()
                }) { Text("Exit") }
            }
        )
    }
    if (showStreams) {
        StreamSelectionDialog(streams, activeStream, {
            val resumeAt = playerState.positionMs
            DesktopLog.logger.info("Manual stream selected name={} provider={}", it.displayName, it.provider)
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
                    Text("Cycle through the tracks exposed by the current stream.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LaunchedEffect(route) {
        DesktopLog.logger.info(
            "LivePlayerScreen opened name={} url={}",
            route.name,
            route.url.logSafe(500)
        )
    }
    val scope = rememberCoroutineScope()
    val player = remember { MpvPlayer(services.settings, scope) }
    val state by player.state.collectAsState()
    val videoCanvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
            preferredSize = Dimension(1280, 720)
        }
    }
    var videoWindowId by remember { mutableStateOf<Long?>(null) }
    var videoSurfaceError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(videoCanvas) {
        val surfaceReady = withTimeoutOrNull(5_000L) {
            while (
                isActive &&
                (!videoCanvas.isDisplayable || !videoCanvas.isShowing ||
                    videoCanvas.width <= 0 || videoCanvas.height <= 0)
            ) {
                delay(50L)
            }
            true
        }
        videoWindowId = if (surfaceReady == true) {
            runCatching { windowsMpvWindowId(videoCanvas) }
                .onFailure { DesktopLog.logger.error("Failed to obtain live embedded mpv window ID", it) }
                .getOrNull()
                ?.takeIf { it != 0L }
        } else {
            null
        }
        DesktopLog.logger.info(
            "Live embedded mpv surface ready={} displayable={} showing={} size={}x{} windowId={}",
            surfaceReady == true,
            videoCanvas.isDisplayable,
            videoCanvas.isShowing,
            videoCanvas.width,
            videoCanvas.height,
            videoWindowId
        )
        if (videoWindowId == null) {
            videoSurfaceError = "The embedded video surface could not be initialized."
            DesktopLog.logger.error("Live embedded mpv surface initialization failed")
        }
    }
    val stream = remember(route) {
        StreamItem(name = route.name, title = route.name, url = route.url, provider = "Live TV", type = "hls", headers = route.headers)
    }
    LaunchedEffect(route, videoWindowId) {
        videoWindowId?.let {
            DesktopLog.logger.info("Starting live stream name={} url={} windowId={}", route.name, route.url.logSafe(500), it)
            player.play(stream, windowId = it)
        }
    }
    LaunchedEffect(state.running, state.playing, state.error) {
        DesktopLog.logger.info(
            "Live player state running={} playing={} positionMs={} durationMs={} error={}",
            state.running,
            state.playing,
            state.positionMs,
            state.durationMs,
            state.error ?: "<none>"
        )
    }
    DisposableEffect(route) {
        onDispose {
            DesktopLog.logger.info("LivePlayerScreen disposed name={} positionMs={}", route.name, state.positionMs)
            player.close()
        }
    }
    PlayerLayout(
        title = route.name,
        backdrop = null,
        videoCanvas = videoCanvas,
        state = state,
        stream = stream,
        loading = videoSurfaceError == null && (videoWindowId == null || state.running && !state.playing),
        loadingMessage = "Buffering…",
        status = videoSurfaceError ?: state.error,
        videoObscured =
            videoWindowId == null || (state.running && !state.playing) ||
                videoSurfaceError != null || state.error != null,
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
    videoCanvas: Canvas,
    state: com.kiduyuk.klausk.kiduyutv.desktop.player.MpvState,
    stream: StreamItem?,
    loading: Boolean,
    loadingMessage: String,
    status: String?,
    videoObscured: Boolean,
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
                stream?.let { Text("${it.displayName} • ${it.quality}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            })
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                SwingPanel(
                    factory = { videoCanvas },
                    background = Color.Black,
                    modifier = if (videoObscured) {
                        // Keep the native HWND alive for mpv, but move its heavyweight
                        // surface out of the way so Compose status content and dialogs
                        // are painted above it.
                        Modifier.align(Alignment.BottomEnd).size(1.dp)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    // AWT Canvas is heavyweight on Windows and can otherwise paint over
                    // Compose dialogs/status content. Resizing instead of hiding preserves
                    // the HWND that the running mpv process is attached to.
                    update = { canvas ->
                        canvas.background = java.awt.Color.BLACK
                        canvas.isVisible = true
                    }
                )
                if (loading && !state.playing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(loadingMessage, color = Color.White)
                    }
                } else if (!status.isNullOrBlank() && !state.playing) {
                    Text(
                        status,
                        color = Color.White,
                        modifier = Modifier.background(Color(0xB3000000), RoundedCornerShape(10.dp)).padding(16.dp)
                    )
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
                    Text(formatTime(state.positionMs), color = Color.White)
                    Text("Buffered ${formatTime(state.bufferedMs)}", color = Color.White)
                    Text(formatTime(state.durationMs), color = Color.White)
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
                                Text(stream.provider, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stream.quality, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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

/** mpv expects a Windows HWND represented as an unsigned 32-bit value. */
private fun windowsMpvWindowId(canvas: Canvas): Long =
    Native.getComponentID(canvas) and 0xFFFF_FFFFL
