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
import android.util.Log
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
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject

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
        const val EXTRA_TVG_ID = "tvg_id"
        const val EXTRA_TVG_NAME = "tvg_name"
        const val EXTRA_GROUP = "group"
        const val EXTRA_REQUEST_HEADERS = "request_headers"
        const val EXTRA_STREAM_COOKIE = "stream_cookie"
        const val EXTRA_STREAM_MIME_TYPE = "stream_mime_type"
        const val EXTRA_RETURN_TO_SCHEDULE_ON_ERROR = "return_to_schedule_on_error"
        const val EXTRA_PLAYLIST_CHANNELS = "playlist_channels"
        const val EXTRA_FAILED_STREAM_URL = "failed_stream_url"
        const val RESULT_PLAYBACK_FAILED = android.app.Activity.RESULT_FIRST_USER + 41

        private const val OVERLAY_HIDE_DELAY_MS = 5_000L //
        private const val SEEK_POLL_MS          =   500L

        /** Open this player from anywhere in the app. */
        fun createIntent(
            context: Context,
            channelName: String,
            streamUrl: String,
            channelLogo: String? = null,
            tvgId: String? = null,
            tvgName: String? = null,
            group: String? = null,
            requestHeaders: Map<String, String> = emptyMap(),
            cookie: String? = null,
            mimeType: String? = null,
            returnToScheduleOnError: Boolean = false,
            playlistChannels: List<IptvChannel> = emptyList()
        ) = Intent(context, IptvPlayerActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_STREAM_URL,   streamUrl)
            putExtra(EXTRA_CHANNEL_LOGO, channelLogo)
            putExtra(EXTRA_TVG_ID, tvgId)
            putExtra(EXTRA_TVG_NAME, tvgName)
            putExtra(EXTRA_GROUP, group)
            putExtra(EXTRA_REQUEST_HEADERS, JSONObject(requestHeaders).toString())
            putExtra(EXTRA_STREAM_COOKIE, cookie)
            putExtra(EXTRA_STREAM_MIME_TYPE, mimeType)
            putExtra(EXTRA_RETURN_TO_SCHEDULE_ON_ERROR, returnToScheduleOnError)
            putExtra(EXTRA_PLAYLIST_CHANNELS, JSONArray(playlistChannels.map { it.toJson() }).toString())
        }
    }

    // ── Video Resize Mode Enum ──────────────────────────────────────────────

    private enum class VideoResizeMode {
        FIT,
        FILL,
        STRETCH
    }

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private var trackSelectionInitialized = false

    // ── View references ──────────────────────────────────────────────────────

    // Root (used to host the Compose dialog overlay)
    private lateinit var rootLayout: ConstraintLayout

    // Top bar
    private lateinit var topBar: View
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
    private lateinit var tvSubtitleTracks: TextView
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnVolume: ImageButton
    private lateinit var btnCast: ImageButton
    private lateinit var btnPip: ImageButton

    // ── State ────────────────────────────────────────────────────────────────

    private var channelName = "Live TV"
    private var streamUrl   = ""
    private var channelLogo: String? = null
    private var tvgId: String? = null
    private var tvgName: String? = null
    private var channelGroup: String? = null
    private var requestHeaders: Map<String, String> = emptyMap()
    private var streamCookie: String? = null
    private var streamMimeType: String? = null
    private var playlistChannels: List<IptvChannel> = emptyList()

    private var isOverlayVisible = true
    private var isLocked         = false
    private var currentResizeMode = VideoResizeMode.FIT
    private var isMuted = false

    // D-pad navigation tracking for keeping controls visible
    private var isDpadNavigating = false

    private val liveTvViewModel: LiveTvViewModel by viewModels()

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

    private val dpadTimeoutHandler = Handler(Looper.getMainLooper())
    private val dpadTimeoutRunnable = Runnable {
        isDpadNavigating = false
    }
    private val DPAD_TIMEOUT_MS = 3000L // Reset dpad state after 3 seconds of inactivity

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl   = intent.getStringExtra(EXTRA_STREAM_URL)   ?: ""
        channelLogo = intent.getStringExtra(EXTRA_CHANNEL_LOGO)
        tvgId = intent.getStringExtra(EXTRA_TVG_ID)
        tvgName = intent.getStringExtra(EXTRA_TVG_NAME)
        channelGroup = intent.getStringExtra(EXTRA_GROUP)
        requestHeaders = parseRequestHeaders(intent.getStringExtra(EXTRA_REQUEST_HEADERS))
        streamCookie = intent.getStringExtra(EXTRA_STREAM_COOKIE)
        streamMimeType = intent.getStringExtra(EXTRA_STREAM_MIME_TYPE)
        playlistChannels = parsePlaylistChannels(intent.getStringExtra(EXTRA_PLAYLIST_CHANNELS))

        Log.i(
            TAG,
            "Opening channel=${channelName.take(80)} source=${safeStreamDescription(streamUrl)} " +
                "mime=${streamMimeType ?: "auto"} headers=${requestHeaders.keys.sorted()} " +
                "hasCookie=${!streamCookie.isNullOrBlank()}"
        )

        if (streamUrl.isBlank()) {
            Log.i(TAG, "Closing player because the stream URL is empty")
            finish()
            return
        }

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Full-screen, edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContentView(R.layout.activity_player_iptv)

        bindViews()
        applyMaterialPlayerStyling()
        liveTvViewModel.initialize(applicationContext)
        updateFavoriteButtonState()
        populateTopBar()
        wireOverlayToggle()
        wireOverlayButtons()
        wireLiveSeekBar()
        initPlayer()

    }

    private fun parseRequestHeaders(encodedHeaders: String?): Map<String, String> {
        if (encodedHeaders.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(encodedHeaders)
            val headers = linkedMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                json.optString(key).takeIf { it.isNotBlank() }?.let { value ->
                    headers[key] = value
                }
            }
            headers
        }.getOrDefault(emptyMap())
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
        // Track dpad navigation activity
        if (isDpadKey(event)) {
            isDpadNavigating = true
            // Cancel any pending hide and show overlay
            hideHandler.removeCallbacks(hideRunnable)
            if (!isOverlayVisible) {
                showOverlay()
            }
            // Reset the timeout
            dpadTimeoutHandler.removeCallbacks(dpadTimeoutRunnable)
            dpadTimeoutHandler.postDelayed(dpadTimeoutRunnable, DPAD_TIMEOUT_MS)
            // Reset hide timer to 5 seconds
            scheduleHideOverlay()
        }

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

    /**
     * Checks if the key event is from a d-pad button.
     */
    private fun isDpadKey(event: KeyEvent): Boolean {
        return event.source and android.view.InputDevice.SOURCE_DPAD == android.view.InputDevice.SOURCE_DPAD ||
                event.keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        // Track dpad navigation and show controls
        if (isDpadKeyCode(keyCode)) {
            isDpadNavigating = true
            // Cancel any pending hide and show overlay
            hideHandler.removeCallbacks(hideRunnable)
            if (!isOverlayVisible) {
                showOverlay()
            }
            // Reset the timeout
            dpadTimeoutHandler.removeCallbacks(dpadTimeoutRunnable)
            dpadTimeoutHandler.postDelayed(dpadTimeoutRunnable, DPAD_TIMEOUT_MS)
            // Reset hide timer to 5 seconds
            scheduleHideOverlay()
        }

        when (keyCode) {
            // ── Show/Hide Controls ──────────────────────────────────────────────
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (isLocked) return true
                if (!isOverlayVisible) {
                    showOverlay()
                } else {
                    //Toggle visibility on repeated press
                    hideOverlay()
                }
                return true
            }

            // ── Playback Controls ──────────────────────────────────────────────
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }

            // ── Mute/Unmute ────────────────────────────────────────────────────
            KeyEvent.KEYCODE_MUTE,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleMute()
                return true
            }

            // ── Volume Controls (optional - also toggle mute) ──────────────────
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (isMuted) {
                    toggleMute()
                }
                // Let system handle volume adjustment
                return false
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * Checks if the key code is from a d-pad button.
     */
    private fun isDpadKeyCode(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS
        )
    }

    /**
     * Toggles play/pause state and shows brief feedback.
     */
    private fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
                showPlaybackStateIndicator(false)
            } else {
                p.play()
                showPlaybackStateIndicator(true)
            }
        }
    }

    /**
     * Toggles mute/unmute state.
     */
    private fun toggleMute() {
        isMuted = !isMuted
        player?.volume = if (isMuted) 0f else 1f
        btnVolume.setImageResource(
            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
        showMuteStateIndicator(isMuted)
        if (isOverlayVisible) scheduleHideOverlay()
    }

    /**
     * Shows a brief toast indicator for playback state changes.
     */
    private fun showPlaybackStateIndicator(isPlaying: Boolean) {
        val message = if (isPlaying) "▶ Playing" else "⏸ Paused"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows a brief toast indicator for mute state changes.
     */
    private fun showMuteStateIndicator(isMuted: Boolean) {
        val message = if (isMuted) "🔇 Muted" else "🔊 Unmuted"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        topBar         = findViewById(R.id.topBar)

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
        tvSubtitleTracks = findViewById(R.id.tvSubtitleTracks)
        btnFavorite      = findViewById(R.id.btnFavorite)
        btnSettings     = findViewById(R.id.btnSettings)
        btnVolume       = findViewById(R.id.btnVolume)
        btnCast         = findViewById(R.id.btnCast)
        btnPip          = findViewById(R.id.btnPip)

        // Set up D-pad focus chain - request initial focus on btnFill (first control in bottom row)
        btnFill.isFocusable = true
        btnFill.isFocusableInTouchMode = true
        btnFill.requestFocus()
    }

    /** Applies runtime colors that follow the active Material player theme. */
    private fun applyMaterialPlayerStyling() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.WHITE
        )
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(
            0xFFFF5252.toInt()
        )
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
        val info = buildString {
            appendLine("Channel: $channelName")
            tvgId?.let { appendLine("TVG ID: $it") }
            tvgName?.let { appendLine("TVG Name: $it") }
            channelGroup?.let { appendLine("Group: $it") }
            appendLine("Stream: $streamUrl")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(channelName)
            .setMessage(info)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun parsePlaylistChannels(encodedChannels: String?): List<IptvChannel> {
        if (encodedChannels.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encodedChannels)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (url.isNotBlank()) {
                        add(IptvChannel(
                            name = item.optString("name", "Live channel"),
                            logo = item.optString("logo").takeIf { it.isNotBlank() },
                            url = url,
                            group = item.optString("group").takeIf { it.isNotBlank() },
                            tvgId = item.optString("tvgId").takeIf { it.isNotBlank() },
                            tvgName = item.optString("tvgName").takeIf { it.isNotBlank() }
                        ))
                    }
                }
            }
        }.getOrDefault(emptyList())
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

        topBar.animate().alpha(1f).setDuration(200).withStartAction {
            topBar.visibility = View.VISIBLE
        }.start()

        overlayControls.animate().alpha(1f).setDuration(200).withStartAction {
            overlayControls.visibility = View.VISIBLE
        }.withEndAction {
            // Use the view's own post — NOT hideHandler — to avoid racing the hide timer
            btnFill.post { btnFill.requestFocus() }
        }.start()

        scheduleHideOverlay()
    }

    private fun hideOverlay() {
        if (isLocked) {
            // Keep only the lock button visible
            setOverlayChildrenVisibility(View.GONE)
            btnLock.visibility = View.VISIBLE
            overlayControls.visibility = View.VISIBLE
            overlayControls.alpha = 1f
            return
        }

        // Don't hide overlay if user is navigating with dpad
        if (isDpadNavigating) {
            // Just reset the hide timer
            scheduleHideOverlay()
            return
        }

        isOverlayVisible = false

        // Hide top bar
        topBar.animate().alpha(0f).setDuration(500).withEndAction {
            topBar.visibility = View.GONE
            topBar.alpha = 1f // Reset alpha for next show
        }.start()

        // Hide overlay controls
        overlayControls.animate().alpha(0f).setDuration(500).withEndAction {
            overlayControls.visibility = View.INVISIBLE
            overlayControls.alpha = 1f // Reset alpha for next show
        }.start()
    }

    private fun scheduleHideOverlay() {
        hideHandler.removeCallbacks(hideRunnable)
        // When dpad navigating, use longer timeout (5 seconds as per user request)
        val delay = if (isDpadNavigating) OVERLAY_HIDE_DELAY_MS else OVERLAY_HIDE_DELAY_MS
        hideHandler.postDelayed(hideRunnable, delay)
    }

    private fun setOverlayChildrenVisibility(visibility: Int) {
        for (i in 0 until overlayControls.childCount) {
            val child = overlayControls.getChildAt(i)
            if (child.id != R.id.btnLock) child.visibility = visibility
        }
    }

    private fun updateFavoriteButtonState() {
        val channel = IptvChannel(
            name = channelName,
            logo = channelLogo,
            url = streamUrl,
            group = channelGroup,
            tvgId = tvgId,
            tvgName = tvgName
        )
        val isSaved = liveTvViewModel.isFavorite(channel)
        btnFavorite.setImageResource(
            if (isSaved) android.R.drawable.star_big_on else android.R.drawable.star_big_off
        )
        // Tint to red when favorited
        if (isSaved) {
            btnFavorite.setColorFilter(
                android.graphics.Color.RED,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            btnFavorite.clearColorFilter()
        }
    }

    private fun confirmAddFavorite(channel: IptvChannel) {
        if (liveTvViewModel.isFavorite(channel)) {
            // Show dialog to confirm removal
            MaterialAlertDialogBuilder(this)
                .setTitle("Remove from favorites")
                .setMessage("Remove $channelName from your favorites?")
                .setPositiveButton("Remove") { _, _ ->
                    liveTvViewModel.removeFavorite(channel)
                    btnFavorite.setImageResource(android.R.drawable.star_big_off)
                    btnFavorite.clearColorFilter()
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add to favorites")
            .setMessage("Add $channelName to your favorites?")
            .setPositiveButton("Add") { _, _ ->
                liveTvViewModel.addFavorite(channel)
                btnFavorite.setImageResource(android.R.drawable.star_big_on)
                btnFavorite.setColorFilter(
                    android.graphics.Color.RED,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        // ── Channel List → Playlist channels ───────────────────────────────────
        btnChannelList.setOnClickListener {
            showPlaylistDialog()
            scheduleHideOverlay()
        }

        // ── Fill / Fit / Stretch ───────────────────────────────────────────────
        btnFill.setOnClickListener {
            when (currentResizeMode) {
                VideoResizeMode.FIT -> {
                    currentResizeMode = VideoResizeMode.FILL
                    btnFill.text = "Fill"
                }
                VideoResizeMode.FILL -> {
                    currentResizeMode = VideoResizeMode.STRETCH
                    btnFill.text = "Stretch"
                }
                VideoResizeMode.STRETCH -> {
                    currentResizeMode = VideoResizeMode.FIT
                    btnFill.text = "Fit"
                }
            }
            applyResizeMode(currentResizeMode)
            scheduleHideOverlay()
        }

        // ── CC → open Subtitles tab directly ────────────────────────────────
        btnCC.setOnClickListener {
            showTrackDialog(startTab = 2)   // 0=Video 1=Audio 2=Subtitles
            scheduleHideOverlay()
        }

        // ── Favorite / Save channel ─────────────────────────────────────────
        btnFavorite.setOnClickListener {
            val channel = IptvChannel(
                name = channelName,
                logo = channelLogo,
                url = streamUrl,
                group = channelGroup,
                tvgId = tvgId,
                tvgName = tvgName
            )
            confirmAddFavorite(channel)
        }

        // ── Settings → open full track dialog ───────────────────────────────
        btnSettings.setOnClickListener {
            showTrackDialog(startTab = 0)
            scheduleHideOverlay()
        }

        // Make sure favorites button reflects current save state
        updateFavoriteButtonState()

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

    /**
     * Applies the selected video resize mode to the player view.
     */
    private fun applyResizeMode(mode: VideoResizeMode) {
        when (mode) {
            VideoResizeMode.FIT -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            VideoResizeMode.FILL -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM  // Fills view while maintaining aspect ratio (crops)
            }
            VideoResizeMode.STRETCH -> {
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL  // Stretches to fill view (distorts)
            }
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
        Log.i(
            TAG,
            "Initializing Media3 source=${safeStreamDescription(streamUrl)} " +
                "mime=${streamMimeType ?: "auto"}"
        )
        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setForceLowestBitrate(false)
                    .setExceedVideoConstraintsIfNecessary(true) // set
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }
        val customLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,        // Min buffer depth (Default is 50,000 but lower helps live feeds stay close to real-time)
                60_000,        // Max buffer depth
                2_500,         // Buffer needed to start initial playback (increase if users experience instant source errors)
                5_000          // Buffer needed to resume playback after a network dip
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 1. Configure aggressive timeouts and a realistic User-Agent header
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000) // Increase to 15 seconds for slow headers
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true) // Crucial if your M3U references redirect across http/https
            .setDefaultRequestProperties(
                LinkedHashMap(requestHeaders).apply {
                    if (
                        !streamCookie.isNullOrBlank() &&
                        keys.none { it.equals("Cookie", ignoreCase = true) }
                    ) {
                        put("Cookie", streamCookie.orEmpty())
                    }
                }
            )

        // 2. Wrap it inside the MediaSource Factory
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory) // <--- Inject network rules here
            .setLoadControl(customLoadControl) // <--- Inject buffer constraints
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exo ->
                playerView.player = exo
                playerView.useController = false
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // Default to FIT

                configureSubtitleStyling()

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .apply {
                        streamMimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                    }
                    .build()
                exo.setMediaItem(mediaItem)
                exo.playWhenReady = true
                exo.addListener(playerListener)
                exo.prepare()
                Log.i(TAG, "Media item prepared; waiting for playback")
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            Log.i(
                TAG,
                "Tracks changed groups=${tracks.groups.size} initialized=$trackSelectionInitialized"
            )
            if (!trackSelectionInitialized) {
                trackSelectionInitialized = true
                selectHighestQualityVideoTrack(tracks)
                selectDefaultSubtitleTrack(tracks)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> playbackState.toString()
            }
            Log.i(
                TAG,
                "Playback state=$stateName playWhenReady=${player?.playWhenReady} " +
                    "isPlaying=${player?.isPlaying}"
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "isPlaying=$isPlaying")
        }

        override fun onPlayerError(error: PlaybackException) {
            // Extract the underlying system cause if it exists
            val cause = error.cause
            val causeMessage = cause?.localizedMessage ?: cause?.message
            val responseCode =
                (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                    ?.responseCode

            Log.i(
                TAG,
                "Playback failed code=${error.errorCodeName}(${error.errorCode}) " +
                    "cause=${cause?.javaClass?.simpleName ?: "none"} http=${responseCode ?: "none"} " +
                    "source=${safeStreamDescription(streamUrl)} message=${causeMessage?.take(240)}"
            )

            val fullDetailedMessage = buildString {
                appendLine("Error Code: ${error.errorCodeName} (${error.errorCode})")
                appendLine()
                appendLine("Primary Message: ${error.message}")
                if (causeMessage != null) {
                    appendLine()
                    appendLine("Underlying Cause:")
                    appendLine(causeMessage)
                }
                // If it's an HTTP status error, extract the exact status code (e.g., 403, 404, 500)
                if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    appendLine()
                    appendLine("HTTP Status Code: ${cause.responseCode}")
                    appendLine("Request URI: ${cause.dataSpec.uri}")
                }
            }

            if (intent.getBooleanExtra(EXTRA_RETURN_TO_SCHEDULE_ON_ERROR, false)) {
                Toast.makeText(
                    this@IptvPlayerActivity,
                    "Playback failed. Trying another server…",
                    Toast.LENGTH_SHORT
                ).show()
                setResult(
                    RESULT_PLAYBACK_FAILED,
                    Intent().putExtra(EXTRA_FAILED_STREAM_URL, streamUrl)
                )
                finish()
            } else {
                showPlaybackErrorDialog(fullDetailedMessage, causeMessage)
            }
        }
    }

    private fun safeStreamDescription(url: String): String {
        if (url.isBlank()) return "<empty>"
        return runCatching {
            val uri = Uri.parse(url)
            buildString {
                append(uri.scheme ?: "?")
                append("://")
                append(uri.host ?: "?")
                append(uri.path.orEmpty().take(160))
                if (!uri.query.isNullOrBlank()) append("?<redacted>")
            }
        }.getOrDefault("<invalid-url>")
    }

    private fun showPlaybackErrorDialog(fullMessage: String, shortMessage: String?) {
        // Use UiModeManager to detect television vs phone
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isPhone = uiModeManager?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION

        val messageToShow = if (isPhone) (shortMessage ?: fullMessage) else fullMessage

        QuitDialog(
            context             = this,
            title               = "Playback Error",
            message             = messageToShow,
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

    private fun selectHighestQualityVideoTrack(tracks: Tracks) {
        val bestTrack = tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).map { index ->
                    val format = group.getTrackFormat(index)
                    val qualityScore = when {
                        format.width > 0 && format.height > 0 -> format.width.toLong() * format.height + format.bitrate
                        format.bitrate > 0 -> format.bitrate.toLong()
                        else -> 0L
                    }
                    Triple(group, index, qualityScore)
                }
            }
            .maxByOrNull { it.third }

        bestTrack?.let { (group, index, _) ->
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                .build()
        }
    }

    private fun selectDefaultSubtitleTrack(tracks: Tracks) {
        val subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (subtitleGroups.isEmpty()) {
            runOnUiThread { tvSubtitleTracks.visibility = View.GONE }
            return
        }

        val englishSubtitle = subtitleGroups.asSequence()
            .flatMap { group ->
                (0 until group.length).map { index -> Pair(group, index) }
            }
            .firstOrNull { (group, index) ->
                val language = group.getTrackFormat(index).language?.lowercase()
                language == "en" || language == "eng"
            }

        val selectedSubtitle = englishSubtitle ?: subtitleGroups.asSequence()
            .flatMap { group ->
                (0 until group.length).map { index -> Pair(group, index) }
            }
            .firstOrNull()

        selectedSubtitle?.let { (group, index) ->
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                .build()

            // Update subtitle tracks indicator with available languages
            val availableLanguages = subtitleGroups.flatMap { g ->
                (0 until g.length).map { i -> g.getTrackFormat(i).language ?: "und" }
            }.distinct()

            runOnUiThread {
                if (availableLanguages.isNotEmpty()) {
                    tvSubtitleTracks.text = "Subtitles: ${availableLanguages.joinToString(", ")}" 
                    tvSubtitleTracks.visibility = View.VISIBLE
                } else {
                    tvSubtitleTracks.visibility = View.GONE
                }
            }
        }
    }

    /** Shows the channels from the playlist that opened this player. */
    private fun showPlaylistDialog() {
        if (composeDialogView != null) return
        val channels = if (playlistChannels.isEmpty()) {
            listOf(IptvChannel(channelName, channelLogo, streamUrl, channelGroup, tvgId, tvgName))
        } else playlistChannels

        composeDialogView = ComposeView(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                MaterialTheme {
                    PlaylistChannelDialog(
                        channels = channels,
                        selectedUrl = streamUrl,
                        onChannelSelected = { selected ->
                            dismissComposeDialog()
                            if (selected.url != streamUrl) {
                                startActivity(createIntent(
                                    context = this@IptvPlayerActivity,
                                    channelName = selected.name,
                                    streamUrl = selected.url,
                                    channelLogo = selected.logo,
                                    tvgId = selected.tvgId,
                                    tvgName = selected.tvgName,
                                    group = selected.group,
                                    requestHeaders = requestHeaders,
                                    cookie = streamCookie,
                                    mimeType = streamMimeType,
                                    playlistChannels = channels
                                ))
                                finish()
                            }
                        },
                        onDismissRequest = { dismissComposeDialog() }
                    )
                }
            }
        }
        rootLayout.addView(composeDialogView)
    }

    private fun releasePlayer() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    /**
     * Configures the subtitle view styling with:
     * - Semi-transparent black background (50% opacity)
     * - Centered at bottom of video
     * - White text color, normal style, 21sp
     */
    private fun configureSubtitleStyling() {
        playerView.subtitleView?.apply {
            // Optional fractional sizing/padding (kept minimal)
            setFractionalTextSize(0.04f)
            setBottomPaddingFraction(0.02f)

            // Configure custom style: white text, semi-transparent black background
            val semiTransparentBlack = 0x80000000.toInt() // 50% alpha black
            val customStyle = CaptionStyleCompat(
                android.graphics.Color.WHITE,
                semiTransparentBlack,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.BLACK,
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            )
            setStyle(customStyle)

            // Set fixed text size to 21sp
            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 21f)
        }
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

