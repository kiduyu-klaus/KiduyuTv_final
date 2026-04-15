package com.kiduyuk.klausk.kiduyutv.ui.player.youtube

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * YouTube Player Activity for playing trailers on mobile devices.
 * Uses the official android-youtube-player library for optimal mobile playback.
 * Based on the official sample app implementation.
 */
class YouTubePlayerActivity : AppCompatActivity() {

    private lateinit var youTubePlayer: YouTubePlayer
    private lateinit var youTubePlayerView: YouTubePlayerView
    private lateinit var fullscreenViewContainer: FrameLayout
    private var videoId: String = ""
    private var isFullscreen = false

    companion object {
        private const val TAG = "YouTubePlayer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_player)

        videoId = intent.getStringExtra("VIDEO_ID") ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra("TITLE") ?: "Trailer"

        youTubePlayerView = findViewById(R.id.youtube_player_view)
        fullscreenViewContainer = findViewById(R.id.full_screen_view_container)

        val iFramePlayerOptions = IFramePlayerOptions.Builder(applicationContext)
            .controls(1)
            .fullscreen(1) // enable full screen button
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
                youTubePlayer.loadVideo(videoId, 0f)
                youTubePlayer.play()
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
}