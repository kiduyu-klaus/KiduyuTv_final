package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersBackendUnavailableException
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.PlayerEngine
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamCatalog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamProviderChoice
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamResolver
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Phone-first native direct-stream player.
 *
 * TV keeps using [DirectStreamActivity]'s D-pad-oriented chrome. This activity
 * shares the resolver and Media3 engine but presents source, track and playback
 * controls through a compact Material 3 Compose surface suited to touch input.
 */
class MobileDirectStreamActivity : ComponentActivity() {

    enum class ProviderStatus { PENDING, CHECKING, RETRYING, FOUND, EMPTY, FAILED }

    data class ProviderUiState(
        val name: String = "Provider",
        val status: ProviderStatus = ProviderStatus.PENDING,
        val streamCount: Int = 0
    )

    private lateinit var settingsManager: SettingsManager
    private lateinit var engine: PlayerEngine
    private val resolver = StreamResolver()

    private var endpoint = ""
    private var provider: StreamProviderChoice = StreamCatalog.default
    private var mediaType = DirectStreamActivity.TYPE_MOVIE
    private var tmdbId = 0
    private var season: Int? = null
    private var episode: Int? = null
    private var title = ""
    private var streams: List<StreamItem> = emptyList()
    private var activeStream: StreamItem? = null
    private var videoDecoderRecoveryAttempted = false
    private var wasPlayingBeforePause = false
    private var screenState by mutableStateOf(MobilePlayerState())

    private fun providerStarted(index: Int, name: String) {
        val position = index - 1
        if (position !in screenState.providers.indices) return
        val providers = screenState.providers.toMutableList()
        providers[position] = providers[position].copy(
            name = name,
            status = ProviderStatus.CHECKING
        )
        screenState = screenState.copy(
            providers = providers,
            activeProviderIndex = position,
            providerProgress = "Checking $name…"
        )
    }

    private fun providerRetrying(index: Int, name: String) {
        val position = index - 1
        if (position !in screenState.providers.indices) return
        val providers = screenState.providers.toMutableList()
        providers[position] = providers[position].copy(
            name = name,
            status = ProviderStatus.RETRYING
        )
        screenState = screenState.copy(
            providers = providers,
            activeProviderIndex = position,
            providerProgress = "Retrying $name…"
        )
    }

