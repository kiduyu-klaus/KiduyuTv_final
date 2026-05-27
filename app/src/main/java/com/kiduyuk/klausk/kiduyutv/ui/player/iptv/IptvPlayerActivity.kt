package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

/**
 * IPTV Player Activity — uses activity_player.xml layout.
 *
 * All controls wired to live-stream–appropriate IPTV actions:
 *  • Top bar       – channel name / ● LIVE badge / info
 *  • Overlay       – lock, channel-up (skip), next channel, channel list,
 *                    live seekbar (buffer indicator), Fill·CC·Settings·Volume·Cast·PiP
 */
@OptIn(UnstableApi::class)
class IptvPlayerActivity : AppCompatActivity() {

    // ── Companion ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "IptvPlayerActivity"
        // Keep TAG for potential logging in future releases
        @Suppress("UNUSED")
        private val isDebugEnabled = false

        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL   = "stream_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"

        private const val OVERLAY_HIDE_DELAY_MS = 4_000L
        private const val SEEK_POLL_MS          =   500L

        /** Open this player from anywhere in the app. */
        fun createIntent(
            context: Context,
            channelName: String,
            streamUrl: String,
            channelLogo: String? = null
        ) = Intent(context, IptvPlayerActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_STREAM_URL,   streamUrl)
            putExtra(EXTRA_CHANNEL_LOGO, channelLogo)
        }
    }

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector

    // ── View references ──────────────────────────────────────────────────────

    // Root (used to host the Compose dialog overlay)
    private lateinit var rootLayout: ConstraintLayout

    // Top bar
    private lateinit var tvChannelName: TextView   // reuses @id/tvDate
    private lateinit var tvLiveBadge: TextView     // reuses @id/tvTime
    private lateinit var btnBack: ImageButton
    private lateinit var btnInfo: ImageButton

    // Video
    private lateinit var playerView: PlayerView
    private lateinit var overlayControls: FrameLayout

    // Overlay – centre controls
    private lateinit var btnLock: ImageButton
    private lateinit var btnChannelUp: ImageButton  // reuses @id/btnSkipForward
    private lateinit var btnNextChannel: ImageButton
    private lateinit var btnChannelList: ImageButton

    // Overlay – bottom strip
    private lateinit var seekBar: SeekBar
    private lateinit var btnFill: TextView
    private lateinit var btnCC: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnVolume: ImageButton
    private lateinit var btnCast: ImageButton
    private lateinit var btnPip: ImageButton

    // ── State ────────────────────────────────────────────────────────────────

    private var channelName = "Live TV"
    private var streamUrl   = ""

    private var isOverlayVisible = true
    private var isLocked         = false
    private var isFillMode       = true
