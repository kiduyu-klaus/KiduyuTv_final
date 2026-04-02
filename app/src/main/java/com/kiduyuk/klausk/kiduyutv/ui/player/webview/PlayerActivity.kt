package com.kiduyuk.klausk.kiduyutv.ui.player.webview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.AdvancedAdBlocker
import java.io.ByteArrayInputStream

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cursorView: MouseCursorView
    private var cursorX = 0f
    private var cursorY = 0f
    private val moveSpeed = 50f
    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        private const val TAG = "VideasyPlayer"
    }

    // JavaScript interface class to receive messages from WebView (supports Videasy, VidKing, and VidLink)
    @Suppress("UNUSED")
    inner class VideasyJavaScriptInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            Log.i(TAG, "PostMessage received: $message")
            try {
                // Parse the JSON message from player
                val json = org.json.JSONObject(message)

                // Check if this is VidKing/VidLink format (nested structure with PLAYER_EVENT type)
                if (json.has("type") && json.getString("type") == "PLAYER_EVENT" && json.has("data")) {
                    val data = json.getJSONObject("data")
                    // Check for mtmdbId (VidLink) or id (VidKing)
                    val id = if (data.has("mtmdbId")) {
                        data.get("mtmdbId").toString()
                    } else {
                        data.optString("id", "unknown")
                    }
                    val mediaType = data.optString("mediaType", "unknown")
                    val progress = data.optDouble("progress", 0.0)
                    val currentTime = data.optDouble("currentTime", 0.0)
                    val duration = data.optDouble("duration", 0.0)
                    val season = data.optInt("season", 0)
                    val episode = data.optInt("episode", 0)
                    val event = data.optString("event", "unknown")

                    // Determine which player sent this (based on field names)
                    val playerType = if (data.has("mtmdbId")) "VidLink" else "VidKing"

                    Log.i(TAG, String.format(
                        "[%s] Progress update: id=%s, type=%s, event=%s, progress=%.1f%%, timestamp=%.1fs, duration=%.1fs, season=%d, episode=%d",
                        playerType, id, mediaType, event, progress, currentTime, duration, season, episode
                    ))
                } else {
                    // Videasy format (direct fields)
                    val id = json.optString("id", json.optString("contentId", json.optString("movieId", json.optString("tvId", "unknown"))))
                    val type = json.optString("type", json.optString("contentType", json.optString("playerType", "unknown")))
                    val progress = json.optDouble("progress", json.optDouble("percent", 0.0))
                    val timestamp = json.optDouble("timestamp", json.optDouble("currentTime", json.optDouble("time", 0.0)))
                    val duration = json.optDouble("duration", json.optDouble("totalDuration", json.optDouble("totalTime", 0.0)))
                    val season = json.optInt("season", json.optInt("seasonNumber", 0))
                    val episode = json.optInt("episode", json.optInt("episodeNumber", 0))

                    Log.i(TAG, String.format(
                        "[Videasy] Progress update: id=%s, type=%s, progress=%.1f%%, timestamp=%.1fs, duration=%.1fs, season=%d, episode=%d",
                        id, type, progress, timestamp, duration, season, episode
                    ))
                }

                // You can save progress to local storage or backend here
                runOnUiThread {
                    // Update UI or save progress as needed
                    // For example, save to SharedPreferences or Room database
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }
    }

    private val cursorHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        cursorView.animate().alpha(0f).setDuration(500).start()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AdvancedAdBlocker.init(this)

        val tmdbId = intent.getIntExtra("TMDB_ID", -1)
        val isTv = intent.getBooleanExtra("IS_TV", false)
        val seasonNumber = intent.getIntExtra("SEASON_NUMBER", 1)
        val episodeNumber = intent.getIntExtra("EPISODE_NUMBER", 1)
        val title = intent.getStringExtra("TITLE") ?: "Unknown"

        if (tmdbId == -1) {
            finish()
            return
        }

        val repository = TmdbRepository()
        repository.saveToWatchHistory(
            this,
            WatchHistoryItem(
                id = tmdbId,
                title = title,
                overview = intent.getStringExtra("OVERVIEW"),
                posterPath = intent.getStringExtra("POSTER_PATH"),
                backdropPath = intent.getStringExtra("BACKDROP_PATH"),
                voteAverage = intent.getDoubleExtra("VOTE_AVERAGE", 0.0),
                releaseDate = intent.getStringExtra("RELEASE_DATE"),
                isTv = isTv,
                seasonNumber = if (isTv) seasonNumber else null,
                episodeNumber = if (isTv) episodeNumber else null
            )
        )

        val url = intent.getStringExtra("STREAM_URL") ?: if (isTv) {
            "https://vidlink.pro/tv/$tmdbId/$seasonNumber/$episodeNumber?autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?autoplay=true"
        }

        // Check if this is a player URL that supports progress tracking (Videasy, VidKing, or VidLink)
        val isVideasyPlayer = url.startsWith("https://player.videasy.net")
        val isVidKingPlayer = url.startsWith("https://www.vidking.net") || url.startsWith("https://vidking.")
        val isVidLinkPlayer = url.startsWith("https://vidlink.pro")
        val isTrackingEnabled = isVideasyPlayer || isVidKingPlayer || isVidLinkPlayer

        // Setup Layout
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }

            // Add JavaScript interface for receiving postMessage from players that support progress tracking
            if (isTrackingEnabled) {
                addJavascriptInterface(VideasyJavaScriptInterface(), "VideasyInterface")
            }



            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url.toString()
                    if (AdvancedAdBlocker.shouldBlock(url)) {
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    if (!request!!.hasGesture()) return true
                    if (AdvancedAdBlocker.shouldBlock(url)) return true
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val advancedJs = """
                        (function() {
                            function removeAdsAdvanced() {
                                const elements = document.querySelectorAll('*');
                                elements.forEach(el => {
                                    const text = (el.innerText || '').toLowerCase();
                                    const cls = (el.className || '').toString().toLowerCase();
                                    if (
                                        text.includes('advert') ||
                                        text.includes('sponsored') ||
                                        cls.includes('ad') ||
                                        cls.includes('popup')
                                    ) {
                                        el.remove();
                                    }
                                });
                            }
                            function blockRedirects() {
                                window.open = () => null;
                                window.location.assign = () => {};
                                window.location.replace = () => {};
                            }
                            function setMaxVolume() {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    videos[i].volume = 1.0;
                                    videos[i].muted = false;
                                }
                            }
                            
                            ${if (isTrackingEnabled) """
                            // Listen for postMessage from player (Videasy/VidKing/VidLink)
                            function setupMessageListener() {
                                // Also add console logging for debugging
                                console.log('Player message listener initialized');
                                
                                window.addEventListener('message', function(event) {
                                    console.log('Message event received:', typeof event.data === 'string' ? event.data : JSON.stringify(event.data));
                                    try {
                                        // Forward ALL messages to Android JavaScript interface
                                        if (window.VideasyInterface) {
                                            if (typeof event.data === 'string') {
                                                window.VideasyInterface.postMessage(event.data);
                                            } else {
                                                window.VideasyInterface.postMessage(JSON.stringify(event.data));
                                            }
                                        }
                                    } catch (e) {
                                        console.log('Error processing message: ' + e);
                                    }
                                });
                                
                                // Override window.postMessage to capture messages sent from within the page
                                (function() {
                                    var originalPostMessage = window.postMessage;
                                    window.postMessage = function(message, targetOrigin, transfer) {
                                        console.log('postMessage intercepted:', typeof message === 'string' ? message : JSON.stringify(message));
                                        try {
                                            if (window.VideasyInterface) {
                                                if (typeof message === 'string') {
                                                    window.VideasyInterface.postMessage(message);
                                                } else {
                                                    window.VideasyInterface.postMessage(JSON.stringify(message));
                                                }
                                            }
                                        } catch (e) {
                                            console.log('Error capturing postMessage: ' + e);
                                        }
                                        return originalPostMessage.apply(this, arguments);
                                    };
                                })();
                                
                                // Monitor for video element events
                                function monitorVideoEvents() {
                                    var videos = document.getElementsByTagName('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        (function(video) {
                                            video.addEventListener('timeupdate', function() {
                                                console.log('Video timeupdate:', video.currentTime, '/', video.duration);
                                                if (window.VideasyInterface) {
                                                    var data = {
                                                        type: 'PLAYER_EVENT',
                                                        data: {
                                                            event: 'timeupdate',
                                                            currentTime: video.currentTime,
                                                            duration: video.duration,
                                                            progress: (video.duration > 0) ? (video.currentTime / video.duration * 100) : 0,
                                                            id: '',
                                                            mediaType: 'unknown',
                                                            season: 0,
                                                            episode: 0,
                                                            timestamp: Date.now()
                                                        }
                                                    };
                                                    window.VideasyInterface.postMessage(JSON.stringify(data));
                                                }
                                            });
                                            video.addEventListener('loadedmetadata', function() {
                                                console.log('Video loaded:', video.duration);
                                            });
                                        })(videos[i]);
                                    }
                                }
                                
                                // Check periodically for new video elements
                                monitorVideoEvents();
                                setInterval(monitorVideoEvents, 3000);
                            }
                            """ else """
                            // No message listener needed for non-tracking players
                            function setupMessageListener() {
                                console.log('Non-tracking player, skipping message listener');
                            }
                            """}
                            
                            blockRedirects();
                            removeAdsAdvanced();
                            setMaxVolume();
                            setupMessageListener();
                            setInterval(function() {
                                setMaxVolume();
                            }, 3000);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(AdvancedAdBlocker.getCss(), null)
                    view?.evaluateJavascript(advancedJs, null)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false
            }

            loadUrl(url)
        }

        // Initialize Cursor
        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(webView)
        rootLayout.addView(cursorView)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )

        setContentView(rootLayout)
        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        // Set initial cursor position to center
        rootLayout.post {
            screenWidth = rootLayout.width
            screenHeight = rootLayout.height
            cursorX = screenWidth / 2f
            cursorY = screenHeight / 2f
            updateCursorPosition()
            showCursorAndResetTimer() // ADD
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@PlayerActivity)
                    .setTitle("Exit Player")
                    .setMessage("Stop watching?")
                    .setPositiveButton("Yes") { _, _ -> finish() }
                    .setNegativeButton("No", null)
                    .show()
            }
        })
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                showCursorAndResetTimer() // ADD
                cursorY = (cursorY - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showCursorAndResetTimer() // ADD
                cursorY = (cursorY + moveSpeed).coerceAtMost(screenHeight.toFloat())
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                showCursorAndResetTimer() // ADD
                cursorX = (cursorX - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showCursorAndResetTimer() // ADD
                cursorX = (cursorX + moveSpeed).coerceAtMost(screenWidth.toFloat())
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showCursorAndResetTimer() // ADD
                simulateClick(cursorX, cursorY)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun updateCursorPosition() {
        cursorView.x = cursorX          // tip is at x
        cursorView.y = cursorY          // tip is at y
        cursorView.invalidate()         // force redraw
    }

    private fun simulateClick(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_DOWN, x, y, 0
        )
        val upEvent = MotionEvent.obtain(
            downTime, eventTime + 100,
            MotionEvent.ACTION_UP, x, y, 0
        )

        // Set source as touchscreen so WebView treats it as a real tap
        downEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN

        // Dispatch through the window instead of directly to WebView
        window.decorView.dispatchTouchEvent(downEvent)
        window.decorView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> return onKeyDown(event.keyCode, event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showCursorAndResetTimer() {
        cursorView.animate().cancel()
        cursorView.alpha = 1f
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        webView?.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        //webView = null
        super.onDestroy()
    }
}