    private fun providerFinished(index: Int, name: String, streamCount: Int, failed: Boolean) {
        val position = index - 1
        if (position !in screenState.providers.indices) return
        val providers = screenState.providers.toMutableList()
        providers[position] = providers[position].copy(
            name = name,
            status = when {
                failed -> ProviderStatus.FAILED
                streamCount > 0 -> ProviderStatus.FOUND
                else -> ProviderStatus.EMPTY
            },
            streamCount = streamCount
        )
        val next = providers.indices.firstOrNull { candidate ->
            providers[candidate].status == ProviderStatus.CHECKING ||
                providers[candidate].status == ProviderStatus.RETRYING ||
                providers[candidate].status == ProviderStatus.PENDING
        } ?: position
        screenState = screenState.copy(
            providers = providers,
            activeProviderIndex = next,
            providerProgress = "${index}/${providers.size} providers checked"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        enterImmersiveFullscreen()
        settingsManager = SettingsManager(this)
        endpoint = settingsManager.getDirectStreamEndpoint()
        mediaType = intent.getStringExtra(DirectStreamActivity.EXTRA_TYPE)
            ?: if (intent.getBooleanExtra(DirectStreamActivity.EXTRA_IS_TV, false)) {
                DirectStreamActivity.TYPE_SERIES
            } else {
                DirectStreamActivity.TYPE_MOVIE
            }
        tmdbId = intent.getIntExtra(DirectStreamActivity.EXTRA_TMDB_ID, 0)
        season = intent.getIntExtra(DirectStreamActivity.EXTRA_SEASON, 0).takeIf { it > 0 }
        episode = intent.getIntExtra(DirectStreamActivity.EXTRA_EPISODE, 0).takeIf { it > 0 }
        title = intent.getStringExtra(DirectStreamActivity.EXTRA_TITLE).orEmpty()
        provider = StreamCatalog.resolve(
            intent.getStringExtra(DirectStreamActivity.EXTRA_PROVIDER)
                ?: settingsManager.getDefaultDirectStreamProvider()
        )

        engine = PlayerEngine(this).apply {
            onError = { error ->
                if (!tryRecoverFromUnsupportedVideo(error)) {
                    screenState = screenState.copy(
                        isBuffering = false,
                        error = error.ifBlank { getString(R.string.playback_link_unavailable) }
                    )
                }
            }
            onPlaybackStateChanged = { state ->
                screenState = screenState.copy(
                    isBuffering = state == Player.STATE_BUFFERING,
                    isPlaying = state == Player.STATE_READY && player.isPlaying
                )
            }
            onIsPlayingChanged = { isPlaying ->
                screenState = screenState.copy(isPlaying = isPlaying, isBuffering = false)
            }
            onTracksChanged = { tracks ->
                screenState = screenState.copy(hasTracks = tracks.groups.isNotEmpty())
            }
        }

        setContent {
            KiduyuTvTheme {
                MobileDirectStreamPlayer(
                    state = screenState,
                    engine = engine,
                    title = title.ifBlank { getString(R.string.app_name) },
                    isEpisode = mediaType == DirectStreamActivity.TYPE_SERIES,
                    onBack = ::finish,
                    onTogglePlayback = {
                        if (engine.player.isPlaying) engine.pause() else engine.resume()
                    },
                    onSeekBack = { engine.seekBy(-30_000L) },
                    onSeekForward = { engine.seekBy(30_000L) },
                    streams = streams,
                    activeStream = activeStream,
                    tracks = engine.currentTracks(),
                    trackParameters = engine.currentTrackSelectionParameters(),
                    onSelectStream = ::showStreamSelection,
                    onStreamSelected = ::play,
                    onTracks = ::showTrackSelection,
                    onApplyTracks = engine::applyTrackSelectionParameters,
                    onDismissStreamDialog = {
                        screenState = screenState.copy(isStreamDialogVisible = false)
                    },
                    onDismissTrackDialog = {
                        screenState = screenState.copy(isTrackDialogVisible = false)
                    },
                    onRetry = ::loadStreams,
                    onDismissError = { screenState = screenState.copy(error = null) }
                )
            }
        }

        if (tmdbId <= 0) {
            screenState = screenState.copy(error = getString(R.string.playback_link_unavailable))
        } else {
            parseSniffedStream()?.let { stream ->
                streams = listOf(stream)
                play(stream)
            } ?: loadStreams()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    override fun onPause() {
        if (::engine.isInitialized) {
            wasPlayingBeforePause = engine.player.isPlaying
            if (wasPlayingBeforePause) engine.pause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::engine.isInitialized && wasPlayingBeforePause) {
            wasPlayingBeforePause = false
            engine.resume()
        }
    }

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun loadStreams() {
        if (tmdbId <= 0) return
        lifecycleScope.launch {
            screenState = screenState.copy(
                isLoading = true,
                isBackendDown = false,
                error = null,
                providerProgress = "Finding streams…",
                providers = emptyList(),
                activeProviderIndex = 0
            )
            runCatching {
                resolver.load(
                    type = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                    provider = provider,
                    endpoint = endpoint,
                    onProviderStreamsStart = { total ->
                        screenState = screenState.copy(
                            providers = List(total) { ProviderUiState() },
                            activeProviderIndex = 0
                        )
                    },
                    onProviderStarted = { index, _, name -> providerStarted(index, name) },
                    onProviderFinished = { index, _, name, streamCount, failed ->
                        providerFinished(index, name, streamCount, failed)
                    },
                    onProviderProgress = { index, total, name ->
                        screenState = screenState.copy(providerProgress = "$index/$total · $name")
                    },
                    onProviderRetry = { index, total, name ->
                        providerRetrying(index, name)
                        screenState = screenState.copy(providerProgress = "Retrying $index/$total · $name")
                    }
                )
            }.onSuccess { loaded ->
                streams = loaded
                val first = loaded.firstOrNull()
                screenState = screenState.copy(
                    isLoading = false,
                    providerProgress = "",
                    providers = emptyList(),
                    availableStreamCount = loaded.size,
                    error = if (first == null) getString(R.string.no_streams_dialog_message) else null
                )
                first?.let(::play)
            }.onFailure { error ->
                screenState = screenState.copy(
                    isLoading = false,
                    providerProgress = "",
                    providers = emptyList(),
                    isBackendDown = error is ProvidersBackendUnavailableException,
                    error = error.message ?: getString(R.string.streams_failed)
                )
            }
        }
    }

    private fun play(stream: StreamItem) {
        // A manually selected stream starts a new recovery cycle. Automatic
        // recovery below deliberately bypasses this method so it cannot retry
        // the same failing source indefinitely.
        videoDecoderRecoveryAttempted = false
        startPlayback(stream)
    }

    private fun startPlayback(stream: StreamItem) {
        val resumePosition = if (activeStream == null) 0L else engine.player.currentPosition.coerceAtLeast(0L)
        activeStream = stream
        screenState = screenState.copy(
            activeStreamName = stream.name.ifBlank { stream.title },
            error = null,
            isBuffering = true
        )
        engine.play(stream, resumePosition, stream.subtitles)
    }

    /**
     * The bundled FFmpeg extension supplies audio decoding, but this build does
     * not contain an FFmpeg video renderer. When MediaCodec rejects Dolby
     * Vision/HEVC, retry with another or lower-quality source before showing an
     * error. This follows the alternate-stream recovery used by the TV player.
     */
    private fun tryRecoverFromUnsupportedVideo(error: String): Boolean {
        val details = error.lowercase()
        val unsupportedVideo = details.contains("video/dolby-vision") ||
            details.contains("dolby vision") ||
            details.contains("c2.mtk.hevc.decoder") ||
            details.contains("mediacodecvideorenderer") ||
            details.contains("hevc")
        if (!unsupportedVideo || videoDecoderRecoveryAttempted) return false

        val failedUrl = activeStream?.url
        val fallback = streams
            .asSequence()
            .filter { it.url != failedUrl && it.httpStatusCode != 404 && !it.isFailed }
            .sortedWith(
                compareBy<StreamItem> { mobileQualityRank(it.quality) }
                    .thenBy { it.provider }
            )
            .firstOrNull()
            ?: return false

        videoDecoderRecoveryAttempted = true
        val resumePosition = engine.player.currentPosition.coerceAtLeast(0L)
        activeStream = fallback
        screenState = screenState.copy(
            activeStreamName = fallback.name.ifBlank { fallback.title },
            error = null,
            isBuffering = true
        )
        Toast.makeText(
            this,
            "Video format unavailable; trying a compatible stream",
            Toast.LENGTH_LONG
        ).show()
        Log.w(
            "MobileDirectStream",
            "Unsupported video decoder; retrying provider=${fallback.provider} " +
                "quality=${fallback.quality} positionMs=$resumePosition"
        )
        engine.play(fallback, resumePosition, fallback.subtitles)
        return true
    }

    private fun mobileQualityRank(quality: String): Int {
        val normalized = quality.lowercase()
        if (normalized.contains("auto") || normalized.contains("adaptive")) return 720
        return Regex("(\\d{3,4})")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    private fun showStreamSelection() {
        if (streams.isEmpty()) return
        screenState = screenState.copy(isStreamDialogVisible = true)
    }

    private fun showTrackSelection() {
        if (engine.currentTracks().groups.none {
                it.type == C.TRACK_TYPE_VIDEO ||
                    it.type == C.TRACK_TYPE_AUDIO ||
                    it.type == C.TRACK_TYPE_TEXT
            }) {
            Toast.makeText(this, "Tracks are not available yet", Toast.LENGTH_SHORT).show()
            return
        }
        screenState = screenState.copy(isTrackDialogVisible = true)
    }

    private fun parseSniffedStream(): StreamItem? {
        val url = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_URL)
            ?.takeIf { it.isNotBlank() } ?: return null
        val headers = linkedMapOf<String, String>()
        intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_HEADERS)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?.let { json -> json.keys().forEach { key -> headers[key] = json.optString(key) } }
        intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_COOKIE)
            ?.takeIf { it.isNotBlank() }
            ?.let { headers.putIfAbsent("Cookie", it) }
        return StreamItem(
            title = "Captured stream",
            name = "Captured stream",
            url = url,
            quality = "Auto",
            provider = "Web Sniffer",
            type = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_TYPE).orEmpty(),
            mimeType = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_MIME_TYPE).orEmpty(),
            headers = headers
        )
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.release()
        super.onDestroy()
    }

    data class MobilePlayerState(
        val isLoading: Boolean = false,
        val isBuffering: Boolean = false,
        val isPlaying: Boolean = false,
        val hasTracks: Boolean = false,
        val isBackendDown: Boolean = false,
        val availableStreamCount: Int = 0,
        val activeStreamName: String = "",
        val providerProgress: String = "",
        val providers: List<ProviderUiState> = emptyList(),
        val activeProviderIndex: Int = 0,
        val error: String? = null,
        val isStreamDialogVisible: Boolean = false,
        val isTrackDialogVisible: Boolean = false
    )
}

