package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
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
                //.setMaxVideoSizeSd()
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
            
            // Enabling the listener forces the Fullscreen toggle button to appear
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
        
        val rootLayout = FrameLayout(this).apply {
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

    private fun showTrackOptionsDialog() {
        val options = arrayOf("Audio Tracks", "Subtitles")
        AlertDialog.Builder(this)
            .setTitle("Select Track Type")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAudioTracksDialog()
                    1 -> showTextTracksDialog()
                }
            }
            .show()
    }

    private fun showAudioTracksDialog() {
        player?.let {
            TrackSelectionDialogBuilder(this, "Audio Tracks", it, C.TRACK_TYPE_AUDIO)
                .setShowDisableOption(false)
                .build()
                .show()
        }
    }

    private fun showTextTracksDialog() {
        player?.let {
            TrackSelectionDialogBuilder(this, "Subtitles", it, C.TRACK_TYPE_TEXT)
                .setShowDisableOption(true)
                .build()
                .show()
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
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitConfirmationDialog()
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