private var isMuted = false

    // Compose dialog overlay (track selector or any other sheet)
    private var composeDialogView: ComposeView? = null

    // Handlers
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }

    private val seekHandler = Handler(Looper.getMainLooper())
    private val seekRunnable = object : Runnable {
        override fun run() {
            updateLiveSeekBar()
            seekHandler.postDelayed(this, SEEK_POLL_MS)
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl   = intent.getStringExtra(EXTRA_STREAM_URL)   ?: ""

        if (streamUrl.isBlank()) { finish(); return }

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Full-screen, edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContentView(R.layout.activity_player_iptv)

        bindViews()
        populateTopBar()
        wireOverlayToggle()
        wireOverlayButtons()
        wireLiveSeekBar()
        initPlayer()
    }

    override fun onStart() {
        super.onStart()
        player?.play()
        seekHandler.post(seekRunnable)
        scheduleHideOverlay()
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
        seekHandler.removeCallbacks(seekRunnable)
        hideHandler.removeCallbacks(hideRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        composeDialogView = null
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val vis = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.topBar).visibility = vis
        overlayControls.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        composeDialogView?.visibility = vis
    }

    // ── Hardware key routing ─────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS -> {
                    showTrackDialog(startTab = 0)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (composeDialogView != null) { dismissComposeDialog(); return true }
                    if (isLocked)                  { return true }  // block back when locked
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {

        when {
            composeDialogView != null -> dismissComposeDialog()
            isLocked                  -> { /* swallow – user must unlock first */ }
            else                      -> showExitDialog()
        }
        super.onBackPressed()
    }

    // ── Bind views ───────────────────────────────────────────────────────────

    private fun bindViews() {
        rootLayout      = findViewById(R.id.rootLayout)

        btnBack         = findViewById(R.id.btnBack)
        tvChannelName   = findViewById(R.id.tvDate)      // repurposed
        tvLiveBadge     = findViewById(R.id.tvTime)      // repurposed
        btnInfo         = findViewById(R.id.btnInfo)

        playerView      = findViewById(R.id.playerView)
        overlayControls = findViewById(R.id.overlayControls)

        btnLock         = findViewById(R.id.btnLock)
        btnChannelUp    = findViewById(R.id.btnSkipForward)  // repurposed: channel up
        btnNextChannel  = findViewById(R.id.btnNext)
        btnChannelList  = findViewById(R.id.btnPlaylist)

        seekBar         = findViewById(R.id.seekBar)
        btnFill         = findViewById(R.id.btnFill)
        btnCC           = findViewById(R.id.btnCC)
        btnSettings     = findViewById(R.id.btnSettings)
        btnVolume       = findViewById(R.id.btnVolume)
        btnCast         = findViewById(R.id.btnCast)
        btnPip          = findViewById(R.id.btnPip)
    }

    // ── Top bar ──────────────────────────────────────────────────────────────

    private fun populateTopBar() {
        tvChannelName.text = channelName
        tvLiveBadge.text   = "● LIVE"
        tvLiveBadge.setTextColor(0xFFFF4444.toInt())

        btnBack.setOnClickListener { showExitDialog() }
        btnInfo.setOnClickListener { showChannelInfo() }
    }

    private fun showChannelInfo() {
        Toast.makeText(this, "Stream: $streamUrl", Toast.LENGTH_LONG).show()
    }

    // ── Overlay show/hide ────────────────────────────────────────────────────

    private fun wireOverlayToggle() {
        playerView.setOnClickListener {
            if (isLocked) {
                // In locked mode, flash only the lock button
                btnLock.visibility = View.VISIBLE
                scheduleHideOverlay()
                return@setOnClickListener
            }
            if (isOverlayVisible) hideOverlay() else showOverlay()
        }
    }

    private fun showOverlay() {
        if (isLocked) return
        isOverlayVisible = true
        overlayControls.animate().alpha(1f).setDuration(200).withStartAction {
            overlayControls.visibility = View.VISIBLE
        }.start()
        scheduleHideOverlay()
    }

    private fun hideOverlay() {
        if (isLocked) {
            // Keep only the lock button visible
            setOverlayChildrenVisibility(View.INVISIBLE)
            btnLock.visibility = View.VISIBLE
            overlayControls.visibility = View.VISIBLE
            overlayControls.alpha = 1f
            return
        }
        isOverlayVisible = false
        overlayControls.animate().alpha(0f).setDuration(300).withEndAction {
            overlayControls.visibility = View.INVISIBLE
        }.start()
    }

    private fun scheduleHideOverlay() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, OVERLAY_HIDE_DELAY_MS)
    }

    private fun setOverlayChildrenVisibility(visibility: Int) {
        for (i in 0 until overlayControls.childCount) {
            val child = overlayControls.getChildAt(i)
            if (child.id != R.id.btnLock) child.visibility = visibility
        }
    }

    // ── Overlay buttons ──────────────────────────────────────────────────────

    private fun wireOverlayButtons() {

        // ── Lock / Unlock ────────────────────────────────────────────────────
        btnLock.setOnClickListener {
            isLocked = !isLocked
            btnLock.setImageResource(
                if (isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open
            )
            if (isLocked) {
                setOverlayChildrenVisibility(View.INVISIBLE)
                btnLock.visibility     = View.VISIBLE
                overlayControls.alpha  = 1f
                overlayControls.visibility = View.VISIBLE
                hideHandler.removeCallbacks(hideRunnable)
            } else {
                setOverlayChildrenVisibility(View.VISIBLE)
                scheduleHideOverlay()
            }
        }

        // ── Channel Up (repurposed skip-forward slot) ────────────────────────
        // Update the content description so accessibility is correct
        btnChannelUp.contentDescription = "Channel up"
        btnChannelUp.setImageResource(R.drawable.ic_forward_15)
        btnChannelUp.setOnClickListener {
            // Notify host app to switch to next channel (same as btnNextChannel below).
            // If your playlist is managed externally, fire a broadcast / callback here.
            Toast.makeText(this, "Channel up — wire your playlist callback here", Toast.LENGTH_SHORT).show()
            scheduleHideOverlay()
        }

        // ── Next Channel ─────────────────────────────────────────────────────
        btnNextChannel.setOnClickListener {
            Toast.makeText(this, "Next channel — wire your playlist callback here", Toast.LENGTH_SHORT).show()
            scheduleHideOverlay()
        }

        // ── Channel List ─────────────────────────────────────────────────────
        btnChannelList.setOnClickListener {
            Toast.makeText(this, "Channel list — wire your EPG/playlist here", Toast.LENGTH_SHORT).show()
            scheduleHideOverlay()
        }

        // ── Fill / Fit ───────────────────────────────────────────────────────
        btnFill.setOnClickListener {
            isFillMode = !isFillMode
            playerView.resizeMode = if (isFillMode)
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            else
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            btnFill.text = if (isFillMode) "Fill" else "Fit"
            scheduleHideOverlay()
        }

        // ── CC → open Subtitles tab directly ────────────────────────────────
        btnCC.setOnClickListener {
            showTrackDialog(startTab = 2)   // 0=Video 1=Audio 2=Subtitles
            scheduleHideOverlay()
        }

        // ── Settings → open full track dialog ───────────────────────────────
        btnSettings.setOnClickListener {
            showTrackDialog(startTab = 0)
            scheduleHideOverlay()
        }

        // ── Volume / Mute ────────────────────────────────────────────────────
        btnVolume.setOnClickListener {
            isMuted = !isMuted
            player?.volume = if (isMuted) 0f else 1f
            btnVolume.setImageResource(
                if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
            )
            scheduleHideOverlay()
        }

        // ── Cast ─────────────────────────────────────────────────────────────
        btnCast.setOnClickListener {
            Toast.makeText(this, "Cast — wire Google Cast SDK here", Toast.LENGTH_SHORT).show()
            scheduleHideOverlay()
        }

        // ── Picture-in-Picture ───────────────────────────────────────────────
        btnPip.setOnClickListener {
            enterPipMode()
        }
    }

    // ── Live seekbar (buffer indicator — live streams don't support scrubbing) ──

    private fun wireLiveSeekBar() {
        // Disable thumb dragging for live streams.
        // The bar shows buffered progress only.
        seekBar.isEnabled = false
        seekBar.alpha     = 0.6f
        seekBar.setOnSeekBarChangeListener(null)
    }

    /**
     * Updates the seekbar to reflect the ExoPlayer buffer level (0–100).
     * For streams that DO support DVR timeshift, replace this with
     * (currentPosition / duration * 100) and re-enable the seekbar.
     */
    private fun updateLiveSeekBar() {
        player?.let { p ->
            val buffPct = p.bufferedPercentage.coerceIn(0, 100)
            seekBar.progress = buffPct
        }
    }

    // ── Track Selection Dialog (Compose) ─────────────────────────────────────

    /**
     * @param startTab 0=Video, 1=Audio, 2=Subtitles
     */
    private fun showTrackDialog(startTab: Int = 0) {
        if (composeDialogView != null) return
        val activePlayer = player ?: return

        composeDialogView = ComposeView(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                MaterialTheme {
                    TabbedTrackSelectionDialog(
                        player       = activePlayer,
                        initialTab   = startTab,
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

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    private fun initPlayer() {
        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setForceLowestBitrate(false)
            )
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exo ->
                // Attach to our PlayerView (controller is driven manually)
                playerView.player      = exo
                playerView.useController = false
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                playerView.resizeMode  = AspectRatioFrameLayout.RESIZE_MODE_FILL

                exo.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                exo.playWhenReady = true
                exo.addListener(playerListener)
                exo.prepare()
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            showPlaybackErrorDialog(error.message ?: "Playback error")
        }
    }

    private fun showPlaybackErrorDialog(message: String) {
        QuitDialog(
            context             = this,
            title               = "Playback Error",
            message             = message,
            positiveButtonText  = "Retry",
            negativeButtonText  = "Exit",
            lottieAnimRes       = R.raw.exit,
            onNo                = { finish() },
            onYes               = {
                player?.prepare()
                player?.play()
            }
        ).show()
    }

    private fun releasePlayer() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    // ── Exit dialog ──────────────────────────────────────────────────────────

    private fun showExitDialog() {
        QuitDialog(
            context             = this,
            title               = "Stop Playback?",
            message             = "Are you sure you want to stop watching $channelName?",
            positiveButtonText  = "Stop",
            negativeButtonText  = "Continue",
            lottieAnimRes       = R.raw.exit,
            onNo                = { },
            onYes               = { finish() }
        ).show()
    }

    // ── PiP ─────────────────────────────────────────────────────────────────

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "PiP not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // ── System bars ──────────────────────────────────────────────────────────

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// ============================================================================
// COMPOSE — Tabbed Track Selection Dialog
// ============================================================================

/**
 * Full-screen dialog overlay showing Video / Audio / Subtitles tabs.
 * @param initialTab  Which tab to open first (0=Video, 1=Audio, 2=Subtitles).
 */
@OptIn(UnstableApi::class)
@Composable
fun TabbedTrackSelectionDialog(
    player: ExoPlayer,
    initialTab: Int = 0,
    onDismissRequest: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    // Force recomposition when track selection changes
    val currentTracks by remember { mutableStateOf(player.currentTracks) }
    val closeInteractionSource = remember { MutableInteractionSource() }
    val isCloseFocused by closeInteractionSource.collectIsFocusedAsState()
    val tabs = listOf("Video", "Audio", "Subtitles")

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .background(
                        color = if (isCloseFocused) Color(0xFF448AFF).copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Close",
                    color = if (isCloseFocused) Color(0xFF448AFF) else Color.White
                )
            }
        },
        title = {
            Text(
                text = "Media Settings",
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusable()
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text     = {
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) Color(0xFF448AFF) else Color.White
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                when (selectedTab) {
                    0 -> VideoTrackList(player, currentTracks, onDismissRequest)
                    1 -> GenericTrackList(player, currentTracks, C.TRACK_TYPE_AUDIO, onDismissRequest)
                    2 -> GenericTrackList(player, currentTracks, C.TRACK_TYPE_TEXT, onDismissRequest)
                }
            }
        },
        containerColor = Color(0xFF2D2D2D)
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoTrackList(player: ExoPlayer, tracks: Tracks, onDismiss: () -> Unit) {
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }

    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
        item {
            val isAuto = player.trackSelectionParameters.overrides.values
                .none { it.type == C.TRACK_TYPE_VIDEO }
            TrackSelectionRow(
                title      = "Auto (adjusts to stream quality)",
                isSelected = isAuto,
                onClick    = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                    onDismiss()
                }
            )
        }
        groups.forEach { group ->
            itemsIndexed(List(group.length) { it }) { _, trackIndex ->
                val fmt = group.getTrackFormat(trackIndex)
                val resolution = when {
                    fmt.width > 0 && fmt.height > 0 -> "${fmt.width}×${fmt.height}"
                    fmt.height > 0                  -> "${fmt.height}p"
                    else                            -> "Unknown"
                }
                val bitrateLabel = if (fmt.bitrate > 0) {
                    val mbps = fmt.bitrate / 1_000_000f
                    if (mbps >= 1f) " — %.1f Mbps".format(mbps)
                    else " — ${fmt.bitrate / 1_000} Kbps"
                } else ""

                TrackSelectionRow(
                    title      = "$resolution$bitrateLabel",
                    isSelected = group.isTrackSelected(trackIndex),
                    onClick    = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                            .build()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun GenericTrackList(player: ExoPlayer, tracks: Tracks, trackType: Int, onDismiss: () -> Unit) {
    val groups = tracks.groups.filter { it.type == trackType }
    val isText = trackType == C.TRACK_TYPE_TEXT

    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
        item {
            val isDisabledOrAuto = if (isText)
                player.trackSelectionParameters.disabledTrackTypes.contains(trackType)
            else
                player.trackSelectionParameters.overrides.values.none { it.type == trackType }

            TrackSelectionRow(
                title      = if (isText) "None (subtitles off)" else "Auto (default)",
                isSelected = isDisabledOrAuto,
                onClick    = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(trackType)
                        .apply { if (isText) setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }
                        .build()
                    onDismiss()
                }
            )
        }
        groups.forEach { group ->
            itemsIndexed(List(group.length) { it }) { _, trackIndex ->
                val fmt       = group.getTrackFormat(trackIndex)
                val trackName = fmt.language?.uppercase() ?: "Track ${trackIndex + 1}"
                TrackSelectionRow(
                    title      = trackName,
                    isSelected = group.isTrackSelected(trackIndex),
                    onClick    = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .apply { if (isText) setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false) }
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                            .build()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun TrackSelectionRow(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.background(
                        color = Color(0xFF448AFF).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            interactionSource = interactionSource,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF448AFF),
                unselectedColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isFocused) Color(0xFF448AFF) else Color.White
        )
    }
}