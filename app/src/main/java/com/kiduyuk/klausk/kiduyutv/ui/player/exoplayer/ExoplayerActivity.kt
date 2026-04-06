package com.kiduyuk.klausk.kiduyutv.ui.player.exoplayer

import android.net.Uri
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
import org.json.JSONObject
import java.net.URLDecoder

@UnstableApi
class ExoplayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    companion object {
        private const val TAG = "ExoplayerActivity"
        const val EXTRA_STREAM_URL = "STREAM_URL"

        private val DEFAULT_HEADERS = mapOf(
            "Origin" to "https://cineby.gd",
            "Referer" to "https://cineby.gd/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )
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
            Log.i(TAG, "No stream URL provided — finishing")
            finish()
            return
        }

        initPlayer(streamUrl)
    }

    private fun initPlayer(streamUrl: String) {
        Log.i(TAG, "[Init] Original Stream : $streamUrl")

        // Start with default headers
        val headers = DEFAULT_HEADERS.toMutableMap()
        val headers2 = DEFAULT_HEADERS.toMutableMap()
        var finalUrl = streamUrl

        try {
            val uri = Uri.parse(streamUrl)

            // 1. Extract headers from JSON string in query param
            uri.getQueryParameter("headers")?.let { rawHeaders ->
                try {
                    val jsonString = if (rawHeaders.startsWith("%")) {
                        URLDecoder.decode(rawHeaders, "UTF-8")
                    } else {
                        rawHeaders
                    }

                    val jsonHeaders = JSONObject(jsonString)
                    val keys = jsonHeaders.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (key.lowercase()) {
                            "referer" -> headers["Referer"] = jsonHeaders.getString(key)
                            "origin" -> headers["Origin"] = jsonHeaders.getString(key)
                            "user-agent" -> headers["User-Agent"] = jsonHeaders.getString(key)
                            else -> headers[key] = jsonHeaders.getString(key)
                        }
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "Error parsing headers JSON: ${e.message}")
                }
            }

            // 2. Extract host from query param
            uri.getQueryParameter("host")?.let { rawHost ->
                try {
                    val decodedHost = if (rawHost.startsWith("http")) rawHost else URLDecoder.decode(rawHost, "UTF-8")
                    val hostUri = Uri.parse(decodedHost)
                    val hostValue = hostUri.host ?: decodedHost.removePrefix("https://").removePrefix("http://").substringBefore("/")

                    if (hostValue.isNotEmpty()) {
                        headers["Host"] = hostValue
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "Error parsing host: ${e.message}")
                }
            }

            // 3. The actual media URL is everything before the query parameters
            finalUrl = streamUrl.substringBefore("?")

        } catch (e: Exception) {
            Log.i(TAG, "Error parsing stream URL components: ${e.message}")
        }

        Log.i(TAG, "[Init] Final Stream : $finalUrl")
        Log.i(TAG, "[Init] Headers: $headers")

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers2)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(finalUrl))

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
                    Log.i(TAG, "[Error] ${error.errorCode} — ${error.message}")
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
