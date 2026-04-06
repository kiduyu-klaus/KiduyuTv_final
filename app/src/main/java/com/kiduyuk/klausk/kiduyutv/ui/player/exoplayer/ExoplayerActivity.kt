package com.kiduyuk.klausk.kiduyutv.ui.player.exoplayer

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView

@UnstableApi
class ExoplayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    companion object {
        private const val TAG = "ExoplayerActivity"
        const val EXTRA_STREAM_URL = "STREAM_URL"
        const val EXTRA_COOKIES    = "COOKIES"
        const val EXTRA_REFERER    = "REFERER"
        const val EXTRA_ORIGIN     = "ORIGIN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ── Programmatic layout ───────────────────────────────────────────────
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        root.addView(playerView)
        setContentView(root)
        // ─────────────────────────────────────────────────────────────────────

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        if (streamUrl.isNullOrBlank()) {
            Log.e(TAG, "No stream URL provided — finishing")
            finish()
            return
        }

        val cookies = intent.getStringExtra(EXTRA_COOKIES)
        val referer = intent.getStringExtra(EXTRA_REFERER) ?: "https://videostr.net/"
        val origin  = intent.getStringExtra(EXTRA_ORIGIN)  ?: "https://videostr.net"

        initPlayer(streamUrl, cookies, referer, origin)
    }

    private fun initPlayer(
        streamUrl: String,
        cookies: String?,
        referer: String,
        origin: String
    ) {
        val headers = mutableMapOf(
            "Referer" to referer,
            "Origin"  to origin
        )
        cookies?.let { headers["Cookie"] = it }

        Log.i(TAG, "[Init] Stream : $streamUrl")
        Log.i(TAG, "[Init] Headers: $headers")

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> Log.i(TAG, "[State] Buffering…")
                        Player.STATE_READY     -> Log.i(TAG, "[State] Ready — playing")
                        Player.STATE_ENDED     -> Log.i(TAG, "[State] Ended")
                        Player.STATE_IDLE      -> Log.i(TAG, "[State] Idle")
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "[Error] ${error.errorCode} — ${error.message}")
                }
            })

            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStart()   { super.onStart();   if (::player.isInitialized) player.play() }
    override fun onStop()    { super.onStop();    if (::player.isInitialized) player.pause() }
    override fun onDestroy() { if (::player.isInitialized) player.release(); super.onDestroy() }
}