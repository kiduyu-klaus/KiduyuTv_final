package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

/**
 * Activity for playing IPTV live streams using ExoPlayer.
 * Supports live TV streaming with proper buffering and error handling.
 */
@OptIn(UnstableApi::class)
class IptvPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "IptvPlayerActivity"
        
        // Intent extras keys
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"
        
        /**
         * Creates an Intent to start the IPTV player activity.
         *
         * @param context Application context
         * @param channelName Name of the channel being played
         * @param streamUrl URL of the stream to play
         * @param channelLogo URL of the channel logo
         * @return Intent to start the activity
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
    private var channelName: String = ""
    private var streamUrl: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get intent extras
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
        
        if (streamUrl.isBlank()) {
            finish()
            return
        }
        
        setupPlayer()
    }
    
    /**
     * Sets up the ExoPlayer with the stream URL.
     */
    private fun setupPlayer() {
        // Create track selector for adaptive streaming
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSizeSd()
                .setForceLowestBitrate(false)
        )
        
        // Create renderers factory
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        
        // Create ExoPlayer with corrected Builder syntax
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        // Create PlayerView
        playerView = PlayerView(this).apply {
            player = this@IptvPlayerActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Create root layout
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(playerView)
        }
        
        setContentView(rootLayout)
        
        // Prepare and play
        player?.apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            setMediaItem(mediaItem)
            playWhenReady = true
            addListener(playerListener)
            prepare()
        }
    }
    
    /**
     * Player listener for handling playback events.
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    // Show buffering indicator if needed
                }
                Player.STATE_READY -> {
                    // Player is ready to play
                }
                Player.STATE_ENDED -> {
                    // Live stream ended (rare for live TV)
                }
                Player.STATE_IDLE -> {
                    // Player is idle
                }
            }
        }
        
        override fun onPlayerError(error: PlaybackException) {
            // Handle playback error
            showErrorDialog(error.message ?: "Playback error occurred")
        }
    }
    
    /**
     * Shows an error dialog when playback fails.
     */
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
    
    /**
     * Shows exit confirmation dialog.
     */
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
