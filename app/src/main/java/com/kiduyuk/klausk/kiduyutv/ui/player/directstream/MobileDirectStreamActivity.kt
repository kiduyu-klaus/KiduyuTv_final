package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.ProvidersBackendUnavailableException
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.PlayerEngine
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamCatalog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamProviderChoice
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamResolver
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamSelectionDialog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.TrackSelectionDialog
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
    private var selectionDialog: StreamSelectionDialog? = null
    private var tracksDialog: TrackSelectionDialog? = null

    private var screenState by mutableStateOf(MobilePlayerState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                screenState = screenState.copy(
                    isBuffering = false,
                    error = error.ifBlank { getString(R.string.playback_link_unavailable) }
                )
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
                tracksDialog?.updateCurrentTracks(tracks)
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
                    onSelectStream = ::showStreamSelection,
                    onTracks = ::showTrackSelection,
                    onRetry = ::loadStreams
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

    private fun loadStreams() {
        if (tmdbId <= 0) return
        lifecycleScope.launch {
            screenState = screenState.copy(
                isLoading = true,
                isBackendDown = false,
                error = null,
                providerProgress = "Finding streams…"
            )
            runCatching {
                resolver.load(
                    type = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                    provider = provider,
                    endpoint = endpoint,
                    onProviderProgress = { index, total, name ->
                        screenState = screenState.copy(providerProgress = "$index/$total · $name")
                    },
                    onProviderRetry = { index, total, name ->
                        screenState = screenState.copy(providerProgress = "Retrying $index/$total · $name")
                    }
                )
            }.onSuccess { loaded ->
                streams = loaded
                val first = loaded.firstOrNull()
                screenState = screenState.copy(
                    isLoading = false,
                    providerProgress = "",
                    availableStreamCount = loaded.size,
                    error = if (first == null) getString(R.string.no_streams_dialog_message) else null
                )
                first?.let(::play)
            }.onFailure { error ->
                screenState = screenState.copy(
                    isLoading = false,
                    providerProgress = "",
                    isBackendDown = error is ProvidersBackendUnavailableException,
                    error = error.message ?: getString(R.string.streams_failed)
                )
            }
        }
    }

    private fun play(stream: StreamItem) {
        val resumePosition = if (activeStream == null) 0L else engine.player.currentPosition.coerceAtLeast(0L)
        activeStream = stream
        screenState = screenState.copy(
            activeStreamName = stream.name.ifBlank { stream.title },
            error = null,
            isBuffering = true
        )
        engine.play(stream, resumePosition, stream.subtitles)
        selectionDialog?.updateStreams(streams, stream.url, stream.name)
    }

    private fun showStreamSelection() {
        if (streams.isEmpty()) return
        selectionDialog?.dismiss()
        selectionDialog = StreamSelectionDialog(
            context = this,
            streams = streams,
            activeUrl = activeStream?.url,
            activeName = activeStream?.name,
            onStreamSelected = ::play
        ).also { it.show() }
    }

    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private fun showTrackSelection() {
        if (engine.currentTracks().groups.isEmpty()) {
            Toast.makeText(this, "Tracks are not available yet", Toast.LENGTH_SHORT).show()
            return
        }
        tracksDialog?.dismiss()
        tracksDialog = TrackSelectionDialog(
            context = this,
            tracks = engine.currentTracks(),
            initialParameters = engine.currentTrackSelectionParameters(),
            onApply = engine::applyTrackSelectionParameters
        ).also { it.show() }
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
        selectionDialog?.dismiss()
        tracksDialog?.dismiss()
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
        val error: String? = null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileDirectStreamPlayer(
    state: MobileDirectStreamActivity.MobilePlayerState,
    engine: PlayerEngine,
    title: String,
    isEpisode: Boolean,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSelectStream: () -> Unit,
    onTracks: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
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
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, "Reload streams")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
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
                if (state.isLoading || state.isBuffering) {
                    CircularProgressIndicator(color = PrimaryRed)
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.activeStreamName.isNotBlank()) {
                    Text(state.activeStreamName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                if (state.providerProgress.isNotBlank()) {
                    Text(state.providerProgress, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onSelectStream,
                        label = { Text("${state.availableStreamCount} sources") },
                        leadingIcon = { Icon(Icons.Default.MoreHoriz, null) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = CardDark)
                    )
                    AssistChip(
                        onClick = onTracks,
                        enabled = state.hasTracks,
                        label = { Text("Tracks") },
                        leadingIcon = { Icon(Icons.Default.GraphicEq, null) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = CardDark)
                    )
                }
                Surface(color = CardDark, shape = MaterialTheme.shapes.large) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSeekBack) { Icon(Icons.Default.Replay30, "Back 30 seconds") }
                        Button(
                            onClick = onTogglePlayback,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.isPlaying) "Pause" else "Play")
                        }
                        IconButton(onClick = onSeekForward) { Icon(Icons.Default.Forward30, "Forward 30 seconds") }
                    }
                }
            }
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (state.isBackendDown) "Backend site is down" else "Playback unavailable") },
            text = {
                Text(
                    if (state.isBackendDown) "$message\n\nEndpoint: ${context.let { SettingsManager(it).getDirectStreamEndpoint() }}" else message
                )
            },
            confirmButton = {
                Button(onClick = onRetry) { Text("Retry") }
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
