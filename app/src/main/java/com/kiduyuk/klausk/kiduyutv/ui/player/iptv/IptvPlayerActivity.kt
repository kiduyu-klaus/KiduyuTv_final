package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

/**
 * Activity for playing IPTV live streams using ExoPlayer.
 * Supports live TV streaming with proper buffering, error handling, and full track controls.
 */
@OptIn(UnstableApi::class)
class IptvPlayerActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "IptvPlayerActivity"
        
        // Intent extras keys
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"
        
        /**
         * Creates an Intent to start the IPTV player activity.
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
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var rootLayout: FrameLayout
    private var composeDialogView: ComposeView? = null
    
    private var channelName: String = ""
    private var streamUrl: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
        
        if (streamUrl.isBlank()) {
            finish()
            return
        }
        
        setupPlayer()
    }
    
    private fun setupPlayer() {
        trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearVideoSizeConstraints() 
                .setForceLowestBitrate(false)
        )
        
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        playerView = PlayerView(this).apply {
            player = this@IptvPlayerActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            
            // --- ENABLE ALL CONTROLS ---
            setShowSubtitleButton(true)
            setShowFastForwardButton(true)
            setShowRewindButton(true)
            setShowNextButton(true)
            setShowPreviousButton(true)
            
            setFullscreenButtonClickListener { isFullScreen ->
                if (!isFullScreen) {
                    showExitConfirmationDialog()
                }
            }
            
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(playerView)
        }
        
        setContentView(rootLayout)
        
        player?.apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            setMediaItem(mediaItem)
            playWhenReady = true
            addListener(playerListener)
            prepare()
        }
    }

    /**
     * Shows the Jetpack Compose tabbed track selection dialog
     */
    private fun showTrackOptionsDialog() {
        // If a dialog is already showing, do not stack another one
        if (composeDialogView != null) return

        val activePlayer = player ?: return

        composeDialogView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    TabbedTrackSelectionDialog(
                        player = activePlayer,
                        onDismissRequest = { dismissComposeDialog() }
                    )
                }
            }
        }
        rootLayout.addView(composeDialogView)
    }

    private fun dismissComposeDialog() {
        composeDialogView?.let {
            rootLayout.removeView(it)
            composeDialogView = null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS -> {
                    showTrackOptionsDialog()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            showErrorDialog(error.message ?: "Playback error occurred")
        }
    }
    
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
        composeDialogView = null
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (composeDialogView != null) {
            dismissComposeDialog()
        } else {
            showExitConfirmationDialog()
        }
    }
    
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

// ==========================================
// COMPOSE COMPONENTS FOR TRACK DIALOG
// ==========================================

@Composable
fun TabbedTrackSelectionDialog(
    player: ExoPlayer,
    onDismissRequest: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Video", "Audio", "Subtitles")
    val currentTracks = player.currentTracks

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
        title = { Text(text = "Media Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> VideoTrackList(player, currentTracks)
                    1 -> GenericTrackList(player, currentTracks, C.TRACK_TYPE_AUDIO)
                    2 -> GenericTrackList(player, currentTracks, C.TRACK_TYPE_TEXT)
                }
            }
        }
    )
}

@Composable
fun VideoTrackList(player: ExoPlayer, tracks: Tracks) {
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    
    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
        item {
            // Use clearOverridesOfType instead of clearOverrideForType
            val isAutoSelected = !player.trackSelectionParameters.overrides.containsKey(C.TRACK_TYPE_VIDEO)
            TrackSelectionRow(
                title = "Auto (Adjusts to stream)",
                isSelected = isAutoSelected,
                onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                }
            )
        }
        groups.forEach { group ->
            itemsIndexed(List(group.length) { it }) { _, trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                val resolution = if (format.width > 0 && format.height > 0) {
                    "${format.width}x${format.height}"
                } else if (format.height > 0) {
                    "${format.height}p"
                } else {
                    "Unknown"
                }
                val speedLabel = if (format.bitrate > 0) {
                    val mbps = format.bitrate / 1_000_000f
                    if (mbps >= 1f) String.format("%.1f Mbps", mbps) else "${format.bitrate / 1_000} Kbps"
                } else ""
                val displayTitle = if (speedLabel.isNotEmpty()) "$resolution — $speedLabel" else resolution
                
                TrackSelectionRow(
                    title = displayTitle,
                    isSelected = isSelected,
                    onClick = {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        // setOverrideForType takes only one argument (the override object)
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .build()
                    }
                )
            }
        }
    }
}

@Composable
fun GenericTrackList(player: ExoPlayer, tracks: Tracks, @C.TrackType trackType: Int) {
    val groups = tracks.groups.filter { it.type == trackType }
    
    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
        item {
            // Use isDisabled instead of getTrackTypeDisabled
            val isAutoSelected = !player.trackSelectionParameters.overrides.containsKey(trackType)
            val isTextDisabled = trackType == C.TRACK_TYPE_TEXT && 
                player.trackSelectionParameters.disabledTrackTypes.contains(trackType)
            
            val isSelected = if (trackType == C.TRACK_TYPE_TEXT) isTextDisabled else isAutoSelected
            val disabledText = if (trackType == C.TRACK_TYPE_TEXT) "None (Turn off subtitles)" else "Auto (Default)"
            
            TrackSelectionRow(
                title = disabledText,
                isSelected = isSelected,
                onClick = {
                    val builder = player.trackSelectionParameters.buildUpon()
                    builder.clearOverridesOfType(trackType)
                    if (trackType == C.TRACK_TYPE_TEXT) {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    }
                    player.trackSelectionParameters = builder.build()
                }
            )
        }
        
        groups.forEach { group ->
            itemsIndexed(List(group.length) { it }) { _, trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                val trackName = format.language?.uppercase() ?: "Track ${trackIndex + 1}"
                
                TrackSelectionRow(
                    title = trackName,
                    isSelected = isSelected,
                    onClick = {
                        val builder = player.trackSelectionParameters.buildUpon()
                        if (trackType == C.TRACK_TYPE_TEXT) {
                            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        }
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        // Correct API usage: setOverrideForType takes only the override object
                        builder.setOverrideForType(override)
                        player.trackSelectionParameters = builder.build()
                    }
                )
            }
        }
    }
}

@Composable
fun TrackSelectionRow(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