private fun IptvChannel.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("logo", logo)
    put("url", url)
    put("group", group)
    put("tvgId", tvgId)
    put("tvgName", tvgName)
}

// ============================================================================
// COMPOSE — Playlist channel picker
// ============================================================================

@Composable
fun PlaylistChannelDialog(
    channels: List<IptvChannel>,
    selectedUrl: String,
    onChannelSelected: (IptvChannel) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Playlist channels", color = Color.White) },
        text = {
            if (channels.isEmpty()) {
                Text("No channels are available in this playlist.", color = Color.White)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) { _, channel ->
                        val isSelected = channel.url == selectedUrl
                        Card(
                            onClick = { onChannelSelected(channel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF3D1F24) else Color(0xFF242424)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    channel.group?.takeIf { it.isNotBlank() }?.let { group ->
                                        Text(
                                            text = group,
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text("PLAYING", color = Color(0xFFFF5A66), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Close", color = Color(0xFFFF5A66)) }
        },
        containerColor = Color(0xFF1C1B1F)
    )
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

    // Focus requester for first item in track lists
    val firstItemFocusRequester = remember { FocusRequester() }

    // Request focus on first item when dialog opens
    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

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
                    0 -> VideoTrackList(
                        player = player,
                        tracks = currentTracks,
                        onDismiss = onDismissRequest,
                        firstItemFocusRequester = firstItemFocusRequester
                    )
                    1 -> GenericTrackList(
                        player = player,
                        tracks = currentTracks,
                        trackType = C.TRACK_TYPE_AUDIO,
                        onDismiss = onDismissRequest,
                        firstItemFocusRequester = if (selectedTab == 1) firstItemFocusRequester else null
                    )
                    2 -> GenericTrackList(
                        player = player,
                        tracks = currentTracks,
                        trackType = C.TRACK_TYPE_TEXT,
                        onDismiss = onDismissRequest,
                        firstItemFocusRequester = if (selectedTab == 2) firstItemFocusRequester else null
                    )
                }
            }
        },
        containerColor = Color(0xFF2D2D2D)
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoTrackList(
    player: ExoPlayer,
    tracks: Tracks,
    onDismiss: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
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
                },
                focusModifier = if (firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else Modifier
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
fun GenericTrackList(
    player: ExoPlayer,
    tracks: Tracks,
    trackType: Int,
    onDismiss: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
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
                },
                focusModifier = if (firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else Modifier
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
fun TrackSelectionRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusModifier: Modifier = Modifier
) {
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
            .then(focusModifier)
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