@Composable
private fun ProviderWheel(
    providers: List<MobileDirectStreamActivity.ProviderUiState>,
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val centeredIndex = activeIndex.coerceIn(0, (providers.size - 1).coerceAtLeast(0))

    LaunchedEffect(providers.size, centeredIndex) {
        if (providers.isNotEmpty()) listState.animateScrollToItem(centeredIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp),
        contentPadding = PaddingValues(vertical = 48.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(providers) { index, provider ->
            val distance = kotlin.math.abs(index - centeredIndex)
            val rowAlpha = when (distance) {
                0 -> 1f
                1 -> 0.58f
                2 -> 0.28f
                else -> 0.12f
            }
            val isActive = distance == 0
            val isFailure = provider.status == MobileDirectStreamActivity.ProviderStatus.EMPTY ||
                provider.status == MobileDirectStreamActivity.ProviderStatus.FAILED
            val labelColor = when {
                isFailure -> Color(0xFFE53935)
                isActive -> TextPrimary
                else -> TextSecondary
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(rowAlpha)
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isActive && (provider.status == MobileDirectStreamActivity.ProviderStatus.CHECKING ||
                        provider.status == MobileDirectStreamActivity.ProviderStatus.RETRYING)) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .width(10.dp),
                        color = PrimaryRed,
                        strokeWidth = 1.5.dp
                    )
                }
                Text(
                    text = provider.name,
                    color = labelColor,
                    fontSize = if (isActive) 11.sp else 9.sp,
                    maxLines = 1
                )
                if (isActive && isFailure) {
                    Text(
                        text = if (provider.status == MobileDirectStreamActivity.ProviderStatus.EMPTY) {
                            "  No streams"
                        } else {
                            "  Failed"
                        },
                        color = labelColor,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileDirectStreamPlayer(
    state: MobileDirectStreamActivity.MobilePlayerState,
    engine: PlayerEngine,
    title: String,
    isEpisode: Boolean,
    streams: List<StreamItem>,
    activeStream: StreamItem?,
    tracks: Tracks,
    trackParameters: TrackSelectionParameters,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSelectStream: () -> Unit,
    onStreamSelected: (StreamItem) -> Unit,
    onTracks: () -> Unit,
    onApplyTracks: (TrackSelectionParameters) -> Unit,
    onDismissStreamDialog: () -> Unit,
    onDismissTrackDialog: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, fontSize = 18.sp)
                            if (isEpisode) Text("Direct stream", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onRetry,
                            enabled = !state.isLoading,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, "Reload streams")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            player = engine.player
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { it.player = engine.player }
                )
                if (state.isBuffering && !state.isLoading) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color.Black.copy(alpha = .72f),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(22.dp),
                                color = PrimaryRed,
                                strokeWidth = 2.5.dp
                            )
                            Text("Buffering…", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (state.isLoading && state.providers.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        color = Color.Black.copy(alpha = .78f),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Finding the best stream", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            ProviderWheel(
                                providers = state.providers,
                                activeIndex = state.activeProviderIndex
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryRed,
                    trackColor = CardDark
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.activeStreamName.isBlank()) "Ready to play" else state.activeStreamName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        if (state.providerProgress.isNotBlank()) {
                            Text(
                                state.providerProgress,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            color = PrimaryRed,
                            strokeWidth = 2.dp
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    item {
                    AssistChip(
                        onClick = onSelectStream,
                        enabled = state.availableStreamCount > 0,
                        label = { Text("${state.availableStreamCount} sources") },
                        leadingIcon = { Icon(Icons.Default.MoreHoriz, null) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = CardDark)
                    )
                    }
                    item {
                    AssistChip(
                        onClick = onTracks,
                        enabled = state.hasTracks,
                        label = { Text("Tracks") },
                        leadingIcon = { Icon(Icons.Default.GraphicEq, null) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = CardDark)
                    )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSeekBack,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
                        ) { Icon(Icons.Default.Replay30, "Back 30 seconds") }
                        FilledIconButton(
                            onClick = onTogglePlayback,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PrimaryRed,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play"
                            )
                        }
                        IconButton(
                            onClick = onSeekForward,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
                        ) { Icon(Icons.Default.Forward30, "Forward 30 seconds") }
                    }
                }
                HorizontalDivider(color = CardDark.copy(alpha = .7f))
                Text(
                    text = "Tap the video to open the full player controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    if (state.isStreamDialogVisible) {
        MobileStreamSelectionDialog(
            streams = streams.filterNot { it.httpStatusCode == 404 },
            activeStream = activeStream,
            onSelect = onStreamSelected,
            onDismiss = onDismissStreamDialog
        )
    }
    if (state.isTrackDialogVisible) {
        MobileTrackSelectionDialog(
            tracks = tracks,
            initialParameters = trackParameters,
            onApply = onApplyTracks,
            onDismiss = onDismissTrackDialog
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            title = { Text(if (state.isBackendDown) "Backend site is down" else "Playback unavailable") },
            text = {
                Text(
                    if (state.isBackendDown) "$message\n\nEndpoint: ${context.let { SettingsManager(it).getDirectStreamEndpoint() }}" else message
                )
            },
            confirmButton = {
                Button(onClick = onDismissError) { Text("Close") }
            },
            dismissButton = {
                if (state.isBackendDown) {
                    FilledTonalButton(onClick = {
                        SettingsManager(context).setDirectStreamEnabled(false)
                        (context as? MobileDirectStreamActivity)?.setResult(DirectStreamActivity.RESULT_SHOW_STREAM_LINKS)
                        (context as? MobileDirectStreamActivity)?.finish()
                    }) { Text("Disable Direct Stream") }
                }
            }
        )
    }
}

@Composable
private fun MobileStreamSelectionDialog(
    streams: List<StreamItem>,
    activeStream: StreamItem?,
    onSelect: (StreamItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a stream", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${streams.size} available sources",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            if (streams.isEmpty()) {
                Text("No playable sources are available.", color = TextSecondary)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(streams, key = { it.url }) { stream ->
                        val selected = stream === activeStream
                        val title = stream.name.ifBlank { stream.title.ifBlank { "Stream" } }
                        val details = listOfNotNull(
                            stream.quality.takeIf { it.isNotBlank() && !it.equals("Auto", true) },
                            stream.provider.takeIf { it.isNotBlank() },
                            stream.mimeType.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(stream); onDismiss() },
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) PrimaryRed.copy(alpha = .18f) else CardDark,
                            tonalElevation = if (selected) 4.dp else 0.dp
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(title, color = TextPrimary, maxLines = 1)
                                },
                                supportingContent = {
                                    Column {
                                        if (details.isNotBlank()) {
                                            Text(details, color = TextSecondary, maxLines = 1)
                                        }
                                        Text(
                                            text = when {
                                                stream.isChecking -> "Checking source…"
                                                stream.isValid -> "Ready to play"
                                                stream.isFailed -> "Source unavailable"
                                                else -> "Tap to play"
                                            },
                                            color = if (stream.isFailed) MaterialTheme.colorScheme.error else TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                leadingContent = {
                                    RadioButton(selected = selected, onClick = null)
                                },
                                colors = androidx.compose.material3.ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private data class MobileTrackChoice(
    val group: Tracks.Group,
    val trackIndex: Int
)

private data class MobileTrackOption(
    val type: Int,
    val label: String,
    val detail: String,
    val group: Tracks.Group? = null,
    val trackIndex: Int = -1,
    val isAuto: Boolean = false
)

@Composable
private fun MobileTrackSelectionDialog(
    tracks: Tracks,
    initialParameters: TrackSelectionParameters,
    onApply: (TrackSelectionParameters) -> Unit,
    onDismiss: () -> Unit
) {
    val availableTypes = listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)
    val initialChoices = tracks.groups.mapNotNull { group ->
        if (group.type !in availableTypes) return@mapNotNull null
        val selectedIndex = (0 until group.length).firstOrNull { group.isTrackSelected(it) }
            ?: return@mapNotNull null
        group.type to MobileTrackChoice(group, selectedIndex)
    }.toMap()
    var selectedTab by remember(tracks) {
        mutableStateOf(
            availableTypes.firstOrNull { type -> tracks.groups.any { it.type == type } }
                ?: C.TRACK_TYPE_VIDEO
        )
    }
    var choices by remember(tracks, initialParameters) { mutableStateOf(initialChoices) }
    var disabledTypes by remember(tracks, initialParameters) {
        mutableStateOf(initialParameters.disabledTrackTypes.toSet())
    }
    val tabs = availableTypes.filter { type -> tracks.groups.any { it.type == type } }
    val options = mobileTrackOptions(tracks, selectedTab)

    fun applyOption(option: MobileTrackOption) {
        val nextChoices = if (option.isAuto) {
            choices - option.type
        } else if (option.group != null) {
            choices + (option.type to MobileTrackChoice(option.group, option.trackIndex))
        } else {
            choices
        }
        val nextDisabled = if (option.isAuto && option.type == C.TRACK_TYPE_TEXT) {
            disabledTypes + option.type
        } else if (option.isAuto) {
            disabledTypes - option.type
        } else {
            disabledTypes - option.type
        }
        val builder = initialParameters.buildUpon().clearOverrides()
        availableTypes.forEach { type ->
            builder.setTrackTypeDisabled(type, nextDisabled.contains(type))
        }
        nextChoices.values.forEach { choice ->
            builder.setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex))
        }
        val nextParameters = builder.build()
        choices = nextChoices
        disabledTypes = nextDisabled
        onApply(nextParameters)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback tracks", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
                    tabs.forEach { type ->
                        Tab(
                            selected = selectedTab == type,
                            onClick = { selectedTab = type },
                            text = { Text(mobileTrackTypeLabel(type)) }
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(options, key = { "${it.type}:${it.trackIndex}:${it.label}" }) { option ->
                        val selected = when {
                            option.isAuto -> choices[option.type] == null &&
                                (option.type != C.TRACK_TYPE_TEXT || disabledTypes.contains(option.type))
                            else -> choices[option.type]?.trackIndex == option.trackIndex &&
                                choices[option.type]?.group == option.group
                            }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyOption(option) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) PrimaryRed.copy(alpha = .14f) else Color.Transparent
                        ) {
                            ListItem(
                                headlineContent = { Text(option.label, color = TextPrimary) },
                                supportingContent = option.detail.takeIf { it.isNotBlank() }?.let { detail ->
                                    { Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                                },
                                leadingContent = { RadioButton(selected = selected, onClick = null) },
                                colors = androidx.compose.material3.ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun mobileTrackOptions(tracks: Tracks, type: Int): List<MobileTrackOption> {
    val options = mutableListOf(
        MobileTrackOption(
            type = type,
            label = if (type == C.TRACK_TYPE_TEXT) "Off" else "Auto",
            detail = if (type == C.TRACK_TYPE_TEXT) "Disable subtitles" else "Let the player choose",
            isAuto = true
        )
    )
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            options += MobileTrackOption(
                type = type,
                label = mobileTrackTitle(format, type),
                detail = mobileTrackDetail(format),
                group = group,
                trackIndex = index
            )
        }
    }
    return options
}

private fun mobileTrackTypeLabel(type: Int): String = when (type) {
    C.TRACK_TYPE_VIDEO -> "Video"
    C.TRACK_TYPE_AUDIO -> "Audio"
    C.TRACK_TYPE_TEXT -> "Subtitles"
    else -> "Tracks"
}

private fun mobileTrackTitle(format: Format, type: Int): String {
    val label = format.label?.trim().orEmpty()
    val language = format.language?.takeIf { it.isNotBlank() && !it.equals("und", true) }
    return when {
        label.isNotBlank() -> label
        language != null -> language.uppercase()
        type == C.TRACK_TYPE_VIDEO -> "Video track"
        type == C.TRACK_TYPE_AUDIO -> "Audio track"
        else -> "Subtitle track"
    }
}

private fun mobileTrackDetail(format: Format): String = listOfNotNull(
    format.language?.takeIf { it.isNotBlank() && !it.equals("und", true) },
    format.width.takeIf { it > 0 }?.let { width -> "${width}p" },
    format.sampleMimeType,
    format.channelCount.takeIf { it > 0 }?.let { channels -> "${channels}ch" }
).joinToString(" · ")
