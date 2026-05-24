package com.kiduyuk.klausk.kiduyutv.ui.player.youtube

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * YouTube Player Activity for playing trailers on mobile devices.
 * Uses the official android-youtube-player library for optimal mobile playback.
 * Based on the official sample app implementation.
 *
 * Features:
 * - Loading Lottie animation overlay while video is preparing
 * - 10-second timeout to fallback to YouTube app if video doesn't start
 * - Error handling that redirects to YouTube app
 */
class YouTubePlayerActivity : AppCompatActivity() {

    private lateinit var youTubePlayer: YouTubePlayer
    private lateinit var youTubePlayerView: YouTubePlayerView
    private lateinit var fullscreenViewContainer: FrameLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingText: TextView
    private lateinit var lottieLoading: LottieAnimationView
    private var videoId: String = ""
    private var isFullscreen = false
    private var isVideoStarted = false

    private val handler = Handler(Looper.getMainLooper())
    private val loadingTimeoutRunnable = Runnable {
        if (!isVideoStarted) {
            hideLoading()
            openInYouTubeApp()
        }
    }

    companion object {
        private const val TAG = "YouTubePlayer"
        private const val LOADING_TIMEOUT_MS = 10000L // 10 seconds timeout
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_player)

        videoId = intent.getStringExtra("VIDEO_ID") ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra("TITLE") ?: "Trailer"

        // Initialize views
        youTubePlayerView = findViewById(R.id.youtube_player_view)
        fullscreenViewContainer = findViewById(R.id.full_screen_view_container)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingText = loadingOverlay.findViewById(R.id.loading_text)
        lottieLoading = findViewById(R.id.lottie_loading)

        // Start loading timeout
        startLoadingTimeout()

        val iFramePlayerOptions = IFramePlayerOptions.Builder(applicationContext)
            .controls(1)
            .fullscreen(1)
            //.langPref("en")      // prefer English captions
            //.ccLoadPolicy(1)     // show captions by default
            .build()

        // we need to initialize manually in order to pass IFramePlayerOptions to the player
        youTubePlayerView.enableAutomaticInitialization = false

        youTubePlayerView.addFullscreenListener(object : FullscreenListener {
            override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                isFullscreen = true

                // the video will continue playing in fullscreenView
                youTubePlayerView.visibility = View.GONE
                fullscreenViewContainer.visibility = View.VISIBLE
                fullscreenViewContainer.addView(fullscreenView)
            }

            override fun onExitFullscreen() {
                isFullscreen = false

                // the video will continue playing in the player
                youTubePlayerView.visibility = View.VISIBLE
                fullscreenViewContainer.visibility = View.GONE
                fullscreenViewContainer.removeAllViews()
            }
        })

        youTubePlayerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                this@YouTubePlayerActivity.youTubePlayer = youTubePlayer
                //youTubePlayer.setPlaybackQuality(PlayerConstants.PlaybackQuality.HIGH)
                youTubePlayer.loadVideo(videoId, 0f)
                youTubePlayer.play()
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                //super.onStateChange(youTubePlayer, state)
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> {
                        // Video started playing successfully
                        onVideoStarted()
                    }
                    PlayerConstants.PlayerState.BUFFERING -> {
                        // Video is buffering - update text to show buffering status
                        runOnUiThread {
                            loadingText.text = "Buffering..."
                        }
                    }
                    PlayerConstants.PlayerState.ENDED -> {
                        finish()
                    }
                    else -> {
                        // Other states (UNSTARTED, PAUSED, VIDEO_CUED, UNKNOWN)
                        // Keep the loading overlay visible
                    }
                }
            }

            override fun onError(
                youTubePlayer: YouTubePlayer,
                error: PlayerConstants.PlayerError
            ) {
                hideLoading()
                openInYouTubeApp()
            }
        }, iFramePlayerOptions)

        lifecycle.addObserver(youTubePlayerView)

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    /**
     * Starts the 5-second timeout for loading.
     * If the video hasn't started playing within this time, it will fallback to YouTube app.
     */
    private fun startLoadingTimeout() {
        handler.postDelayed(loadingTimeoutRunnable, LOADING_TIMEOUT_MS)
    }

    /**
     * Called when the video successfully starts playing.
     * Cancels the loading timeout and hides the loading overlay.
     */
    private fun onVideoStarted() {
        isVideoStarted = true
        handler.removeCallbacks(loadingTimeoutRunnable)
        hideLoading()
    }

    /**
     * Hides the loading overlay with a fade-out animation.
     */
    private fun hideLoading() {
        runOnUiThread {
            loadingOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    loadingOverlay.visibility = View.GONE
                    lottieLoading.cancelAnimation()
                }
                .start()
        }
    }

    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Stop Trailer?",
            message = "Are you sure you want to stop the trailer?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { /* dismiss */ },
            onYes = { finish() }
        ).show()
    }

    /**
     * Opens the video in the native YouTube app or browser.
     * Falls back to YouTube app if available, otherwise opens in browser.
     */
    private fun openInYouTubeApp() {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=$videoId")
            )
            startActivity(intent)
            finish() // close your player activity
        } catch (e: Exception) {
            // Optional fallback if no app/browser
            QuitDialog(
                context = this,
                title = "Error",
                message = "Unable to open YouTube.",
                positiveButtonText = "Close",
                negativeButtonText = "",
                lottieAnimRes = R.raw.exit,
                onNo = {},
                onYes = { finish() }
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks to prevent memory leaks
        handler.removeCallbacks(loadingTimeoutRunnable)
    }
}